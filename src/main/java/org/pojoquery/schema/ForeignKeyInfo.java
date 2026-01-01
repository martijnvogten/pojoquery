package org.pojoquery.schema;

/**
 * Data classes for foreign key and relationship information used during schema generation.
 */
class ForeignKeyInfo {
    
    /**
     * Represents an inferred foreign key column with its reference information.
     * Used when a collection field implies a FK in the referenced entity's table.
     */
    static class InferredForeignKey {
        final String columnName;
        final String referencedTable;
        final String referencedColumn;
        final String referencedSchema;
        
        InferredForeignKey(String columnName, String referencedTable, String referencedColumn, String referencedSchema) {
            this.columnName = columnName;
            this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn;
            this.referencedSchema = referencedSchema;
        }
        
        boolean hasReference() {
            return referencedTable != null && referencedColumn != null;
        }
    }
    
    /**
     * Represents a deferred foreign key constraint to be added via ALTER TABLE.
     */
    static class DeferredForeignKey {
        final String tableName;
        final String tableSchema;
        final String columnName;
        final String referencedTable;
        final String referencedColumn;
        final String referencedSchema;
        
        DeferredForeignKey(String tableName, String tableSchema, String columnName, 
                          String referencedTable, String referencedColumn, String referencedSchema) {
            this.tableName = tableName;
            this.tableSchema = tableSchema;
            this.columnName = columnName;
            this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn;
            this.referencedSchema = referencedSchema;
        }
    }
    
    /**
     * Represents a link table for many-to-many relationships.
     */
    static class LinkTableInfo {
        final String tableName;
        final String schemaName;
        final String ownerColumn;
        final String ownerTable;
        final String ownerIdColumn;
        final String ownerSchema;
        final String foreignColumn;
        final String foreignTable;
        final String foreignIdColumn;
        final String foreignSchema;
        
        LinkTableInfo(String tableName, String schemaName,
                      String ownerColumn, String ownerTable, String ownerIdColumn, String ownerSchema,
                      String foreignColumn, String foreignTable, String foreignIdColumn, String foreignSchema) {
            this.tableName = tableName;
            this.schemaName = schemaName;
            this.ownerColumn = ownerColumn;
            this.ownerTable = ownerTable;
            this.ownerIdColumn = ownerIdColumn;
            this.ownerSchema = ownerSchema;
            this.foreignColumn = foreignColumn;
            this.foreignTable = foreignTable;
            this.foreignIdColumn = foreignIdColumn;
            this.foreignSchema = foreignSchema;
        }
    }
    
    /**
     * Holds merged column annotations from multiple classes mapping to the same table.
     */
    static class MergedColumnAnnotations {
        boolean unique = false;
        boolean notNull = false;
        
        void mergeWith(org.pojoquery.annotations.Column columnAnn) {
            if (columnAnn != null) {
                // Most restrictive wins
                if (columnAnn.unique()) {
                    unique = true;
                }
                if (!columnAnn.nullable()) {
                    notNull = true;
                }
            }
        }
    }
}
