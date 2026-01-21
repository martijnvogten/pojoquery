package org.pojoquery.integrationtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Lob;
import org.pojoquery.annotations.Table;
import org.pojoquery.schema.SchemaGenerator;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import com.mysql.cj.jdbc.MysqlDataSource;

/**
 * Integration tests that run against real databases using Testcontainers.
 * Tests basic CRUD operations, LOB handling, and dialect-specific features.
 * 
 * <p>HSQLDB tests always run. MySQL and PostgreSQL tests require Docker.</p>
 * <p>If Docker is not available, MySQL/PostgreSQL tests are skipped.</p>
 */
@Disabled("Disabled: takes too long to run automatically on every build")
public class MultiDialectIT {

    // Testcontainers - lazily initialized only when Docker is available
    private static MySQLContainer<?> mysql;
    private static PostgreSQLContainer<?> postgres;
    private static boolean dockerAvailable = false;
    
    // Instance variables for test state
    private DataSource dataSource;
    private DbContext dbContext;
    private Dialect dialect;
    private static int hsqldbCounter = 0;

    @BeforeAll
    @SuppressWarnings("resource")
    public static void checkDocker() {
        try {
            // Try starting MySQL container - this will fail if Docker is not available
            mysql = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");
            mysql.start();
            
            postgres = new PostgreSQLContainer<>("postgres:15")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");
            postgres.start();
            
            dockerAvailable = true;
            System.out.println("Docker is available - MySQL and PostgreSQL tests will run");
        } catch (Exception e) {
            System.out.println("Docker is not available - MySQL and PostgreSQL tests will be skipped");
            System.out.println("  Reason: " + e.getMessage());
            dockerAvailable = false;
        }
    }

    // Test entities
    @Table("users")
    public static class User {
        @Id
        public Long id;
        public String username;
        public String email;
        public Boolean active;
        
        public User() {}
        
        public User(String username, String email) {
            this.username = username;
            this.email = email;
            this.active = true;
        }
    }

    @Table("articles")
    public static class Article {
        @Id
        public Long id;
        public String title;
        @Lob
        public String content;
        
        public Article() {}
        
        public Article(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }

    static Stream<Dialect> dialects() {
        return Stream.of(Dialect.HSQLDB, Dialect.MYSQL, Dialect.POSTGRES);
    }

    void setUp(Dialect dialect) {
        this.dialect = dialect;
        // Skip MySQL/PostgreSQL tests if Docker is not available
        if (dialect == Dialect.MYSQL || dialect == Dialect.POSTGRES) {
            assumeTrue(dockerAvailable, "Docker is not available - skipping " + dialect + " tests");
        }
        
        dbContext = DbContext.forDialect(dialect);
        DbContext.setDefault(dbContext);
        
        switch (dialect) {
            case HSQLDB:
                dataSource = createHsqldbDataSource();
                break;
            case MYSQL:
                dataSource = createMysqlDataSource();
                break;
            case POSTGRES:
                dataSource = createPostgresDataSource();
                break;
            default:
                throw new IllegalArgumentException("Unsupported dialect: " + dialect);
        }
        
        // Create tables
        createTables();
    }

    @AfterEach
    public void tearDown() {
        // Drop tables to ensure clean state
        dropTables();
    }

    private DataSource createHsqldbDataSource() {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:multitest_" + (++hsqldbCounter));
        ds.setUser("SA");
        ds.setPassword("");
        return ds;
    }

    private DataSource createMysqlDataSource() {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(mysql.getJdbcUrl());
        ds.setUser(mysql.getUsername());
        ds.setPassword(mysql.getPassword());
        return ds;
    }

    private DataSource createPostgresDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        return ds;
    }

    private void createTables() {
        for (String ddl : SchemaGenerator.generateCreateTableStatements(dbContext, User.class, Article.class)) {
            System.out.println(dialect + ": " + ddl);
            DB.executeDDL(dataSource, ddl);
        }
    }

