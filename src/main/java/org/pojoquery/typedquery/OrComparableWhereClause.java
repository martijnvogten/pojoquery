package org.pojoquery.typedquery;

/**
 * ComparableWhereClause variant that returns to an OrClauseBuilder.
 *
 * @param <E> the entity type
 * @param <T> the field type (must be Comparable)
 * @param <Q> the parent query type
 */
public class OrComparableWhereClause<E, T extends Comparable<? super T>, Q extends TypedQuery<E, Q>>
        extends OrWhereClause<E, T, Q> {

    public OrComparableWhereClause(OrClauseBuilder<E, Q> builder, ComparableQueryField<E, T> field) {
        super(builder, field);
    }

    /**
     * Adds a greater than condition: field > value
     */
    public OrClauseBuilder<E, Q> greaterThan(T value) {
        builder.addWhere(field.getQualifiedColumn() + " > ?", value);
        return builder;
    }

    /**
     * Adds a greater than or equal condition: field >= value
     */
    public OrClauseBuilder<E, Q> greaterThanOrEqual(T value) {
        builder.addWhere(field.getQualifiedColumn() + " >= ?", value);
        return builder;
    }

    /**
     * Adds a less than condition: field < value
     */
    public OrClauseBuilder<E, Q> lessThan(T value) {
        builder.addWhere(field.getQualifiedColumn() + " < ?", value);
        return builder;
    }

    /**
     * Adds a less than or equal condition: field <= value
     */
    public OrClauseBuilder<E, Q> lessThanOrEqual(T value) {
        builder.addWhere(field.getQualifiedColumn() + " <= ?", value);
        return builder;
    }

    /**
     * Adds a BETWEEN condition: field BETWEEN low AND high
     */
    public OrClauseBuilder<E, Q> between(T low, T high) {
        builder.addWhere(field.getQualifiedColumn() + " BETWEEN ? AND ?", low, high);
        return builder;
    }
}
