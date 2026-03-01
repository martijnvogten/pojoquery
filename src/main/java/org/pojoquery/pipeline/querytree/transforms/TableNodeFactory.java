package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.QueryModel.determineSqlFieldName;

import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;

/**
 * Helper methods for creating TableNode instances.
 */
public final class TableNodeFactory {
    
    private TableNodeFactory() {}
    
    /**
     * Creates an empty TableNode (for link tables with no type/fields).
     * 
     * @param alias The table alias
     * @param schema The schema name (may be null or empty)
     * @param tableName The table name
     * @return An empty TableNode
     */
    public static TableNode forLinkTable(String alias, String schema, String tableName) {
        String schemaName = (schema == null || schema.isEmpty()) ? null : schema;
        return new TableNode(
            alias, null, schemaName, tableName,
            List.of(), List.of(), List.of(),
            false, null, null, null, null
        );
    }
    
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
        return new FieldSelection(alias, expr, field, null);
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
        return new FieldSelection(alias, new SqlExpression(expression), field, null);
    }
}
