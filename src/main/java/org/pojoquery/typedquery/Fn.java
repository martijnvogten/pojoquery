package org.pojoquery.typedquery;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory class for creating SQL function expressions.
 * Use with field comparisons to build complex conditions.
 *
 * <p>Example usage:
 * <pre>
 * q.fullName.eq(Fn.concat(q.author.firstName, " ", q.author.lastName))
 * q.name.eq(Fn.lower(q.author.name))
 * </pre>
 */
public class Fn {

    /**
     * Creates a CONCAT expression from the given parts.
     * Parts can be ConditionBuilderField instances or literal values.
     *
     * @param parts the parts to concatenate (fields or literals)
     * @return a FieldExpression representing the CONCAT
     */
    public static FieldExpression<String> concat(Object... parts) {
        StringBuilder sql = new StringBuilder("CONCAT(");
        List<Object> parameters = new ArrayList<>();
        
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            appendPart(sql, parameters, parts[i]);
        }
        sql.append(")");
        
        return new FieldExpression<>(sql.toString(), parameters);
    }

    /**
     * Creates a LOWER expression.
     *
     * @param part a field or literal value
     * @return a FieldExpression representing LOWER(part)
     */
    public static FieldExpression<String> lower(Object part) {
        StringBuilder sql = new StringBuilder("LOWER(");
        List<Object> parameters = new ArrayList<>();
        appendPart(sql, parameters, part);
        sql.append(")");
        return new FieldExpression<>(sql.toString(), parameters);
    }

    /**
     * Creates an UPPER expression.
     *
     * @param part a field or literal value
     * @return a FieldExpression representing UPPER(part)
     */
    public static FieldExpression<String> upper(Object part) {
        StringBuilder sql = new StringBuilder("UPPER(");
        List<Object> parameters = new ArrayList<>();
        appendPart(sql, parameters, part);
        sql.append(")");
        return new FieldExpression<>(sql.toString(), parameters);
    }

    /**
     * Creates a TRIM expression.
     *
     * @param part a field or literal value
     * @return a FieldExpression representing TRIM(part)
     */
    public static FieldExpression<String> trim(Object part) {
        StringBuilder sql = new StringBuilder("TRIM(");
        List<Object> parameters = new ArrayList<>();
        appendPart(sql, parameters, part);
        sql.append(")");
        return new FieldExpression<>(sql.toString(), parameters);
    }

    /**
     * Creates a LENGTH expression.
     *
     * @param part a field or literal value
     * @return a FieldExpression representing LENGTH(part)
     */
    public static FieldExpression<Number> length(Object part) {
        StringBuilder sql = new StringBuilder("LENGTH(");
        List<Object> parameters = new ArrayList<>();
        appendPart(sql, parameters, part);
        sql.append(")");
        return new FieldExpression<>(sql.toString(), parameters);
    }

    /**
     * Creates a COALESCE expression.
     *
     * @param parts the values to coalesce (fields or literals)
     * @return a FieldExpression representing COALESCE(parts...)
     */
    public static <T> FieldExpression<T> coalesce(Object... parts) {
        StringBuilder sql = new StringBuilder("COALESCE(");
        List<Object> parameters = new ArrayList<>();
        
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            appendPart(sql, parameters, parts[i]);
        }
        sql.append(")");
        
        return new FieldExpression<>(sql.toString(), parameters);
    }

    /**
     * Creates an ABS expression.
     *
     * @param part a field or literal value
     * @return a FieldExpression representing ABS(part)
     */
    public static <T extends Number> FieldExpression<T> abs(Object part) {
        StringBuilder sql = new StringBuilder("ABS(");
        List<Object> parameters = new ArrayList<>();
        appendPart(sql, parameters, part);
        sql.append(")");
        return new FieldExpression<>(sql.toString(), parameters);
    }

    /**
     * Creates a SUBSTRING expression.
     *
     * @param part a field or literal value
     * @param start the start position (1-based)
     * @param length the length of the substring
     * @return a FieldExpression representing SUBSTRING(part, start, length)
     */
    public static FieldExpression<String> substring(Object part, int start, int length) {
        StringBuilder sql = new StringBuilder("SUBSTRING(");
        List<Object> parameters = new ArrayList<>();
        appendPart(sql, parameters, part);
        sql.append(", ").append(start).append(", ").append(length).append(")");
        return new FieldExpression<>(sql.toString(), parameters);
    }

    private static void appendPart(StringBuilder sql, List<Object> parameters, Object part) {
        if (part instanceof ConditionBuilderField<?, ?> field) {
            sql.append("{").append(field.tableAlias).append(".").append(field.columnName).append("}");
        } else if (part instanceof FieldExpression<?> expr) {
            sql.append(expr.getSql());
            parameters.addAll(expr.getParameters());
        } else {
            sql.append("?");
            parameters.add(part);
        }
    }
}
