package org.pojoquery.pipeline.querytree;

import java.lang.annotation.Annotation;
import java.util.Map;
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
    String columnName,
    SqlExpression expression,
    FieldModel field,
    FieldMapping customMapping
) implements FieldSelectionBase {
    
    public FieldSelection {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(expression, "expression");
    }
    
    /**
     * Creates a simple field selection without custom mapping.
     */
    public static FieldSelection of(String alias, SqlExpression expression, FieldModel field) {
        return new FieldSelection(alias, null, expression, field, null);
    }
    
    /**
     * Creates a field selection for a simple column reference.
     */
    public static FieldSelection column(String sourceAlias, String targetAlias, String columnName, FieldModel field) {
        String alias = targetAlias + "." + (field != null ? field.getName() : columnName);
        SqlExpression expr = new SqlExpression("{" + sourceAlias + "." + columnName + "}");
        return new FieldSelection(alias, columnName, expr, field, null);
    }
    
    // ========== Annotation transform methods ==========
    /**
     * Returns a new FieldSelection with an additional annotation on its field.
     * The underlying field identity does not change, only its perceived annotations.
     * Returns this unchanged if there is no field.
     */
    public FieldSelection withFieldAnnotation(Class<? extends Annotation> annType, Map<String, Object> values) {
        return field == null ? this : new FieldSelection(alias, columnName, expression,
            field.withAddedAnnotation(annType, values), customMapping);
    }
    
    /**
     * Returns a new FieldSelection with a modified annotation attribute on its field.
     * The underlying field identity does not change, only its perceived annotations.
     * Returns this unchanged if there is no field.
     */
    public FieldSelection withFieldAnnotationAttribute(Class<? extends Annotation> annType, String attr, Object value) {
        return field == null ? this : new FieldSelection(alias, columnName, expression,
            field.withAnnotationAttribute(annType, attr, value), customMapping);
    }
    
    /**
     * Returns a new FieldSelection with modified annotation attributes on its field.
     * The underlying field identity does not change, only its perceived annotations.
     * Returns this unchanged if there is no field.
     */
    public FieldSelection withFieldAnnotationAttributes(Class<? extends Annotation> annType, Map<String, Object> attrs) {
        return field == null ? this : new FieldSelection(alias, columnName, expression,
            field.withAnnotationAttributes(annType, attrs), customMapping);
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

    public FieldSelection withField(FieldModel transformedField) {
        return new FieldSelection(alias, columnName, expression, transformedField, customMapping);
    }
}
