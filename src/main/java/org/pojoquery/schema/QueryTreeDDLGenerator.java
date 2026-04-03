package org.pojoquery.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.DbContext;
import org.pojoquery.schema.QueryTreeFieldExtractor.FieldDef;
import org.pojoquery.schema.QueryTreeFieldExtractor.TableDef;
import org.pojoquery.typemodel.ReflectionTypeModel;

/**
 * Generates CREATE TABLE DDL statements from extracted field definitions.
 */
public class QueryTreeDDLGenerator {

    /**
     * Generates CREATE TABLE statements for all tables.
     */
    public static List<String> generateCreateTableStatements(Map<String, TableDef> tables, DbContext dbContext) {
        List<String> statements = new ArrayList<>();
        for (TableDef table : tables.values()) {
            statements.add(generateCreateTable(table, dbContext));
        }
        return statements;
    }

    /**
     * Generates a CREATE TABLE statement for a single table.
     */
    public static String generateCreateTable(TableDef table, DbContext dbContext) {
        StringBuilder sb = new StringBuilder();
        
        String tableName = formatTableName(table.schemaName(), table.tableName(), dbContext);
        sb.append("CREATE TABLE ").append(tableName).append(" (\n");
        
        List<String> columnDefs = new ArrayList<>();
        
        for (FieldDef col : table.columns()) {
            columnDefs.add(formatColumnDef(col, table.primaryKey().size(), dbContext));
        }
        
        // Add primary key constraint
        if (!table.primaryKey().isEmpty()) {
            StringBuilder pkDef = new StringBuilder("PRIMARY KEY (");
            for (int i = 0; i < table.primaryKey().size(); i++) {
                if (i > 0) pkDef.append(", ");
                pkDef.append(dbContext.quoteObjectNames(table.primaryKey().get(i)));
            }
            pkDef.append(")");
            columnDefs.add(pkDef.toString());
        }
        
        sb.append("  ");
        sb.append(String.join(",\n  ", columnDefs));
        sb.append("\n)");
        
        String suffix = dbContext.getCreateTableSuffix();
        if (suffix != null && !suffix.isEmpty()) {
            sb.append(suffix);
        }
        sb.append(";");
        
        return sb.toString();
    }

    private static String formatColumnDef(FieldDef col, int primaryKeyCount, DbContext dbContext) {
        StringBuilder sb = new StringBuilder();
        sb.append(dbContext.quoteObjectNames(col.key().columnName()));
        sb.append(" ");
        
        if (col.isDiscriminator()) {
            sb.append("VARCHAR(255) NOT NULL");
        } else if (col.isAutoIncrement() && !dbContext.getAutoIncrementKeyColumnType().equals("BIGINT")) {
            sb.append(dbContext.getAutoIncrementKeyColumnType());
        } else if (col.field() != null) {
            sb.append(dbContext.mapJavaTypeToSql(((ReflectionTypeModel)col.field().getType()).getReflectionClass(), null));
            
            // Add constraints from field annotations
            if (!col.isAutoIncrement()) {
                AnnotationHelper.ColumnMetadata meta = AnnotationHelper.getColumnMetadata(col.field());
                if (meta != null && !meta.nullable) {
                    sb.append(" NOT NULL");
                }
            }
            
            if (col.isAutoIncrement()) {
                String autoInc = dbContext.getAutoIncrementSyntax();
                if (!autoInc.isEmpty()) {
                    sb.append(" ").append(autoInc);
                }
            }
            
            if (col.field() != null) {
                AnnotationHelper.ColumnMetadata meta = AnnotationHelper.getColumnMetadata(col.field());
                if (meta != null && meta.unique) {
                    sb.append(" UNIQUE");
                }
            }
        } else {
            // Foreign key or unknown column - default to BIGINT
            sb.append(dbContext.getForeignKeyColumnType());
        }
        
        return sb.toString();
    }

    private static String formatTableName(String schemaName, String tableName, DbContext dbContext) {
        if (schemaName != null && !schemaName.isEmpty()) {
            return dbContext.quoteObjectNames(schemaName) + "." + dbContext.quoteObjectNames(tableName);
        }
        return dbContext.quoteObjectNames(tableName);
    }
}
