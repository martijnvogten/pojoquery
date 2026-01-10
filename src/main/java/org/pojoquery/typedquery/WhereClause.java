package org.pojoquery.typedquery;

import java.util.Collection;

/**
 * Builder for type-safe WHERE clause conditions.
 *
 * <p>This class provides a fluent API for building WHERE conditions with
 * type safety. It's returned by {@code query.where(field)} and allows
 * chaining conditions.
 *
 * <p>Internally delegates to {@link QueryField} condition-building methods
 * to avoid code duplication.
 *
 * @param <E> the entity type
 * @param <T> the field type
 * @param <Q> the query type (for fluent chaining)
 */
public class WhereClause<E, T, Q extends WhereTarget<Q>> {

    /** The query being built. */
    protected final Q query;
    /** The field to filter on. */
    protected final QueryField<E, T> field;

    /**
     * Creates a new WhereClause.
     * @param query the query being built
     * @param field the field to filter on
     */
    public WhereClause(Q query, QueryField<E, T> field) {
        this.query = query;
        this.field = field;
    }

    /**
     * Applies a condition to the query and returns the query for chaining.
     * @param condition the condition to apply
     * @return the query for further chaining
     */
    protected Q apply(Condition<E> condition) {
        query.addWhere(condition.toExpression());
        return query;
    }

    /**
     * Adds an equality condition: field = value
     * @param value the value to compare
     * @return the query for further chaining
     */
    public Q is(T value) {
        return apply(field.eq(value));
    }

    /**
     * Adds a not-equal condition: field != value
     * @param value the value to compare
     * @return the query for further chaining
     */
    public Q isNot(T value) {
        return apply(field.ne(value));
    }

    /**
     * Adds an IS NULL condition.
     * @return the query for further chaining
     */
    public Q isNull() {
        return apply(field.isNull());
    }

    /**
     * Adds an IS NOT NULL condition.
     * @return the query for further chaining
     */
    public Q isNotNull() {
        return apply(field.isNotNull());
    }

    /**
     * Adds a LIKE condition: field LIKE pattern
     * @param pattern the LIKE pattern
     * @return the query for further chaining
     */
    public Q like(String pattern) {
        return apply(field.like(pattern));
    }

    /**
     * Adds a NOT LIKE condition.
     * @param pattern the LIKE pattern
     * @return the query for further chaining
     */
    public Q notLike(String pattern) {
        return apply(field.notLike(pattern));
    }

    /**
     * Adds an IN condition: field IN (values)
     * @param values the values to check against
     * @return the query for further chaining
     */
    public Q in(Collection<? extends T> values) {
        return apply(field.in(values));
    }

    /**
     * Adds an IN condition with varargs.
     * @param values the values to check against
     * @return the query for further chaining
     */
    @SafeVarargs
    public final Q in(T... values) {
        return apply(field.in(values));
    }

    /**
     * Adds a NOT IN condition.
     * @param values the values to check against
     * @return the query for further chaining
     */
    public Q notIn(Collection<? extends T> values) {
        return apply(field.notIn(values));
    }
}
