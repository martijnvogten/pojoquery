package org.pojoquery.schema;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.QueryTreeBuilder;
import org.pojoquery.schema.QueryTreeFieldExtractor.TableDef;

/**
 * Generates CREATE TABLE statements from QueryTrees or entity classes.
 * 
 * <p>This generator extracts schema information from QueryTree structures,
 * ensuring the generated schema matches exactly what the queries expect.</p>
 * 
 * <p>Uses {@link QueryTreeFieldExtractor} to extract and merge field definitions,
 * and {@link QueryTreeDDLGenerator} to generate the actual DDL statements.</p>
 */
public class QueryTreeSchemaGenerator {
    
    // ========== Class-based convenience methods ==========
    
    /**
     * Generates CREATE TABLE statements for the given entity class.
     * 
     * @param entityClass the entity class annotated with @Table
     * @return list of CREATE TABLE statements
     */
    public static List<String> generateCreateTableStatements(Class<?> entityClass) {
        return generateCreateTableStatements(entityClass, DbContext.getDefault());
    }
    
    /**
     * Generates CREATE TABLE statements for the given entity class with custom DbContext.
     * 
     * @param entityClass the entity class annotated with @Table
     * @param dbContext the database context for dialect-specific generation
     * @return list of CREATE TABLE statements
     */
    public static List<String> generateCreateTableStatements(Class<?> entityClass, DbContext dbContext) {
        QueryTree tree = QueryTreeBuilder.from(entityClass);
        return generateCreateTableStatements(tree, dbContext);
    }
    
    /**
     * Generates CREATE TABLE statements for multiple entity classes.
     * 
     * @param entityClasses the entity classes
     * @return list of CREATE TABLE statements
     */
    public static List<String> generateCreateTableStatements(Class<?>... entityClasses) {
        return generateCreateTableStatements(DbContext.getDefault(), entityClasses);
    }
    
    /**
     * Generates CREATE TABLE statements for multiple entity classes with custom DbContext.
     * 
     * @param dbContext the database context for dialect-specific generation
     * @param entityClasses the entity classes
     * @return list of CREATE TABLE statements
     */
    public static List<String> generateCreateTableStatements(DbContext dbContext, Class<?>... entityClasses) {
        QueryTree[] trees = Arrays.stream(entityClasses)
            .map(QueryTreeBuilder::from)
            .toArray(QueryTree[]::new);
        return generateCreateTableStatements(trees, dbContext);
    }
    
    /**
     * Creates tables in the database for the given entity classes.
     * 
     * @param db the data source to execute the statements on
     * @param classes the entity classes to create tables for
     */
    public static void createTables(javax.sql.DataSource db, Class<?>... classes) {
        createTables(db, DbContext.getDefault(), classes);
    }
    
    /**
     * Creates tables in the database for the given entity classes with custom DbContext.
     * 
     * @param db the data source to execute the statements on
     * @param dbContext the database context for dialect-specific generation
     * @param classes the entity classes to create tables for
     */
    public static void createTables(javax.sql.DataSource db, DbContext dbContext, Class<?>... classes) {
        QueryTree[] trees = Arrays.stream(classes)
            .map(QueryTreeBuilder::from)
            .toArray(QueryTree[]::new);
        createTables(db, trees, dbContext);
    }
    
    // ========== QueryTree-based methods ==========
    
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
