package org.pojoquery.typedquery;

import org.pojoquery.SqlExpression;

/**
 * Represents a composable query condition that can be combined with AND/OR.
 *
 * <p>Conditions are created from field comparisons and can be combined:
 * <pre>{@code
 * Condition nameFilter = lastName.eq("Smith").or(lastName.eq("Johnson"));
 * Condition salaryFilter = salary.gt(50000);
 *
 * new EmployeeQuery()
 *     .where(nameFilter.and(salaryFilter))
 *     .list(connection);
 * }</pre>
 *
 * @param <E> the entity type this condition applies to
 */
public class Condition<E> {

    private final String sql;
    private final Object[] params;

    /**
     * Creates a condition with the given SQL and parameters.
     * @param sql the SQL expression
     * @param params the parameters for the SQL expression
     */
    public Condition(String sql, Object... params) {
        this.sql = sql;
        this.params = params != null ? params : new Object[0];
    }

    /**
     * Creates a condition from a SqlExpression.
     * @param expr the SQL expression
     */
    public Condition(SqlExpression expr) {
        this.sql = expr.getSql();
        this.params = toArray(expr.getParameters());
    }

    private static Object[] toArray(Iterable<Object> iterable) {
        java.util.List<Object> list = new java.util.ArrayList<>();
        for (Object obj : iterable) {
            list.add(obj);
        }
        return list.toArray();
    }

    /**
     * Combines this condition with another using AND.
     * @param other the other condition
     * @return the combined condition
     */
    public Condition<E> and(Condition<E> other) {
        String combinedSql = "(" + this.sql + " AND " + other.sql + ")";
        Object[] combinedParams = combineParams(this.params, other.params);
        return new Condition<>(combinedSql, combinedParams);
    }

    /**
     * Combines this condition with another using OR.
     * @param other the other condition
     * @return the combined condition
     */
    public Condition<E> or(Condition<E> other) {
        String combinedSql = "(" + this.sql + " OR " + other.sql + ")";
        Object[] combinedParams = combineParams(this.params, other.params);
        return new Condition<>(combinedSql, combinedParams);
    }

    /**
     * Negates this condition.
     * @return the negated condition
     */
    public Condition<E> not() {
        return new Condition<>("NOT (" + this.sql + ")", this.params);
    }

    /**
     * Wraps this condition in parentheses for explicit grouping.
     * @return the grouped condition
     */
    public Condition<E> grouped() {
        return new Condition<>("(" + this.sql + ")", this.params);
    }

    /**
     * Returns the SQL for this condition.
     * @return the SQL string
     */
    public String getSql() {
        return sql;
    }

    /**
     * Returns the parameters for this condition.
     * @return the parameters array
     */
    public Object[] getParams() {
        return params;
    }

    /**
     * Converts this condition to a SqlExpression.
     * @return the SqlExpression
     */
    public SqlExpression toExpression() {
        return SqlExpression.sql(sql, params);
    }

    private static Object[] combineParams(Object[] a, Object[] b) {
        Object[] result = new Object[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    // === Static factory methods for common conditions ===

    /**
     * Creates a condition that is always true (1=1).
     * @param <E> the entity type
     * @return a condition that always evaluates to true
     */
    public static <E> Condition<E> alwaysTrue() {
        return new Condition<>("1=1");
    }

    /**
     * Creates a condition that is always false (1=0).
     * @param <E> the entity type
     * @return a condition that always evaluates to false
     */
    public static <E> Condition<E> alwaysFalse() {
        return new Condition<>("1=0");
    }

    /**
     * Combines multiple conditions with AND.
     * @param <E> the entity type
     * @param conditions the conditions to combine
     * @return the combined condition
     */
    @SafeVarargs
    public static <E> Condition<E> and(Condition<E>... conditions) {
        if (conditions == null || conditions.length == 0) {
            return alwaysTrue();
        }
        Condition<E> result = conditions[0];
        for (int i = 1; i < conditions.length; i++) {
            result = result.and(conditions[i]);
        }
        return result;
    }

    /**
     * Combines multiple conditions with OR.
     * @param <E> the entity type
     * @param conditions the conditions to combine
     * @return the combined condition
     */
    @SafeVarargs
    public static <E> Condition<E> or(Condition<E>... conditions) {
        if (conditions == null || conditions.length == 0) {
            return alwaysFalse();
        }
        Condition<E> result = conditions[0];
        for (int i = 1; i < conditions.length; i++) {
            result = result.or(conditions[i]);
        }
        return result;
    }
}
