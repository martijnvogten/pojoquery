package org.pojoquery.pipeline.querytree;

import java.util.Objects;

import org.pojoquery.FieldMapping;
import org.pojoquery.SqlExpression;
import org.pojoquery.typemodel.FieldModel;

/**
 * Represents a field selection in the query's SELECT clause.
 *
 * @param alias The column alias in the result set
 * @param expression The SQL expression to select (may contain {table.column} placeholders)
 * @param field The Java field this maps to (may be null for computed fields)
 * @param customMapping Optional custom mapping logic (may be null)
 */
public record FieldSelection(
    String alias,
    SqlExpression expression,
    FieldModel field,
    FieldMapping customMapping
) {
    
    public FieldSelection {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(expression, "expression");
    }
    
    /**
     * Creates a simple field selection without custom mapping.
     */
    public static FieldSelection of(String alias, SqlExpression expression, FieldModel field) {
        return new FieldSelection(alias, expression, field, null);
    }
    
    /**
     * Creates a field selection for a simple column reference.
     */
    public static FieldSelection column(String tableAlias, String columnName, FieldModel field) {
        String alias = tableAlias + "." + (field != null ? field.getName() : columnName);
        SqlExpression expr = new SqlExpression("{" + tableAlias + "." + columnName + "}");
        return new FieldSelection(alias, expr, field, null);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\"").append(alias).append("\" <- ");
        sb.append(expression.getSql());
        if (field != null) {
            sb.append(" (").append(field.getType().getSimpleName()).append(")");
        }
        return sb.toString();
    }
}
