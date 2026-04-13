package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.pojoquery.DbContext;
import org.pojoquery.pipeline.AQTSchemaGenerator.CollectedSchemaInfo;
import org.pojoquery.pipeline.AQTSchemaGenerator.DDLColumn;
import org.pojoquery.pipeline.AQTSchemaGenerator.DDLColumnKey;
import org.pojoquery.pipeline.AQTSchemaGenerator.DDLForeignKey;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.schema.SchemaInfo;

/**
 * Computes the difference between a current database schema and a desired schema,
 * and generates DDL statements to migrate from current to desired.
 */
public class SchemaDiff {

    private final Set<TableInfo> tablesToCreate;
    private final Map<TableInfo, Set<DDLColumn>> columnsToAdd;
    private final Set<DDLForeignKey> foreignKeysToAdd;
    private final CollectedSchemaInfo desiredSchema;

    private SchemaDiff(
            Set<TableInfo> tablesToCreate,
            Map<TableInfo, Set<DDLColumn>> columnsToAdd,
            Set<DDLForeignKey> foreignKeysToAdd,
            CollectedSchemaInfo desiredSchema) {
        this.tablesToCreate = tablesToCreate;
        this.columnsToAdd = columnsToAdd;
        this.foreignKeysToAdd = foreignKeysToAdd;
        this.desiredSchema = desiredSchema;
    }

    /**
     * Computes the difference between the current database schema and the desired schema.
     * 
     * @param currentSchema the current database schema (from SchemaInfo.fromConnection)
     * @param desiredSchema the desired schema (from entity classes)
     * @return a SchemaDiff representing the changes needed
     */
    public static SchemaDiff diffSchemas(SchemaInfo currentSchema, CollectedSchemaInfo desiredSchema) {
        Set<TableInfo> tablesToCreate = new LinkedHashSet<>();
        Map<TableInfo, Set<DDLColumn>> columnsToAdd = new LinkedHashMap<>();
        Set<DDLForeignKey> foreignKeysToAdd = new LinkedHashSet<>();

        // Combine columns and primary keys for analysis
        Map<DDLColumnKey, DDLColumn> allColumns = new LinkedHashMap<>();
        allColumns.putAll(desiredSchema.columns());
        allColumns.putAll(desiredSchema.primaryKeys());

        // Check each table in the desired schema
        for (TableInfo desiredTable : desiredSchema.tables()) {
            SchemaInfo.TableInfo currentTable = currentSchema.getTable(
                    desiredTable.schemaName(), 
                    desiredTable.tableName());

            if (currentTable == null) {
                // Table doesn't exist - need to create it
                tablesToCreate.add(desiredTable);
                
                // All foreign keys for this new table should be added
                for (DDLForeignKey fk : desiredSchema.foreignKeys().values()) {
                    if (fk.referringColumn().tableKey().equals(desiredTable)) {
                        foreignKeysToAdd.add(fk);
                    }
                }
            } else {
                // Table exists - check for missing columns
                Set<DDLColumn> missingColumns = new LinkedHashSet<>();
                
                for (Map.Entry<DDLColumnKey, DDLColumn> entry : allColumns.entrySet()) {
                    DDLColumnKey columnKey = entry.getKey();
                    DDLColumn column = entry.getValue();
                    
                    if (columnKey.tableKey().equals(desiredTable)) {
                        if (!currentTable.hasColumn(columnKey.columnName())) {
                            missingColumns.add(column);
                            
                            // Check if this column has an associated foreign key
                            DDLForeignKey fk = desiredSchema.foreignKeys().get(columnKey);
                            if (fk != null) {
                                foreignKeysToAdd.add(fk);
                            }
                        }
                    }
                }
                
                if (!missingColumns.isEmpty()) {
                    columnsToAdd.put(desiredTable, missingColumns);
                }
            }
        }

        return new SchemaDiff(tablesToCreate, columnsToAdd, foreignKeysToAdd, desiredSchema);
    }

    /**
     * Generates DDL statements to migrate from the current schema to the desired schema.
     * 
     * @param dbContext the database context for dialect-specific SQL generation
     * @return list of DDL statements (CREATE TABLE, ALTER TABLE ADD COLUMN, etc.)
     */
    public List<String> generateMigrationDDL(DbContext dbContext) {
        DDLStatementBuilder builder = new DDLStatementBuilder(dbContext, desiredSchema);
        List<String> statements = new ArrayList<>();

        // Generate CREATE TABLE statements for new tables
        for (TableInfo table : tablesToCreate) {
            statements.add(builder.generateCreateTableStatement(table));
        }

        // Generate ALTER TABLE ADD COLUMN statements for existing tables
        for (Map.Entry<TableInfo, Set<DDLColumn>> entry : columnsToAdd.entrySet()) {
            TableInfo table = entry.getKey();
            for (DDLColumn column : entry.getValue()) {
                statements.add(builder.generateAddColumnStatement(table, column));
            }
        }

        // Generate ALTER TABLE ADD CONSTRAINT statements for foreign keys
        for (DDLForeignKey fk : foreignKeysToAdd) {
            statements.add(builder.generateAddForeignKeyStatement(fk));
        }

        return statements;
    }

    // Getters for testing/inspection
    
    public Set<TableInfo> getTablesToCreate() {
        return tablesToCreate;
    }

    public Map<TableInfo, Set<DDLColumn>> getColumnsToAdd() {
        return columnsToAdd;
    }

    public Set<DDLForeignKey> getForeignKeysToAdd() {
        return foreignKeysToAdd;
    }

    public boolean isEmpty() {
        return tablesToCreate.isEmpty() && columnsToAdd.isEmpty() && foreignKeysToAdd.isEmpty();
    }
}
