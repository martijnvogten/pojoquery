package system.sql;

import static system.util.Strings.implode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import system.db.DB;
import system.sql.annotations.GroupBy;
import system.sql.annotations.Id;
import system.sql.annotations.Join;
import system.sql.annotations.Link;
import system.sql.annotations.Select;
import system.sql.annotations.Table;


public class Query<T> {

	private Class<T> resultClass; 
	private String table;
	private List<String> fields = new ArrayList<String>();
	private List<String> joins = new ArrayList<String>();
	private List<SqlExpression> wheres = new ArrayList<SqlExpression>();
	private List<String> groupBy = new ArrayList<String>();
	private List<Object> parameters = new ArrayList<Object>();

	public Query(String table) {
		this.table = table;
	}

	public Query<T> addField(String expression) {
		fields.add(expression);
		return this;
	}

	public Query<T> addJoin(String join) {
		joins.add(join);
		return this;
	}
	
	public Query<T> addGroupBy(String group) {
		groupBy.add(group);
		return this;
	}
	
	public Query<T> addWhere(String where) {
		wheres.add(new SqlExpression(where));
		return this;
	}
	
	public Query<T> addWhere(String sql, Object... params) {
		wheres.add(new SqlExpression(sql, Arrays.asList(params)));
		return this;
	}
	
	public Query<T> setResultClass(Class<T> resultClass) {
		this.resultClass = resultClass;
		return this;
	}
	
	public String toSql() {
		parameters.clear();
		String groupByClause = "";
		if (groupBy.size() > 0) {
			groupByClause = "GROUP BY " + implode(", ", groupBy);
		}
		String whereClause = "";
		if (wheres.size() > 0) {
			List<String> clauses = new ArrayList<String>();
			for(SqlExpression exp : wheres) {
				clauses.add(exp.getSql());
				for(Object o : exp.getParameters()) {
					parameters.add(o);
				}
			}
			whereClause = "\nWHERE " + implode("\n AND ", clauses);
		}
		
		return implode(" ", Arrays.asList("SELECT\n", implode(",\n ", fields), "\nFROM", table, "\n", implode("\n ", joins), whereClause, groupByClause));
	}
	
	public List<T> execute(DataSource db) {
		return (List<T>) execute(db, resultClass, toSql(), this.parameters.toArray());
	}

