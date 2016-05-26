package nl.pojoquery;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import nl.pojoquery.annotations.Embedded;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.NoUpdate;
import nl.pojoquery.annotations.Other;
import nl.pojoquery.internal.MappingException;
import nl.pojoquery.internal.TableMapping;
import nl.pojoquery.pipeline.QueryBuilder;
import nl.pojoquery.pipeline.SqlQuery;
import nl.pojoquery.pipeline.SqlQuery.JoinType;
import nl.pojoquery.pipeline.SqlQuery.SqlField;
import nl.pojoquery.pipeline.SqlQuery.SqlJoin;

public class PojoQuery<T> {
	private final QueryBuilder<T> queryBuilder; 
	private final SqlQuery query;
	private Class<T> resultClass;

	private PojoQuery(Class<T> clz) {
		this.resultClass = clz;
		this.queryBuilder = QueryBuilder.from(clz);
		this.query = queryBuilder.getQuery();
	}
	

	public static <T> PojoQuery<T> build(Class<T> clz) {
		return new PojoQuery<T>(clz);
	}
	
	public SqlQuery getQuery() {
		return query;
	}

	public List<SqlField> getFields() {
		return query.getFields();
	}

	public void setFields(List<SqlField> fields) {
		query.setFields(fields);
	}

	public List<SqlJoin> getJoins() {
		return query.getJoins();
	}

	public PojoQuery<T> setJoins(List<SqlJoin> joins) {
		query.setJoins(joins);
		return this;
	}

	public List<SqlExpression> getWheres() {
		return query.getWheres();
	}

	public PojoQuery<T> setWheres(List<SqlExpression> wheres) {
		query.setWheres(wheres);
		return this;
	}

	public List<String> getGroupBy() {
		return query.getGroupBy();
	}

	public PojoQuery<T> setGroupBy(List<String> groupBy) {
		query.setGroupBy(groupBy);
		return this;
	}

	public List<String> getOrderBy() {
		return query.getOrderBy();
	}

	public PojoQuery<T> setOrderBy(List<String> orderBy) {
		query.setOrderBy(orderBy);
		return this;
	}

	public PojoQuery<T> addField(SqlExpression expression) {
		query.addField(expression);
		return this;
	}

	public PojoQuery<T> addField(SqlExpression expression, String alias) {
		query.addField(expression, alias);
		return this;
	}

	public PojoQuery<T> addGroupBy(String group) {
		query.addGroupBy(group);
		return this;
	}

	public PojoQuery<T> addWhere(SqlExpression where) {
		query.addWhere(where);
		return this;
	}

	public PojoQuery<T> addWhere(String sql, Object... params) {
		query.addWhere(sql, params);
		return this;
	}

	public PojoQuery<T> addOrderBy(String order) {
		query.addOrderBy(order);
		return this;
	}

	public PojoQuery<T> setLimit(int rowCount) {
		query.setLimit(rowCount);
		return this;
	}

	public PojoQuery<T> setLimit(int offset, int rowCount) {
		query.setLimit(offset, rowCount);
		return this;
	}

	public SqlExpression toStatement() {
		return query.toStatement();
	}

	public PojoQuery<T> addJoin(JoinType type, String tableName, String alias, SqlExpression joinCondition) {
		query.addJoin(type, tableName, alias, joinCondition);
		return this;
	}

	public String getTable() {
		return query.getTable();
	}

	public List<T> execute(DataSource db) {
		return queryBuilder.processRows(DB.queryRows(db, query.toStatement()));
	}

	public List<T> execute(Connection connection) {
		return queryBuilder.processRows(DB.queryRows(connection, query.toStatement()));
	}

	public static <PK> PK insert(Connection conn, Class<?> type, Object o) {
		return insert(conn, null, type, o);
	}

	public static <PK> PK insert(DataSource db, Class<?> type, Object o) {
		return insert(null, db, type, o);
	}

	public static <PK> PK insert(DataSource db, Object o) {
		return insert(db, o.getClass(), o);
	}

	public static <PK> PK insert(Connection connection, Object o) {
		return insert(connection, o.getClass(), o);
	}

