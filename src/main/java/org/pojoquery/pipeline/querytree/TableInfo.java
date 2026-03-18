package org.pojoquery.pipeline.querytree;

import java.util.Objects;

/**
 * Encapsulates embed metadata for a node in the query tree.
 * This record captures how a node is embedded within its parent.
 *
 * @param linkField The Java field that created this embed (may be null for manual embeds)
 * @param fieldPrefix The prefix to apply to column names
 * @param isCollection True if this is a one-to-many relationship (List/Set/Array)
 */
public record TableInfo(
    String schemaName,
    String tableName
) {
    public TableInfo {
        Objects.requireNonNull(tableName, "tableName");
    }

    public static TableInfo of(String tableName) {
        Objects.requireNonNull(tableName, "tableName");
        return new TableInfo(null, tableName);
    }
    
    public static TableInfo of(String schemaName, String tableName) {
        Objects.requireNonNull(tableName, "tableName");
        return new TableInfo("".equals(schemaName) ? null : schemaName, tableName);
    }

    @Override
    public final String toString() {
        return "TableInfo[" + 
            (schemaName == null ? "" : "schemaName=" + schemaName + ", ") + 
            "tableName=" + tableName + "]";
    }
}
