package org.pojoquery;

import static org.pojoquery.pipeline.PojoMetadata.determineIdFields;
import static org.pojoquery.pipeline.PojoMetadata.getFieldNames;
import static org.pojoquery.util.Strings.implode;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.pojoquery.internal.MappingException;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.AQTCascadingUpdater;
import org.pojoquery.pipeline.AQTJsonDirectTransformer;
import org.pojoquery.pipeline.AQTRowProcessor;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.DefaultSqlQuery;
import org.pojoquery.pipeline.ExistsSubqueryBuilder;
import org.pojoquery.pipeline.JsonSqlQuery;
import org.pojoquery.pipeline.PojoMetadata;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.SqlQuery.SqlField;
import org.pojoquery.pipeline.SqlQuery.SqlJoin;
import org.pojoquery.pipeline.TransformPipeline;
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
	
	private final RootNode tree;
	private final SqlQuery<DefaultSqlQuery> query;
	private Class<T> resultClass;
	private DbContext dbContext;

	private PojoQuery(DbContext context, Class<T> clz) {
		this(context, clz, TransformPipeline.defaultPipeline());
	}

	private PojoQuery(DbContext context, Class<T> clz, TransformPipeline pipeline) {
		this.dbContext = context;
		this.resultClass = clz;
		this.query = new DefaultSqlQuery(context);
		this.tree = AQTTransformer.buildQueryTreeForType(new ReflectionTypeModel(clz), pipeline);
		AQTTransformer.toSql(this.tree, this.query);
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
		return new PojoQuery<T>(context, clz, TransformPipeline.defaultPipeline());
	}

	public static <T> PojoQuery<T> build(DbContext context, TransformPipeline pipeline, Class<T> clz) {
		return new PojoQuery<T>(context, clz, pipeline);
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
	 * Adds a semi-join {@code EXISTS} condition that keeps only root rows for which
	 * the given collection contains at least one element matching {@code sql}.
	 *
	 * <p>
	 * Unlike {@link #addWhere(String, Object...)} against a joined collection alias
	 * (which both filters the root rows and truncates the hydrated collection), this
	 * condition only decides whether a root row is returned; the collection itself is
	 * still loaded in full.
	 *
	 * @param collectionAlias the alias of the collection to test
	 * @param sql             the condition applied inside the sub-query, using
	 *                        {@code {alias.column}} markers
	 * @param params          the parameters for {@code sql}
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> whereExists(String collectionAlias, String sql, Object... params) {
		return whereExists(collectionAlias, SqlExpression.sql(sql, params));
	}

	/**
	 * Adds a semi-join {@code EXISTS} condition using a prepared {@link SqlExpression}.
	 *
	 * @param collectionAlias the alias of the collection to test
	 * @param condition       the condition applied inside the sub-query
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> whereExists(String collectionAlias, SqlExpression condition) {
		query.addWhere(ExistsSubqueryBuilder.buildExists(dbContext, tree, collectionAlias, condition, false));
		return this;
	}

	/**
	 * Adds a semi-join {@code EXISTS} condition that keeps only root rows whose
	 * collection is non-empty.
	 *
	 * @param collectionAlias the alias of the collection to test
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> whereExists(String collectionAlias) {
		return whereExists(collectionAlias, (SqlExpression) null);
	}

	/**
	 * Adds a semi-join {@code NOT EXISTS} condition that keeps only root rows for
	 * which the given collection contains no element matching {@code sql}.
	 *
	 * @param collectionAlias the alias of the collection to test
	 * @param sql             the condition applied inside the sub-query, using
	 *                        {@code {alias.column}} markers
	 * @param params          the parameters for {@code sql}
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> whereNotExists(String collectionAlias, String sql, Object... params) {
		return whereNotExists(collectionAlias, SqlExpression.sql(sql, params));
	}

	/**
	 * Adds a semi-join {@code NOT EXISTS} condition using a prepared
	 * {@link SqlExpression}.
	 *
	 * @param collectionAlias the alias of the collection to test
	 * @param condition       the condition applied inside the sub-query
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> whereNotExists(String collectionAlias, SqlExpression condition) {
		query.addWhere(ExistsSubqueryBuilder.buildExists(dbContext, tree, collectionAlias, condition, true));
		return this;
	}

	/**
	 * Adds a semi-join {@code NOT EXISTS} condition that keeps only root rows whose
	 * collection is empty.
	 *
	 * @param collectionAlias the alias of the collection to test
	 * @return the current PojoQuery instance
	 */
	public PojoQuery<T> whereNotExists(String collectionAlias) {
		return whereNotExists(collectionAlias, (SqlExpression) null);
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
		try {
			SqlExpression stmt = query.toStatement();
			LOG.debug("Executing query: {}", stmt.getSql());
			return AQTRowProcessor.processRows(tree, DB.queryRows(db, stmt));
		} catch (SQLException e) {
			throw new RuntimeException("Failed to execute query", e);
		}
	}
	
	/**
	 * Executes the query using the specified Connection.
	*
	* @param connection the database connection
	* @return the list of results
	*/
	public List<T> execute(Connection connection) {
		try {
			SqlExpression stmt = query.toStatement();
			LOG.debug("Executing query: {}", stmt.getSql());
			return AQTRowProcessor.processRows(tree, DB.queryRows(connection, stmt));
		} catch (SQLException e) {
			throw new RuntimeException("Failed to execute query", e);
		}
	}

	/**
	 * Builds a SELECT statement that returns each result entity as a single JSON
	 * document, assembled by the database itself using its native JSON functions.
	 *
	 * <p>The statement produces one row per root entity with a single column
	 * named {@code json}. Scalar fields become JSON properties, references and
	 * embedded objects become nested objects, and collections become nested
	 * arrays (empty collections yield {@code []}). Polymorphic entities carry a
	 * {@code _type} property with the concrete type's simple name.</p>
	 *
	 * <p>WHERE conditions, ORDER BY clauses and limits added to this query apply
	 * to the root entity. Conditions may only reference the root table and
	 * joined references, not collection contents (collections are evaluated in
	 * derived tables).</p>
	 *
	 * @return the JSON SQL statement
	 * @see #executeJson(DataSource)
	 */
	public SqlExpression toJsonStatement() {
		JsonSqlQuery jsonQuery = new JsonSqlQuery(dbContext);
		AQTJsonDirectTransformer.toSql(tree, jsonQuery);
		for (SqlExpression where : query.getWheres()) {
			jsonQuery.addWhere(where);
		}
		if (!query.getOrderBy().isEmpty()) {
			jsonQuery.setOrderBy(query.getOrderBy());
		}
		jsonQuery.setLimit(query.getOffset(), query.getRowCount());
		return jsonQuery.toStatement();
	}

	/**
	 * Converts the query to a JSON-producing SQL string.
	 *
	 * @return the SQL string
	 * @see #toJsonStatement()
	 */
	public String toJsonSql() {
		return toJsonStatement().getSql();
	}

	/**
	 * Executes the JSON variant of this query, returning one JSON document per
	 * result entity.
	 *
	 * @param db the DataSource
	 * @return one JSON document string per root entity
	 * @see #toJsonStatement()
	 */
	public List<String> executeJson(DataSource db) {
		SqlExpression stmt = toJsonStatement();
		LOG.debug("Executing JSON query: {}", stmt.getSql());
		return extractJsonColumn(DB.queryRows(db, stmt));
	}

	/**
	 * Executes the JSON variant of this query, returning one JSON document per
	 * result entity.
	 *
	 * @param connection the database connection
	 * @return one JSON document string per root entity
	 * @see #toJsonStatement()
	 */
	public List<String> executeJson(Connection connection) {
		SqlExpression stmt = toJsonStatement();
		LOG.debug("Executing JSON query: {}", stmt.getSql());
		return extractJsonColumn(DB.queryRows(connection, stmt));
	}

	private static List<String> extractJsonColumn(List<Map<String, Object>> rows) {
		List<String> result = new ArrayList<>(rows.size());
		for (Map<String, Object> row : rows) {
			Object value = row.containsKey(JsonSqlQuery.JSON_COLUMN)
					? row.get(JsonSqlQuery.JSON_COLUMN)
					: row.values().iterator().next();
			result.add(value == null ? null : value.toString());
		}
		return result;
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
		ensureOrderByPrimaryId(this.tree);
		AQTRowProcessor handler = new AQTRowProcessor(tree, consumer);
		queryExecutor.accept(row -> { 
			try {
				handler.processRow(row);
			} catch (SQLException e) {
				throw new RuntimeException("Failed to process row", e);
			}
		});
		handler.flush();
	}

	public static <PK> PK insert(Connection connection, Object o) {
		return insertCascading(DbContext.getDefault(), connection, o);
	}

	public static <PK> PK insert(DbContext context, Connection connection, Object o) {
		Objects.requireNonNull(o, "entity must not be null");
		return insertCascading(context, connection, o);
	}

	private static class DatabaseOperationsImpl implements AQTCascadingUpdater.DatabaseOperations {
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
		public <PK> PK insert(String table, String schema, Map<String, Object> values, String generatedKeyColumn) {
			return DB.insert(context, connection, schema, table, values, generatedKeyColumn);
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
			// Delete all existing rows for this owner
			Map<String, Object> deleteCondition = new HashMap<>();
			deleteCondition.put(ownerFkColumn, ownerId);
			executeDelete(context, connection, table, buildConditionFromValuesMap(context, table, deleteCondition));
			
			// Insert new rows for each target
			for (Object targetId : targetIds) {
				Map<String, Object> values = new LinkedHashMap<>();
				values.put(ownerFkColumn, ownerId);
				values.put(targetFkColumn, targetId);
				DB.insert(context, connection, schema, table, values);
			}
		}
	}

	private static <PK> PK insertCascading(DbContext context, Connection connection, Object o) {
		Objects.requireNonNull(o, "entity must not be null");
		RootNode tree = AQTTransformer.buildQueryTreeForType(o.getClass());
		return AQTCascadingUpdater.insert(tree, o, new DatabaseOperationsImpl(context, connection));
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
		RootNode tree = AQTTransformer.buildQueryTreeForType(object.getClass());
		return AQTCascadingUpdater.update(tree, object, new DatabaseOperationsImpl(context, connection));
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
		RootNode tree = AQTTransformer.buildQueryTreeForType(entity.getClass());
		AQTCascadingUpdater.delete(tree, entity, new DatabaseOperationsImpl(context, conn));
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
		try {
			return AQTRowProcessor.processRows(tree, rows);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to process rows", e);
		}
	}

	/**
	 * Builds and executes a query to retrieve only the IDs of the result entities.
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
	public void ensureOrderByPrimaryId(RootNode tree) {
		List<FieldModel> idFields = PojoMetadata.determineIdFields(new ReflectionTypeModel(resultClass));
		String tableName = query.getTable();
		List<String> currentOrderBy = new ArrayList<>(query.getOrderBy());

		// Validate that ORDER BY clauses only reference the root alias
		validateOrderByAliases(currentOrderBy, tree.alias());

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


	public RootNode getTree() {
		return tree;
	}

}
