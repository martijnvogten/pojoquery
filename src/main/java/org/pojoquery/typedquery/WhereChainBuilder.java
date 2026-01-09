package org.pojoquery.typedquery;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for fluent where clause chain building.
 * Supports patterns like: where().lastName.eq("Smith").or().firstName.eq("John")
 *
 * @param <E> The entity type
 * @param <Q> The query type (for fluent return)
 * @param <W> The where builder type (self-referential for fluent chains)
 */
public abstract class WhereChainBuilder<E, Q extends TypedQuery<E, Q>, W extends WhereChainBuilder<E, Q, W>> {

    // Prefixed with underscore to avoid clashes with entity field names
    protected final Q _query;
    protected final List<ConditionPart> _parts = new ArrayList<>();
    protected String _nextOperator = null; // null for first, "AND" or "OR" for subsequent

    protected WhereChainBuilder(Q query) {
        this._query = query;
    }

    /**
     * Returns self for fluent chaining (to be overridden by generated subclasses)
     */
    @SuppressWarnings("unchecked")
    protected W self() {
        return (W) this;
    }

    /**
     * Adds a condition part (called by field builders)
     */
    @SuppressWarnings("unchecked")
    protected void addCondition(Condition<?> condition) {
        _parts.add(new ConditionPart(_nextOperator, (Condition<E>) condition));
        _nextOperator = "AND"; // default to AND for next condition
    }

    /**
     * Builds the accumulated conditions into a single Condition object.
     * Returns null if no conditions have been added.
     */
    protected Condition<E> buildCondition() {
        if (_parts.isEmpty()) {
            return null;
        }

        Condition<E> result = _parts.get(0).condition;
        for (int i = 1; i < _parts.size(); i++) {
            ConditionPart part = _parts.get(i);
            if ("OR".equals(part.operator)) {
                result = result.or(part.condition);
            } else {
                result = result.and(part.condition);
            }
        }
        return result;
    }

    /**
     * Builds the final condition and applies it to the query.
     */
    protected Q build() {
        Condition<E> condition = buildCondition();
        if (condition == null) {
            return _query;
        }
        return _query.where(condition);
    }

    // Terminal methods that finish the chain and return to the query

    public Q and(Condition<E> condition) {
        _nextOperator = _parts.isEmpty() ? null : "AND";
        addCondition(condition);
        return build();
    }

    public Q or(Condition<E> condition) {
        _nextOperator = _parts.isEmpty() ? null : "OR";
        addCondition(condition);
        return build();
    }

    public java.util.List<E> list(java.sql.Connection connection) {
        return build().list(connection);
    }

    public java.util.List<E> list(javax.sql.DataSource dataSource) {
        return build().list(dataSource);
    }

    public void stream(java.sql.Connection connection, java.util.function.Consumer<E> callback) {
        build().stream(connection, callback);
    }

    public void stream(javax.sql.DataSource dataSource, java.util.function.Consumer<E> callback) {
        build().stream(dataSource, callback);
    }

    public E first(java.sql.Connection connection) {
        return build().first(connection);
    }

    public E first(javax.sql.DataSource dataSource) {
        return build().first(dataSource);
    }

    public String toSql() {
        return build().toSql();
    }

    public Q orderBy(QueryField<E, ?> field) {
        return build().orderBy(field);
    }

    public Q orderByDesc(QueryField<E, ?> field) {
        return build().orderByDesc(field);
    }

    public Q limit(int limit) {
        return build().limit(limit);
    }

    public Q limit(int offset, int rowCount) {
        return build().limit(offset, rowCount);
    }

    /**
     * Internal class to hold condition parts with their joining operator
     */
    protected class ConditionPart {
        final String operator; // null for first, "AND" or "OR" for subsequent
        final Condition<E> condition;

        ConditionPart(String operator, Condition<E> condition) {
            this.operator = operator;
            this.condition = condition;
        }
    }

    /**
     * Chain result that allows .or() or .and() to continue, or terminal operations.
     */
    public class ChainResult {
        
        public W or() {
            _nextOperator = "OR";
            return self();
        }

        public W and() {
            _nextOperator = "AND";
            return self();
        }

        /**
         * Extracts the accumulated conditions as a standalone {@link Condition} object.
         * This allows building reusable conditions without name clashes from static imports.
         * 
         * <pre>{@code
         * // Build a reusable condition
         * Condition<Employee> emailFilter = new EmployeeQuery()
         *     .where().email.like("%@example.com").toCondition();
         * 
         * // Use it in queries
         * new EmployeeQuery().where(emailFilter).list(connection);
         * }</pre>
         * 
         * @return the accumulated condition
         */
        public Condition<E> toCondition() {
            return buildCondition();
        }

