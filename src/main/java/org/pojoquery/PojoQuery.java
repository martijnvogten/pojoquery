package org.pojoquery;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.NoUpdate;
import org.pojoquery.annotations.Other;
import org.pojoquery.internal.MappingException;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.QueryBuilder;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.SqlQuery.SqlField;
import org.pojoquery.pipeline.SqlQuery.SqlJoin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main entry point for building and executing type-safe SQL queries.
 * 
 * <p>PojoQuery uses POJOs (Plain Old Java Objects) to define query results, automatically
 * generating SQL with proper joins based on field relationships. The result class defines
 * <em>what you want to retrieve</em>, not how the data is stored.</p>
 * 
 * <h2>Basic Usage</h2>
 * <pre>{@code
 * // Define a result class
 * @Table("user")
 * public class User {
 *     @Id Long id;
 *     String name;
 *     String email;
 * }
 * 
 * // Build and execute a query
 * List<User> users = PojoQuery.build(User.class)
 *     .addWhere("{user}.name LIKE ?", "%John%")
 *     .addOrderBy("{user}.name ASC")
 *     .execute(dataSource);
 * }</pre>
 * 
 * <h2>Alias Syntax</h2>
 * <p>Use curly braces to reference table aliases in WHERE, ORDER BY, and other clauses.
 * PojoQuery will automatically quote identifiers appropriately for your database:</p>
 * <pre>{@code
 * .addWhere("{user}.status = ?", "active")
 * .addOrderBy("{user}.created_at DESC")
 * }</pre>
 * 
 * <h2>Relationships</h2>
 * <p>PojoQuery automatically handles relationships through fields referencing other entities:</p>
 * <pre>{@code
 * @Table("order")
 * public class OrderWithCustomer {
 *     @Id Long id;
 *     Customer customer;  // Automatically joins to customer table
 *     BigDecimal total;
 * }
 * }</pre>
 * 
 * <h2>Static CRUD Methods</h2>
 * <p>For simple operations, use the static convenience methods:</p>
 * <ul>
 *   <li>{@link #insert(DataSource, Object)} - Insert a new entity</li>
 *   <li>{@link #update(DataSource, Object)} - Update an existing entity</li>
 *   <li>{@link #delete(DataSource, Object)} - Delete an entity</li>
 * </ul>
 * 
 * <p>For finding by ID, build a query and use the instance method:</p>
 * <pre>{@code
 * User user = PojoQuery.build(User.class).findById(dataSource, 123L);
 * }</pre>
 * 
 * @param <T> the type of the result class
 * @see DbContext
 * @see SqlExpression
 * @see org.pojoquery.annotations.Table
 * @see org.pojoquery.annotations.Id
 */
public class PojoQuery<T> {
	private static final Logger LOG = LoggerFactory.getLogger(PojoQuery.class);
	
	private final QueryBuilder<T> queryBuilder; 
	private final SqlQuery<DefaultSqlQuery> query;
	private Class<T> resultClass;
	private DbContext dbContext;

	private PojoQuery(DbContext context, Class<T> clz) {
		this.dbContext = context;
		this.resultClass = clz;
		this.queryBuilder = QueryBuilder.from(clz);
		this.query = queryBuilder.getQuery();
	}
	

	/**
	 * Builds a PojoQuery instance using the default DbContext.
	 *
	 * @param clz the class type of the result
	 * @param <T> the type of the result
	 * @return a new PojoQuery instance
	 */
	public static <T> PojoQuery<T> build(Class<T> clz) {
		Objects.requireNonNull(clz, "class must not be null");
		return build(DbContext.getDefault(), clz);
	}

	/**
	 * Builds a PojoQuery instance using the specified DbContext.
	 *
	 * @param context the database context
	 * @param clz the class type of the result
	 * @param <T> the type of the result
	 * @return a new PojoQuery instance
	 */
	public static <T> PojoQuery<T> build(DbContext context, Class<T> clz) {
		return new PojoQuery<T>(context, clz);
	}

	/**
	 * Gets the underlying SQL query.
	 *
	 * @return the SQL query
	 */
	public SqlQuery<DefaultSqlQuery> getQuery() {
		return query;
	}

	/**
	 * Gets the list of SQL fields in the query.
	 *
	 * @return the list of SQL fields
	 */
	public List<SqlField> getFields() {
		return query.getFields();
	}

	/**
	 * Sets the list of SQL fields in the query.
	 *
	 * @param fields the list of SQL fields
	 */
	public void setFields(List<SqlField> fields) {
		query.setFields(fields);
	}

	/**
	 * Gets the list of SQL joins in the query.
	 *
	 * @return the list of SQL joins
	 */
	public List<SqlJoin> getJoins() {
		return query.getJoins();
	}

	/**
	 * Sets the list of SQL joins in the query.
	 *
	 * @param joins the list of SQL joins
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> setJoins(List<SqlJoin> joins) {
		query.setJoins(joins);
		return this;
	}

	/**
	 * Gets the list of WHERE conditions in the query.
	 *
	 * @return the list of WHERE conditions
	 */
	public List<SqlExpression> getWheres() {
		return query.getWheres();
	}

	/**
	 * Sets the list of WHERE conditions in the query.
	 *
	 * @param wheres the list of WHERE conditions
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> setWheres(List<SqlExpression> wheres) {
		query.setWheres(wheres);
		return this;
	}

	/**
	 * Gets the list of GROUP BY clauses in the query.
	 *
	 * @return the list of GROUP BY clauses
	 */
	public List<String> getGroupBy() {
		return query.getGroupBy();
	}

	/**
	 * Sets the list of GROUP BY clauses in the query.
	 *
	 * @param groupBy the list of GROUP BY clauses
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> setGroupBy(List<String> groupBy) {
		query.setGroupBy(groupBy);
		return this;
	}

	/**
	 * Gets the list of ORDER BY clauses in the query.
	 *
	 * @return the list of ORDER BY clauses
	 */
	public List<String> getOrderBy() {
		return query.getOrderBy();
	}

	/**
	 * Sets the list of ORDER BY clauses in the query.
	 *
	 * @param orderBy the list of ORDER BY clauses
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> setOrderBy(List<String> orderBy) {
		query.setOrderBy(orderBy);
		return this;
	}

	/**
	 * Adds a field to the query.
	 *
	 * @param expression the SQL expression for the field
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> addField(SqlExpression expression) {
		query.addField(expression);
		return this;
	}

	/**
	 * Adds a field with an alias to the query.
	 *
	 * @param expression the SQL expression for the field
	 * @param alias the alias for the field
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> addField(SqlExpression expression, String alias) {
		query.addField(expression, alias);
		return this;
	}

	/**
	 * Adds a GROUP BY clause to the query.
	 *
	 * @param group the GROUP BY clause
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> addGroupBy(String group) {
		query.addGroupBy(group);
		return this;
	}

	/**
	 * Adds a WHERE condition to the query.
	 *
	 * @param where the WHERE condition
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> addWhere(SqlExpression where) {
		query.addWhere(where);
		return this;
	}

	/**
	 * Adds a WHERE condition with parameters to the query.
	 *
	 * @param sql the SQL string for the WHERE condition
	 * @param params the parameters for the WHERE condition
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> addWhere(String sql, Object... params) {
		query.addWhere(sql, params);
		return this;
	}

	/**
	 * Adds an ORDER BY clause to the query.
	 *
	 * @param order the ORDER BY clause
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> addOrderBy(String order) {
		query.addOrderBy(order);
		return this;
	}

	/**
	 * Sets a limit on the number of rows returned by the query.
	 *
	 * @param rowCount the maximum number of rows
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> setLimit(int rowCount) {
		query.setLimit(rowCount);
		return this;
	}

	/**
	 * Sets a limit with an offset on the number of rows returned by the query.
	 *
	 * @param offset the starting row offset
	 * @param rowCount the maximum number of rows
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> setLimit(int offset, int rowCount) {
		query.setLimit(offset, rowCount);
		return this;
	}

	/**
	 * Converts the query to a SQL statement.
	 *
	 * @return the SQL statement
	 */
	public SqlExpression toStatement() {
		return query.toStatement();
	}

	/**
	 * Adds a join to the query.
	 *
	 * @param type the type of join
	 * @param tableName the name of the table to join
	 * @param alias the alias for the table
	 * @param joinCondition the join condition
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> addJoin(JoinType type, String tableName, String alias, SqlExpression joinCondition) {
		query.addJoin(type, tableName, alias, joinCondition);
		return this;
	}

	/**
	 * Gets the name of the table associated with the query.
	 *
	 * @return the table name
	 */
	public String getTable() {
		return query.getTable();
	}

	/**
	 * Executes the query using the specified DataSource.
	 *
	 * @param db the DataSource
	 * @return the list of results
	 */
	public List<T> execute(DataSource db) {
		SqlExpression stmt = query.toStatement();
		LOG.debug("Executing query: {}", stmt.getSql());
		return queryBuilder.processRows(DB.queryRows(db, stmt));
	}

	/**
	 * Executes the query using the specified Connection.
	 *
	 * @param connection the database connection
	 * @return the list of results
	 */
	public List<T> execute(Connection connection) {
		SqlExpression stmt = query.toStatement();
		LOG.debug("Executing query: {}", stmt.getSql());
		return queryBuilder.processRows(DB.queryRows(connection, stmt));
	}

	/**
	 * Executes the query in streaming mode using the specified DataSource.
	 *
	 * @param db the DataSource
	 * @return the list of results
	 */
	public List<T> executeStreaming(DataSource db) {
		List<T> result = new ArrayList<>();
		Map<Object, Object> allEntities = new HashMap<>();
		DB.queryRowsStreaming(db, query.toStatement(), row -> {
			queryBuilder.processRow(result, allEntities, row);
		});
		return result;
	}

	public static <PK> PK insert(Connection conn, Class<?> type, Object o) {
		return insert(DbContext.getDefault(), conn, null, type, o);
	}

	public static <PK> PK insert(DbContext context, Connection conn, Class<?> type, Object o) {
		return insert(context, conn, null, type, o);
	}

	public static <PK> PK insert(DataSource db, Class<?> type, Object o) {
		return insert(DbContext.getDefault(), null, db, type, o);
	}

	public static <PK> PK insert(DbContext context, DataSource db, Class<?> type, Object o) {
		return insert(context, null, db, type, o);
	}

	public static <PK> PK insert(DataSource db, Object o) {
		Objects.requireNonNull(o, "entity must not be null");
		return insert(DbContext.getDefault(), db, o.getClass(), o);
	}

	public static <PK> PK insert(DbContext context, DataSource db, Object o) {
		Objects.requireNonNull(o, "entity must not be null");
		return insert(context, db, o.getClass(), o);
	}

	public static <PK> PK insert(Connection connection, Object o) {
		Objects.requireNonNull(o, "entity must not be null");
		return insert(DbContext.getDefault(), connection, o.getClass(), o);
	}

	public static <PK> PK insert(DbContext context, Connection connection, Object o) {
		Objects.requireNonNull(o, "entity must not be null");
		return insert(context, connection, o.getClass(), o);
	}

	private static <PK> PK insert(DbContext context, Connection conn, DataSource db, Class<?> type, Object o) {
		// If the class hierarchy contains multiple tables, create separate
		// inserts
		List<TableMapping> tables = QueryBuilder.determineTableMapping(type);
		if (tables.size() == 0) {
			throw new MappingException("Missing @Table annotation on class " + type.getName() + " or any of its superclasses");
		}

		if (tables.size() == 1) {
			PK ids;
			if (conn != null) {
				ids = DB.insert(context, conn, tables.get(0).tableName, extractValues(type, o));
			} else {
				ids = DB.insert(context, db, tables.get(0).tableName, extractValues(type, o));
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
				ids = DB.insert(context, conn, topType.tableName, values);
			} else {
				ids = DB.insert(context, db, topType.tableName, values);
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
					DB.insert(context, conn, supertype.tableName, subvals);
				} else {
					DB.insert(context, db, supertype.tableName, subvals);
				}
				topType = supertype;
			}
			return ids;
		}

	}

	public static int update(DataSource db, Object object) {
		Objects.requireNonNull(object, "entity must not be null");
		return update(DbContext.getDefault(), null, db, object.getClass(), object);
	}

	public static int update(DbContext context, DataSource db, Object object) {
		Objects.requireNonNull(object, "entity must not be null");
		return update(context, null, db, object.getClass(), object);
	}

	public static int update(Connection connection, Object object) {
		Objects.requireNonNull(object, "entity must not be null");
		return update(DbContext.getDefault(), connection, null, object.getClass(), object);
	}

	public static int update(DbContext context, Connection connection, Object object) {
		Objects.requireNonNull(object, "entity must not be null");
		return update(context, connection, null, object.getClass(), object);
	}

	public static int update(DataSource db, Class<?> type, Object object) {
		return update(DbContext.getDefault(), null, db, type, object);
	}

	public static int update(DbContext context, DataSource db, Class<?> type, Object object) {
		return update(context, null, db, type, object);
	}

	public static int update(Connection connection, Class<?> type, Object object) {
		return update(DbContext.getDefault(), connection, null, type, object);
	}

	public static int update(DbContext context, Connection connection, Class<?> type, Object object) {
		return update(context, connection, null, type, object);
	}

	private static int update(DbContext context, Connection conn, DataSource db, Class<?> type, Object o) {
		// If the class hierarchy contains multiple tables, create separate
		// inserts
		List<TableMapping> tables = QueryBuilder.determineTableMapping(type);
		
		Long currentVersion = null;
		if (o instanceof HasVersion) {
			currentVersion = ((HasVersion) o).getVersion();
			if (currentVersion == null) {
				currentVersion = 0L;
			}
			((HasVersion) o).setVersion(currentVersion + 1);
		}
		
		if (tables.size() == 1) {
			Map<String, Object> values = extractValues(type, o);
			Map<String, Object> ids = splitIdFields(o, values);
			
			if (o instanceof HasVersion) {
				ids.put("version", currentVersion);
			}

			int affectedRows;
			if (conn != null) {
				affectedRows = DB.update(context, conn, tables.get(0).tableName, values, ids);
			} else {
				affectedRows = DB.update(context, db, tables.get(0).tableName, values, ids);
			}
			if (o instanceof HasVersion && affectedRows == 0) {
				throw new StaleObjectException();
			}
			return affectedRows;
		} else {

			int affectedRows = 0;

			TableMapping topType = tables.remove(0);
			Map<String, Object> values = extractValues(topType.clazz, o);
			Map<String, Object> ids = splitIdFields(o, values);
			Map<String, Object> topIds = new HashMap<>(ids);

			if (o instanceof HasVersion) {
				topIds.put("version", currentVersion);
			}

			if (conn != null) {
				affectedRows = DB.update(context, conn, topType.tableName, values, topIds);
			} else {
				affectedRows = DB.update(context, db, topType.tableName, values, topIds);
			}
			
			if (affectedRows == 0) {
				throw new StaleObjectException();
			}

			while (tables.size() > 0) {
				TableMapping supertype = tables.remove(0);
				Map<String, Object> subvals = extractValues(tables.size() > 0 ? supertype.clazz : type, o, topType.clazz);
				if (conn != null) {
					DB.update(context, conn, supertype.tableName, subvals, ids);
				} else {
					DB.update(context, db, supertype.tableName, subvals, ids);
				}
				topType = supertype;
			}
			return affectedRows;
		}

	}

	public static Map<String, Object> extractValues(Class<?> type, Object o) {
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
				} else if (f.getAnnotation(Link.class) != null && !f.getAnnotation(Link.class).linktable().equals(Link.NONE)) {
				} else if (f.getType().isArray()) {
					if (f.getType().getComponentType().isPrimitive()) {
						// Data like byte[] long[]
						values.put(QueryBuilder.determineSqlFieldName(f), val);
					}
				} else if (Collection.class.isAssignableFrom(f.getType())) {
				} else if (QueryBuilder.isLinkedClass(f.getType())) {
					// Linked entity.
					String linkfieldName = f.getName() + "_id";
					if (f.getAnnotation(Link.class) != null) {
						if (!Link.NONE.equals(f.getAnnotation(Link.class).linkfield())) {
							linkfieldName = f.getAnnotation(Link.class).linkfield();
						}
					}
					if (val == null) {
						values.put(linkfieldName, null);
					} else {
						Field idField = QueryBuilder.determineIdField(f.getType());
						idField.setAccessible(true);
						Object idValue = idField.get(val);
						values.put(linkfieldName, idValue);
					}
                } else if (f.getAnnotation(Id.class) != null && val == null) {
                	// Skip auto-generated ID field when value is null (for INSERT)
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
				Object value = ids;
				if (ids instanceof BigInteger && idField.getType().isAssignableFrom(Long.class)) {
					// See https://bugs.mysql.com/bug.php?id=101823
					// generated keys are always biginteger so we must convert if idField is Long
					value = ((BigInteger)ids).longValue();
				} else if (ids instanceof Integer && idField.getType().isAssignableFrom(Long.class)) {
					// HSQLDB returns Integer for BIGINT identity columns
					value = ((Integer)ids).longValue();
				}
				idField.set(o, value);
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
	
	/**
	 * Converts the query to a SQL string.
	 *
	 * @return the SQL string
	 */
	public String toSql() {
		return query.toStatement().getSql();
	}

	/**
	 * Finds an entity by its ID using the specified Connection.
	 *
	 * @param connection the database connection
	 * @param id the ID of the entity
	 * @return the entity, or null if not found
	 */
	public T findById(Connection connection, Object id) {
		query.getWheres().addAll(QueryBuilder.buildIdCondition(dbContext, resultClass, id));
		return returnSingleRow(execute(connection));
	}

	/**
	 * Finds an entity by its ID using the specified DataSource.
	 *
	 * @param db the DataSource
	 * @param id the ID of the entity
	 * @return the entity, or null if not found
	 */
	public T findById(DataSource db, Object id) {
		query.getWheres().addAll(QueryBuilder.buildIdCondition(dbContext, resultClass, id));
		return returnSingleRow(executeStreaming(db));
	}

	public static void delete(Connection conn, Object entity) {
		Objects.requireNonNull(entity, "entity must not be null");
		delete(DbContext.getDefault(), conn, null, entity);
	}
	
	public static void delete(DbContext context, Connection conn, Object entity) {
		Objects.requireNonNull(entity, "entity must not be null");
		delete(context, conn, null, entity);
	}
	
	public static void delete(DataSource db, Object entity) {
		Objects.requireNonNull(entity, "entity must not be null");
		delete(DbContext.getDefault(), db, entity);
	}
	
	public static void delete(DbContext context, DataSource db, Object entity) {
		Objects.requireNonNull(entity, "entity must not be null");
		delete(context, null, db, entity);
	}
	
	private static void delete(DbContext context, Connection conn, DataSource db, Object entity) {
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
					whereCondition.add(new SqlExpression(context.quoteObjectNames(table.tableName, field.getName()) + "=?", Arrays.asList(idvalue)));
				}
				if (db != null) {
					executeDelete(context, null, db, table.tableName, whereCondition);
				} else {
					executeDelete(context, conn, null, table.tableName, whereCondition);
				}
			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new MappingException(e);
		}
	}
	
	public static void deleteById(DbContext context, DataSource db, Class<?> clz, Object id) {
		for (TableMapping table : QueryBuilder.determineTableMapping(clz)) {
			List<SqlExpression> wheres = QueryBuilder.buildIdCondition(context, table.clazz, id);
			executeDelete(context, null, db, table.tableName, wheres);
		}
	}

	private static void executeDelete(DbContext context, Connection conn, DataSource db, String tableName, List<SqlExpression> where) {
		SqlExpression wheres = SqlExpression.implode(" AND ", where);
		SqlExpression deleteStatement = new SqlExpression("DELETE FROM " + context.quoteObjectNames(tableName) + " WHERE " + wheres.getSql(), wheres.getParameters());
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
	
	/**
	 * Processes rows and maps them to the result type.
	 *
	 * @param rows the list of rows
	 * @return the list of mapped results
	 */
	public List<T> processRows(List<Map<String, Object>> rows) {
		return queryBuilder.processRows(rows);
	}

	/**
	 * Lists the IDs of the entities in the query result.
	 *
	 * @param db the DataSource
	 * @param <PK> the type of the primary key
	 * @return the list of IDs
	 */
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
	
	/**
	 * Counts the total number of rows in the query result.
	 *
	 * @param db the DataSource
	 * @return the total count
	 */
	public int countTotal(DataSource db) {
		SqlExpression stmt = queryBuilder.buildCountStatement();
		List<Map<String, Object>> rows = DB.queryRows(db, stmt);
		return ((Long) rows.get(0).values().iterator().next()).intValue();
	}

}
