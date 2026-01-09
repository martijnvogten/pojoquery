package org.pojoquery.typedquery;

import java.util.Collection;

import org.pojoquery.SqlExpression;

/**
 * Builder for type-safe WHERE clause conditions.
 *
 * <p>This class provides a fluent API for building WHERE conditions with
 * type safety. It's returned by {@code query.where(field)} and allows
 * chaining conditions.
 *
 * @param <E> the entity type
 * @param <T> the field type
 * @param <Q> the query type (for fluent chaining)
 */
public class WhereClause<E, T, Q extends WhereTarget<Q>> {

    protected final Q query;
    protected final QueryField<E, T> field;

    public WhereClause(Q query, QueryField<E, T> field) {
        this.query = query;
        this.field = field;
    }

    /**
     * Adds an equality condition: field = value
     */
    public Q is(T value) {
        query.addWhere(field.getQualifiedColumn() + " = ?", value);
        return query;
    }

    /**
     * Adds a not-equal condition: field != value
     */
    public Q isNot(T value) {
        query.addWhere(field.getQualifiedColumn() + " != ?", value);
        return query;
    }

    /**
     * Adds an IS NULL condition.
     */
    public Q isNull() {
        query.addWhere(field.getQualifiedColumn() + " IS NULL");
        return query;
    }

    /**
     * Adds an IS NOT NULL condition.
     */
    public Q isNotNull() {
        query.addWhere(field.getQualifiedColumn() + " IS NOT NULL");
        return query;
    }

    /**
     * Adds a LIKE condition: field LIKE pattern
     */
    public Q like(String pattern) {
        query.addWhere(field.getQualifiedColumn() + " LIKE ?", pattern);
        return query;
    }

    /**
     * Adds a NOT LIKE condition.
     */
    public Q notLike(String pattern) {
        query.addWhere(field.getQualifiedColumn() + " NOT LIKE ?", pattern);
        return query;
    }

    /**
     * Adds an IN condition: field IN (values)
     */
    public Q in(Collection<? extends T> values) {
        if (values == null || values.isEmpty()) {
            // Empty IN is always false
            query.addWhere("1 = 0");
            return query;
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
        query.addWhere(SqlExpression.sql(sb.toString(), values.toArray()));
        return query;
    }

    /**
     * Adds an IN condition with varargs.
     */
    @SafeVarargs
    public final Q in(T... values) {
        if (values == null || values.length == 0) {
            query.addWhere("1 = 0");
            return query;
        }
        StringBuilder sb = new StringBuilder(field.getQualifiedColumn());
        sb.append(" IN (");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        sb.append(")");
        query.addWhere(SqlExpression.sql(sb.toString(), values));
        return query;
    }

    /**
     * Adds a NOT IN condition.
     */
    public Q notIn(Collection<? extends T> values) {
        if (values == null || values.isEmpty()) {
            // NOT IN empty set is always true, so no condition needed
            return query;
        }
        StringBuilder sb = new StringBuilder(field.getQualifiedColumn());
        sb.append(" NOT IN (");
        boolean first = true;
        for (T value : values) {
            if (!first) sb.append(", ");
            sb.append("?");
            first = false;
        }
        sb.append(")");
        query.addWhere(SqlExpression.sql(sb.toString(), values.toArray()));
        return query;
    }
}
