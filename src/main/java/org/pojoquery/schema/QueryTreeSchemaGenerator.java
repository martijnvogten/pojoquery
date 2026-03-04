package org.pojoquery.schema;

import java.util.List;
import java.util.Map;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.schema.QueryTreeFieldExtractor.TableDef;

/**
 * Generates CREATE TABLE statements from QueryTrees.
 * 
 * <p>Unlike {@link SchemaGenerator} which derives schema information from POJO classes,
 * this generator extracts all information from already-built QueryTree structures.
 * This ensures the schema matches exactly what the query trees expect.</p>
 * 
 * <p>Uses {@link QueryTreeFieldExtractor} to extract and merge field definitions,
 * and {@link QueryTreeDDLGenerator} to generate the actual DDL statements.</p>
 */
public class QueryTreeSchemaGenerator {
    
    /**
     * Generates CREATE TABLE statements for all tables in the QueryTree.
     */
    public static List<String> generateCreateTableStatements(QueryTree tree) {
        return generateCreateTableStatements(tree, DbContext.getDefault());
    }
    
    /**
     * Generates CREATE TABLE statements for all tables in the QueryTrees.
     */
    public static List<String> generateCreateTableStatements(QueryTree tree, DbContext dbContext) {
        return generateCreateTableStatements(new QueryTree[]{tree}, dbContext);
    }
    
    /**
     * Generates CREATE TABLE statements for all tables in the QueryTrees.
     */
    public static List<String> generateCreateTableStatements(QueryTree[] trees, DbContext dbContext) {
        Map<String, TableDef> tables = QueryTreeFieldExtractor.extractTables(List.of(trees));
        return QueryTreeDDLGenerator.generateCreateTableStatements(tables, dbContext);
    }
    
    /**
     * Creates tables in the database for the given QueryTrees.
     */
    public static void createTables(javax.sql.DataSource db, QueryTree... trees) {
        createTables(db, trees, DbContext.getDefault());
    }
    
    /**
     * Creates tables in the database for the given QueryTrees (List version).
     */
    public static void createTables(javax.sql.DataSource db, List<QueryTree> trees) {
        createTables(db, trees.toArray(new QueryTree[0]), DbContext.getDefault());
    }
    
    /**
     * Creates tables in the database for the given QueryTree with custom DbContext.
     */
    public static void createTables(javax.sql.DataSource db, QueryTree tree, DbContext dbContext) {
        createTables(db, new QueryTree[]{tree}, dbContext);
    }
    
    /**
     * Creates tables in the database for the given QueryTrees with custom DbContext.
     */
    public static void createTables(javax.sql.DataSource db, QueryTree[] trees, DbContext dbContext) {
        List<String> ddlStatements = generateCreateTableStatements(trees, dbContext);
        DB.runInTransaction(db, c -> {
            for (String ddl : ddlStatements) {
                DB.executeDDL(c, ddl);
            }
        });
    }
}
