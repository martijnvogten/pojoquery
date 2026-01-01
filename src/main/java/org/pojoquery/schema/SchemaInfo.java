package org.pojoquery.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.pojoquery.DB;

/**
 * Provides schema information about existing database tables and columns.
 * Used by SchemaGenerator to determine whether to CREATE or ALTER tables.
 */
public class SchemaInfo {
    
    private final Map<String, TableInfo> tables = new HashMap<>();
    
    /**
     * Information about a single table.
     */
    public static class TableInfo {
        private final String tableName;
        private final String schemaName;
        private final Set<String> columnNames = new HashSet<>();
        
        public TableInfo(String tableName, String schemaName) {
            this.tableName = tableName;
            this.schemaName = schemaName;
        }
        
        public String getTableName() {
            return tableName;
        }
        
        public String getSchemaName() {
            return schemaName;
        }
        
        public Set<String> getColumnNames() {
            return columnNames;
        }
        
        public boolean hasColumn(String columnName) {
            return columnNames.contains(columnName.toLowerCase());
        }
        
        void addColumn(String columnName) {
            columnNames.add(columnName.toLowerCase());
        }
    }
    
    /**
     * Creates a SchemaInfo by querying the database metadata.
     * 
     * @param dataSource the data source to query
     * @return SchemaInfo containing table and column information
     */
    public static SchemaInfo fromDataSource(DataSource dataSource) {
        return DB.runInTransaction(dataSource, (DB.Transaction<SchemaInfo>) connection -> fromConnection(connection));
    }
    
    /**
     * Creates a SchemaInfo by querying the database metadata.
     * 
     * @param connection the database connection to query
     * @return SchemaInfo containing table and column information
     */
    public static SchemaInfo fromConnection(Connection connection) {
        SchemaInfo info = new SchemaInfo();
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Get all tables
            try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String catalog = tables.getString("TABLE_CAT");
                    String schema = tables.getString("TABLE_SCHEM");
                    String tableName = tables.getString("TABLE_NAME");
                    
                    // Use catalog as schema for MySQL (which uses catalog instead of schema)
                    String effectiveSchema = schema != null ? schema : catalog;
                    
                    TableInfo tableInfo = new TableInfo(tableName, effectiveSchema);
                    info.addTable(effectiveSchema, tableName, tableInfo);
                    
                    // Get columns for this table
                    try (ResultSet columns = metaData.getColumns(catalog, schema, tableName, "%")) {
                        while (columns.next()) {
                            String columnName = columns.getString("COLUMN_NAME");
                            tableInfo.addColumn(columnName);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new DB.DatabaseException(e);
        }
        return info;
    }
    
    /**
     * Creates a SchemaInfo for specific schemas only.
     * 
     * @param dataSource the data source to query
     * @param schemaNames the schema/database names to include
     * @return SchemaInfo containing table and column information for the specified schemas
     */
    public static SchemaInfo fromDataSource(DataSource dataSource, String... schemaNames) {
        return DB.runInTransaction(dataSource, (DB.Transaction<SchemaInfo>) connection -> fromConnection(connection, schemaNames));
    }
    
    /**
     * Creates a SchemaInfo for specific schemas only.
     * 
     * @param connection the database connection to query
     * @param schemaNames the schema/database names to include
     * @return SchemaInfo containing table and column information for the specified schemas
     */
    public static SchemaInfo fromConnection(Connection connection, String... schemaNames) {
        SchemaInfo info = new SchemaInfo();
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            
            for (String schemaName : schemaNames) {
                // Try as catalog (MySQL) and schema (PostgreSQL, etc.)
                try (ResultSet tables = metaData.getTables(schemaName, null, "%", new String[]{"TABLE"})) {
                    while (tables.next()) {
                        String tableName = tables.getString("TABLE_NAME");
                        
                        TableInfo tableInfo = new TableInfo(tableName, schemaName);
                        info.addTable(schemaName, tableName, tableInfo);
                        
                        // Get columns for this table
                        try (ResultSet columns = metaData.getColumns(schemaName, null, tableName, "%")) {
                            while (columns.next()) {
                                String columnName = columns.getString("COLUMN_NAME");
                                tableInfo.addColumn(columnName);
                            }
                        }
                    }
                }
                
                // Also try with schema parameter (for databases that distinguish catalog from schema)
                try (ResultSet tables = metaData.getTables(null, schemaName, "%", new String[]{"TABLE"})) {
                    while (tables.next()) {
                        String tableName = tables.getString("TABLE_NAME");
                        
                        // Skip if already added
                        if (info.hasTable(schemaName, tableName)) {
                            continue;
                        }
                        
                        TableInfo tableInfo = new TableInfo(tableName, schemaName);
                        info.addTable(schemaName, tableName, tableInfo);
                        
                        // Get columns for this table
                        try (ResultSet columns = metaData.getColumns(null, schemaName, tableName, "%")) {
                            while (columns.next()) {
                                String columnName = columns.getString("COLUMN_NAME");
                                tableInfo.addColumn(columnName);
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new DB.DatabaseException(e);
        }
        return info;
    }
    
    private void addTable(String schemaName, String tableName, TableInfo tableInfo) {
        String key = makeKey(schemaName, tableName);
        tables.put(key, tableInfo);
    }
    
    /**
     * Adds a table for testing purposes.
     * This method is package-private to allow tests to construct schema info manually.
     */
    public void addTableForTesting(String schemaName, String tableName, TableInfo tableInfo) {
        addTable(schemaName, tableName, tableInfo);
    }
    
    /**
     * Checks if a table exists.
     * 
     * @param schemaName the schema name (can be null for default schema)
     * @param tableName the table name
     * @return true if the table exists
     */
    public boolean hasTable(String schemaName, String tableName) {
        return tables.containsKey(makeKey(schemaName, tableName));
    }
    
    /**
     * Checks if a table exists (without schema).
     * 
     * @param tableName the table name
     * @return true if the table exists
     */
    public boolean hasTable(String tableName) {
        return hasTable(null, tableName);
    }
    
    /**
     * Gets table information.
     * 
     * <p>When schemaName is null, this method first tries to find a table with no schema,
     * then falls back to searching across all schemas for a matching table name.
     * 
     * @param schemaName the schema name (can be null for default schema)
     * @param tableName the table name
     * @return TableInfo or null if table doesn't exist
     */
    public TableInfo getTable(String schemaName, String tableName) {
        TableInfo result = tables.get(makeKey(schemaName, tableName));
        
        // If not found and no schema was specified, try to find the table in any schema
        if (result == null && (schemaName == null || schemaName.isEmpty())) {
            String lowerTableName = tableName.toLowerCase();
            for (Map.Entry<String, TableInfo> entry : tables.entrySet()) {
                String key = entry.getKey();
                // Check if this key ends with the table name (after a dot, or is the table name itself)
                if (key.equals(lowerTableName) || key.endsWith("." + lowerTableName)) {
                    return entry.getValue();
                }
            }
        }
        
        return result;
    }
    
    /**
     * Gets table information (without schema).
     * 
     * @param tableName the table name
     * @return TableInfo or null if table doesn't exist
     */
    public TableInfo getTable(String tableName) {
        return getTable(null, tableName);
    }
    
    private String makeKey(String schemaName, String tableName) {
        if (schemaName == null || schemaName.isEmpty()) {
            return tableName.toLowerCase();
        }
        return (schemaName + "." + tableName).toLowerCase();
    }
}
