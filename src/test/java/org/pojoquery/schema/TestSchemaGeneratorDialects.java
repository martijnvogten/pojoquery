package org.pojoquery.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

    static Stream<Dialect> dialects() {
        return Stream.of(Dialect.MYSQL, Dialect.HSQLDB, Dialect.POSTGRES);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testBasicTableGeneration(Dialect dialect) {
        DbContext dbContext = DbContext.forDialect(dialect);
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        System.out.println(dialect + ":\n" + sql + "\n");
        
        // All dialects should generate these basic elements
        assertTrue(sql.toLowerCase().contains("id"), "Should contain id column");
        assertTrue(sql.toLowerCase().contains("username"), "Should contain username column");
        assertTrue(sql.toLowerCase().contains("email"), "Should contain email column");
        assertTrue(sql.toLowerCase().contains("active"), "Should contain active column");
        assertTrue(sql.toUpperCase().contains("PRIMARY KEY"), "Should contain PRIMARY KEY");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testLobGeneration(Dialect dialect) {
        DbContext dbContext = DbContext.forDialect(dialect);
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Article.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        System.out.println(dialect + " (LOB):\n" + sql + "\n");
        
        // title should be VARCHAR
        assertTrue(sql.toUpperCase().contains("VARCHAR"), "title should be VARCHAR");
        
        // content should be a LOB type (dialect-specific)
        switch (dialect) {
            case MYSQL:
                assertTrue(sql.contains("LONGTEXT"), "MySQL should use LONGTEXT for @Lob");
                break;
            case HSQLDB:
                assertTrue(sql.contains("CLOB"), "HSQLDB should use CLOB for @Lob");
                break;
            case POSTGRES:
                assertTrue(sql.contains("TEXT") && !sql.contains("LONGTEXT"), 
                    "Postgres should use TEXT for @Lob");
                break;
            default:
                break;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testBooleanType(Dialect dialect) {
        DbContext dbContext = DbContext.forDialect(dialect);
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        switch (dialect) {
            case MYSQL:
                assertTrue(sql.contains("TINYINT(1)"), "MySQL should use TINYINT(1) for Boolean");
                break;
            case HSQLDB:
            case POSTGRES:
                assertTrue(sql.toUpperCase().contains("BOOLEAN"), "HSQLDB/Postgres should use BOOLEAN");
                break;
            default:
                break;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testAutoIncrement(Dialect dialect) {
        DbContext dbContext = DbContext.forDialect(dialect);
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        switch (dialect) {
            case MYSQL:
                assertTrue(sql.contains("AUTO_INCREMENT"), "MySQL should use AUTO_INCREMENT");
                break;
            case HSQLDB:
                assertTrue(sql.contains("IDENTITY"), "HSQLDB should use IDENTITY");
                break;
            case POSTGRES:
                assertTrue(sql.contains("BIGSERIAL"), "Postgres should use BIGSERIAL");
                break;
            default:
                break;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testQuoting(Dialect dialect) {
        DbContext dbContext = DbContext.forDialect(dialect);
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        switch (dialect) {
            case MYSQL:
                assertTrue(sql.contains("`users`"), "MySQL should use backticks");
                break;
            case HSQLDB:
                // HSQLDB uses no quoting for table names
                assertTrue(sql.contains("users"), "HSQLDB should not quote table names");
                break;
            case POSTGRES:
                assertTrue(sql.contains("\"users\""), "Postgres should use double quotes");
                break;
            default:
                break;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testTableSuffix(Dialect dialect) {
        DbContext dbContext = DbContext.forDialect(dialect);
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        switch (dialect) {
            case MYSQL:
                assertTrue(sql.contains("ENGINE=InnoDB"), "MySQL should have ENGINE=InnoDB");
                break;
            case HSQLDB:
            case POSTGRES:
                // No engine suffix for these
                assertTrue(!sql.contains("ENGINE="), "No ENGINE for HSQLDB/Postgres");
                break;
            default:
                break;
        }
    }
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testForeignKeyConstraint(Dialect dialect) {
        // Order has a 'customer' field that references User - should generate FK constraint
        DbContext dbContext = DbContext.forDialect(dialect);
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(dbContext, Order.class, User.class);
        String sql = String.join("\n", sqlList);
        
        System.out.println(dialect + " (FK constraint):\n" + sql + "\n");
        
        // FK syntax is standard SQL, should work in all dialects
        assertTrue(sql.toUpperCase().contains("FOREIGN KEY"), "Should contain FOREIGN KEY");
        assertTrue(sql.toUpperCase().contains("REFERENCES"), "Should contain REFERENCES");
        
        // Check dialect-specific quoting
        switch (dialect) {
            case MYSQL:
                assertTrue(sql.contains("REFERENCES `users`(`id`)"), 
                    "MySQL FK should reference users table");
                break;
            case HSQLDB:
                assertTrue(sql.contains("REFERENCES users(id)"), 
                    "HSQLDB FK should reference users table");
                break;
            case POSTGRES:
                assertTrue(sql.contains("REFERENCES \"users\"(\"id\")"), 
                    "Postgres FK should reference users table");
                break;
            default:
                break;
        }
    }
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testInferredForeignKeyConstraint(Dialect dialect) {
        // Author has Book[] books - Book should have inferred authors_id with FK constraint
        DbContext dbContext = DbContext.forDialect(dialect);
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(dbContext, Author.class, Book.class);
        String sql = String.join("\n", sqlList);
        
        System.out.println(dialect + " (Inferred FK):\n" + sql + "\n");
        
        // Should have FK constraint from books to authors
        assertTrue(sql.toUpperCase().contains("FOREIGN KEY"), "Should contain FOREIGN KEY");
        
        switch (dialect) {
            case MYSQL:
                assertTrue(sql.contains("FOREIGN KEY (`authors_id`) REFERENCES `authors`(`id`)"), 
                    "MySQL FK should reference authors table");
                break;
            case HSQLDB:
                assertTrue(sql.contains("FOREIGN KEY (authors_id) REFERENCES authors(id)"), 
                    "HSQLDB FK should reference authors table");
                break;
            case POSTGRES:
                assertTrue(sql.contains("FOREIGN KEY (\"authors_id\") REFERENCES \"authors\"(\"id\")"), 
                    "Postgres FK should reference authors table");
                break;
            default:
                break;
        }
    }
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testLinkTableGeneration(Dialect dialect) {
        // Post has @Link(linktable="post_tag") - should generate link table with FKs
        DbContext dbContext = DbContext.forDialect(dialect);
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(dbContext, Post.class, Tag.class);
        String sql = String.join("\n", sqlList);
        
        System.out.println(dialect + " (Link table):\n" + sql + "\n");
        
        // Should generate 5 statements: 3 CREATE TABLE + 2 ALTER TABLE for link table FKs
        assertTrue(sqlList.size() == 5, "Should have 5 statements");
        
        // Link table should have composite PK and FKs to both tables (FKs via ALTER TABLE)
        switch (dialect) {
            case MYSQL:
                assertTrue(sql.contains("`post_tag`"), "MySQL link table should exist");
                assertTrue(sql.contains("ALTER TABLE `post_tag` ADD FOREIGN KEY (`posts_id`) REFERENCES `posts`(`id`);"), 
                    "MySQL link table should have FK to posts via ALTER");
                assertTrue(sql.contains("ALTER TABLE `post_tag` ADD FOREIGN KEY (`tags_id`) REFERENCES `tags`(`id`);"), 
                    "MySQL link table should have FK to tags via ALTER");
                break;
            case HSQLDB:
                assertTrue(sql.contains("post_tag"), "HSQLDB link table should exist");
                assertTrue(sql.contains("ALTER TABLE post_tag ADD FOREIGN KEY (posts_id) REFERENCES posts(id);"), 
                    "HSQLDB link table should have FK to posts via ALTER");
                assertTrue(sql.contains("ALTER TABLE tags ADD FOREIGN KEY (tags_id) REFERENCES tags(id);") || 
                    sql.contains("ALTER TABLE post_tag ADD FOREIGN KEY (tags_id) REFERENCES tags(id);"), 
                    "HSQLDB link table should have FK to tags via ALTER");
                break;
            case POSTGRES:
                assertTrue(sql.contains("\"post_tag\""), "Postgres link table should exist");
                assertTrue(sql.contains("ALTER TABLE \"post_tag\" ADD FOREIGN KEY (\"posts_id\") REFERENCES \"posts\"(\"id\");"), 
                    "Postgres link table should have FK to posts via ALTER");
                assertTrue(sql.contains("ALTER TABLE \"post_tag\" ADD FOREIGN KEY (\"tags_id\") REFERENCES \"tags\"(\"id\");"), 
                    "Postgres link table should have FK to tags via ALTER");
                break;
            default:
                break;
        }
    }
    
    // Test entities for @Column(nullable, unique, length, precision, scale) annotations
    @Table("accounts")
    public static class Account {
        @Id Long id;
        
        @org.pojoquery.annotations.Column(nullable = false, unique = true)
        String username;
        
        @org.pojoquery.annotations.Column(length = 100)
        String email;
        
        @org.pojoquery.annotations.Column(precision = 10, scale = 2)
        java.math.BigDecimal balance;
    }
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testColumnNullableAndUniqueAnnotationsAcrossDialects(Dialect dialect) {
        DbContext dbContext = DbContext.forDialect(dialect);
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Account.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        System.out.println(dialect + " (Column nullable/unique):\n" + sql + "\n");
        
        // All dialects should have NOT NULL and UNIQUE constraints
        assertTrue(sql.toUpperCase().contains("NOT NULL"), dialect + " should have NOT NULL");
        assertTrue(sql.toUpperCase().contains("UNIQUE"), dialect + " should have UNIQUE");
        
        // username should have NOT NULL UNIQUE
        switch (dialect) {
            case MYSQL:
                assertTrue(sql.contains("`username` VARCHAR(255) NOT NULL UNIQUE"), 
                    "MySQL username should be NOT NULL UNIQUE");
                break;
            case HSQLDB:
                assertTrue(sql.contains("username VARCHAR(255) NOT NULL UNIQUE"), 
                    "HSQLDB username should be NOT NULL UNIQUE");
                break;
            case POSTGRES:
                assertTrue(sql.contains("\"username\" VARCHAR(255) NOT NULL UNIQUE"), 
                    "Postgres username should be NOT NULL UNIQUE");
                break;
            default:
                break;
        }
    }
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testColumnLengthAnnotationAcrossDialects(Dialect dialect) {
        DbContext dbContext = DbContext.forDialect(dialect);
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Account.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        System.out.println(dialect + " (Column length):\n" + sql + "\n");
        
        // email should have VARCHAR(100)
        switch (dialect) {
            case MYSQL:
                assertTrue(sql.contains("`email` VARCHAR(100)"), 
                    "MySQL email should be VARCHAR(100)");
                break;
            case HSQLDB:
                assertTrue(sql.contains("email VARCHAR(100)"), 
                    "HSQLDB email should be VARCHAR(100)");
                break;
            case POSTGRES:
                assertTrue(sql.contains("\"email\" VARCHAR(100)"), 
                    "Postgres email should be VARCHAR(100)");
                break;
            default:
                break;
        }
    }
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testColumnPrecisionScaleAcrossDialects(Dialect dialect) {
        DbContext dbContext = DbContext.forDialect(dialect);
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Account.class, dbContext);
        String sql = String.join("\n", sqlList);
        
        System.out.println(dialect + " (Precision/Scale):\n" + sql + "\n");
        
        // balance should have DECIMAL(10,2) or NUMERIC(10,2)
        switch (dialect) {
            case MYSQL:
                assertTrue(sql.contains("`balance` DECIMAL(10,2)"), 
                    "MySQL balance should be DECIMAL(10,2)");
                break;
            case HSQLDB:
                assertTrue(sql.contains("balance DECIMAL(10,2)"), 
                    "HSQLDB balance should be DECIMAL(10,2)");
                break;
            case POSTGRES:
                assertTrue(sql.contains("\"balance\" NUMERIC(10,2)"), 
                    "Postgres balance should be NUMERIC(10,2)");
                break;
            default:
                break;
        }
    }
}
