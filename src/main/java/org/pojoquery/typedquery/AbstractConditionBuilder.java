package org.pojoquery.typedquery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Abstract base class for fluent condition building.
 *
 * <p>This class provides the common infrastructure for building WHERE conditions
 * in a fluent style, including the {@link ChainField} and {@link ComparableChainField}
 * inner classes that provide type-safe field operations.
 *
 * @param <E> The entity type
 * @param <W> The builder type (self-referential for fluent chains)
 * @param <R> The chain result type (returned after each condition)
 */
public abstract class AbstractConditionBuilder<E, W extends AbstractConditionBuilder<E, W, R>, R> {

    /** Accumulated condition parts. Prefixed with underscore to avoid clashes with entity field names. */
    protected final List<ConditionPart> _parts = new ArrayList<>();
    /** The operator for the next condition. Prefixed with underscore to avoid clashes with entity field names. */
    protected String _nextOperator = null;

    /**
     * Returns self for fluent chaining (to be overridden by generated subclasses).
     * @return this builder instance
     */
    @SuppressWarnings("unchecked")
    protected W self() {
        return (W) this;
    }

    /**
     * Creates a new chain result after a condition is added.
     * @return the chain result
     */
    protected abstract R createChainResult();

    /**
     * Returns the default operator for the next condition ("AND" or "OR").
     * @return the default operator
     */
    protected abstract String getDefaultNextOperator();

    /**
     * Adds a condition part (called by field builders).
     * @param condition the condition to add
     */
    @SuppressWarnings("unchecked")
    protected void addCondition(Condition<?> condition) {
        _parts.add(new ConditionPart(_nextOperator, (Condition<E>) condition));
        _nextOperator = getDefaultNextOperator();
    }

    /**
     * Builds the accumulated conditions into a single Condition object.
     * Returns null if no conditions have been added.
     * @return the combined condition, or null if no conditions exist
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
     * Internal class to hold condition parts with their joining operator.
     */
    protected class ConditionPart {
        /** The operator (null for first, "AND" or "OR" for subsequent). */
        final String operator;
        /** The condition. */
        final Condition<E> condition;

        /**
         * Creates a new condition part.
         * @param operator the joining operator
         * @param condition the condition
         */
        ConditionPart(String operator, Condition<E> condition) {
            this.operator = operator;
            this.condition = condition;
        }
    }

    // === Inner classes for chain field access ===

    /**
     * Chain field wrapper for basic (non-comparable) fields.
     * Provides fluent methods for building conditions.
     *
     * @param <T> the field type
     */
    public class ChainField<T> {
        /** The wrapped query field. */
        protected final QueryField<E, T> field;

        /**
         * Creates a new chain field wrapper.
         * @param field the query field to wrap
         */
        public ChainField(QueryField<E, T> field) {
            this.field = field;
        }

        /**
         * Adds an equality condition: field = value
         * @param value the value to compare
         * @return the chain result for further chaining
         */
        public R eq(T value) {
            addCondition(field.eq(value));
            return createChainResult();
        }

        /**
         * Alias for {@link #eq(Object)} - adds an equality condition.
         * @param value the value to compare
         * @return the chain result for further chaining
         */
        public R is(T value) {
            return eq(value);
        }

        /**
         * Adds a not-equal condition: field != value
         * @param value the value to compare
         * @return the chain result for further chaining
         */
        public R ne(T value) {
            addCondition(field.ne(value));
            return createChainResult();
        }

        /**
         * Alias for {@link #ne(Object)} - adds a not-equal condition.
         * @param value the value to compare
         * @return the chain result for further chaining
         */
        public R isNot(T value) {
            return ne(value);
        }

        /**
         * Adds an IS NULL condition.
         * @return the chain result for further chaining
         */
        public R isNull() {
            addCondition(field.isNull());
            return createChainResult();
        }

        /**
         * Adds an IS NOT NULL condition.
         * @return the chain result for further chaining
         */
        public R isNotNull() {
            addCondition(field.isNotNull());
            return createChainResult();
        }

        /**
         * Adds a LIKE condition: field LIKE pattern
         * @param pattern the LIKE pattern
         * @return the chain result for further chaining
         */
        public R like(String pattern) {
            addCondition(field.like(pattern));
            return createChainResult();
        }

