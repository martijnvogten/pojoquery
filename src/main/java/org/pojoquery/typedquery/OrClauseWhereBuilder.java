package org.pojoquery.typedquery;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for fluent where clause building within OR groups (begin/end blocks).
 * Supports patterns like: begin().where().lastName.eq("Smith").or().firstName.eq("John").end()
 *
 * @param <E> The entity type
 * @param <Q> The query type (for returning after end())
 * @param <W> The where builder type (self-referential for fluent chains)
 */
public abstract class OrClauseWhereBuilder<E, Q extends TypedQuery<E, Q>, W extends OrClauseWhereBuilder<E, Q, W>> {

    // Prefixed with underscore to avoid clashes with entity field names
    protected final OrClauseBuilder<E, Q> _orClauseBuilder;
    protected final List<ConditionPart> _parts = new ArrayList<>();
    protected String _nextOperator = null; // null for first, "OR" for subsequent (default in OR groups)

    protected OrClauseWhereBuilder(OrClauseBuilder<E, Q> orClauseBuilder) {
        this._orClauseBuilder = orClauseBuilder;
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
    protected void addCondition(Condition<E> condition) {
        _parts.add(new ConditionPart(_nextOperator, condition));
        _nextOperator = "OR"; // default to OR for next condition (we're in an OR group)
    }

    // === Inner classes for chain field access ===

    /**
     * Chain field wrapper for basic (non-comparable) fields.
     */
    public class ChainField<T> {
        private final QueryField<E, T> field;

        public ChainField(QueryField<E, T> field) {
            this.field = field;
        }

        public ChainResult is(T value) {
            addCondition(field.eq(value));
            return new ChainResult();
        }

        public ChainResult isNot(T value) {
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

        @SafeVarargs
        public final ChainResult in(T... values) {
            addCondition(field.in(java.util.Arrays.asList(values)));
            return new ChainResult();
        }

        public ChainResult in(java.util.Collection<T> values) {
            addCondition(field.in(values));
            return new ChainResult();
        }

        @SafeVarargs
        public final ChainResult notIn(T... values) {
            addCondition(field.notIn(java.util.Arrays.asList(values)));
            return new ChainResult();
        }

        public ChainResult notIn(java.util.Collection<T> values) {
            addCondition(field.notIn(values));
            return new ChainResult();
        }
    }

    /**
     * Chain field wrapper for Comparable fields (adds comparison operators).
     */
    public class ComparableChainField<T extends Comparable<? super T>> extends ChainField<T> {
        private final ComparableQueryField<E, T> comparableField;

        public ComparableChainField(ComparableQueryField<E, T> field) {
            super(field);
            this.comparableField = field;
        }

        public ChainResult gt(T value) {
            addCondition(comparableField.gt(value));
            return new ChainResult();
        }

        public ChainResult greaterThan(T value) {
            return gt(value);
        }

        public ChainResult ge(T value) {
            addCondition(comparableField.ge(value));
            return new ChainResult();
        }

        public ChainResult greaterThanOrEqual(T value) {
            return ge(value);
        }

        public ChainResult lt(T value) {
            addCondition(comparableField.lt(value));
            return new ChainResult();
        }

        public ChainResult lessThan(T value) {
            return lt(value);
        }

        public ChainResult le(T value) {
            addCondition(comparableField.le(value));
            return new ChainResult();
        }

        public ChainResult lessThanOrEqual(T value) {
            return le(value);
        }

        public ChainResult between(T lower, T upper) {
            addCondition(comparableField.between(lower, upper));
            return new ChainResult();
        }
    }

    /**
     * Chain result that allows .or() to continue, or .end() to finish the OR group.
     */
    public class ChainResult {
        
        /**
         * Continue with another OR condition using fluent field access.
         */
        public W or() {
            _nextOperator = "OR";
            return self();
        }

        /**
         * Continue with an AND condition using fluent field access.
         * Note: AND within an OR group creates (A OR (B AND C)) semantics.
         */
        public W and() {
            _nextOperator = "AND";
            return self();
        }

        /**
         * Ends the OR group and returns to the parent query.
         * Combines all conditions with the specified operators.
         */
        public Q end() {
            return finishOrClause();
        }
    }

    /**
     * Finishes the OR clause and returns to the parent query.
     */
    protected Q finishOrClause() {
        if (_parts.isEmpty()) {
            return _orClauseBuilder.end();
        }

        // Build the combined condition
        Condition<E> result = _parts.get(0).condition;
        for (int i = 1; i < _parts.size(); i++) {
            ConditionPart part = _parts.get(i);
            if ("AND".equals(part.operator)) {
                result = result.and(part.condition);
            } else {
                result = result.or(part.condition);
            }
        }

        // Add to the parent builder and end
        _orClauseBuilder.addCondition(result);
        return _orClauseBuilder.endInternal();
    }

    /**
     * Internal class to hold condition and its operator.
     */
    protected class ConditionPart {
        final String operator; // null, "AND", or "OR"
        final Condition<E> condition;

        ConditionPart(String operator, Condition<E> condition) {
            this.operator = operator;
            this.condition = condition;
        }
    }
}
