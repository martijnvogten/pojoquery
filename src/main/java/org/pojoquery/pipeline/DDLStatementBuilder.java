package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.pojoquery.DbContext;
import org.pojoquery.pipeline.AQTSchemaGenerator.CollectedSchemaInfo;
import org.pojoquery.pipeline.AQTSchemaGenerator.DDLColumn;
import org.pojoquery.pipeline.AQTSchemaGenerator.DDLColumnKey;
import org.pojoquery.pipeline.AQTSchemaGenerator.DDLColumnMetadata;
import org.pojoquery.pipeline.AQTSchemaGenerator.DDLFieldColumn;
import org.pojoquery.pipeline.AQTSchemaGenerator.DDLForeignKey;
import org.pojoquery.pipeline.AQTSchemaGenerator.DDLInferredColumn;
import org.pojoquery.pipeline.AQTSchemaGenerator.DDLPrimaryKeyColumn;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.typemodel.ReflectionTypeModel;

/**
 * Builds DDL statements (CREATE TABLE, ALTER TABLE, etc.) from schema information.
 */
public class DDLStatementBuilder {

    private final DbContext dbContext;
    private final CollectedSchemaInfo schemaInfo;

    public DDLStatementBuilder(DbContext dbContext, CollectedSchemaInfo schemaInfo) {
        this.dbContext = dbContext;
        this.schemaInfo = schemaInfo;
    }

    /**
     * Generates a CREATE TABLE statement for the given table.
     */
    public String generateCreateTableStatement(TableInfo table) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ");
        sb.append(quoteSchemaAndTable(table));
        sb.append(" (\n");

        List<DDLColumn> tableColumns = getColumnsForTable(table);

        List<String> columnDefs = new ArrayList<>();
        for (DDLColumn col : tableColumns) {
            columnDefs.add("  " + generateColumnDefinition(col));
        }

        // Add primary key constraint
        List<DDLColumn> primaryKeys = tableColumns.stream()
                .filter(col -> col instanceof DDLPrimaryKeyColumn)
                .toList();
        if (!primaryKeys.isEmpty()) {
            String pkColumns = primaryKeys.stream()
                    .map(pk -> dbContext.getQuoteStyle().quote(pk.columnKey().columnName()))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            columnDefs.add("  PRIMARY KEY (" + pkColumns + ")");
        }

        sb.append(String.join(",\n", columnDefs));
        sb.append("\n)");
        return sb.toString();
    }

    /**
     * Generates an ALTER TABLE ADD COLUMN statement.
     */
    public String generateAddColumnStatement(TableInfo table, DDLColumn column) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(quoteSchemaAndTable(table));
        sb.append(" ADD COLUMN ");
        sb.append(generateColumnDefinition(column));
        return sb.toString();
    }

    /**
     * Generates an ALTER TABLE ADD CONSTRAINT statement for a foreign key.
     */
    public String generateAddForeignKeyStatement(DDLForeignKey fk) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(quoteSchemaAndTable(fk.referringColumn().tableKey()));
        sb.append(" ADD CONSTRAINT ");
        sb.append(dbContext.quoteObjectNames("fk_" + fk.referringColumn().tableKey().tableName() + "_" + fk.referringColumn().columnName()));
        sb.append(" FOREIGN KEY (");
        sb.append(dbContext.quoteObjectNames(fk.referringColumn().columnName()));
        sb.append(") REFERENCES ");
        sb.append(quoteSchemaAndTable(fk.referencedIdColumn().tableKey()));
        sb.append(" (");
        sb.append(dbContext.quoteObjectNames(fk.referencedIdColumn().columnName()));
        sb.append(")");
        return sb.toString();
    }

    /**
     * Generates a column definition (name, type, constraints).
     */
    public String generateColumnDefinition(DDLColumn col) {
        StringBuilder colDef = new StringBuilder();
        colDef.append(dbContext.getQuoteStyle().quote(col.columnKey().columnName()));
        colDef.append(" ");

        DDLColumnMetadata metadata = schemaInfo.buildColumnMetadata(col.columnKey());

        if (col instanceof DDLPrimaryKeyColumn pkColumn && pkColumn.isAutoIncrement()) {
            colDef.append(dbContext.getAutoIncrementKeyColumnType());
            colDef.append(" ");
            colDef.append(dbContext.getAutoIncrementSyntax());
        } else {
            Class<?> javaType = resolveColumnJavaType(col);
            if (javaType != null) {
                colDef.append(dbContext.mapJavaTypeToSql(javaType, metadata));
            } else {
                colDef.append(dbContext.getForeignKeyColumnType());
            }
        }

        if (metadata != null) {
            if (!metadata.nullable()) {
                colDef.append(" NOT NULL");
            }
            if (metadata.unique()) {
                colDef.append(" UNIQUE");
            }
        }

        return colDef.toString();
    }

    /**
     * Resolves the Java type for a column, used for SQL type mapping.
     * @return the Java class for type mapping, or null if foreign key type should be used
     */
    private Class<?> resolveColumnJavaType(DDLColumn col) {
        if (col instanceof DDLPrimaryKeyColumn pkColumn) {
            if (pkColumn.field() != null) {
                return ((ReflectionTypeModel) pkColumn.field().getType()).getReflectionClass();
            }
            // Check if there's type info from an inferred column (e.g., ValueCollection fetch column)
            DDLColumn inferredCol = schemaInfo.columns().get(col.columnKey());
            if (inferredCol instanceof DDLInferredColumn ic) {
                return ((ReflectionTypeModel) ic.scalarType()).getReflectionClass();
            }
            return null;
        } else if (col instanceof DDLFieldColumn fieldColumn && fieldColumn.field() != null) {
            if (schemaInfo.foreignKeys().containsKey(col.columnKey())) {
                return null;
            }
            return ((ReflectionTypeModel) fieldColumn.field().getType()).getReflectionClass();
        } else if (col instanceof DDLInferredColumn inferredColumn) {
            return ((ReflectionTypeModel) inferredColumn.scalarType()).getReflectionClass();
        }
        return null;
    }

    /**
     * Gets all columns (regular + primary key) for a table.
     */
    public List<DDLColumn> getColumnsForTable(TableInfo table) {
        Map<DDLColumnKey, DDLColumn> allColumns = new LinkedHashMap<>();
        allColumns.putAll(schemaInfo.columns());
        allColumns.putAll(schemaInfo.primaryKeys());

        return allColumns.values().stream()
                .filter(col -> col.columnKey().tableKey().equals(table))
                .toList();
    }

    /**
     * Quotes schema and table name appropriately for the database context.
     */
    public String quoteSchemaAndTable(TableInfo tableInfo) {
        if (tableInfo.schemaName() != null) {
            return dbContext.quoteObjectNames(tableInfo.schemaName(), tableInfo.tableName());
        } else {
            return dbContext.quoteObjectNames(tableInfo.tableName());
        }
    }
}
