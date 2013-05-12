package nl.pojoquery;

import static nl.pojoquery.util.Strings.implode;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import nl.pojoquery.annotations.Embedded;
import nl.pojoquery.annotations.GroupBy;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Join;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Select;
import nl.pojoquery.annotations.Table;

public class PojoQuery<T> {

	private Class<T> resultClass;
	private String table;
	private List<String> fields = new ArrayList<String>();
	private List<String> joins = new ArrayList<String>();
	private List<SqlExpression> wheres = new ArrayList<SqlExpression>();
	private List<String> groupBy = new ArrayList<String>();
	private List<String> orderBy = new ArrayList<String>();
	private List<Object> parameters = new ArrayList<Object>();
	private int offset = -1;
	private int rowCount = -1;

	public PojoQuery(String table) {
		this.table = table;
	}

	public PojoQuery<T> addField(String expression) {
		fields.add(expression);
		return this;
	}

	public PojoQuery<T> addJoin(String join) {
		joins.add(join);
		return this;
	}

	public PojoQuery<T> addGroupBy(String group) {
		groupBy.add(group);
		return this;
	}

	public PojoQuery<T> addWhere(String where) {
		wheres.add(new SqlExpression(where));
		return this;
	}

	public PojoQuery<T> addWhere(String sql, Object... params) {
		wheres.add(new SqlExpression(sql, Arrays.asList(params)));
		return this;
	}

	public PojoQuery<T> addOrderBy(String order) {
		orderBy.add(order);
		return this;
	}

	public PojoQuery<T> setLimit(int offset, int rowCount) {
		this.offset = offset;
		this.rowCount = rowCount;
		return this;
	}

	public PojoQuery<T> setResultClass(Class<T> resultClass) {
		this.resultClass = resultClass;
		return this;
	}

	public String toSql() {
		parameters.clear();
		String groupByClause = "";
		if (groupBy.size() > 0) {
			groupByClause = "\nGROUP BY " + implode(", ", groupBy);
		}
		String orderByClause = "";
		if (orderBy.size() > 0) {
			orderByClause = "\nORDER BY " + implode(", ", orderBy);
		}
		String whereClause = "";
		if (wheres.size() > 0) {
			List<String> clauses = new ArrayList<String>();
			for (SqlExpression exp : wheres) {
				clauses.add(exp.getSql());
				for (Object o : exp.getParameters()) {
					parameters.add(o);
				}
			}
			whereClause = "\nWHERE " + implode("\n AND ", clauses);
		}
		String limitClause = "";
		if (offset > -1 || rowCount > -1) {
			if (rowCount < 0) {
				// No rowcount
				rowCount = Integer.MAX_VALUE;
			}
			if (offset > -1) {
				limitClause = "\nLIMIT " + offset + "," + rowCount;
			} else {
				limitClause = "\nLIMIT " + rowCount;
			}
		}

		return implode(" ", Arrays.asList("SELECT\n", implode(",\n ", fields), "\nFROM", table, "\n", implode("\n ", joins), whereClause, groupByClause, orderByClause, limitClause));
	}

	public List<T> execute(DataSource db) {
		List<Map<String, Object>> rows = DB.queryRows(db, toSql(), this.parameters.toArray());
		return (List<T>) processRows(rows, resultClass);
	}
	
	public List<T> execute(Connection connection) {
		List<Map<String, Object>> rows = DB.queryRows(connection, toSql(), this.parameters.toArray());
		return (List<T>) processRows(rows, resultClass);
	}

	public static <R> List<R> execute(DataSource db, Class<R> clz, String sql, Object... params) {
		List<Map<String, Object>> rows = DB.queryRows(db, sql, params);
		return (List<R>) processRows(rows, clz);
	}
	
	public T findById(Connection connection, Object id) {
		wheres.addAll(buildIdCondition(resultClass, id));
		return returnSingleRow(execute(connection));
	}

