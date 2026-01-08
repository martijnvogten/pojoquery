package org.pojoquery.typedquery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
 *   <li>{@link #mapRow(ResultSet)} - efficient direct ResultSet mapping</li>
 *   <li>{@link #getEntityClass()} - return the entity class</li>
 * </ul>
 *
 * @param <E> the entity type this query returns
 * @param <Q> the query type itself (for fluent chaining)
 */
public abstract class TypedQuery<E, Q extends TypedQuery<E, Q>> {

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
     * Maps a single row from the ResultSet to an entity.
     * Generated implementations read columns directly without reflection.
     *
     * @param rs the ResultSet positioned at the current row
     * @return the mapped entity
     * @throws SQLException if a database access error occurs
     */
    protected abstract E mapRow(ResultSet rs) throws SQLException;

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
     * Adds a raw WHERE condition.
     *
     * @param sql    the SQL condition
     * @param params the parameters
     * @return this query for chaining
     */
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
    public Q addWhere(SqlExpression expression) {
        query.addWhere(expression);
        return self();
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
        return executeQuery(connection, stmt);
    }

    /**
     * Executes the query and returns the first matching entity, or null if none found.
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
     * @param connection the database connection
     * @return the first matching entity, or null
     */
    public E first(Connection connection) {
        query.setLimit(1);
        List<E> results = list(connection);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Streams results through a callback for memory-efficient processing of large result sets.
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
     * @param connection the database connection
     * @param callback   the callback to process each entity
     */
    public void stream(Connection connection, Consumer<E> callback) {
        SqlExpression stmt = query.toStatement();
        String sql = stmt.getSql();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (Object param : stmt.getParameters()) {
                Object converted = dbContext.convertParameterForJdbc(param);
                ps.setObject(index++, converted);
            }

            int fetchSize = dbContext.getStreamingFetchSize();
            if (fetchSize != 0) {
                ps.setFetchSize(fetchSize);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    E entity = mapRow(rs);
                    callback.accept(entity);
                }
            }
        } catch (SQLException e) {
            throw new DB.DatabaseException(e);
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

    // === Private helper methods ===

    private List<E> executeQuery(Connection connection, SqlExpression stmt) {
        String sql = stmt.getSql();
        List<E> results = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (Object param : stmt.getParameters()) {
                Object converted = dbContext.convertParameterForJdbc(param);
                ps.setObject(index++, converted);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    E entity = mapRow(rs);
                    results.add(entity);
                }
            }
        } catch (SQLException e) {
            throw new DB.DatabaseException(e);
        }

        return results;
    }
}
