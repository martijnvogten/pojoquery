package org.pojoquery.typedquery;

import org.pojoquery.SqlExpression;

/**
 * Interface for query builders that can accept WHERE conditions.
 * Implemented by generated query classes to enable fluent WHERE clause building.
 *
 * @param <Q> the query type itself (for fluent chaining)
 */
public interface WhereTarget<Q> {

    /**
     * Adds a raw WHERE condition.
     *
     * @param sql    the SQL condition
     * @param params the parameters
     * @return this query for chaining
     */
    Q addWhere(String sql, Object... params);

    /**
     * Adds a WHERE condition from a SqlExpression.
     *
     * @param expression the SQL expression
     * @return this query for chaining
     */
    Q addWhere(SqlExpression expression);
}
