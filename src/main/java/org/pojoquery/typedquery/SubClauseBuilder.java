package org.pojoquery.typedquery;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.SqlExpression;

/**
 * Builder for type-safe sub-clause groups (parenthesized conditions).
 *
 * <p>Usage example:
 * <pre>{@code
 * new EmployeeQuery()
 *     .begin()
 *         .where().lastName.is("Smith")
 *         .or().firstName.is("Johnson")
 *     .end()
 *     .orderBy(firstName)
 *     .list(connection);
 * }</pre>
 *
 * @param <E> the entity type
 * @param <Q> the parent query type (for returning after end())
 */
public class SubClauseBuilder<E, Q extends TypedQuery<E, Q>> implements WhereTarget<SubClauseBuilder<E, Q>> {

    private final Q parentQuery;
    private final List<SqlExpression> conditions = new ArrayList<>();

    public SubClauseBuilder(Q parentQuery) {
        this.parentQuery = parentQuery;
    }

    /**
     * Gets the parent query (for use by generated SubClauseWhere classes).
     */
    protected Q getParentQuery() {
        return parentQuery;
    }

    /**
     * Adds a condition to the sub-clause group (for use by SubClauseWhereBuilder).
     */
    protected void addCondition(Condition<E> condition) {
        conditions.add(condition.toExpression());
    }

    /**
     * Internal end method that doesn't combine conditions (used when SubClauseWhereBuilder
     * has already combined the conditions into one).
     */
    protected Q endInternal() {
        return applyConditionsToParent();
    }

    /**
     * Ends the sub-clause group and returns to the parent query.
     * Combines all conditions with OR and adds them to the parent.
     */
    public Q end() {
        return applyConditionsToParent();
    }

    /**
     * Applies the accumulated conditions to the parent query.
     * Combines multiple conditions with OR and wraps in parentheses.
     */
    private Q applyConditionsToParent() {
        if (conditions.isEmpty()) {
            return parentQuery;
        }
        if (conditions.size() == 1) {
            parentQuery.addWhere(conditions.get(0));
            return parentQuery;
        }

        // Multiple conditions - wrap in parentheses with OR
        StringBuilder sql = new StringBuilder("(");
        List<Object> allParams = new ArrayList<>();
        
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            SqlExpression cond = conditions.get(i);
            sql.append(cond.getSql());
            for (Object param : cond.getParameters()) {
                allParams.add(param);
            }
        }
        sql.append(")");

        parentQuery.addWhere(SqlExpression.sql(sql.toString(), allParams.toArray()));
        return parentQuery;
    }

    // === WhereTarget implementation ===

    @Override
    public SubClauseBuilder<E, Q> addWhere(String sql, Object... params) {
        conditions.add(SqlExpression.sql(sql, params));
        return this;
    }

    @Override
    public SubClauseBuilder<E, Q> addWhere(SqlExpression expression) {
        conditions.add(expression);
        return this;
    }
}
