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

    // Prefixed with underscore to avoid clashes with entity field names
    protected final List<ConditionPart> _parts = new ArrayList<>();
    protected String _nextOperator = null;

    /**
     * Returns self for fluent chaining (to be overridden by generated subclasses).
     */
    @SuppressWarnings("unchecked")
    protected W self() {
        return (W) this;
    }

    /**
     * Creates a new chain result after a condition is added.
     */
    protected abstract R createChainResult();

    /**
     * Returns the default operator for the next condition ("AND" or "OR").
     */
    protected abstract String getDefaultNextOperator();

    /**
     * Adds a condition part (called by field builders).
     */
    @SuppressWarnings("unchecked")
    protected void addCondition(Condition<?> condition) {
        _parts.add(new ConditionPart(_nextOperator, (Condition<E>) condition));
        _nextOperator = getDefaultNextOperator();
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
     * Internal class to hold condition parts with their joining operator.
     */
    protected class ConditionPart {
        final String operator; // null for first, "AND" or "OR" for subsequent
        final Condition<E> condition;

        ConditionPart(String operator, Condition<E> condition) {
            this.operator = operator;
            this.condition = condition;
        }
    }

    // === Inner classes for chain field access ===

    /**
     * Chain field wrapper for basic (non-comparable) fields.
     * Provides fluent methods for building conditions.
     */
    public class ChainField<T> {
        protected final QueryField<E, T> field;

        public ChainField(QueryField<E, T> field) {
            this.field = field;
        }

        /**
         * Adds an equality condition: field = value
         */
        public R eq(T value) {
            addCondition(field.eq(value));
            return createChainResult();
        }

        /**
         * Alias for {@link #eq(Object)} - adds an equality condition.
         */
        public R is(T value) {
            return eq(value);
        }

        /**
         * Adds a not-equal condition: field != value
         */
        public R ne(T value) {
            addCondition(field.ne(value));
            return createChainResult();
        }

        /**
         * Alias for {@link #ne(Object)} - adds a not-equal condition.
         */
        public R isNot(T value) {
            return ne(value);
        }

        /**
         * Adds an IS NULL condition.
         */
        public R isNull() {
            addCondition(field.isNull());
            return createChainResult();
        }

        /**
         * Adds an IS NOT NULL condition.
         */
        public R isNotNull() {
            addCondition(field.isNotNull());
            return createChainResult();
        }

        /**
         * Adds a LIKE condition: field LIKE pattern
         */
        public R like(String pattern) {
            addCondition(field.like(pattern));
            return createChainResult();
        }

        /**
         * Adds a NOT LIKE condition.
         */
        public R notLike(String pattern) {
            addCondition(field.notLike(pattern));
            return createChainResult();
        }

        /**
         * Adds an IN condition with varargs.
         */
        @SafeVarargs
        public final R in(T... values) {
            addCondition(field.in(values));
            return createChainResult();
        }

        /**
         * Adds an IN condition with a collection.
         */
        public R in(Collection<T> values) {
            addCondition(field.in(values));
            return createChainResult();
        }

        /**
         * Adds a NOT IN condition with varargs.
         */
        @SafeVarargs
        public final R notIn(T... values) {
            addCondition(field.notIn(Arrays.asList(values)));
            return createChainResult();
        }

        /**
         * Adds a NOT IN condition with a collection.
         */
        public R notIn(Collection<T> values) {
            addCondition(field.notIn(values));
            return createChainResult();
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

        /**
         * Adds a greater-than condition: field > value
         */
        public R gt(T value) {
            addCondition(comparableField.gt(value));
            return createChainResult();
        }

        /**
         * Alias for {@link #gt(Comparable)} - greater than.
         */
        public R greaterThan(T value) {
            return gt(value);
        }

        /**
         * Adds a greater-than-or-equal condition: field >= value
         */
        public R ge(T value) {
            addCondition(comparableField.ge(value));
            return createChainResult();
        }

        /**
         * Alias for {@link #ge(Comparable)} - greater than or equal.
         */
        public R greaterThanOrEqual(T value) {
            return ge(value);
        }

        /**
         * Adds a less-than condition: field < value
         */
        public R lt(T value) {
            addCondition(comparableField.lt(value));
            return createChainResult();
        }

        /**
         * Alias for {@link #lt(Comparable)} - less than.
         */
        public R lessThan(T value) {
            return lt(value);
        }

        /**
         * Adds a less-than-or-equal condition: field <= value
         */
        public R le(T value) {
            addCondition(comparableField.le(value));
            return createChainResult();
        }

        /**
         * Alias for {@link #le(Comparable)} - less than or equal.
         */
        public R lessThanOrEqual(T value) {
            return le(value);
        }

        /**
         * Adds a BETWEEN condition: field BETWEEN lower AND upper
         */
        public R between(T lower, T upper) {
            addCondition(comparableField.between(lower, upper));
            return createChainResult();
        }

        /**
         * Adds a NOT BETWEEN condition: field NOT BETWEEN lower AND upper
         */
        public R notBetween(T lower, T upper) {
            addCondition(comparableField.notBetween(lower, upper));
            return createChainResult();
        }
    }
}