        /**
         * Adds a NOT LIKE condition.
         * @param pattern the LIKE pattern
         * @return the chain result for further chaining
         */
        public R notLike(String pattern) {
            addCondition(field.notLike(pattern));
            return createChainResult();
        }

        /**
         * Adds an IN condition with varargs.
         * @param values the values to check against
         * @return the chain result for further chaining
         */
        @SafeVarargs
        public final R in(T... values) {
            addCondition(field.in(values));
            return createChainResult();
        }

        /**
         * Adds an IN condition with a collection.
         * @param values the values to check against
         * @return the chain result for further chaining
         */
        public R in(Collection<T> values) {
            addCondition(field.in(values));
            return createChainResult();
        }

        /**
         * Adds a NOT IN condition with varargs.
         * @param values the values to check against
         * @return the chain result for further chaining
         */
        @SafeVarargs
        public final R notIn(T... values) {
            addCondition(field.notIn(Arrays.asList(values)));
            return createChainResult();
        }

        /**
         * Adds a NOT IN condition with a collection.
         * @param values the values to check against
         * @return the chain result for further chaining
         */
        public R notIn(Collection<T> values) {
            addCondition(field.notIn(values));
            return createChainResult();
        }
    }

    /**
     * Chain field wrapper for Comparable fields (adds comparison operators).
     *
     * @param <T> the comparable field type
     */
    public class ComparableChainField<T extends Comparable<? super T>> extends ChainField<T> {
        private final ComparableQueryField<E, T> comparableField;

        /**
         * Creates a new comparable chain field wrapper.
         * @param field the comparable query field to wrap
         */
        public ComparableChainField(ComparableQueryField<E, T> field) {
            super(field);
            this.comparableField = field;
        }

        /**
         * Adds a greater-than condition: field &gt; value
         * @param value the value to compare
         * @return the chain result for further chaining
         */
        public R gt(T value) {
            addCondition(comparableField.gt(value));
            return createChainResult();
        }

        /**
         * Alias for {@link #gt(Comparable)} - greater than.
         * @param value the value to compare
         * @return the chain result for further chaining
         */
        public R greaterThan(T value) {
            return gt(value);
        }

        /**
         * Adds a greater-than-or-equal condition: field &gt;= value
         * @param value the value to compare
         * @return the chain result for further chaining
         */
        public R ge(T value) {
            addCondition(comparableField.ge(value));
            return createChainResult();
        }

        /**
         * Alias for {@link #ge(Comparable)} - greater than or equal.
         * @param value the value to compare
         * @return the chain result for further chaining
         */
        public R greaterThanOrEqual(T value) {
            return ge(value);
        }

        /**
         * Adds a less-than condition: field &lt; value
         * @param value the value to compare
         * @return the chain result for further chaining
         */
        public R lt(T value) {
            addCondition(comparableField.lt(value));
            return createChainResult();
        }

        /**
         * Alias for {@link #lt(Comparable)} - less than.
         * @param value the value to compare
         * @return the chain result for further chaining
         */
        public R lessThan(T value) {
            return lt(value);
        }

        /**
         * Adds a less-than-or-equal condition: field &lt;= value
         * @param value the value to compare
         * @return the chain result for further chaining
         */
        public R le(T value) {
            addCondition(comparableField.le(value));
            return createChainResult();
        }

        /**
         * Alias for {@link #le(Comparable)} - less than or equal.
         * @param value the value to compare
         * @return the chain result for further chaining
         */
        public R lessThanOrEqual(T value) {
            return le(value);
        }

        /**
         * Adds a BETWEEN condition: field BETWEEN lower AND upper
         * @param lower the lower bound
         * @param upper the upper bound
         * @return the chain result for further chaining
         */
        public R between(T lower, T upper) {
            addCondition(comparableField.between(lower, upper));
            return createChainResult();
        }

        /**
         * Adds a NOT BETWEEN condition: field NOT BETWEEN lower AND upper
         * @param lower the lower bound
         * @param upper the upper bound
         * @return the chain result for further chaining
         */
        public R notBetween(T lower, T upper) {
            addCondition(comparableField.notBetween(lower, upper));
            return createChainResult();
        }
    }
}
