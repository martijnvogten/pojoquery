package org.pojoquery.typedquery;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.pojoquery.SqlExpression;

/**
 * Represents a SQL expression that can be used in comparisons.
 * This allows comparing fields to computed values like CONCAT, LOWER, etc.
 *
 * <p>Example usage:
 * <pre>
 * // Use with where() to build conditions directly from expressions
 * q.where(q.concat(q.firstName, " ", q.lastName).eq("James Brown"))
 * q.where(q.lower(q.email).like("%@example.com"))
 * </pre>
 *
 * @param <T> the result type of the expression
 */
public class FieldExpression<T> {
    private final String sql;
    private final List<Object> parameters;

    public FieldExpression(String sql, List<Object> parameters) {
        this.sql = sql;
        this.parameters = parameters;
    }

    public FieldExpression(String sql) {
        this(sql, List.of());
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    /**
     * Creates a field expression from a ConditionBuilderField.
     */
    public static <T> FieldExpression<T> of(ConditionBuilderField<T, ?> field) {
        return new FieldExpression<>("{" + field.tableAlias + "." + field.columnName + "}");
    }

    /**
     * Creates a field expression from a literal value.
     */
    public static <T> FieldExpression<T> literal(T value) {
        return new FieldExpression<>("?", List.of(value));
    }

    // === Comparison methods that return conditions ===

    /**
     * Creates an equality condition comparing this expression to a value.
     * @param value the value to compare against
     * @return a condition supplier for use with where()
     */
    public Supplier<SqlExpression> eq(T value) {
        List<Object> params = new ArrayList<>(parameters);
        params.add(value);
        return () -> SqlExpression.sql(sql + " = ?", params.toArray());
    }

    /**
     * Creates an equality condition comparing this expression to another field.
     * @param other the field to compare against
     * @return a condition supplier for use with where()
     */
    public Supplier<SqlExpression> eq(ConditionBuilderField<T, ?> other) {
        return () -> SqlExpression.sql(sql + " = {" + other.tableAlias + "." + other.columnName + "}", parameters.toArray());
    }

    /**
     * Creates an equality condition comparing this expression to another expression.
     * @param other the expression to compare against
     * @return a condition supplier for use with where()
     */
    public Supplier<SqlExpression> eq(FieldExpression<T> other) {
        List<Object> params = new ArrayList<>(parameters);
        params.addAll(other.getParameters());
        return () -> SqlExpression.sql(sql + " = " + other.getSql(), params.toArray());
    }

    /**
     * Creates a not-equal condition comparing this expression to a value.
     * @param value the value to compare against
     * @return a condition supplier for use with where()
     */
    public Supplier<SqlExpression> ne(T value) {
        List<Object> params = new ArrayList<>(parameters);
        params.add(value);
        return () -> SqlExpression.sql(sql + " <> ?", params.toArray());
    }

    /**
     * Creates a not-equal condition comparing this expression to another field.
     * @param other the field to compare against
     * @return a condition supplier for use with where()
     */
    public Supplier<SqlExpression> ne(ConditionBuilderField<T, ?> other) {
        return () -> SqlExpression.sql(sql + " <> {" + other.tableAlias + "." + other.columnName + "}", parameters.toArray());
    }

    /**
     * Creates an IS NULL condition.
     * @return a condition supplier for use with where()
     */
    public Supplier<SqlExpression> isNull() {
        return () -> SqlExpression.sql(sql + " IS NULL", parameters.toArray());
    }

    /**
     * Creates an IS NOT NULL condition.
     * @return a condition supplier for use with where()
     */
    public Supplier<SqlExpression> isNotNull() {
        return () -> SqlExpression.sql(sql + " IS NOT NULL", parameters.toArray());
    }

    /**
     * Creates a LIKE condition.
     * @param pattern the pattern to match (use % for wildcards)
     * @return a condition supplier for use with where()
     */
    public Supplier<SqlExpression> like(String pattern) {
        List<Object> params = new ArrayList<>(parameters);
        params.add(pattern);
        return () -> SqlExpression.sql(sql + " LIKE ?", params.toArray());
    }

    /**
     * Creates a NOT LIKE condition.
     * @param pattern the pattern to match (use % for wildcards)
     * @return a condition supplier for use with where()
     */
    public Supplier<SqlExpression> notLike(String pattern) {
        List<Object> params = new ArrayList<>(parameters);
        params.add(pattern);
        return () -> SqlExpression.sql(sql + " NOT LIKE ?", params.toArray());
    }

    /**
     * Creates an IN condition.
     * @param values the values to check against
     * @return a condition supplier for use with where()
     */
    @SuppressWarnings("unchecked")
    public Supplier<SqlExpression> in(T... values) {
        List<Object> params = new ArrayList<>(parameters);
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) placeholders.append(", ");
            placeholders.append("?");
            params.add(values[i]);
        }
        String inSql = sql + " IN (" + placeholders + ")";
        return () -> SqlExpression.sql(inSql, params.toArray());
    }

    /**
     * Creates a NOT IN condition.
     * @param values the values to check against
     * @return a condition supplier for use with where()
     */
    @SuppressWarnings("unchecked")
    public Supplier<SqlExpression> notIn(T... values) {
        List<Object> params = new ArrayList<>(parameters);
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) placeholders.append(", ");
            placeholders.append("?");
            params.add(values[i]);
        }
        String notInSql = sql + " NOT IN (" + placeholders + ")";
        return () -> SqlExpression.sql(notInSql, params.toArray());
    }
}
