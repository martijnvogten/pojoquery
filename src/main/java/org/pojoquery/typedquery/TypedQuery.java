package org.pojoquery.typedquery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;
import org.pojoquery.pipeline.SqlQuery;

/**
 * Base class for generated type-safe query builders.
 *
 * <p>Generated query classes extend this base to provide a fluent API for
 * building and executing queries with compile-time type safety.
 *
 * <p>Subclasses must implement:
 * <ul>
 *   <li>{@link #initializeQuery()} - set up fields and joins</li>
 *   <li>{@link #getEntityClass()} - return the entity class</li>
 *   <li>{@link #processRowsToEntities(List)} - map raw rows to entities</li>
 * </ul>
 *
 * @param <E> the entity type this query returns
 * @param <Q> the query type itself (for fluent chaining)
 */
public abstract class TypedQuery<E, Q extends TypedQuery<E, Q>> implements WhereTarget<Q> {

    protected final SqlQuery<?> query;
    protected final DbContext dbContext;

    /**
     * Creates a new TypedQuery.
     *
     * @param tableName the main table name (also used as alias)
     */
    protected TypedQuery(String tableName) {
        this(DbContext.getDefault(), null, tableName);
    }

    /**
     * Creates a new TypedQuery with schema.
     *
     * @param schemaName the database schema (can be null)
     * @param tableName  the main table name (also used as alias)
     */
    protected TypedQuery(String schemaName, String tableName) {
        this(DbContext.getDefault(), schemaName, tableName);
    }

    /**
     * Creates a new TypedQuery with explicit DbContext.
     *
     * @param dbContext  the database context
     * @param schemaName the database schema (can be null)
     * @param tableName  the main table name (also used as alias)
     */
    protected TypedQuery(DbContext dbContext, String schemaName, String tableName) {
        this.dbContext = dbContext;
        this.query = new DefaultSqlQuery(dbContext);
        this.query.setTable(schemaName, tableName);
        initializeQuery();
    }

    /**
     * Override this method to add fields, joins, etc. to the query.
     * Called by the constructor after basic setup.
     */
    protected abstract void initializeQuery();

    /**
     * Returns the entity class this query operates on.
     */
    protected abstract Class<E> getEntityClass();

    /**
     * Returns this query cast to the concrete type for fluent chaining.
     */
    @SuppressWarnings("unchecked")
    protected Q self() {
        return (Q) this;
    }

    // === WHERE clause methods ===

    /**
     * Begins a type-safe WHERE condition for the given field.
     *
     * @param field the field to filter on
     * @param <T>   the field type
     * @return a WhereClause builder for the condition
     */
    public <T> WhereClause<E, T, Q> where(QueryField<E, T> field) {
        return new WhereClause<>(self(), field);
    }

    /**
     * Begins a type-safe WHERE condition for a comparable field.
     * This overload provides additional comparison methods like greaterThan(), lessThan(), between().
     *
     * @param field the comparable field to filter on
     * @param <T>   the field type (must be Comparable)
     * @return a ComparableWhereClause builder with comparison operations
     */
    public <T extends Comparable<? super T>> ComparableWhereClause<E, T, Q> where(ComparableQueryField<E, T> field) {
        return new ComparableWhereClause<>(self(), field);
    }

    /**
     * Adds a raw WHERE condition.
     *
     * @param sql    the SQL condition
     * @param params the parameters
     * @return this query for chaining
     */
    @Override
    public Q addWhere(String sql, Object... params) {
        query.addWhere(sql, params);
        return self();
    }

    /**
     * Adds a WHERE condition from a SqlExpression.
     *
     * @param expression the SQL expression
     * @return this query for chaining
     */
    @Override
    public Q addWhere(SqlExpression expression) {
        query.addWhere(expression);
        return self();
    }

    /**
     * Adds a composed condition to the WHERE clause (jOOQ-style).
     *
     * <p>Example:
     * <pre>{@code
     * Condition<Employee> nameFilter = lastName.eq("Smith").or(lastName.eq("Johnson"));
     * Condition<Employee> salaryFilter = salary.gt(50000);
     * 
     * new EmployeeQuery()
     *     .where(nameFilter.and(salaryFilter))
     *     .list(connection);
     * }</pre>
     *
     * @param condition the composed condition
     * @return this query for chaining
     */
    public Q where(Condition<E> condition) {
        query.addWhere(condition.toExpression());
        return self();
    }

    /**
     * Begins an OR clause group for combining conditions with OR logic.
     *
     * <p>Example:
     * <pre>{@code
     * new EmployeeQuery()
     *     .begin()
     *         .where(lastName).is("Smith")
     *         .or(lastName).is("Johnson")
     *     .end()
     *     .orderBy(firstName)
     *     .list(connection);
     * }</pre>
     *
     * <p>This generates: {@code WHERE (last_name = 'Smith' OR last_name = 'Johnson')}
     *
     * @return a SubClauseBuilder for building the sub-clause group
     */
    public SubClauseBuilder<E, Q> begin() {
        return new SubClauseBuilder<>(self());
    }

