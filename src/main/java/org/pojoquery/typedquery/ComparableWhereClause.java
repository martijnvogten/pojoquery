package org.pojoquery.typedquery;

/**
 * Extended WhereClause for comparable types with comparison operations.
 *
 * <p>This class adds type-safe comparison methods that are only available
 * for fields with Comparable types. Methods like {@code greaterThan()},
 * {@code lessThan()}, and {@code between()} are restricted to fields
 * where such comparisons make semantic sense.
 *
 * @param <E> the entity type
 * @param <T> the field type (must be Comparable)
 * @param <Q> the query type (for fluent chaining)
 */
public class ComparableWhereClause<E, T extends Comparable<? super T>, Q extends WhereTarget<Q>>
        extends WhereClause<E, T, Q> {

    public ComparableWhereClause(Q query, ComparableQueryField<E, T> field) {
        super(query, field);
    }

    /**
     * Adds a greater than condition: field &gt; value
     */
    public Q greaterThan(T value) {
        query.addWhere(field.getQualifiedColumn() + " > ?", value);
        return query;
    }

    /**
     * Adds a greater than or equal condition: field &gt;= value
     */
    public Q greaterThanOrEqual(T value) {
        query.addWhere(field.getQualifiedColumn() + " >= ?", value);
        return query;
    }

    /**
     * Adds a less than condition: field &lt; value
     */
    public Q lessThan(T value) {
        query.addWhere(field.getQualifiedColumn() + " < ?", value);
        return query;
    }

    /**
     * Adds a less than or equal condition: field &lt;= value
     */
    public Q lessThanOrEqual(T value) {
        query.addWhere(field.getQualifiedColumn() + " <= ?", value);
        return query;
    }

    /**
     * Adds a BETWEEN condition: field BETWEEN low AND high
     */
    public Q between(T low, T high) {
        query.addWhere(field.getQualifiedColumn() + " BETWEEN ? AND ?", low, high);
        return query;
    }
}
