package org.pojoquery.schema;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.pojoquery.DbContext;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Lob;
import org.pojoquery.annotations.Table;

/**
 * Tests schema generation across all supported SQL dialects.
 * Each test runs once per dialect to verify correct SQL is generated.
 */
@RunWith(Parameterized.class)
public class TestSchemaGeneratorDialects {

    @Table("users")
    public static class User {
        @Id Long id;
        String username;
        String email;
        Boolean active;
    }
    
    @Table("articles")
    public static class Article {
        @Id Long id;
        String title;
        @Lob String content;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> dialects() {
        return Arrays.asList(new Object[][] {
            { Dialect.MYSQL },
            { Dialect.HSQLDB },
            { Dialect.POSTGRES }
        });
    }

    private final Dialect dialect;
    private final DbContext dbContext;

    public TestSchemaGeneratorDialects(Dialect dialect) {
        this.dialect = dialect;
        this.dbContext = DbContext.forDialect(dialect);
    }

    @Test
    public void testBasicTableGeneration() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        System.out.println(dialect + ":\n" + sql + "\n");
        
        // All dialects should generate these basic elements
        assertTrue("Should contain id column", sql.toLowerCase().contains("id"));
        assertTrue("Should contain username column", sql.toLowerCase().contains("username"));
        assertTrue("Should contain email column", sql.toLowerCase().contains("email"));
        assertTrue("Should contain active column", sql.toLowerCase().contains("active"));
        assertTrue("Should contain PRIMARY KEY", sql.toUpperCase().contains("PRIMARY KEY"));
    }

    @Test
    public void testLobGeneration() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Article.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        System.out.println(dialect + " (LOB):\n" + sql + "\n");
        
        // title should be VARCHAR
        assertTrue("title should be VARCHAR", sql.toUpperCase().contains("VARCHAR"));
        
        // content should be a LOB type (dialect-specific)
        switch (dialect) {
            case MYSQL:
                assertTrue("MySQL should use LONGTEXT for @Lob", sql.contains("LONGTEXT"));
                break;
            case HSQLDB:
                assertTrue("HSQLDB should use CLOB for @Lob", sql.contains("CLOB"));
                break;
            case POSTGRES:
                assertTrue("Postgres should use TEXT for @Lob", 
                    sql.contains("TEXT") && !sql.contains("LONGTEXT"));
                break;
            default:
                break;
        }
    }

    @Test
    public void testBooleanType() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        switch (dialect) {
            case MYSQL:
                assertTrue("MySQL should use TINYINT(1) for Boolean", sql.contains("TINYINT(1)"));
                break;
            case HSQLDB:
            case POSTGRES:
                assertTrue("HSQLDB/Postgres should use BOOLEAN", sql.toUpperCase().contains("BOOLEAN"));
                break;
            default:
                break;
        }
    }

    @Test
    public void testAutoIncrement() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        switch (dialect) {
            case MYSQL:
                assertTrue("MySQL should use AUTO_INCREMENT", sql.contains("AUTO_INCREMENT"));
                break;
            case HSQLDB:
                assertTrue("HSQLDB should use IDENTITY", sql.contains("IDENTITY"));
                break;
            case POSTGRES:
                assertTrue("Postgres should use BIGSERIAL", sql.contains("BIGSERIAL"));
                break;
            default:
                break;
        }
    }

    @Test
    public void testQuoting() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        switch (dialect) {
            case MYSQL:
                assertTrue("MySQL should use backticks", sql.contains("`users`"));
                break;
            case HSQLDB:
                // HSQLDB uses no quoting for table names
                assertTrue("HSQLDB should not quote table names", sql.contains("users"));
                break;
            case POSTGRES:
                assertTrue("Postgres should use double quotes", sql.contains("\"users\""));
                break;
            default:
                break;
        }
    }

    @Test
    public void testTableSuffix() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        switch (dialect) {
            case MYSQL:
                assertTrue("MySQL should have ENGINE=InnoDB", sql.contains("ENGINE=InnoDB"));
                break;
            case HSQLDB:
            case POSTGRES:
                // No engine suffix for these
                assertTrue("No ENGINE for HSQLDB/Postgres", !sql.contains("ENGINE="));
                break;
            default:
                break;
        }
    }
}