    // === ORDER BY methods ===

    /**
     * Adds an ascending ORDER BY clause for the given field.
     *
     * @param field the field to order by
     * @return this query for chaining
     */
    public Q orderBy(QueryField<E, ?> field) {
        query.addOrderBy(field.getQualifiedColumn() + " ASC");
        return self();
    }

    /**
     * Adds an ascending ORDER BY clause for the given field.
     *
     * @param field the field to order by
     * @return this query for chaining
     */
    public Q orderByAsc(QueryField<E, ?> field) {
        query.addOrderBy(field.getQualifiedColumn() + " ASC");
        return self();
    }

    /**
     * Adds a descending ORDER BY clause for the given field.
     *
     * @param field the field to order by
     * @return this query for chaining
     */
    public Q orderByDesc(QueryField<E, ?> field) {
        query.addOrderBy(field.getQualifiedColumn() + " DESC");
        return self();
    }

    /**
     * Adds a raw ORDER BY clause.
     *
     * @param orderBy the ORDER BY expression
     * @return this query for chaining
     */
    public Q addOrderBy(String orderBy) {
        query.addOrderBy(orderBy);
        return self();
    }

    // === LIMIT methods ===

    /**
     * Sets the maximum number of rows to return.
     *
     * @param rowCount the maximum number of rows
     * @return this query for chaining
     */
    public Q limit(int rowCount) {
        query.setLimit(rowCount);
        return self();
    }

    /**
     * Sets the offset and limit for pagination.
     *
     * @param offset   the number of rows to skip
     * @param rowCount the maximum number of rows to return
     * @return this query for chaining
     */
    public Q limit(int offset, int rowCount) {
        query.setLimit(offset, rowCount);
        return self();
    }

    // === Execution methods ===