	private static <PK> PK insert(Connection conn, DataSource db, Class<?> type, Object o) {
		// If the class hierarchy contains multiple tables, create separate
		// inserts
		List<TableMapping> tables = QueryBuilder.determineTableMapping(type);
		if (tables.size() == 0) {
			throw new MappingException("Missing @Table annotation on class " + type.getName() + " or any of its superclasses");
		}

		if (tables.size() == 1) {
			PK ids;
			if (conn != null) {
				ids = DB.insert(conn, tables.get(0).tableName, extractValues(type, o));
			} else {
				ids = DB.insert(db, tables.get(0).tableName, extractValues(type, o));
			}
			if (ids != null) {
				applyGeneratedId(o, ids);
			}
			return ids;
		} else {
			TableMapping topType = tables.remove(0);
			Map<String, Object> values = extractValues(topType.clazz, o);
			PK ids;
			if (conn != null) {
				ids = DB.insert(conn, topType.tableName, values);
			} else {
				ids = DB.insert(db, topType.tableName, values);
			}
			if (ids != null) {
				applyGeneratedId(o, ids);
			}
			List<Field> idFields = QueryBuilder.determineIdFields(type);
			if (idFields.size() != 1) {
				throw new MappingException("Need single ID field annotated with @Id for inserting joined subclasses");
			}
			String idField = idFields.get(0).getName();

			while (tables.size() > 0) {
				TableMapping supertype = tables.remove(0);
				Map<String, Object> subvals = extractValues(tables.size() > 0 ? supertype.clazz : type, o, topType.clazz);
				subvals.put(idField, ids);
				if (conn != null) {
					DB.insert(conn, supertype.tableName, subvals);
				} else {
					DB.insert(db, supertype.tableName, subvals);
				}
				topType = supertype;
			}
			return ids;
		}

	}

	public static int update(DataSource db, Object object) {
		return update(null, db, object.getClass(), object);
	}

	public static int update(Connection connection, Object object) {
		return update(connection, null, object.getClass(), object);
	}

	public static int update(DataSource db, Class<?> type, Object object) {
		return update(null, db, type, object);
	}

	public static int update(Connection connection, Class<?> type, Object object) {
		return update(connection, null, type, object);
	}

	private static int update(Connection conn, DataSource db, Class<?> type, Object o) {
		// If the class hierarchy contains multiple tables, create separate
		// inserts
		List<TableMapping> tables = QueryBuilder.determineTableMapping(type);

		if (tables.size() == 1) {
			Map<String, Object> values = extractValues(type, o);
			Map<String, Object> ids = splitIdFields(o, values);

			if (conn != null) {
				return DB.update(conn, tables.get(0).tableName, values, ids);
			} else {
				return DB.update(db, tables.get(0).tableName, values, ids);
			}
		} else {

			int affectedRows = 0;

			TableMapping topType = tables.remove(0);
			Map<String, Object> values = extractValues(topType.clazz, o);
			Map<String, Object> ids = splitIdFields(o, values);

			if (conn != null) {
				affectedRows = DB.update(conn, topType.tableName, values, ids);
			} else {
				affectedRows = DB.update(db, topType.tableName, values, ids);
			}

			while (tables.size() > 0) {
				TableMapping supertype = tables.remove(0);
				Map<String, Object> subvals = extractValues(tables.size() > 0 ? supertype.clazz : type, o, topType.clazz);
				if (conn != null) {
					DB.update(conn, supertype.tableName, subvals, ids);
				} else {
					DB.update(db, supertype.tableName, subvals, ids);
				}
				topType = supertype;
			}
			return affectedRows;
		}

	}

	private static Map<String, Object> extractValues(Class<?> type, Object o) {
		return extractValues(type, o, null);
	}

	private static Map<String, Object> extractValues(Class<?> type, Object o, Class<?> stopAtSuperclass) {
		try {
			Map<String, Object> values = new HashMap<String, Object>();
			for (Field f : QueryBuilder.collectFieldsOfClass(type, stopAtSuperclass)) {
				f.setAccessible(true);
				
				Other otherAnn = f.getAnnotation(Other.class);
				if (otherAnn != null) {
					@SuppressWarnings("unchecked")
					Map<String,Object> otherMap = (Map<String, Object>) f.get(o);
					if (otherMap != null) {
						for(String fieldName : otherMap.keySet()) {
							values.put(otherAnn.prefix() + fieldName, otherMap.get(fieldName));
						}
					}
					continue;
				}

				Object val = f.get(o);
				if (f.getAnnotation(Embedded.class) != null) {
					if (val != null) {
						Map<String, Object> embeddedVals = extractValues(f.getType(), val);
						String prefix = QueryBuilder.determinePrefix(f);
						for (String embeddedField : embeddedVals.keySet()) {
							values.put(prefix + embeddedField, embeddedVals.get(embeddedField));
						}
					}
				} else if (f.getAnnotation(NoUpdate.class) != null) {
				} else if (f.getAnnotation(Link.class) != null) {
				} else if (f.getType().isArray()) {
					if (f.getType().getComponentType().isPrimitive()) {
						// Data like byte[] long[]
						values.put(QueryBuilder.determineSqlFieldName(f), val);
					}
				} else if (Collection.class.isAssignableFrom(f.getType())) {
				} else if (QueryBuilder.isLinkedClass(f.getType())) {
					// Linked entity.
					if (val == null) {
						values.put(f.getName() + "_id", null);
					} else {
						Field idField = QueryBuilder.determineIdField(f.getType());
						idField.setAccessible(true);
						Object idValue = idField.get(val);
						values.put(f.getName() + "_id", idValue);
					}
                } else {
                	values.put(QueryBuilder.determineSqlFieldName(f), val);
                }
			}
			return values;
		} catch (IllegalArgumentException e) {
			throw new MappingException(e);
		} catch (IllegalAccessException e) {
			throw new MappingException(e);
		}
	}

