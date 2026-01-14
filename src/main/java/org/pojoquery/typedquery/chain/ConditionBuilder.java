package org.pojoquery.typedquery.chain;

import org.pojoquery.SqlExpression;

/**
 * Builder interface for constructing SQL condition expressions.
 */
public interface ConditionBuilder {
    /**
     * Starts a clause (adds opening parenthesis).
     * @return this builder for fluent chaining
     */
    ConditionBuilder startClause();

    /**
     * Ends a clause (adds closing parenthesis).
     * @return this builder for fluent chaining
     */
    ConditionBuilder endClause();

    /**
     * Adds an SQL expression to the condition.
     * @param expr the SQL expression to add
     * @return this builder for fluent chaining
     */
    ConditionBuilder add(SqlExpression expr);
}
