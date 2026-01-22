package org.pojoquery.typedquery;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;
import org.pojoquery.pipeline.SqlQuery;

/**
 * Abstract base class for generated typed query builders.
 * 
 * @param <T> the entity type this query returns
 * @param <Q> the concrete query class type (for fluent method chaining)
 */
public abstract class TypedQuery<T, Q extends TypedQuery<T, Q>> {

    protected SqlQuery<?> query = new DefaultSqlQuery(DbContext.getDefault());
    protected DbContext dbContext = DbContext.getDefault();

    /**
     * Initialize the query with table, joins, and fields.
     * Implemented by generated subclasses.
     */
    protected abstract void initializeQuery();

    /**
     * Process raw database rows into entity objects.
     * Implemented by generated subclasses.
     *
     * @param rows the raw database rows
     * @return the list of mapped entities
     * @throws NoSuchFieldException if a field cannot be found
     * @throws IllegalAccessException if field access is denied
     */
    protected abstract List<T> processRows(List<Map<String, Object>> rows) 
            throws NoSuchFieldException, IllegalAccessException;

    /**
     * Process a row for streaming with entity accumulation.
     * This method accumulates rows for the same entity before returning it.
     * 
     * <p>Subclasses should override this to provide efficient row-by-row processing
     * that properly handles to-many joins by reusing entity instances.</p>
     *
     * @param row the raw database row
     * @param entityCache cache of entities by their IDs for deduplication
     * @return the root entity from this row (may be same instance as previous rows)
     * @throws NoSuchFieldException if a field cannot be found
     * @throws IllegalAccessException if field access is denied
     */
    protected abstract T processRowStreaming(Map<String, Object> row, Map<Object, Object> entityCache) 
            throws NoSuchFieldException, IllegalAccessException;

    /**
     * Get the name of the primary key field for the root entity.
     * Implemented by generated subclasses.
     *
     * @return the ID field name (e.g., "id", "articleId")
     */
    protected abstract String getIdFieldName();

    /**
     * Get the entity class for this query.
     * Implemented by generated subclasses.
     *
     * @return the entity class
     */
    protected abstract Class<T> getEntityClass();

    /**
     * Get the primary key value from a row for the root entity.
     * Used by streaming to detect when we've moved to a new entity.
     * 
     * <p>Subclasses should override this to return the actual primary key.</p>
     *
     * @param row the raw database row
     * @return the primary key value, or null if the row has no root entity
     */
    protected Object getPrimaryKeyFromRow(Map<String, Object> row) {
        String tableName = query.getTable();
        String idFieldName = getIdFieldName();
        Object pk = row.get(tableName + "." + idFieldName);
        if (pk == null) {
            pk = row.get(idFieldName);
        }
        return pk;
    }

    /**
     * Returns this query cast to the concrete type Q for fluent chaining.
     */
    @SuppressWarnings("unchecked")
    protected Q self() {
        return (Q) this;
    }

