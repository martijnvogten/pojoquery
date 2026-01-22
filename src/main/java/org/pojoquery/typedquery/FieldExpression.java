package org.pojoquery.typedquery;

import java.util.List;

/**
 * Represents a SQL expression that can be used in comparisons.
 * This allows comparing fields to computed values like CONCAT, LOWER, etc.
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
}