    private void dropTables() {
        try {
            DB.executeDDL(dataSource, "DROP TABLE IF EXISTS " + dbContext.quoteObjectNames("articles"));
            DB.executeDDL(dataSource, "DROP TABLE IF EXISTS " + dbContext.quoteObjectNames("users"));
        } catch (Exception e) {
            // Ignore errors during cleanup
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testInsertAndSelect(Dialect dialect) {
        setUp(dialect);
        DB.withConnection(dataSource, c -> {
            User user = new User("alice", "alice@example.com");
            PojoQuery.insert(c, user);
            
            assertNotNull(user.id, "User should have an ID after insert");
            assertTrue(user.id > 0, "User ID should be positive");
            
            User loaded = PojoQuery.build(User.class).findById(c, user.id);
            assertNotNull(loaded, "User should be found");
            assertEquals("alice", loaded.username);
            assertEquals("alice@example.com", loaded.email);
            assertEquals(Boolean.TRUE, loaded.active);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testUpdate(Dialect dialect) {
        setUp(dialect);
        DB.withConnection(dataSource, c -> {
            User user = new User("bob", "bob@example.com");
            PojoQuery.insert(c, user);
            
            user.email = "bob.updated@example.com";
            user.active = false;
            PojoQuery.update(c, user);
            
            User loaded = PojoQuery.build(User.class).findById(c, user.id);
            assertEquals("bob.updated@example.com", loaded.email);
            assertEquals(Boolean.FALSE, loaded.active);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testDelete(Dialect dialect) {
        setUp(dialect);
        DB.withConnection(dataSource, c -> {
            User user = new User("charlie", "charlie@example.com");
            PojoQuery.insert(c, user);
            Long id = user.id;
            
            PojoQuery.delete(c, user);
            
            User loaded = PojoQuery.build(User.class).findById(c, id);
            assertNull(loaded, "User should be deleted");
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testSelectAll(Dialect dialect) {
        setUp(dialect);
        DB.withConnection(dataSource, c -> {
            PojoQuery.insert(c, new User("user1", "user1@example.com"));
            PojoQuery.insert(c, new User("user2", "user2@example.com"));
            PojoQuery.insert(c, new User("user3", "user3@example.com"));
            
            List<User> users = PojoQuery.build(User.class).execute(c);
            assertEquals(3, users.size());
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testWhereClause(Dialect dialect) {
        setUp(dialect);
        DB.withConnection(dataSource, c -> {
            PojoQuery.insert(c, new User("active1", "a1@example.com"));
            User inactive = new User("inactive1", "i1@example.com");
            inactive.active = false;
            PojoQuery.insert(c, inactive);
            PojoQuery.insert(c, new User("active2", "a2@example.com"));
            
            List<User> activeUsers = PojoQuery.build(User.class)
                    .addWhere("active = ?", true)
                    .execute(c);
            
            assertEquals(2, activeUsers.size());
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testLobField(Dialect dialect) {
        setUp(dialect);
        DB.withConnection(dataSource, c -> {
            // Create a large content string
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("Line ").append(i).append(": This is test content for LOB testing.\n");
            }
            String largeContent = sb.toString();
            
            Article article = new Article("Test Article", largeContent);
            PojoQuery.insert(c, article);
            
            assertNotNull(article.id, "Article should have an ID");
            
            Article loaded = PojoQuery.build(Article.class).findById(c, article.id);
            assertNotNull(loaded, "Article should be found");
            assertEquals("Test Article", loaded.title);
            assertEquals(largeContent, loaded.content);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testLobUpdate(Dialect dialect) {
        setUp(dialect);
        DB.withConnection(dataSource, c -> {
            Article article = new Article("Original Title", "Original content");
            PojoQuery.insert(c, article);
            
            String updatedContent = "Updated content that is different from the original.";
            article.content = updatedContent;
            article.title = "Updated Title";
            PojoQuery.update(c, article);
            
            Article loaded = PojoQuery.build(Article.class).findById(c, article.id);
            assertEquals("Updated Title", loaded.title);
            assertEquals(updatedContent, loaded.content);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testNullValues(Dialect dialect) {
        setUp(dialect);
        DB.withConnection(dataSource, c -> {
            User user = new User();
            user.username = "nulltest";
            // email and active are null
            PojoQuery.insert(c, user);
            
            User loaded = PojoQuery.build(User.class).findById(c, user.id);
            assertEquals("nulltest", loaded.username);
            assertNull(loaded.email, "Email should be null");
            assertNull(loaded.active, "Active should be null");
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    public void testMultipleInserts(Dialect dialect) {
        setUp(dialect);
        DB.withConnection(dataSource, c -> {
            for (int i = 0; i < 100; i++) {
                User user = new User("user" + i, "user" + i + "@example.com");
                PojoQuery.insert(c, user);
                assertEquals(Long.valueOf(i + 1), user.id, "Each user should get a unique ID");
            }
            
            List<User> users = PojoQuery.build(User.class).execute(c);
            assertEquals(100, users.size());
        });
    }
}
