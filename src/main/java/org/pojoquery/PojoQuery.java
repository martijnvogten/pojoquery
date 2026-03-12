package org.pojoquery;

import static org.pojoquery.pipeline.PojoMetadata.determineIdFields;
import static org.pojoquery.pipeline.PojoMetadata.getFieldNames;
import static org.pojoquery.util.Strings.implode;

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
import java.util.Optional;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.NoUpdate;
import org.pojoquery.annotations.Other;
import org.pojoquery.internal.MappingException;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.DefaultSqlQuery;
import org.pojoquery.pipeline.PojoMetadata;
import org.pojoquery.pipeline.QueryTreeRowProcessor;
import org.pojoquery.pipeline.QueryTreeRowProcessor.StreamingRowHandler;
import org.pojoquery.pipeline.SQLQueryFromTree;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.SqlQuery.SqlField;
import org.pojoquery.pipeline.SqlQuery.SqlJoin;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.QueryTreeBuilder;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionFieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;
import org.pojoquery.util.CurlyMarkers;
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
 *     .addWhere("{user.name} LIKE ?", "%John%")
 *     .addOrderBy("{user.name} ASC")
 *     .execute(dataSource);
 * }</pre>
 * 
 * <h2>Alias Syntax</h2>
 * <p>Use curly braces to reference table aliases in WHERE, ORDER BY, and other clauses.
 * PojoQuery will automatically quote identifiers appropriately for your database:</p>
 * <pre>{@code
 * .addWhere("{user.status} = ?", "active")
 * .addOrderBy("{user.created_at} DESC")
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
 *   <li>{@link #insert(Connection, Object)} - Insert a new entity</li>
 *   <li>{@link #update(Connection, Object)} - Update an existing entity</li>
 *   <li>{@link #delete(Connection, Object)} - Delete an entity</li>
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
	
	private final QueryTree tree;
	private final SqlQuery<DefaultSqlQuery> query;
	private Class<T> resultClass;
	private DbContext dbContext;

	private PojoQuery(DbContext context, Class<T> clz) {
		this.dbContext = context;
		this.resultClass = clz;
		this.query = new DefaultSqlQuery(context);
		this.tree = QueryTreeBuilder.from(clz);
		SQLQueryFromTree.applyQueryTreeToQuery(query, tree);
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
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public List<T> execute(DataSource db) {
		SqlExpression stmt = query.toStatement();
		LOG.debug("Executing query: {}", stmt.getSql());
		return new QueryTreeRowProcessor(tree, this.query.getDbContext()).processRows(DB.queryRows(db, stmt));
	}
	
	/**
	 * Executes the query using the specified Connection.
	*
	* @param connection the database connection
	* @return the list of results
	*/
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public List<T> execute(Connection connection) {
		SqlExpression stmt = query.toStatement();
		LOG.debug("Executing query: {}", stmt.getSql());
		return new QueryTreeRowProcessor(tree, this.query.getDbContext()).processRows(DB.queryRows(connection, stmt));
	}


	/**
	 * Executes the query in streaming mode, calling the consumer for each completed entity.
	 * 
	 * <p>This method processes rows one at a time and emits entities as soon as they are complete
	 * (when all rows belonging to an entity have been processed). This is useful for processing
	 * large result sets without loading everything into memory.</p>
	 * 
	 * <p><strong>Ordering behavior:</strong> Your ORDER BY clause is respected, and the primary
	 * entity's ID is automatically appended as a tiebreaker to ensure all rows for the same entity
	 * stay grouped together.</p>
	 * 
	 * <p><strong>Restriction:</strong> ORDER BY clauses must only reference fields from the primary
	 * entity (root table). Ordering by fields from joined tables (e.g., a collection relationship)
	 * is not supported and will throw a {@link org.pojoquery.internal.MappingException}.</p>
	 * 
	 * <p>Example usage:</p>
	 * <pre>{@code
	 * PojoQuery.build(Order.class)
	 *     .addWhere("{order.status} = ?")
	 *     .addOrderBy("{order.createdAt} DESC")  // OK: ordering by primary entity field
	 *     .executeStreaming(dataSource, order -> {
	 *         processOrder(order);  // Called as each Order is complete
	 *     });
	 * }</pre>
	 *
	 * @param db the DataSource
	 * @param consumer the consumer to receive each completed entity
	 * @throws org.pojoquery.internal.MappingException if ORDER BY references a joined table
	 */
	public void executeStreaming(DataSource db, Consumer<T> consumer) {
		executeStreamingImpl(consumer, rowConsumer -> DB.queryRowsStreaming(db, query.toStatement(), rowConsumer));
	}

	/**
	 * Executes the query in streaming mode using the specified Connection, calling the consumer 
	 * for each completed entity.
	 * 
	 * <p>This method processes rows one at a time and emits entities as soon as they are complete
	 * (when all rows belonging to an entity have been processed). This is useful for processing
	 * large result sets without loading everything into memory.</p>
	 * 
	 * <p><strong>Restriction:</strong> ORDER BY clauses must only reference fields from the primary
	 * entity. See {@link #executeStreaming(DataSource, Consumer)} for details.</p>
	 *
	 * @param connection the database connection
	 * @param consumer the consumer to receive each completed entity
	 * @throws org.pojoquery.internal.MappingException if ORDER BY references a joined table
	 */
	public void executeStreaming(Connection connection, Consumer<T> consumer) {
		executeStreamingImpl(consumer, rowConsumer -> DB.queryRowsStreaming(connection, query.toStatement(), rowConsumer));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void executeStreamingImpl(Consumer<T> consumer, Consumer<Consumer<Map<String, Object>>> queryExecutor) {
		ensureOrderByPrimaryId(tree);
		StreamingRowHandler handler = new QueryTreeRowProcessor<T>(tree, dbContext).createStreamingRowHandler(consumer);
		queryExecutor.accept(handler);
		handler.flush();
	}

	public static <PK> PK insert(Connection connection, Object o) {
		return insertCascading(DbContext.getDefault(), connection, o);
	}

	public static <PK> PK insert(DbContext context, Connection connection, Object o) {
		Objects.requireNonNull(o, "entity must not be null");
		return insertCascading(context, connection, o);
	}

	private static class DatabaseOperationsImpl implements CascadingUpdater.DatabaseOperations {
		private final DbContext context;
		private final Connection connection;

		public DatabaseOperationsImpl(DbContext context, Connection connection) {
			this.context = context;
			this.connection = connection;
		}

		@Override
		public <PK> PK insert(String table, String schema, Map<String, Object> values) {
			return DB.insert(context, connection, schema, table, values);
		}

		@Override
		public int update(String table, String schema, Map<String, Object> values, Map<String, Object> where) {
			return DB.update(context, connection, schema, table, values, where);
		}

		@Override
		public int delete(String table, String schema, Map<String, Object> where) {
			return executeDelete(context, connection, table, buildConditionFromValuesMap(context, table, where));
		}

		@Override
		public int deleteWhere(String table, String schema, SqlExpression condition) {
			// Resolve curly brace markers {identifier} to quoted identifiers
			String resolvedSql = CurlyMarkers.processMarkers(condition.getSql(), 
				id -> context.quoteObjectNames(id));
			SqlExpression resolvedCondition = new SqlExpression(resolvedSql, condition.getParameters());
			return executeDelete(context, connection, table, List.of(resolvedCondition));
		}

		@Override
		public void syncLinkTable(String table, String schema, String ownerFkColumn, Object ownerId,
				String targetFkColumn, List<Object> targetIds) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'syncLinkTable'");
		}
	}

	private static <PK> PK insertCascading(DbContext context, Connection connection, Object o) {
		Objects.requireNonNull(o, "entity must not be null");
		QueryTree tree = QueryTreeBuilder.from(o.getClass());
		return CascadingUpdater.insert(tree, o, new DatabaseOperationsImpl(context, connection));
	}

	static <PK> PK insertInternal(DbContext context, Connection conn, Object o) {
		return insertInternal(context, conn, o.getClass(), o);
	}

	static <PK> PK insertInternal(DbContext context, Connection conn, Class<?> clz, Object o) {
		// If the class hierarchy contains multiple tables, create separate
		// inserts
		TypeModel type = new ReflectionTypeModel(clz);
		
		List<TableMapping> tables = PojoMetadata.determineTableMapping(type);
		if (tables.size() == 0) {
			throw new MappingException("Missing @Table annotation on class " + type.getQualifiedName() + " or any of its superclasses");
		}

		if (tables.size() == 1) {
			PK ids;
			ids = DB.insert(context, conn, tables.get(0).tableName, extractValues(clz, o));
			if (ids != null) {
				applyGeneratedId(o, ids);
			}
			return ids;
		} else {
			TableMapping topType = tables.remove(0);
			Map<String, Object> values = extractValues(((ReflectionTypeModel)topType.getType()).getReflectionClass(), o);
			PK ids;
			ids = DB.insert(context, conn, topType.tableName, values);
			if (ids != null) {
				applyGeneratedId(o, ids);
			}
			List<FieldModel> idFields = PojoMetadata.determineIdFields(type);
			if (idFields.size() != 1) {
				throw new MappingException("Need single ID field annotated with @Id for inserting joined subclasses");
			}
			String idField = idFields.get(0).getName();

			while (tables.size() > 0) {
				TableMapping supertype = tables.remove(0);
				Map<String, Object> subvals = extractValues(tables.size() > 0 ? ((ReflectionTypeModel)supertype.getType()).getReflectionClass() : clz, o, ((ReflectionTypeModel)topType.getType()).getReflectionClass());
				subvals.put(idField, ids);
				DB.insert(context, conn, supertype.tableName, subvals);
				topType = supertype;
			}
			return ids;
		}

	}

	public static int update(Connection connection, Object object) {
		return updateCascading(DbContext.getDefault(), connection, object);
	}

	public static int update(DbContext context, Connection connection, Object object) {
		Objects.requireNonNull(object, "entity must not be null");
		return updateCascading(context, connection, object);
	}

	static int updateCascading(DbContext context, Connection connection, Object object) {
		Objects.requireNonNull(object, "entity must not be null");
		QueryTree tree = QueryTreeBuilder.from(object.getClass());
		return CascadingUpdater.update(tree, object, new DatabaseOperationsImpl(context, connection));
	}

	static int updateInternal(DbContext context, Connection conn, Class<?> clz, Object o) {
		// If the class hierarchy contains multiple tables, create separate
		// inserts
		TypeModel type = new ReflectionTypeModel(clz);
		List<TableMapping> tables = PojoMetadata.determineTableMapping(type);
		
		Long currentVersion = null;
		if (o instanceof HasVersion) {
			currentVersion = ((HasVersion) o).getVersion();
			if (currentVersion == null) {
				currentVersion = 0L;
			}
			((HasVersion) o).setVersion(currentVersion + 1);
		}
		
		if (tables.size() == 1) {
			Map<String, Object> values = extractValues(clz, o);
			Map<String, Object> ids = splitIdFields(o, values);
			
			if (o instanceof HasVersion) {
				ids.put("version", currentVersion);
			}

			int affectedRows = DB.update(context, conn, tables.get(0).tableName, values, ids);
			if (o instanceof HasVersion && affectedRows == 0) {
				throw new StaleObjectException();
			}
			return affectedRows;
		} else {

			int affectedRows = 0;

			TableMapping topType = tables.remove(0);
			Map<String, Object> values = extractValues(((ReflectionTypeModel)topType.getType()).getReflectionClass(), o);
			Map<String, Object> ids = splitIdFields(o, values);
			Map<String, Object> topIds = new HashMap<>(ids);

			if (o instanceof HasVersion) {
				topIds.put("version", currentVersion);
			}

			affectedRows = DB.update(context, conn, topType.tableName, values, topIds);
			
			if (affectedRows == 0) {
				throw new StaleObjectException();
			}

			while (tables.size() > 0) {
				TableMapping supertype = tables.remove(0);
				Map<String, Object> subvals = extractValues(tables.size() > 0 ? ((ReflectionTypeModel)supertype.getType()).getReflectionClass() : clz, o, ((ReflectionTypeModel)topType.getType()).getReflectionClass());
				DB.update(context, conn, supertype.tableName, subvals, ids);
				topType = supertype;
			}
			return affectedRows;
		}

	}

	public static Map<String, Object> extractValues(Class<?> clz, Object o) {
		return extractValues(clz, o, null);
	}

	private static Map<String, Object> extractValues(Class<?> clz, Object o, Class<?> stopAtSuperclass) {
		TypeModel type = new ReflectionTypeModel(clz);
		TypeModel stopAtType = stopAtSuperclass != null ? new ReflectionTypeModel(stopAtSuperclass) : null;
		try {
			Map<String, Object> values = new HashMap<String, Object>();
			for (FieldModel fieldModel : PojoMetadata.collectFieldsOfClass(type, stopAtType)) {
				Field f = ((ReflectionFieldModel) fieldModel).getReflectionField();
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
				if (AnnotationHelper.isEmbedded(fieldModel)) {
					if (val != null) {
						Map<String, Object> embeddedVals = extractValues(f.getType(), val);
						String prefix = PojoMetadata.determinePrefix(fieldModel);
						for (String embeddedField : embeddedVals.keySet()) {
							values.put(prefix + embeddedField, embeddedVals.get(embeddedField));
						}
					}
				} else if (f.getAnnotation(NoUpdate.class) != null) {
				} else if (f.getAnnotation(Link.class) != null && !f.getAnnotation(Link.class).linktable().isEmpty()) {
				} else if (f.getType().isArray()) {
					if (f.getType().getComponentType().isPrimitive()) {
						// Data like byte[] long[]
						values.put(PojoMetadata.determineSqlFieldName(fieldModel), val);
					}
				} else if (Collection.class.isAssignableFrom(f.getType())) {
				} else if (PojoMetadata.isLinkedClass(f.getType())) {
					// Linked entity.
					String linkfieldName = AnnotationHelper.getJoinColumnName(fieldModel);
					if (linkfieldName == null) {
						linkfieldName = f.getName() + "_id";
					}
					if (val == null) {
						values.put(linkfieldName, null);
					} else {
						FieldModel idFieldModel = PojoMetadata.determineIdField(fieldModel.getType());
						Field idField = ((ReflectionFieldModel) idFieldModel).getReflectionField();
						idField.setAccessible(true);
						Object idValue = idField.get(val);
						values.put(linkfieldName, idValue);
					}
                } else if (AnnotationHelper.isId(fieldModel) && val == null) {
                	// Skip auto-generated ID field when value is null (for INSERT)
                } else {
                	values.put(PojoMetadata.determineSqlFieldName(fieldModel), val);
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
		List<FieldModel> idFields = PojoMetadata.determineIdFields(new ReflectionTypeModel(o.getClass()));
		if (ids != null && idFields.size() == 1) {
			Field idField = ((ReflectionFieldModel) idFields.get(0)).getReflectionField();
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
		List<FieldModel> idFields = PojoMetadata.determineIdFields(new ReflectionTypeModel(object.getClass()));
		if (idFields.size() == 0) {
			throw new RuntimeException("No @Id annotations found on fields of class " + object.getClass().getName());
		}
		Map<String, Object> ids = new HashMap<String, Object>();
		for (FieldModel fieldModel : idFields) {
			String columnName = PojoMetadata.determineSqlFieldName(fieldModel);
			ids.put(columnName, values.get(columnName));
			values.remove(columnName);
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
	public Optional<T> findById(Connection connection, Object id) {
		TypeModel type = new ReflectionTypeModel(resultClass);
		query.getWheres().addAll(buildIdCondition(dbContext, type, id));
		return returnSingleRow(execute(connection));
	}

	public static void delete(Connection conn, Object entity) {
		Objects.requireNonNull(entity, "entity must not be null");
		delete(DbContext.getDefault(), conn, entity);
	}
	
	public static void deleteCascading(Connection conn, Object entity) {
		Objects.requireNonNull(entity, "entity must not be null");
		deleteCascading(DbContext.getDefault(), conn, entity);
	}
	
	public static void deleteCascading(DbContext context, Connection conn, Object entity) {
		Objects.requireNonNull(entity, "entity must not be null");
		QueryTree tree = QueryTreeBuilder.from(entity.getClass());
		CascadingUpdater.delete(tree, entity, new DatabaseOperationsImpl(context, conn));
	}
	
	public static void delete(DbContext context, Connection conn, Object entity) {
		Objects.requireNonNull(entity, "entity must not be null");
		try {
			TypeModel type = new ReflectionTypeModel(entity.getClass());
			List<TableMapping> mapping = PojoMetadata.determineTableMapping(type);
			List<FieldModel> idFields = PojoMetadata.determineIdFields(type);
			Collections.reverse(mapping);
			for (TableMapping table : mapping) {
				List<SqlExpression> whereCondition = new ArrayList<>();
				for (FieldModel fieldModel : idFields) {
					Field field = ((ReflectionFieldModel) fieldModel).getReflectionField();
					field.setAccessible(true);
						Object idvalue;
						idvalue = field.get(entity);
					if (idvalue == null) {
						throw new MappingException("Cannot create wherecondition for entity with null value in idfield " + field.getName());
					}
					whereCondition.add(new SqlExpression(context.quoteObjectNames(table.tableName, field.getName()) + "=?", Arrays.asList(idvalue)));
				}
				executeDelete(context, conn, table.tableName, whereCondition);
			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new MappingException(e);
		}
	}
	
	public static void deleteById(DbContext context, Class<?> clz, Object id) {
		TypeModel type = new ReflectionTypeModel(clz);
		for (TableMapping table : PojoMetadata.determineTableMapping(type)) {
			List<SqlExpression> wheres = buildIdCondition(context, type, id);
			executeDelete(context, null, table.tableName, wheres);
		}
	}

	private static int executeDelete(DbContext context, Connection conn, String tableName, List<SqlExpression> where) {
		SqlExpression wheres = SqlExpression.implode(" AND ", where);
		SqlExpression deleteStatement = new SqlExpression("DELETE FROM " + context.quoteObjectNames(tableName) + " WHERE " + wheres.getSql(), wheres.getParameters());
		return DB.update(conn, deleteStatement);
	}

	private Optional<T> returnSingleRow(List<T> resultList) {
		if (resultList.size() == 1) {
			return Optional.of(resultList.get(0));
		}
		if (resultList.size() > 1) {
			throw new RuntimeException("More than one result found in findById on class " + resultClass.getName());
		}
		return Optional.empty();
	}
	
	/**
	 * Processes rows and maps them to the result type.
	 *
	 * @param rows the list of rows
	 * @return the list of mapped results
	 */
	public List<T> processRows(List<Map<String, Object>> rows) {
		return new QueryTreeRowProcessor<T>(tree, dbContext).processRows(rows);
	}

	/**
	 * Lists the IDs of the entities in the query result.
	 *
	 * @param conn the database connection
	 * @param <PK> the type of the primary key
	 * @return the list of IDs
	 */
	@SuppressWarnings("unchecked")
	public <PK> List<PK> listIds(Connection conn) {
		List<FieldModel> idFields = PojoMetadata.determineIdFields(new ReflectionTypeModel(resultClass));
		SqlExpression stmt = buildListIdsStatementFromFields(idFields);
		List<Map<String, Object>> rows = DB.queryRows(conn, stmt);
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
	 * @param conn the database connection
	 * @return the total count
	 */
	public int countTotal(Connection conn) {
		SqlExpression stmt = buildCountStatement();
		List<Map<String, Object>> rows = DB.queryRows(conn, stmt);
		return ((Long) rows.get(0).values().iterator().next()).intValue();
	}

		/**
	 * Ensures that the query is ordered by the primary entity's ID fields.
	 * This is required for streaming mode to work correctly, as it ensures
	 * all rows belonging to the same entity are consecutive in the result set.
	 *
	 * <p>The primary ID fields are appended to the ORDER BY clause as a tiebreaker
	 * to ensure entity grouping while preserving the user's primary ordering.</p>
	 *
	 * @throws MappingException if any ORDER BY clause references a non-root alias (joined table)
	 */
	public void ensureOrderByPrimaryId(QueryTree tree) {
		List<FieldModel> idFields = PojoMetadata.determineIdFields(new ReflectionTypeModel(resultClass));
		String tableName = query.getTable();
		List<String> currentOrderBy = new ArrayList<>(query.getOrderBy());

		// Validate that ORDER BY clauses only reference the root alias
		validateOrderByAliases(currentOrderBy, tree.rootAlias());

		// Append ID fields to the ORDER BY (if not already present) as a tiebreaker
		// Use curly brace syntax for alias + quoted field name
		for (FieldModel idField : idFields) {
			String quotedFieldRef = "{" + tableName + "." + idField.getName() + "}";
			boolean alreadyPresent = currentOrderBy.stream()
				.anyMatch(o -> o.toUpperCase().contains(tableName.toUpperCase() + "}." + idField.getName().toUpperCase()));
			if (!alreadyPresent) {
				currentOrderBy.add(quotedFieldRef);
			}
		}
		query.setOrderBy(currentOrderBy);
	}

	/**
	 * Validates that all ORDER BY clauses only reference the root alias.
	 * Ordering by fields from joined tables would cause rows from different entities
	 * to interleave, breaking streaming mode.
	 *
	 * @param orderByClauses the ORDER BY clauses to validate
	 * @param rootAlias the root alias
	 * @throws MappingException if any clause references a non-root alias
	 */
	private void validateOrderByAliases(List<String> orderByClauses, String rootAlias) {
		for (String clause : orderByClauses) {
			for (String tableAlias : CurlyMarkers.extractAliases(clause)) {
				if (!tableAlias.equals(rootAlias)) {
					throw new MappingException(
						"executeStreaming with consumer does not support ORDER BY on joined tables. " +
						"Found ORDER BY clause '" + clause + "' referencing alias '" + tableAlias + "', " +
						"but only the root alias '" + rootAlias + "' is allowed. " +
						"Ordering by joined table fields would cause incomplete entities. " +
						"Use executeStreaming() without consumer or execute() if you need this ordering.");
				}
			}
		}
	}

	public static List<FieldModel> assertIdFields(TypeModel type) {
		List<FieldModel> idFields = PojoMetadata.determineIdFields(type);
		if (idFields.size() == 0) {
			throw new MappingException("No @Id annotations found on fields of type " + type.getQualifiedName());
		}
		return idFields;
	}

	public static List<SqlExpression> buildIdCondition(DbContext context, TypeModel type, Object id) {
		List<FieldModel> idFields = assertIdFields(type);
		List<TableMapping> tables = PojoMetadata.determineTableMapping(type);
		String tableName = tables.get(tables.size() - 1).tableName;
		if (idFields.size() == 1) {
			return Arrays.asList(new SqlExpression((context.quoteAlias(tableName) + "." + context.quoteObjectNames(idFields.get(0).getName())) + "=?", Arrays.asList((Object) id)));
		} else {
			if (id instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> idvalues = (Map<String, Object>) id;
				return buildConditionFromValuesMap(context, tableName, idvalues);
			} else {
				throw new MappingException("Multiple @Id annotations on type " + type.getQualifiedName() + ": expecting a map id.");
			}
		}
	}

	public static List<SqlExpression> buildConditionFromValuesMap(DbContext context, String tableName, Map<String,Object> idvalues) {
		List<SqlExpression> result = new ArrayList<SqlExpression>();
		for (String field : idvalues.keySet()) {
			result.add(new SqlExpression(context.quoteObjectNames(tableName, field) + "=?", Arrays.asList((Object) idvalues.get(field))));
		}
		return result;
	}

	public SqlExpression buildListIdsStatement(List<FieldModel> idFields) {
		return query.toListIdsStatement(new SqlExpression(implode("\n , ", getFieldNames(query.getTable(), idFields))));
	}

	/** Backward compatible overload that accepts {@code List<Field>} */
	public SqlExpression buildListIdsStatementFromFields(List<FieldModel> idFields) {
		List<FieldModel> fieldModels = new ArrayList<>();
		for (FieldModel fieldModel : idFields) {
			fieldModels.add(new ReflectionFieldModel(((ReflectionFieldModel) fieldModel).getReflectionField()));
		}
		return buildListIdsStatement(fieldModels);
	}

	public SqlExpression buildCountStatement() {
		List<FieldModel> idFields = determineIdFields(new ReflectionTypeModel(resultClass));
		String selectClause = "SELECT COUNT(DISTINCT " + implode(", ", getFieldNames(query.getTable(), idFields)) + ") ";

		return query.toStatement(new SqlExpression(selectClause), query.getSchema(), query.getTable(), query.getJoins(), query.getWheres(), null, null, -1, -1);
	}

}
