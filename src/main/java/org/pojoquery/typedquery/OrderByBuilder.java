package org.pojoquery.typedquery;

import java.sql.Connection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base class for fluent ORDER BY clause building.
 * Supports patterns like: {@code orderBy().lastName.asc().firstName.desc()}
 *
 * @param <E> The entity type
 * @param <Q> The query type (for fluent return)
 * @param <O> The order by builder type (self-referential for fluent chains)
 */
public abstract class OrderByBuilder<E, Q extends TypedQuery<E, Q>, O extends OrderByBuilder<E, Q, O>> {

    // Prefixed with underscore to avoid clashes with entity field names
    protected final Q _query;

    protected OrderByBuilder(Q query) {
        this._query = query;
    }

    /**
     * Returns self for fluent chaining (to be overridden by generated subclasses).
     * @return this builder instance
     */
    @SuppressWarnings("unchecked")
    protected O self() {
        return (O) this;
    }

    // === Inner classes for chain field access ===

    /**
     * Chain field wrapper for ORDER BY operations.
     * Provides fluent methods for specifying sort direction.
     *
     * @param <T> the field type
     */
    public class OrderByField<T> {
        /** The wrapped query field. */
        protected final QueryField<E, T> field;

        /**
         * Creates a new order by field wrapper.
         * @param field the query field to wrap
         */
        public OrderByField(QueryField<E, T> field) {
            this.field = field;
        }

        /**
         * Adds an ascending ORDER BY clause for this field.
         * @return the order by builder for further chaining
         */
        public O asc() {
            _query.orderByAsc(field);
            return self();
        }

        /**
         * Adds a descending ORDER BY clause for this field.
         * @return the order by builder for further chaining
         */
        public O desc() {
            _query.orderByDesc(field);
            return self();
        }
    }

    // === Terminal methods ===

    /**
     * Executes the query and returns all matching entities.
     * @param connection the database connection
     * @return list of matching entities
     */
    public List<E> list(Connection connection) {
        return _query.list(connection);
    }

    /**
     * Executes the query and streams results to the callback.
     * @param connection the database connection
     * @param callback the callback to process each entity
     */
    public void stream(Connection connection, Consumer<E> callback) {
        _query.stream(connection, callback);
    }

    /**
     * Executes the query and returns the first matching entity.
     * @param connection the database connection
     * @return the first entity, or null if none found
     */
    public E first(Connection connection) {
        return _query.first(connection);
    }

    /**
     * Returns the SQL string representation of the query.
     * @return the SQL string
     */
    public String toSql() {
        return _query.toSql();
    }

    /**
     * Sets the maximum number of rows to return.
     * @param rowCount the maximum number of rows
     * @return the query for chaining
     */
    public Q limit(int rowCount) {
        return _query.limit(rowCount);
    }

    /**
     * Sets the offset and limit for pagination.
     * @param offset the number of rows to skip
     * @param rowCount the maximum number of rows to return
     * @return the query for chaining
     */
    public Q limit(int offset, int rowCount) {
        return _query.limit(offset, rowCount);
    }
}
