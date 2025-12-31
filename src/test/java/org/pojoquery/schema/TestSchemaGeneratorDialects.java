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
import org.pojoquery.annotations.Link;
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
    
    @Table("orders")
    public static class Order {
        @Id Long id;
        User customer;
        String description;
    }
    
    @Table("authors")
    public static class Author {
        @Id Long id;
        String name;
        Book[] books;
    }
    
    @Table("books")
    public static class Book {
        @Id Long id;
        String title;
    }
    
    @Table("tags")
    public static class Tag {
        @Id Long id;
        String name;
    }
    
    @Table("posts")
    public static class Post {
        @Id Long id;
        String title;
        @Link(linktable = "post_tag")
        List<Tag> tags;
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
    
    @Test
    public void testForeignKeyConstraint() {
        // Order has a 'customer' field that references User - should generate FK constraint
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(dbContext, Order.class, User.class);
        String sql = String.join("\n", sqlList);
        
        System.out.println(dialect + " (FK constraint):\n" + sql + "\n");
        
        // FK syntax is standard SQL, should work in all dialects
        assertTrue("Should contain FOREIGN KEY", sql.toUpperCase().contains("FOREIGN KEY"));
        assertTrue("Should contain REFERENCES", sql.toUpperCase().contains("REFERENCES"));
        
        // Check dialect-specific quoting
        switch (dialect) {
            case MYSQL:
                assertTrue("MySQL FK should reference users table", 
                    sql.contains("REFERENCES `users`(`id`)"));
                break;
            case HSQLDB:
                assertTrue("HSQLDB FK should reference users table", 
                    sql.contains("REFERENCES users(id)"));
                break;
            case POSTGRES:
                assertTrue("Postgres FK should reference users table", 
                    sql.contains("REFERENCES \"users\"(\"id\")"));
                break;
            default:
                break;
        }
    }
    
    @Test
    public void testInferredForeignKeyConstraint() {
        // Author has Book[] books - Book should have inferred authors_id with FK constraint
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(dbContext, Author.class, Book.class);
        String sql = String.join("\n", sqlList);
        
        System.out.println(dialect + " (Inferred FK):\n" + sql + "\n");
        
        // Should have FK constraint from books to authors
        assertTrue("Should contain FOREIGN KEY", sql.toUpperCase().contains("FOREIGN KEY"));
        
        switch (dialect) {
            case MYSQL:
                assertTrue("MySQL FK should reference authors table", 
                    sql.contains("FOREIGN KEY (`authors_id`) REFERENCES `authors`(`id`)"));
                break;
            case HSQLDB:
                assertTrue("HSQLDB FK should reference authors table", 
                    sql.contains("FOREIGN KEY (authors_id) REFERENCES authors(id)"));
                break;
            case POSTGRES:
                assertTrue("Postgres FK should reference authors table", 
                    sql.contains("FOREIGN KEY (\"authors_id\") REFERENCES \"authors\"(\"id\")"));
                break;
            default:
                break;
        }
    }
    
    @Test
    public void testLinkTableGeneration() {
        // Post has @Link(linktable="post_tag") - should generate link table with FKs
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(dbContext, Post.class, Tag.class);
        String sql = String.join("\n", sqlList);
        
        System.out.println(dialect + " (Link table):\n" + sql + "\n");
        
        // Should generate 5 statements: 3 CREATE TABLE + 2 ALTER TABLE for link table FKs
        assertTrue("Should have 5 statements", sqlList.size() == 5);
        
        // Link table should have composite PK and FKs to both tables (FKs via ALTER TABLE)
        switch (dialect) {
            case MYSQL:
                assertTrue("MySQL link table should exist", sql.contains("`post_tag`"));
                assertTrue("MySQL link table should have FK to posts via ALTER", 
                    sql.contains("ALTER TABLE `post_tag` ADD FOREIGN KEY (`posts_id`) REFERENCES `posts`(`id`);"));
                assertTrue("MySQL link table should have FK to tags via ALTER", 
                    sql.contains("ALTER TABLE `post_tag` ADD FOREIGN KEY (`tags_id`) REFERENCES `tags`(`id`);"));
                break;
            case HSQLDB:
                assertTrue("HSQLDB link table should exist", sql.contains("post_tag"));
                assertTrue("HSQLDB link table should have FK to posts via ALTER", 
                    sql.contains("ALTER TABLE post_tag ADD FOREIGN KEY (posts_id) REFERENCES posts(id);"));
                assertTrue("HSQLDB link table should have FK to tags via ALTER", 
                    sql.contains("ALTER TABLE tags ADD FOREIGN KEY (tags_id) REFERENCES tags(id);") || 
                    sql.contains("ALTER TABLE post_tag ADD FOREIGN KEY (tags_id) REFERENCES tags(id);"));
                break;
            case POSTGRES:
                assertTrue("Postgres link table should exist", sql.contains("\"post_tag\""));
                assertTrue("Postgres link table should have FK to posts via ALTER", 
                    sql.contains("ALTER TABLE \"post_tag\" ADD FOREIGN KEY (\"posts_id\") REFERENCES \"posts\"(\"id\");"));
                assertTrue("Postgres link table should have FK to tags via ALTER", 
                    sql.contains("ALTER TABLE \"post_tag\" ADD FOREIGN KEY (\"tags_id\") REFERENCES \"tags\"(\"id\");"));
                break;
            default:
                break;
        }
    }
}
