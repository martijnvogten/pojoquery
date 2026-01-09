package org.pojoquery.typedquery;

import java.util.Collection;

import org.pojoquery.SqlExpression;

/**
 * WhereClause variant that returns to an OrClauseBuilder instead of the main query.
 *
 * @param <E> the entity type
 * @param <T> the field type
 * @param <Q> the parent query type
 */
public class OrWhereClause<E, T, Q extends TypedQuery<E, Q>> {

    protected final OrClauseBuilder<E, Q> builder;
    protected final QueryField<E, T> field;

    public OrWhereClause(OrClauseBuilder<E, Q> builder, QueryField<E, T> field) {
        this.builder = builder;
        this.field = field;
    }

    /**
     * Adds an equality condition: field = value
     */
    public OrClauseBuilder<E, Q> is(T value) {
        builder.addWhere(field.getQualifiedColumn() + " = ?", value);
        return builder;
    }

    /**
     * Adds a not-equal condition: field != value
     */
    public OrClauseBuilder<E, Q> isNot(T value) {
        builder.addWhere(field.getQualifiedColumn() + " != ?", value);
        return builder;
    }

    /**
     * Adds an IS NULL condition.
     */
    public OrClauseBuilder<E, Q> isNull() {
        builder.addWhere(field.getQualifiedColumn() + " IS NULL");
        return builder;
    }

    /**
     * Adds an IS NOT NULL condition.
     */
    public OrClauseBuilder<E, Q> isNotNull() {
        builder.addWhere(field.getQualifiedColumn() + " IS NOT NULL");
        return builder;
    }

    /**
     * Adds a LIKE condition.
     */
    public OrClauseBuilder<E, Q> like(String pattern) {
        builder.addWhere(field.getQualifiedColumn() + " LIKE ?", pattern);
        return builder;
    }

    /**
     * Adds a NOT LIKE condition.
     */
    public OrClauseBuilder<E, Q> notLike(String pattern) {
        builder.addWhere(field.getQualifiedColumn() + " NOT LIKE ?", pattern);
        return builder;
    }

    /**
     * Adds an IN condition.
     */
    public OrClauseBuilder<E, Q> in(Collection<? extends T> values) {
        if (values == null || values.isEmpty()) {
            builder.addWhere("1 = 0");
            return builder;
        }
        StringBuilder sb = new StringBuilder(field.getQualifiedColumn());
        sb.append(" IN (");
        boolean first = true;
        for (T value : values) {
            if (!first) sb.append(", ");
            sb.append("?");
            first = false;
        }
        sb.append(")");
        builder.addWhere(SqlExpression.sql(sb.toString(), values.toArray()));
        return builder;
    }

    /**
     * Adds an IN condition with varargs.
     */
    @SafeVarargs
    public final OrClauseBuilder<E, Q> in(T... values) {
        if (values == null || values.length == 0) {
            builder.addWhere("1 = 0");
            return builder;
        }
        StringBuilder sb = new StringBuilder(field.getQualifiedColumn());
        sb.append(" IN (");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        sb.append(")");
        builder.addWhere(SqlExpression.sql(sb.toString(), values));
        return builder;
    }
}
