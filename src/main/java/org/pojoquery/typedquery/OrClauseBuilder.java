package org.pojoquery.typedquery;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.SqlExpression;

/**
 * Builder for type-safe OR clause groups.
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
public class OrClauseBuilder<E, Q extends TypedQuery<E, Q>> implements WhereTarget<OrClauseBuilder<E, Q>> {

    private final Q parentQuery;
    private final List<SqlExpression> conditions = new ArrayList<>();

    public OrClauseBuilder(Q parentQuery) {
        this.parentQuery = parentQuery;
    }

    /**
     * Gets the parent query (for use by generated OrClauseWhere classes).
     */
    protected Q getParentQuery() {
        return parentQuery;
    }

    /**
     * Adds a condition to the OR group (for use by OrClauseWhereBuilder).
     */
    protected void addCondition(Condition<E> condition) {
        conditions.add(condition.toExpression());
    }

    /**
     * Internal end method that doesn't combine conditions (used when OrClauseWhereBuilder
     * has already combined the conditions into one).
     */
    protected Q endInternal() {
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

    // === Legacy methods (kept for backward compatibility) ===

    /**
     * Begins a type-safe WHERE condition within this OR group.
     * @deprecated Use where() without parameters for fluent field access
     */
    @Deprecated
    public <T> OrWhereClause<E, T, Q> where(QueryField<E, T> field) {
        return new OrWhereClause<>(this, field);
    }

    /**
     * Begins a type-safe WHERE condition for a comparable field within this OR group.
     * @deprecated Use where() without parameters for fluent field access
     */
    @Deprecated
    public <T extends Comparable<? super T>> OrComparableWhereClause<E, T, Q> where(ComparableQueryField<E, T> field) {
        return new OrComparableWhereClause<>(this, field);
    }

    /**
     * Alias for where() - starts a new OR branch after a previous condition.
     * @deprecated Use or() without parameters for fluent field access
     */
    @Deprecated
    public <T> OrWhereClause<E, T, Q> or(QueryField<E, T> field) {
        return where(field);
    }

    /**
     * Alias for where() for comparable fields - starts a new OR branch.
     * @deprecated Use or() without parameters for fluent field access
     */
    @Deprecated
    public <T extends Comparable<? super T>> OrComparableWhereClause<E, T, Q> or(ComparableQueryField<E, T> field) {
        return where(field);
    }

    /**
     * Ends the OR group and returns to the parent query.
     * Combines all conditions with OR and adds them to the parent.
     */
    public Q end() {
        if (conditions.isEmpty()) {
            return parentQuery;
        }
        if (conditions.size() == 1) {
            parentQuery.addWhere(conditions.get(0));
            return parentQuery;
        }

        // Combine all conditions with OR
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
    public OrClauseBuilder<E, Q> addWhere(String sql, Object... params) {
        conditions.add(SqlExpression.sql(sql, params));
        return this;
    }

    @Override
    public OrClauseBuilder<E, Q> addWhere(SqlExpression expression) {
        conditions.add(expression);
        return this;
    }
}
