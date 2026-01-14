package org.pojoquery.typedquery.chain;

import org.pojoquery.SqlExpression;

import java.util.ArrayList;
import java.util.List;

import static org.pojoquery.SqlExpression.sql;

/**
 * Default implementation of {@link ConditionBuilder}.
 * Accumulates SQL expressions for building condition chains.
 */
public class ConditionBuilderImpl implements ConditionBuilder {
    
    /** The accumulated SQL expressions. */
    public List<SqlExpression> expressions = new ArrayList<>();

    @Override
    public ConditionBuilder startClause() {
        expressions.add(sql("("));
        return this;
    }

    @Override
    public ConditionBuilder endClause() {
        expressions.add(sql(")"));
        return this;
    }

    @Override
    public ConditionBuilder add(SqlExpression expr) {
        expressions.add(expr);
        return this;
    }
}