	public T findById(DataSource db, Object id) {
		wheres.addAll(buildIdCondition(resultClass, id));
		return returnSingleRow(execute(db));
	}

	public static int delete(DataSource db, Class<?> clz, Object id) {
		List<SqlExpression> wheres = buildIdCondition(clz, id);
		String tableName = determineTableName(clz);
		
		List<String> whereClauses = new ArrayList<String>();
		List<Object> params = new ArrayList<Object>();
		for(SqlExpression w : wheres) {
			whereClauses.add(w.getSql());
			for (Object o : w.getParameters()) {
				params.add(o);
			}
		}
		
		return DB.update(db, new SqlExpression("DELETE FROM `" + tableName + "` WHERE " + implode(" AND ", whereClauses), Arrays.asList((Object)params)));
	}

	private T returnSingleRow(List<T> resultList) {
		if (resultList.size() == 1) {
			return resultList.get(0);
		}
		if (resultList.size() > 1) {
			throw new RuntimeException("More than one result found in findById on class " + resultClass.getName());
		}
		return null;
	}
	
	private static List<SqlExpression> buildIdCondition(Class<?> resultClass, Object id) {
		List<Field> idFields = determineIdFields(resultClass);
		if (idFields.size() == 0) {
			throw new RuntimeException("No @Id annotations found on fields of class " + resultClass.getName());
		}
		String tableName = determineTableName(resultClass);
		if (idFields.size() == 1) {
			return Arrays.asList(new SqlExpression("`" + tableName + "`." + idFields.get(0).getName() + "=?", Arrays.asList((Object)id)));
		} else {
			if (id instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String,Object> idvalues = (Map<String, Object>) id;
				
				List<SqlExpression> result = new ArrayList<SqlExpression>();
				for(String field : idvalues.keySet()) {
					result.add(new SqlExpression("`" + tableName + "`." + field + "=?", Arrays.asList((Object)idvalues.get(field))));
				}
				return result;
			} else {
				throw new RuntimeException("Multiple @Id annotations on class " + resultClass.getName() + ": expecting a map id.");
			}
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <R> List<R> processRows(List<Map<String, Object>> rows, Class<R> clz) {
		try {
			String table = determineTableName(clz);

			Map<String, Field> classFields = new HashMap<String, Field>();
			Map<String, List<Field>> idFields = new HashMap<String, List<Field>>();
			Map<String, Class<?>> classes = new HashMap<String, Class<?>>();

			Map<String, Field> linkFields = new HashMap<String, Field>();
			Map<Field, String> linkReverse = new HashMap<Field, String>();

			processClass(clz, table, classes, classFields, linkFields, linkReverse, idFields, true);

			List<R> result = new ArrayList<R>(rows.size());

			Map<String, Map<Object, Object>> allEntities = new HashMap<String, Map<Object, Object>>();

			for (Map<String, Object> row : rows) {
				// Collect all entities for this row
				Map<String, Object> onThisRow = new HashMap<String, Object>();

				for (String key : row.keySet()) {
					String tableAlias = table;
					int dotpos = key.lastIndexOf('.');
					if (dotpos > 0) {
						tableAlias = key.substring(0, dotpos);
					}
					if (!onThisRow.containsKey(tableAlias)) {
						Class<?> valClass = classes.get(tableAlias);
						Object instance;
						if (!valClass.isEnum()) {
							Constructor<?> constructor = valClass.getDeclaredConstructor();
							constructor.setAccessible(true);
							instance = constructor.newInstance();
							onThisRow.put(tableAlias, instance);
						} else {
							Object val = row.get(key);
							if (val instanceof String) {
								instance = Enum.valueOf((Class<? extends Enum>)valClass, (String)val);
								onThisRow.put(tableAlias, instance);
							}
							continue;
						}
					}
					Object instance = onThisRow.get(tableAlias);

					Field f = classFields.get(key);
					f.setAccessible(true);
					if (row.get(key) != null || !f.getType().isPrimitive()) {
						Object val = row.get(key);
						if (val instanceof String && f.getType().isEnum()) {
							val = Enum.valueOf((Class<? extends Enum>)f.getType(), (String)val);
						}
						f.set(instance, val);
					}
				}

				for (String tableAlias : onThisRow.keySet()) {
					if (allEntities.get(tableAlias) == null) {
						allEntities.put(tableAlias, new HashMap<Object, Object>());
					}
					Object entity = onThisRow.get(tableAlias);
					if (entity.getClass().isEnum()) {
						continue;
					}
					List<Field> ids = idFields.get(tableAlias);
					Object id;
					boolean isNull = true;
					if (ids.size() == 1) {
						id = ids.get(0).get(entity);
						isNull = id == null;
					} else {
						id = new ArrayList<Object>();
						isNull = true;
						for (Field f : ids) {
							Object val = f.get(entity);
							if (val != null) {
								isNull = false;
							}
							((List<Object>) id).add(val);
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
								result.add((R) entity);
							}
						}
					}
				}

				for (String tableAlias : onThisRow.keySet()) {
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
									linkField.set(parent, coll);
								}
								if (!coll.contains(entity) && entity != null) {
									coll.add(entity);
								}
							} else if (Set.class.isAssignableFrom(linkField.getType())) {
								Set<Object> coll = (Set<Object>) linkField.get(parent);
								if (coll == null) {
									coll = new HashSet<Object>();
									linkField.set(parent, coll);
								}
								if (!coll.contains(entity) && entity != null) {
									coll.add(entity);
								}
							} else if (linkField.getType().isArray()) {
								Object arr = linkField.get(parent);
								if (arr == null) {
									arr = Array.newInstance(linkField.getType().getComponentType(), 0);
								}
								boolean contains = false;
								int arrlen = Array.getLength(arr);
								for (int i = 0; i < arrlen; i++) {
									if (Array.get(arr, i).equals(entity)) {
										contains = true;
										break;
									}
								}
								if (!contains && entity != null) {
									Object extended = Array.newInstance(linkField.getType().getComponentType(), arrlen + 1);
									if (arrlen > 0) {
										System.arraycopy(arr, 0, extended, 0, arrlen);
									}
									Array.set(extended, arrlen, entity);
									arr = extended;
								}
								linkField.set(parent, arr);
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

	public static <T> PojoQuery<T> build(Class<T> clz) {
		if (clz == null) throw new NullPointerException("clz");
		
		String table = determineTableName(clz);
		if (table == null) {
			throw new RuntimeException("Missing @Table annotation on class " + clz.getName() + " or any of its superclasses");
		}

		PojoQuery<T> q = new PojoQuery<T>(table);
		q.setResultClass(clz);

		if (clz.getAnnotation(Join.class) != null) {
			String[] joins = clz.getAnnotation(Join.class).value();
			for (String join : joins) {
				q.addJoin(join);
			}
		}

		if (clz.getAnnotation(GroupBy.class) != null) {
			String[] groupBy = clz.getAnnotation(GroupBy.class).value();
			for (String group : groupBy) {
				q.addGroupBy(group);
			}
		}

		addFields(q, table, clz, true);

		return q;
	}

	public static String determineTableName(Class<?> clz) {
		String ret = null;
		while (clz != null) {
			Table tableAnn = clz.getAnnotation(Table.class);
			if (tableAnn != null) {
				ret = tableAnn.value();
				break;
			}
			clz = clz.getSuperclass();
		}
		return ret;
	}

	private static <T> void addFields(PojoQuery<T> q, String tableAlias, Class<?> clz, boolean isPrincipalAlias) {
		String table = determineTableName(clz);

		for (Field f : collectFieldsOfClass(clz)) {
			if (f.getName().startsWith("this$")) {
				// For inner classes, this$X is the reference to the containing
				// class(es)
				continue;
			}
			if (f.getAnnotation(Link.class) != null) {
				String linktable = f.getAnnotation(Link.class).linktable();
				if (!linktable.equals(Link.NONE)) {
					String foreignvaluefield = f.getAnnotation(Link.class).foreignvaluefield();
					if (!foreignvaluefield.equals(Link.NONE)) {
						// A list of values
						String idfield = tableAlias + ".id";
						String linktableAlias = combinedAlias(tableAlias, linktable, isPrincipalAlias);
						String linkfield = "`" + linktableAlias + "`." + table + "_id";
						
						q.addJoin("LEFT JOIN " + linktable + " `" + linktableAlias + "` ON " + linkfield + "=" + idfield);
						
						q.addField("`" + linktableAlias + "`." + foreignvaluefield + " `" + combinedAlias(tableAlias, f.getName(), isPrincipalAlias) + ".value`");
					} else {
						// many to many
						String idfield = tableAlias + ".id";
						String linktableAlias = combinedAlias(tableAlias, linktable, isPrincipalAlias);
						String linkfield = "`" + linktableAlias + "`." + table + "_id";
						
						q.addJoin("LEFT JOIN " + linktable + " `" + linktableAlias + "` ON " + linkfield + "=" + idfield);
						
						String foreigntable = null;
						for (String t : linktable.split("_")) {
							if (tableAlias.equalsIgnoreCase(t)) {
								continue;
							} else {
								foreigntable = t;
							}
						}
						String foreignlinkfield = "`" + linktableAlias + "`." + foreigntable + "_id";
						String foreignAlias = combinedAlias(linktableAlias, f.getName(), isPrincipalAlias);
						String foreignidfield = "`" + foreignAlias + "`." + "id";
						
						q.addJoin("LEFT JOIN " + foreigntable + " " + foreignAlias + " ON " + foreignlinkfield + "=" + foreignidfield);
						
						addFields(q, foreignAlias, f.getAnnotation(Link.class).resultClass(), false);
					}
					
				} else {
					Class<?> foreignClass = f.getAnnotation(Link.class).resultClass();
					String foreigntable = foreignClass.getAnnotation(Table.class).value();
					String foreignalias = combinedAlias(tableAlias, f.getName(), isPrincipalAlias);
					String foreignlinkfield = "`" + foreignalias + "`." + table + "_id";
					String idfield = "`" + tableAlias + "`.id";
					q.addJoin("LEFT JOIN " + foreigntable + " `" + foreignalias + "` ON " + foreignlinkfield + "=" + idfield);

					addFields(q, foreignalias, foreignClass, false);
				}
				continue;
			}
			if (!f.getType().isPrimitive() && determineTableName(f.getType()) != null) {
				String foreignalias = combinedAlias(tableAlias, f.getName(), isPrincipalAlias);
				addOneToManyLink(q, tableAlias, foreignalias, f.getType(), f.getName());
				addFields(q, foreignalias, f.getType(), false);
				continue;
			}
			if (f.getType().isArray()) {
				String foreignalias = combinedAlias(tableAlias, f.getName(), isPrincipalAlias);
				addManyToOneLink(q, tableAlias, table, foreignalias, f.getType().getComponentType(), f.getName());
				addFields(q, foreignalias, f.getType().getComponentType(), false);
				continue;
			}
			if (f.getAnnotation(Select.class) != null) {
				q.addField(f.getAnnotation(Select.class).value() + " `" + tableAlias + "." + f.getName() + "`");
			} else {
				if (f.getAnnotation(Embedded.class) != null) {
					String prefix = determinePrefix(f);

					String foreignalias = combinedAlias(tableAlias, f.getName(), isPrincipalAlias);
					for(Field embeddedField : collectFieldsOfClass(f.getType())) {
						q.addField("`" + tableAlias + "`." + prefix + embeddedField.getName() + " `" + foreignalias + "." + embeddedField.getName() + "`");
					}
					continue;
				}

				q.addField("`" + tableAlias + "`." + f.getName() + " `" + tableAlias + "." + f.getName() + "`");
			}
		}
	}

	private static String determinePrefix(Field f) {
		String prefix = f.getAnnotation(Embedded.class).prefix();
		if (prefix.equals(Embedded.DEFAULT)) {
			prefix = f.getName();
		}
		if (!prefix.isEmpty()) {
			prefix = prefix + "_";
		}
		return prefix;
	}

	private static <T> void addManyToOneLink(PojoQuery<T> q, String tableAlias, String localTable, String foreignalias, Class<?> linkedType, String fieldName) {
		String foreigntable = determineTableName(linkedType);
		String linkfield = "`" + foreignalias + "`." + localTable + "_id";
		String idfield = "`" + tableAlias + "`.id";
		q.addJoin("LEFT JOIN " + foreigntable + " `" + foreignalias + "` ON " + linkfield + "=" + idfield);
	}

	private static <T> void addOneToManyLink(PojoQuery<T> q, String tableAlias, String foreignalias, Class<?> linkedType, String fieldName) {
		String foreigntable = determineTableName(linkedType);
		String linkfield = "`" + tableAlias + "`." + fieldName.toLowerCase() + "_id";
		String idfield = "`" + foreignalias + "`.id";
		q.addJoin("LEFT JOIN " + foreigntable + " `" + foreignalias + "` ON " + linkfield + "=" + idfield);
	}

	private static String combinedAlias(String tableAlias, String fieldName, boolean isPrincipalAlias) {
		if (!isPrincipalAlias) {
			return tableAlias + "." + fieldName;
		}
		return fieldName;
	}

	private static void processClass(Class<?> clz, String tableAlias, Map<String, Class<?>> classes, Map<String, Field> classFields, Map<String, Field> linkFields, Map<Field, String> linkReverse, Map<String, List<Field>> idFields, boolean isPrincipalAlias) {
		classes.put(tableAlias, clz);

		List<Field> nonLinkFields = new ArrayList<Field>();

		for (Field f : collectFieldsOfClass(clz)) {
			classFields.put(tableAlias + "." + f.getName(), f);
			if (f.getAnnotation(Id.class) != null) {
				if (idFields.get(tableAlias) == null) {
					idFields.put(tableAlias, new ArrayList<Field>());
				}
				idFields.get(tableAlias).add(f);
			}
			if (f.getAnnotation(Link.class) != null) {
				Class<?> linkedClass;
				if (f.getType().isArray()) {
					linkedClass = f.getType().getComponentType();
				} else {
					linkedClass = f.getAnnotation(Link.class).resultClass();
				}
				String linkedAlias = combinedAlias(tableAlias, f.getName(), isPrincipalAlias);
				classes.put(linkedAlias, linkedClass);
				linkFields.put(linkedAlias, f);
				linkReverse.put(f, tableAlias);
					
				if (f.getAnnotation(Link.class).foreignvaluefield().equals(Link.NONE)) {
					processClass(linkedClass, linkedAlias, classes, classFields, linkFields, linkReverse, idFields, false);
				}
			} else if (!f.getType().isPrimitive() && (f.getType().getAnnotation(Table.class) != null || f.getAnnotation(Embedded.class) != null)) {
				Class<?> linkedClass = f.getType();
				String linkedAlias = combinedAlias(tableAlias, f.getName(), isPrincipalAlias);
				classes.put(linkedAlias, linkedClass);

				linkFields.put(linkedAlias, f);
				linkReverse.put(f, tableAlias);

				processClass(linkedClass, linkedAlias, classes, classFields, linkFields, linkReverse, idFields, false);
			} else if (f.getType().isArray()) {
				Class<?> linkedClass = f.getType().getComponentType();
				String linkedAlias = combinedAlias(tableAlias, f.getName(), isPrincipalAlias);
				classes.put(linkedAlias, linkedClass);

				linkFields.put(linkedAlias, f);
				linkReverse.put(f, tableAlias);

				processClass(linkedClass, linkedAlias, classes, classFields, linkFields, linkReverse, idFields, false);
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
		while (clz != null) {
			result.addAll(0, filterFields(clz));
			clz = clz.getSuperclass();
		}
		return result;
	}

	private static Collection<Field> filterFields(Class<?> clz) {
		List<Field> result = new ArrayList<Field>();
		for(Field f : clz.getDeclaredFields()) {
			if ((f.getModifiers() & Modifier.STATIC) > 0) {
				continue;
			}
			result.add(f);
		}
		return result;
	}

	public static <PK> PK insert(DataSource db, Object o) {
		PK ids = DB.insert(db, determineTableName(o.getClass()), extractValues(o));
		if (ids != null) {
			applyGeneratedId(o, ids);
		}
		return ids;
	}

	public static <PK> PK insert(Connection connection, Object o) {
		PK ids = DB.insert(connection, determineTableName(o.getClass()), extractValues(o));
		if (ids != null) {
			applyGeneratedId(o, ids);
		}
		return ids;
	}
	
	public static int update(DataSource db, Object object) {
		Map<String, Object> values = extractValues(object);
		Map<String, Object> ids = splitIdFields(object, values);
		return DB.update(db, determineTableName(object.getClass()), values, ids);
	}

	public static int update(Connection connection, Object object) {
		Map<String, Object> values = extractValues(object);
		Map<String, Object> ids = splitIdFields(object, values);
		return DB.update(connection, determineTableName(object.getClass()), values, ids);
	}
	
	public static Long insertOrUpdate(DataSource db, Object o) {
		return DB.insertOrUpdate(db, determineTableName(o.getClass()), extractValues(o));
	}
	
	public static Long insertOrUpdate(Connection connection, Object o) {
		return DB.insertOrUpdate(connection, determineTableName(o.getClass()), extractValues(o));
	}

	private static Map<String, Object> extractValues(Object o) {
		try {
			Map<String, Object> values = new HashMap<String, Object>();
			for (Field f : collectFieldsOfClass(o.getClass())) {
				f.setAccessible(true);
				
				Object val = f.get(o);
				if (f.getAnnotation(Embedded.class) != null) {
					if (val != null) {
						Map<String,Object> embeddedVals = extractValues(val);
						String prefix = determinePrefix(f);
						for(String embeddedField : embeddedVals.keySet()) {
							values.put(prefix + embeddedField, embeddedVals.get(embeddedField));
						}
					}
				} else {
					values.put(f.getName(), val);
				}
			}
			return values;
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	private static Map<String, Object> splitIdFields(Object object, Map<String, Object> values) {
		List<Field> idFields = determineIdFields(object.getClass());
		if (idFields.size() == 0) {
			throw new RuntimeException("No @Id annotations found on fields of class " + object.getClass().getName());
		}
		Map<String, Object> ids = new HashMap<String,Object>();
		for(Field f : idFields) {
			ids.put(f.getName(), values.get(f.getName()));
			values.remove(f.getName());
		}
		return ids;
	}
	
	private static final List<Field> determineIdFields(Class<?> clz) {
		Iterable<Field> fields = collectFieldsOfClass(clz);
		ArrayList<Field> result = new ArrayList<Field>();
		for(Field f : fields) {
			if (f.getAnnotation(Id.class) != null) {
				result.add(f);
			}
		}
		return result;
	}

	private static <PK> void applyGeneratedId(Object o, PK ids) {
		List<Field> idFields = determineIdFields(o.getClass());
		try {
			if (ids != null && idFields.size() == 1) {
				Field idField = idFields.get(0);
				idField.setAccessible(true);
				idField.set(o, ids);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
}
