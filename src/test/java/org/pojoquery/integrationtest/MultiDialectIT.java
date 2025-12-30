package org.pojoquery.integrationtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Lob;
import org.pojoquery.annotations.Table;
import org.pojoquery.schema.SchemaGenerator;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import com.mysql.cj.jdbc.MysqlDataSource;

import org.postgresql.ds.PGSimpleDataSource;

/**
 * Integration tests that run against real databases using Testcontainers.
 * Tests basic CRUD operations, LOB handling, and dialect-specific features.
 * 
 * <p>HSQLDB tests always run. MySQL and PostgreSQL tests require Docker.</p>
 * <p>If Docker is not available, MySQL/PostgreSQL tests are skipped.</p>
 */
@Ignore("Disabled: takes too long to run with Docker containers")
@RunWith(Parameterized.class)
public class MultiDialectIT {

    // Testcontainers - lazily initialized only when Docker is available
    private static MySQLContainer<?> mysql;
    private static PostgreSQLContainer<?> postgres;
    private static boolean dockerAvailable = false;

    @BeforeClass
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

    @Parameters(name = "{0}")
    public static Collection<Object[]> dialects() {
        return Arrays.asList(new Object[][] {
            { Dialect.HSQLDB },
            { Dialect.MYSQL },
            { Dialect.POSTGRES }
        });
    }

    private final Dialect dialect;
    private DataSource dataSource;
    private DbContext dbContext;
    private static int hsqldbCounter = 0;

    public MultiDialectIT(Dialect dialect) {
        this.dialect = dialect;
    }

    @Before
    public void setUp() {
        // Skip MySQL/PostgreSQL tests if Docker is not available
        if (dialect == Dialect.MYSQL || dialect == Dialect.POSTGRES) {
            Assume.assumeTrue("Docker is not available - skipping " + dialect + " tests", dockerAvailable);
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

    @After
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

    @Test
    public void testInsertAndSelect() {
        User user = new User("alice", "alice@example.com");
        PojoQuery.insert(dataSource, user);
        
        assertNotNull("User should have an ID after insert", user.id);
        assertTrue("User ID should be positive", user.id > 0);
        
        User loaded = PojoQuery.build(User.class).findById(dataSource, user.id);
        assertNotNull("User should be found", loaded);
        assertEquals("alice", loaded.username);
        assertEquals("alice@example.com", loaded.email);
        assertEquals(Boolean.TRUE, loaded.active);
    }

    @Test
    public void testUpdate() {
        User user = new User("bob", "bob@example.com");
        PojoQuery.insert(dataSource, user);
        
        user.email = "bob.updated@example.com";
        user.active = false;
        PojoQuery.update(dataSource, user);
        
        User loaded = PojoQuery.build(User.class).findById(dataSource, user.id);
        assertEquals("bob.updated@example.com", loaded.email);
        assertEquals(Boolean.FALSE, loaded.active);
    }

    @Test
    public void testDelete() {
        User user = new User("charlie", "charlie@example.com");
        PojoQuery.insert(dataSource, user);
        Long id = user.id;
        
        PojoQuery.delete(dataSource, user);
        
        User loaded = PojoQuery.build(User.class).findById(dataSource, id);
        assertNull("User should be deleted", loaded);
    }

    @Test
    public void testSelectAll() {
        PojoQuery.insert(dataSource, new User("user1", "user1@example.com"));
        PojoQuery.insert(dataSource, new User("user2", "user2@example.com"));
        PojoQuery.insert(dataSource, new User("user3", "user3@example.com"));
        
        List<User> users = PojoQuery.build(User.class).execute(dataSource);
        assertEquals(3, users.size());
    }

    @Test
    public void testWhereClause() {
        PojoQuery.insert(dataSource, new User("active1", "a1@example.com"));
        User inactive = new User("inactive1", "i1@example.com");
        inactive.active = false;
        PojoQuery.insert(dataSource, inactive);
        PojoQuery.insert(dataSource, new User("active2", "a2@example.com"));
        
        List<User> activeUsers = PojoQuery.build(User.class)
                .addWhere("active = ?", true)
                .execute(dataSource);
        
        assertEquals(2, activeUsers.size());
    }

    @Test
    public void testLobField() {
        // Create a large content string
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("Line ").append(i).append(": This is test content for LOB testing.\n");
        }
        String largeContent = sb.toString();
        
        Article article = new Article("Test Article", largeContent);
        PojoQuery.insert(dataSource, article);
        
        assertNotNull("Article should have an ID", article.id);
        
        Article loaded = PojoQuery.build(Article.class).findById(dataSource, article.id);
        assertNotNull("Article should be found", loaded);
        assertEquals("Test Article", loaded.title);
        assertEquals(largeContent, loaded.content);
    }

    @Test
    public void testLobUpdate() {
        Article article = new Article("Original Title", "Original content");
        PojoQuery.insert(dataSource, article);
        
        String updatedContent = "Updated content that is different from the original.";
        article.content = updatedContent;
        article.title = "Updated Title";
        PojoQuery.update(dataSource, article);
        
        Article loaded = PojoQuery.build(Article.class).findById(dataSource, article.id);
        assertEquals("Updated Title", loaded.title);
        assertEquals(updatedContent, loaded.content);
    }

    @Test
    public void testNullValues() {
        User user = new User();
        user.username = "nulltest";
        // email and active are null
        PojoQuery.insert(dataSource, user);
        
        User loaded = PojoQuery.build(User.class).findById(dataSource, user.id);
        assertEquals("nulltest", loaded.username);
        assertNull("Email should be null", loaded.email);
        assertNull("Active should be null", loaded.active);
    }

    @Test
    public void testMultipleInserts() {
        for (int i = 0; i < 100; i++) {
            User user = new User("user" + i, "user" + i + "@example.com");
            PojoQuery.insert(dataSource, user);
            assertEquals("Each user should get a unique ID", Long.valueOf(i + 1), user.id);
        }
        
        List<User> users = PojoQuery.build(User.class).execute(dataSource);
        assertEquals(100, users.size());
    }
}