    /**
     * Executes the query and returns all matching entities.
     *
     * @param dataSource the data source
     * @return list of matching entities
     */
    public List<E> list(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            return list(conn);
        } catch (SQLException e) {
            throw new DB.DatabaseException(e);
        }
    }

    /**
     * Executes the query and returns all matching entities.
     *
     * @param connection the database connection
     * @return list of matching entities
     */
    public List<E> list(Connection connection) {
        SqlExpression stmt = query.toStatement();
        List<Map<String, Object>> rows = DB.queryRows(connection, stmt);
        return processRowsToEntities(rows);
    }

    /**
     * Executes the query and returns the first matching entity, or null if none found.
     * 
     * <p>For entities with relationships (collections), this method streams the results
     * and returns the first complete entity with all its related entities populated.
     * Unlike a naive LIMIT 1 approach, this ensures the entity has all its joined
     * collection items.</p>
     *
     * @param dataSource the data source
     * @return the first matching entity, or null
     */
    public E first(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            return first(conn);
        } catch (SQLException e) {
            throw new DB.DatabaseException(e);
        }
    }

    /**
     * Executes the query and returns the first matching entity, or null if none found.
     * 
     * <p>For entities with relationships (collections), this method streams the results
     * and returns the first complete entity with all its related entities populated.
     * Unlike a naive LIMIT 1 approach, this ensures the entity has all its joined
     * collection items.</p>
     *
     * @param connection the database connection
     * @return the first matching entity, or null
     */
    public E first(Connection connection) {
        // Use streaming to get the first complete entity (including all related items)
        List<E> holder = new ArrayList<>(1);
        streamInternal(connection, entity -> {
            if (holder.isEmpty()) {
                holder.add(entity);
            }
        }, true); // stopAfterFirst = true
        return holder.isEmpty() ? null : holder.get(0);
    }

    /**
     * Streams results through a callback for memory-efficient processing of large result sets.
     * 
     * <p>Each entity is emitted only after all its related entities (from joined collections)
     * have been accumulated. This ensures the callback receives complete entity graphs.</p>
     * 
     * <p><strong>Ordering behavior:</strong> Your ORDER BY clause is respected, and the primary
     * entity's ID is automatically appended as a tiebreaker to ensure all rows for the same
     * entity stay grouped together.</p>
     *
     * @param dataSource the data source
     * @param callback   the callback to process each entity
     */
    public void stream(DataSource dataSource, Consumer<E> callback) {
        try (Connection conn = dataSource.getConnection()) {
            stream(conn, callback);
        } catch (SQLException e) {
            throw new DB.DatabaseException(e);
        }
    }

    /**
     * Streams results through a callback for memory-efficient processing of large result sets.
     * 
     * <p>Each entity is emitted only after all its related entities (from joined collections)
     * have been accumulated. This ensures the callback receives complete entity graphs.</p>
     * 
     * <p><strong>Ordering behavior:</strong> Your ORDER BY clause is respected, and the primary
     * entity's ID is automatically appended as a tiebreaker to ensure all rows for the same
     * entity stay grouped together.</p>
     *
     * @param connection the database connection
     * @param callback   the callback to process each entity
     */
    public void stream(Connection connection, Consumer<E> callback) {
        streamInternal(connection, callback, false);
    }

    /**
     * Internal streaming implementation that processes rows and emits complete entities.
     * 
     * <p>This method ensures ORDER BY includes the primary ID for proper grouping,
     * then streams rows and accumulates them into complete entities before emitting.</p>
     *
     * @param connection the database connection
     * @param callback   the callback to process each entity
     * @param stopAfterFirst if true, stops processing after the first entity is emitted
     */
    protected void streamInternal(Connection connection, Consumer<E> callback, boolean stopAfterFirst) {
        // Ensure ORDER BY ends with primary ID for proper grouping
        ensureOrderByPrimaryId();
        
        SqlExpression stmt = query.toStatement();
        
        // Use the streaming row handler pattern
        StreamingEntityBuilder builder = new StreamingEntityBuilder(callback, stopAfterFirst);
        
        DB.queryRowsStreaming(connection, stmt, builder);
        
        // Emit the final entity if any
        builder.flush();
    }

    /**
     * Ensures the query ORDER BY ends with the primary entity's ID field(s).
     * This is required for streaming to work correctly - all rows for the same
     * entity must be consecutive in the result set.
     */
    protected void ensureOrderByPrimaryId() {
        String idOrderBy = getIdOrderByClause();
        if (idOrderBy != null) {
            List<String> currentOrderBy = query.getOrderBy();
            // Check if ID is already in ORDER BY
            boolean hasIdOrderBy = currentOrderBy.stream()
                .anyMatch(o -> o.contains(idOrderBy.replace(" ASC", "")));
            if (!hasIdOrderBy) {
                query.addOrderBy(idOrderBy);
            }
        }
    }

    /**
     * Returns the ORDER BY clause for the primary ID field(s).
     * Subclasses should override this to provide the correct clause.
     * 
     * @return the ID ORDER BY clause (e.g., "{employee.id} ASC"), or null if not applicable
     */
    protected String getIdOrderByClause() {
        return null; // Generated subclasses will override this
    }

    /**
     * Returns the primary ID value from a row for entity grouping during streaming.
     * Subclasses should override this to extract the correct ID.
     *
     * @param row the row data
     * @return the primary ID value, or null if not found
     */
    protected Object getPrimaryIdFromRow(Map<String, Object> row) {
        return null; // Generated subclasses will override this
    }

    /**
     * Processes a list of raw rows and returns mapped entities.
     * Generated subclasses implement this via processRows().
     *
     * @param rows the raw row data
     * @return list of mapped entities
     */
    protected List<E> processRowsToEntities(List<Map<String, Object>> rows) {
        // Default implementation uses list() behavior
        // Generated subclasses override this
        throw new UnsupportedOperationException("Subclass must implement processRowsToEntities");
    }

    /**
     * Helper class that accumulates rows and emits complete entities.
     */
    protected class StreamingEntityBuilder implements Consumer<Map<String, Object>> {
        private final Consumer<E> entityCallback;
        private final boolean stopAfterFirst;
        private Object currentPrimaryId = null;
        private List<Map<String, Object>> currentRows = new ArrayList<>();
        private boolean stopped = false;

        StreamingEntityBuilder(Consumer<E> entityCallback, boolean stopAfterFirst) {
            this.entityCallback = entityCallback;
            this.stopAfterFirst = stopAfterFirst;
        }

        @Override
        public void accept(Map<String, Object> row) {
            if (stopped) return;
            
            Object primaryId = getPrimaryIdFromRow(row);
            
            // If primary ID changed, emit the current entity
            if (currentPrimaryId != null && primaryId != null && !currentPrimaryId.equals(primaryId)) {
                emitCurrentEntity();
                if (stopped) return;
            }
            
            currentPrimaryId = primaryId;
            currentRows.add(row);
        }

        public void flush() {
            if (!stopped) {
                emitCurrentEntity();
            }
        }

        private void emitCurrentEntity() {
            if (!currentRows.isEmpty()) {
                List<E> entities = processRowsToEntities(currentRows);
                if (!entities.isEmpty()) {
                    entityCallback.accept(entities.get(0));
                    if (stopAfterFirst) {
                        stopped = true;
                    }
                }
                currentRows.clear();
                currentPrimaryId = null;
            }
        }
    }

    /**
     * Returns the generated SQL statement (useful for debugging).
     *
     * @return the SQL statement
     */
    public String toSql() {
        return query.toStatement().getSql();
    }

    /**
     * Returns the underlying SqlQuery for advanced customization.
     */
    public SqlQuery<?> getQuery() {
        return query;
    }
}
