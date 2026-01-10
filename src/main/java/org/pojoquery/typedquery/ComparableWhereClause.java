package org.pojoquery.typedquery;

/**
 * Extended WhereClause for comparable types with comparison operations.
 *
 * <p>This class adds type-safe comparison methods that are only available
 * for fields with Comparable types. Methods like {@code greaterThan()},
 * {@code lessThan()}, and {@code between()} are restricted to fields
 * where such comparisons make semantic sense.
 *
 * <p>Internally delegates to {@link ComparableQueryField} condition-building
 * methods to avoid code duplication.
 *
 * @param <E> the entity type
 * @param <T> the field type (must be Comparable)
 * @param <Q> the query type (for fluent chaining)
 */
public class ComparableWhereClause<E, T extends Comparable<? super T>, Q extends WhereTarget<Q>>
        extends WhereClause<E, T, Q> {

    private final ComparableQueryField<E, T> comparableField;

    /**
     * Creates a new ComparableWhereClause.
     * @param query the query being built
     * @param field the comparable field to filter on
     */
    public ComparableWhereClause(Q query, ComparableQueryField<E, T> field) {
        super(query, field);
        this.comparableField = field;
    }

    /**
     * Adds a greater than condition: field &gt; value
     * @param value the value to compare
     * @return the query for further chaining
     */
    public Q greaterThan(T value) {
        return apply(comparableField.gt(value));
    }

    /**
     * Adds a greater than or equal condition: field &gt;= value
     * @param value the value to compare
     * @return the query for further chaining
     */
    public Q greaterThanOrEqual(T value) {
        return apply(comparableField.ge(value));
    }

    /**
     * Adds a less than condition: field &lt; value
     * @param value the value to compare
     * @return the query for further chaining
     */
    public Q lessThan(T value) {
        return apply(comparableField.lt(value));
    }

    /**
     * Adds a less than or equal condition: field &lt;= value
     * @param value the value to compare
     * @return the query for further chaining
     */
    public Q lessThanOrEqual(T value) {
        return apply(comparableField.le(value));
    }

    /**
     * Adds a BETWEEN condition: field BETWEEN low AND high
     * @param low the lower bound
     * @param high the upper bound
     * @return the query for further chaining
     */
    public Q between(T low, T high) {
        return apply(comparableField.between(low, high));
    }

    /**
     * Adds a NOT BETWEEN condition: field NOT BETWEEN low AND high
     * @param low the lower bound
     * @param high the upper bound
     * @return the query for further chaining
     */
    public Q notBetween(T low, T high) {
        return apply(comparableField.notBetween(low, high));
    }
}