        // Terminal methods
        public java.util.List<E> list(java.sql.Connection connection) {
            return build().list(connection);
        }

        public java.util.List<E> list(javax.sql.DataSource dataSource) {
            return build().list(dataSource);
        }

        public void stream(java.sql.Connection connection, java.util.function.Consumer<E> callback) {
            build().stream(connection, callback);
        }

        public void stream(javax.sql.DataSource dataSource, java.util.function.Consumer<E> callback) {
            build().stream(dataSource, callback);
        }

        public E first(java.sql.Connection connection) {
            return build().first(connection);
        }

        public E first(javax.sql.DataSource dataSource) {
            return build().first(dataSource);
        }

        public String toSql() {
            return build().toSql();
        }

        public Q orderBy(QueryField<E, ?> field) {
            return build().orderBy(field);
        }

        public Q orderByDesc(QueryField<E, ?> field) {
            return build().orderByDesc(field);
        }

        public Q limit(int limit) {
            return build().limit(limit);
        }

        public Q limit(int offset, int rowCount) {
            return build().limit(offset, rowCount);
        }

        public <T> WhereClause<E, T, Q> where(QueryField<E, T> field) {
            return build().where(field);
        }
        
        public Q where(Condition<E> condition) {
            return build().where(condition);
        }

        /**
         * Starts an OR clause group.
         * <p>Example: {@code query.where().firstName.like("A%").begin().where().lastName.is("Smith").end()}
         */
        public OrClauseBuilder<E, Q> begin() {
            return build().begin();
        }
    }

    /**
     * Field wrapper for building conditions in the chain.
     */
    public class ChainField<T> {
        protected final QueryField<E, T> field;

        public ChainField(QueryField<E, T> field) {
            this.field = field;
        }

        public ChainResult eq(T value) {
            addCondition(field.eq(value));
            return new ChainResult();
        }

        public ChainResult ne(T value) {
            addCondition(field.ne(value));
            return new ChainResult();
        }

        public ChainResult isNull() {
            addCondition(field.isNull());
            return new ChainResult();
        }

        public ChainResult isNotNull() {
            addCondition(field.isNotNull());
            return new ChainResult();
        }

        public ChainResult like(String pattern) {
            addCondition(field.like(pattern));
            return new ChainResult();
        }

        public ChainResult notLike(String pattern) {
            addCondition(field.notLike(pattern));
            return new ChainResult();
        }

        @SuppressWarnings("unchecked")
        public ChainResult in(T... values) {
            addCondition(field.in(values));
            return new ChainResult();
        }

        public ChainResult in(java.util.Collection<T> values) {
            addCondition(field.in(values));
            return new ChainResult();
        }

        public ChainResult notIn(java.util.Collection<T> values) {
            addCondition(field.notIn(values));
            return new ChainResult();
        }
    }

    /**
     * Comparable field wrapper with additional comparison methods.
     */
    public class ComparableChainField<T extends Comparable<? super T>> extends ChainField<T> {

        public ComparableChainField(ComparableQueryField<E, T> field) {
            super(field);
        }

        @SuppressWarnings("unchecked")
        private ComparableQueryField<E, T> comparableField() {
            return (ComparableQueryField<E, T>) field;
        }

        public ChainResult gt(T value) {
            addCondition(comparableField().gt(value));
            return new ChainResult();
        }

        public ChainResult greaterThan(T value) {
            return gt(value);
        }

        public ChainResult ge(T value) {
            addCondition(comparableField().ge(value));
            return new ChainResult();
        }

        public ChainResult greaterThanOrEqual(T value) {
            return ge(value);
        }

        public ChainResult lt(T value) {
            addCondition(comparableField().lt(value));
            return new ChainResult();
        }

        public ChainResult lessThan(T value) {
            return lt(value);
        }

        public ChainResult le(T value) {
            addCondition(comparableField().le(value));
            return new ChainResult();
        }

        public ChainResult lessThanOrEqual(T value) {
            return le(value);
        }

        public ChainResult between(T from, T to) {
            addCondition(comparableField().between(from, to));
            return new ChainResult();
        }

        public ChainResult notBetween(T from, T to) {
            addCondition(comparableField().notBetween(from, to));
            return new ChainResult();
        }
    }
}