    /**
     * Execute the query and return all results as a list.
     *
     * @param connection the database connection
     * @return the list of entities matching the query
     */
    public List<T> list(Connection connection) {
        SqlExpression stmt = query.toStatement();
        List<Map<String, Object>> rows = DB.queryRows(connection, stmt);
        try {
            return processRows(rows);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Execute the query and return the first result, if any.
     *
     * @param connection the database connection
     * @return the first result, or null if no results
     */
    public T first(Connection connection) {
        List<T> results = list(connection);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Build the WHERE condition for finding an entity by its ID.
     * Implemented by generated subclasses based on the entity's @Id field(s).
     *
     * @param id the ID value (or composite ID object)
     * @return the SQL expression for the ID condition
     */
    protected abstract SqlExpression buildIdCondition(Object id);

    /**
     * Find an entity by its ID.
     *
     * @param connection the database connection
     * @param id the ID of the entity to find
     * @return the entity, or null if not found
     */
    public T findById(Connection connection, Object id) {
        query.getWheres().add(buildIdCondition(id));
        List<T> results = list(connection);
        if (results.size() > 1) {
            throw new RuntimeException("More than one result found in findById on class " + getEntityClass().getName());
        }
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Execute the query using database streaming for efficient processing of large result sets.
     * 
     * <p>This method uses the database's streaming capability to process rows one at a time
     * without loading the entire result set into memory. Ideal for:
     * <ul>
     *   <li>Processing millions of records</li>
     *   <li>Exporting large datasets</li>
     *   <li>Memory-constrained environments</li>
     * </ul>
     *
     * <p>This method properly handles to-many joins by accumulating all rows for an entity
     * before emitting it to the consumer. Entities are emitted when the primary key changes,
     * so the query should be ordered by the primary entity's ID (this is done automatically).</p>
     *
     * @param connection the database connection
     * @param consumer the consumer to process each entity
     */
    public void stream(Connection connection, Consumer<T> consumer) {
        ensureOrderByPrimaryId();
        SqlExpression stmt = query.toStatement();
        
        StreamingRowHandler handler = new StreamingRowHandler(consumer);
        DB.queryRowsStreaming(dbContext, connection, stmt, handler);
        handler.flush();
    }

    /**
     * Ensures that the query is ordered by the primary entity's ID field.
     * This is required for streaming mode to work correctly, as it ensures
     * all rows belonging to the same entity are consecutive in the result set.
     */
    protected void ensureOrderByPrimaryId() {
        String idFieldName = getIdFieldName();
        List<String> currentOrderBy = new ArrayList<>(query.getOrderBy());
        
        // Add primary ID field to ORDER BY if not already present
        // This ensures all rows for an entity are consecutive
        boolean alreadyPresent = currentOrderBy.stream()
            .anyMatch(o -> o.contains(idFieldName));
        if (!alreadyPresent) {
            currentOrderBy.add("{" + idFieldName + "}");
        }
        query.setOrderBy(currentOrderBy);
    }

    /**
     * Handles streaming row processing, emitting completed entities when the primary ID changes.
     */
    protected class StreamingRowHandler implements Consumer<Map<String, Object>> {
        private final Consumer<T> entityConsumer;
        private Object currentPrimaryId = null;
        private T currentEntity = null;
        private final Map<Object, Object> entityCache = new HashMap<>();

        StreamingRowHandler(Consumer<T> entityConsumer) {
            this.entityConsumer = entityConsumer;
        }

        @Override
        public void accept(Map<String, Object> row) {
            try {
                // Get the primary ID for this row
                Object primaryId = getPrimaryKeyFromRow(row);

                // If primary ID changed, emit the current entity and clear cache
                if (primaryId != null && currentPrimaryId != null && !primaryId.equals(currentPrimaryId)) {
                    emitCurrentEntity();
                }

                // Process the row
                T entity = processRowStreaming(row, entityCache);
                if (entity != null) {
                    currentPrimaryId = primaryId;
                    currentEntity = entity;
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Emits the final entity after all rows have been processed.
         * Must be called after the last row to ensure the final entity is emitted.
         */
        public void flush() {
            emitCurrentEntity();
        }

        private void emitCurrentEntity() {
            if (currentEntity != null) {
                entityConsumer.accept(currentEntity);
                entityCache.clear();
                currentEntity = null;
                currentPrimaryId = null;
            }
        }
    }

    /**
     * Set the maximum number of results to return.
     *
     * @param rowCount the maximum number of results
     * @return this query for method chaining
     */
    public Q setLimit(int rowCount) {
        this.setLimit(-1, rowCount);
        return self();
    }

    /**
     * Set the number of results to skip (for pagination).
     *
     * @param offset the number of results to skip
     * @param rowCount the maximum number of results
     * @return this query for method chaining
     */
    public Q setLimit(int offset, int rowCount) {
        this.query.setLimit(offset, rowCount);
        return self();
    }

    /**
     * Get the underlying SQL query object.
     *
     * @return the SqlQuery object
     */
    public SqlQuery<?> getQuery() {
        return query;
    }

    /**
     * Get the DbContext used by this query.
     *
     * @return the DbContext
     */
    public DbContext getDbContext() {
        return dbContext;
    }

    // === SQL function helper methods for generated subclasses ===

    /**
     * Helper method for building CONCAT expressions.
     * Used by generated subclasses to avoid code duplication.
     */
    protected static <C extends ConditionChain<C>> ChainableExpression<String, C> buildConcat(ChainFactory<C> chainFactory, Object... parts) {
        StringBuilder sql = new StringBuilder("CONCAT(");
        List<Object> parameters = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sql.append(", ");
            ChainableExpression.appendPart(sql, parameters, parts[i]);
        }
        sql.append(")");
        return new ChainableExpression<>(sql.toString(), parameters, chainFactory);
    }

    /**
     * Helper method for building single-argument SQL function expressions.
     * Used by generated subclasses to avoid code duplication.
     */
    protected static <V, C extends ConditionChain<C>> ChainableExpression<V, C> buildSingleArgFunction(String functionName, ChainFactory<C> chainFactory, Object part) {
        StringBuilder sql = new StringBuilder(functionName).append("(");
        List<Object> parameters = new ArrayList<>();
        ChainableExpression.appendPart(sql, parameters, part);
        sql.append(")");
        return new ChainableExpression<>(sql.toString(), parameters, chainFactory);
    }

    /**
     * Helper method for building multi-argument SQL function expressions.
     * Used by generated subclasses to avoid code duplication.
     */
    protected static <V, C extends ConditionChain<C>> ChainableExpression<V, C> buildMultiArgFunction(String functionName, ChainFactory<C> chainFactory, Object... parts) {
        StringBuilder sql = new StringBuilder(functionName).append("(");
        List<Object> parameters = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sql.append(", ");
            ChainableExpression.appendPart(sql, parameters, parts[i]);
        }
        sql.append(")");
        return new ChainableExpression<>(sql.toString(), parameters, chainFactory);
    }

    /**
     * Helper method for building SUBSTRING expressions.
     * Used by generated subclasses to avoid code duplication.
     */
    protected static <C extends ConditionChain<C>> ChainableExpression<String, C> buildSubstring(ChainFactory<C> chainFactory, Object part, int start, int len) {
        StringBuilder sql = new StringBuilder("SUBSTRING(");
        List<Object> parameters = new ArrayList<>();
        ChainableExpression.appendPart(sql, parameters, part);
        sql.append(", ").append(start).append(", ").append(len).append(")");
        return new ChainableExpression<>(sql.toString(), parameters, chainFactory);
    }
}
