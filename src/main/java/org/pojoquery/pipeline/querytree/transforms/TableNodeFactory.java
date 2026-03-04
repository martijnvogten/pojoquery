package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.QueryModel.determineSqlFieldName;

import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.typemodel.FieldModel;

/**
 * Helper methods for creating TableNode instances.
 */
public final class TableNodeFactory {
    
    private TableNodeFactory() {}
    
    /**
     * Creates a FieldSelection for a simple column.
     * 
     * @param tableAlias The table alias
     * @param field The field
     * @return A FieldSelection
     */
    public static FieldSelection fieldSelection(String tableAlias, FieldModel field) {
        return fieldSelection(tableAlias, tableAlias, field);
    }
    
    /**
     * Creates a FieldSelection with separate table reference and alias prefix.
     * Used for superclass tables where fields are aliased to the child's name.
     * 
     * @param tableAlias The table alias for the SQL expression
     * @param aliasPrefix The prefix for the output column alias
     * @param field The field
     * @return A FieldSelection
     */
    public static FieldSelection fieldSelection(String tableAlias, String aliasPrefix, FieldModel field) {
        String colName = determineSqlFieldName(field);
        String alias = aliasPrefix + "." + field.getName();
        SqlExpression expr = new SqlExpression("{" + tableAlias + "." + colName + "}");
        return new FieldSelection(alias, colName, expr, field, null);
    }
    
    /**
     * Creates a FieldSelection for a column with a custom expression.
     * 
     * @param tableAlias The table alias
     * @param field The field
     * @param expression The SQL expression
     * @return A FieldSelection
     */
    public static FieldSelection fieldSelection(String tableAlias, FieldModel field, String expression) {
        String alias = tableAlias + "." + field.getName();
        return new FieldSelection(alias, null, new SqlExpression(expression), field, null);
    }
}