	@SuppressWarnings("unchecked")
	public static <R> List<R> execute(DataSource db, Class<R> clz, String sql, Object... params) {
		try {
			List<Map<String,Object>> rows = DB.queryRows(db, sql, params);
			
			String table = determineTableName(clz);
			
			Map<String,Field> classFields = new HashMap<String,Field>();
			Map<String,List<Field>> idFields = new HashMap<String,List<Field>>();
			Map<String,Class<?>> classes = new HashMap<String,Class<?>>();
			
			Map<String,Field> linkFields = new HashMap<String,Field>();
			Map<Field,String> linkReverse = new HashMap<Field,String>();
			
			processClass(clz, table, classes, classFields, linkFields, linkReverse, idFields);
			
			List<R> result = new ArrayList<R>(rows.size());
			
			Map<String,Map<Object,Object>> allEntities = new HashMap<String,Map<Object,Object>>();
			
			for(Map<String,Object> row : rows) {
				// Collect all entities for this row
				Map<String,Object> onThisRow = new HashMap<String,Object>();
				
				for(String key : row.keySet()) {
					String[] parts = key.split("\\.");
					String tableAlias = table;
					if (parts.length > 1) {
						tableAlias = parts[0];
					}
					
					if (!onThisRow.containsKey(tableAlias)) {
						Object instance = classes.get(tableAlias).newInstance();
						onThisRow.put(tableAlias, instance);
					}
					Object instance = onThisRow.get(tableAlias);
					
					Field f = classFields.get(key);
					f.setAccessible(true);
					if (row.get(key) != null || !f.getType().isPrimitive()) {
						f.set(instance, row.get(key));
					}
				}
				
				for(String tableAlias : onThisRow.keySet()) {
					if (allEntities.get(tableAlias) == null) {
						allEntities.put(tableAlias, new HashMap<Object,Object>());
					}
					Object entity = onThisRow.get(tableAlias);
					List<Field> ids = idFields.get(tableAlias);
					Object id;
					boolean isNull = true;
					if (ids.size() == 1) {
						id = ids.get(0).get(entity);
						isNull = id == null;
					} else {
						id = new ArrayList<Object>();
						isNull = true;
						for(Field f : ids) {
							Object val = f.get(entity);
							if (val != null) {
								isNull = false;
							}
							((List<Object>)id).add(val);
						}
					}
					if (isNull) {
						onThisRow.put(tableAlias, null);
					} else {
						Map<Object, Object> all = allEntities.get(tableAlias);
						if (all.containsKey(id)) {
							onThisRow.put(tableAlias, all.get(id));
						} else {
							all.put(id, entity);
							if (table.equals(tableAlias)) {
								result.add((R)entity);
							}
						}
					}
				}
				
				for(String tableAlias : onThisRow.keySet()) {
					if (!table.equals(tableAlias)) {
						Field linkField = linkFields.get(tableAlias);
						linkField.setAccessible(true);
						Object parent = onThisRow.get(linkReverse.get(linkField));
						if (parent != null) {
							Object entity = onThisRow.get(tableAlias);
							if (List.class.isAssignableFrom(linkField.getType())) {
								List<Object> coll = (List<Object>) linkField.get(parent);
								if (coll == null) {
									coll = new ArrayList<Object>();
								}
								if (!coll.contains(entity) && entity != null) {
									coll.add(entity);
								}
								linkField.set(parent, coll);
							} else {
								linkField.set(parent, entity);
							}
						}
					}
				}
				
			}
			return result;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> Query<T> buildQuery(Class<T> clz) {
		String table = determineTableName(clz);
		
		Query<T> q = new Query<T>(table);
		q.setResultClass(clz);
		
		if (clz.getAnnotation(Join.class) != null) {
			String[] joins = clz.getAnnotation(Join.class).value();
			for(String join : joins) {
				q.addJoin(join);
			}
		}
		
		if (clz.getAnnotation(GroupBy.class) != null) {
			String[] groupBy = clz.getAnnotation(GroupBy.class).value();
			for(String group : groupBy) {
				q.addGroupBy(group);
			}
		}
		
		addFields(q, table, clz);
		
		return q;
	}

	private static String determineTableName(Class<?> clz) {
		Table tableAnn = clz.getAnnotation(Table.class);
		String table;
		if (tableAnn != null) {
			table = tableAnn.value();
		} else {
			table = clz.getSimpleName();
		}
		return table;
	}
	
	private static <T> void addFields(Query<T> q, String tableAlias, Class<?> clz) {
		String table = determineTableName(clz);
		for(Field f : collectFieldsOfClass(clz)) {
			if (f.getAnnotation(Link.class) != null) {
				String linktable = f.getAnnotation(Link.class).linktable();
				if (!linktable.equals(Link.NONE)) {
					// many to many
					String idfield = tableAlias + ".id";
					String linkfield = linktable + "." + table + "_id";
					
					q.addJoin("LEFT JOIN " + linktable + " ON " + linkfield + "=" + idfield);
					
					String foreigntable = null;
					for(String t : linktable.split("_")) {
						if (tableAlias.equalsIgnoreCase(t)) {
							continue;
						} else {
							foreigntable = t;
						}
					}
					String foreignlinkfield = linktable + "." + foreigntable + "_id";
					String foreignAlias = f.getName();
					String foreignidfield = foreignAlias + "." + "id";
					
					q.addJoin("LEFT JOIN " + foreigntable + " " + foreignAlias + " ON " + foreignlinkfield + "=" + foreignidfield);
					
					addFields(q, f.getName(), f.getAnnotation(Link.class).resultClass());
				} else {
					Class<?> foreignClass = f.getAnnotation(Link.class).resultClass();
					String foreigntable = foreignClass.getAnnotation(Table.class).value();
					String foreignalias = f.getName();
					String foreignlinkfield = foreignalias + "." + table + "_id";
					String idfield = tableAlias + ".id";
					q.addJoin("LEFT JOIN " + foreigntable + " " + foreignalias + " ON " + foreignlinkfield + "=" + idfield);
					
					addFields(q, f.getName(), foreignClass);
				}
				continue;
			} 
			if (!f.getType().isPrimitive() && f.getType().getAnnotation(Table.class) != null) {
				String foreigntable = f.getType().getAnnotation(Table.class).value();
				String linkfield = tableAlias + "." + f.getName().toLowerCase() + "_id";
				String idfield = foreigntable + ".id";
				q.addJoin("LEFT JOIN " + foreigntable + " " + f.getName() + " ON " + linkfield + "=" + idfield);
				addFields(q, f.getName(), f.getType());
				continue;
			}
			if (f.getAnnotation(Select.class) != null) {
				q.addField(f.getAnnotation(Select.class).value() + " `" + tableAlias + "." + f.getName() + "`");
			} else {
				q.addField(tableAlias + "." + f.getName() + " `" + tableAlias + "." + f.getName() + "`");
			}
		}
	}
	
	private static void processClass(Class<?> clz, String tableAlias, Map<String,Class<?>> classes, Map<String,Field> classFields, Map<String,Field> linkFields, Map<Field,String> linkReverse, Map<String,List<Field>> idFields) {
		classes.put(tableAlias, clz);

		List<Field> nonLinkFields = new ArrayList<Field>();
		
		for(Field f : collectFieldsOfClass(clz)) {
			classFields.put(tableAlias + "." + f.getName(), f);
			if (f.getAnnotation(Id.class) != null) {
				if (idFields.get(tableAlias) == null) {
					idFields.put(tableAlias, new ArrayList<Field>());
				}
				idFields.get(tableAlias).add(f);
			}
			if (f.getAnnotation(Link.class) != null) {
				Class<?> linkedClass = f.getAnnotation(Link.class).resultClass();
				String linkedAlias = f.getName();
				classes.put(linkedAlias, linkedClass);
				
				linkFields.put(linkedAlias, f);
				linkReverse.put(f, tableAlias);
				
				processClass(linkedClass, linkedAlias, classes, classFields, linkFields, linkReverse, idFields);
			} else if (!f.getType().isPrimitive() && f.getType().getAnnotation(Table.class) != null) {
				Class<?> linkedClass = f.getType();
				String linkedAlias = f.getName();
				classes.put(linkedAlias, linkedClass);
				
				linkFields.put(linkedAlias, f);
				linkReverse.put(f, tableAlias);
				
				processClass(linkedClass, linkedAlias, classes, classFields, linkFields, linkReverse, idFields);
			} else {
				nonLinkFields.add(f);
			}
		}
		
		if (idFields.get(tableAlias) == null) {
			idFields.put(tableAlias, nonLinkFields);
		}
	}

	private static Iterable<Field> collectFieldsOfClass(Class<?> clz) {
		List<Field> result = new ArrayList<Field>();
		while(clz != null) {
			result.addAll(0, Arrays.asList(clz.getDeclaredFields()));
			clz = clz.getSuperclass();
		}
		return result;
	}
	
	public static Long insertOrUpdate(DataSource db, Object o) {
		try {
			Map<String,Object> values = new HashMap<String,Object>();
			for(Field f : collectFieldsOfClass(o.getClass())) {
				f.setAccessible(true);
				values.put(f.getName(), f.get(o));
			}
			return DB.insertOrUpdate(db, determineTableName(o.getClass()), values);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

}
