package org.pojoquery.typedquery;

/**
 * Base class for fluent where clause chain building.
 * Supports patterns like: where().lastName.eq("Smith").or().firstName.eq("John")
 * 
 * <p>Also supports grouped conditions with begin()/end():
 * <pre>{@code
 * query.where()
 *     .begin().firstName.eq("John").and().lastName.eq("Smith").end()
 *     .or()
 *     .begin().firstName.eq("Jane").and().lastName.eq("Doe").end()
 * }</pre>
 *
 * @param <E> The entity type
 * @param <Q> The query type (for fluent return)
 * @param <W> The where builder type (self-referential for fluent chains)
 * @param <I> The inline sub-clause builder type (for begin() return - has direct field access)
 */
public abstract class WhereChainBuilder<E, Q extends TypedQuery<E, Q>, W extends WhereChainBuilder<E, Q, W, I>, I extends InlineSubClauseBuilder<E, Q, W, I>>
        extends AbstractConditionBuilder<E, W, WhereChainBuilder<E, Q, W, I>.ChainResult> {

    // Prefixed with underscore to avoid clashes with entity field names
    protected final Q _query;

    protected WhereChainBuilder(Q query) {
        this._query = query;
    }

    @Override
    protected String getDefaultNextOperator() {
        return "AND";
    }

    @Override
    protected ChainResult createChainResult() {
        return new ChainResult();
    }

    /**
     * Returns the inline sub-clause builder for begin(). Subclasses override to return specific type.
     * This allows direct field access: begin().lastName.eq("Smith").and().firstName.eq("John").end()
     */
    protected abstract I createInlineSubClauseBuilder();

    /**
     * Starts a sub-clause group (parenthesized conditions) with direct field access.
     * Can be called at the start of a where chain.
     * <p>Example: {@code query.where().begin().lastName.is("Smith").or().lastName.is("Jones").end()}
     */
    public I begin() {
        return createInlineSubClauseBuilder();
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

    public void stream(java.sql.Connection connection, java.util.function.Consumer<E> callback) {
        build().stream(connection, callback);
    }

    public E first(java.sql.Connection connection) {
        return build().first(connection);
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

    public Q limit(int rowCount) {
        return build().limit(rowCount);
    }

    public Q limit(int offset, int rowCount) {
        return build().limit(offset, rowCount);
    }

    /**
     * Chain result that allows .or() or .and() to continue, or terminal operations.
     * Terminal methods delegate to the enclosing WhereChainBuilder to avoid duplication.
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

        // Terminal methods - delegate to enclosing class
        public java.util.List<E> list(java.sql.Connection connection) {
            return WhereChainBuilder.this.list(connection);
        }

        public void stream(java.sql.Connection connection, java.util.function.Consumer<E> callback) {
            WhereChainBuilder.this.stream(connection, callback);
        }

        public E first(java.sql.Connection connection) {
            return WhereChainBuilder.this.first(connection);
        }

        public String toSql() {
            return WhereChainBuilder.this.toSql();
        }

        public Q orderBy(QueryField<E, ?> field) {
            return WhereChainBuilder.this.orderBy(field);
        }

        public Q orderByDesc(QueryField<E, ?> field) {
            return WhereChainBuilder.this.orderByDesc(field);
        }

        public Q limit(int rowCount) {
            return WhereChainBuilder.this.limit(rowCount);
        }

        public Q limit(int offset, int rowCount) {
            return WhereChainBuilder.this.limit(offset, rowCount);
        }

        public <T> WhereClause<E, T, Q> where(QueryField<E, T> field) {
            return build().where(field);
        }
        
        public Q where(Condition<E> condition) {
            return build().where(condition);
        }

        /**
         * Starts a sub-clause group (parenthesized conditions) with direct field access.
         * <p>Example: {@code query.where().firstName.like("A%").and().begin().lastName.is("Smith").or().lastName.is("Jones").end()}
         */
        public I begin() {
            return createInlineSubClauseBuilder();
        }
    }
}