	private static <PK> void applyGeneratedId(Object o, PK ids) {
		List<Field> idFields = QueryBuilder.determineIdFields(o.getClass());
		if (ids != null && idFields.size() == 1) {
			Field idField = idFields.get(0);
			idField.setAccessible(true);
			try {
				idField.set(o, ids);
			} catch (IllegalArgumentException e) {
				throw new MappingException("Could not set Id field value " + idField, e);
			} catch (IllegalAccessException e) {
				throw new MappingException("Could not set Id field value " + idField, e);
			}
		}
	}
	
	private static Map<String, Object> splitIdFields(Object object, Map<String, Object> values) {
		List<Field> idFields = QueryBuilder.determineIdFields(object.getClass());
		if (idFields.size() == 0) {
			throw new RuntimeException("No @Id annotations found on fields of class " + object.getClass().getName());
		}
		Map<String, Object> ids = new HashMap<String, Object>();
		for (Field f : idFields) {
			ids.put(f.getName(), values.get(f.getName()));
			values.remove(f.getName());
		}
		return ids;
	}
	
	public String toSql() {
		return query.toStatement().getSql();
	}

	public T findById(Connection connection, Object id) {
		query.getWheres().addAll(QueryBuilder.buildIdCondition(resultClass, id));
		return returnSingleRow(execute(connection));
	}

	public T findById(DataSource db, Object id) {
		query.getWheres().addAll(QueryBuilder.buildIdCondition(resultClass, id));
		return returnSingleRow(execute(db));
	}

	public static void delete(Connection conn, Object entity) {
		delete(conn, null, entity);
	}
	
	public static void delete(DataSource db, Object entity) {
		delete(null, db, entity);
	}
	
	private static void delete(Connection conn, DataSource db, Object entity) {
		try {
			List<TableMapping> mapping = QueryBuilder.determineTableMapping(entity.getClass());
			List<Field> idFields = QueryBuilder.determineIdFields(entity.getClass());
			Collections.reverse(mapping);
			for (TableMapping table : mapping) {
				List<SqlExpression> whereCondition = new ArrayList<>();
				for (Field field : idFields) {
					field.setAccessible(true);
						Object idvalue;
						idvalue = field.get(entity);
					if (idvalue == null) {
						throw new MappingException("Cannot create wherecondition for entity with null value in idfield " + field.getName());
					}
					whereCondition.add(new SqlExpression("`" + table.tableName + "`." + field.getName() + "=?", Arrays.asList(idvalue)));
				}
				if (db != null) {
					executeDelete(null, db, table.tableName, whereCondition);
				} else {
					executeDelete(conn, null, table.tableName, whereCondition);
				}
			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new MappingException(e);
		}
	}
	
	public static void deleteById(DataSource db, Class<?> clz, Object id) {
		for (TableMapping table : QueryBuilder.determineTableMapping(clz)) {
			List<SqlExpression> wheres = QueryBuilder.buildIdCondition(table.clazz, id);
			executeDelete(null, db, table.tableName, wheres);
		}
	}

	private static void executeDelete(Connection conn, DataSource db, String tableName, List<SqlExpression> where) {
		SqlExpression wheres = SqlExpression.implode(" AND ", where);
		SqlExpression deleteStatement = new SqlExpression("DELETE FROM `" + tableName + "` WHERE " + wheres.getSql(), wheres.getParameters());
		if (db != null) {
			DB.update(db, deleteStatement);
		} else {
			DB.update(conn, deleteStatement);
		}
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
	
	public List<T> processRows(List<Map<String, Object>> rows) {
		return queryBuilder.processRows(rows);
	}

	@SuppressWarnings("unchecked")
	public <PK> List<PK> listIds(DataSource db) {
		List<Field> idFields = QueryBuilder.determineIdFields(resultClass);
		SqlExpression stmt = queryBuilder.buildListIdsStatement(idFields);
		List<Map<String, Object>> rows = DB.queryRows(db, stmt);
		if (idFields.size() > 1) {
			return (List<PK>) rows;
		}
		List<PK> result = new ArrayList<PK>();
		for (Map<String, Object> r : rows) {
			result.add((PK) r.values().iterator().next());
		}
		return result;
	}
	
	public int countTotal(DataSource db) {
		SqlExpression stmt = queryBuilder.buildCountStatement();
		List<Map<String, Object>> rows = DB.queryRows(db, stmt);
		return ((Long) rows.get(0).values().iterator().next()).intValue();
	}

}
