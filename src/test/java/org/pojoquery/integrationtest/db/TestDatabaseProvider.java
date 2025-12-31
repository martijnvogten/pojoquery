package org.pojoquery.integrationtest.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DbContext;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.dialects.MysqlDbContext;
import org.pojoquery.dialects.PostgresDbContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Provides test database connections based on the 'test.database' system property.
 * 
 * <p>Supported values:</p>
 * <ul>
 *   <li><code>hsqldb</code> (default) - In-memory HSQLDB database</li>
 *   <li><code>mysql</code> - MySQL via Testcontainers</li>
 *   <li><code>postgres</code> - PostgreSQL via Testcontainers</li>
 * </ul>
 * 
 * <p>Usage in Maven:</p>
 * <pre>
 * mvn failsafe:integration-test -Dtest.database=postgres
 * </pre>
 */
public class TestDatabaseProvider {
    
    private static final String DATABASE_PROPERTY = "test.database";
    
    private static MySQLContainer<?> mysqlContainer;
    private static PostgreSQLContainer<?> postgresContainer;
    private static int dbCounter = 0;
    
    private static DbContext currentContext;
    private static DataSource currentDataSource;
    
    static {
		System.setProperty("api.version", "1.44");
        initializeDatabase();
    }
    
    private static void initializeDatabase() {
        String database = System.getProperty(DATABASE_PROPERTY, "hsqldb").toLowerCase();
        
        switch (database) {
            case "mysql":
                initMySQL();
                break;
            case "postgres":
            case "postgresql":
                initPostgres();
                break;
            case "hsqldb":
            default:
                initHsqldb();
                break;
        }
        
        DbContext.setDefault(currentContext);
    }
    
    private static void initHsqldb() {
        currentContext = DbContext.forDialect(Dialect.HSQLDB);
        System.out.println("[TestDatabaseProvider] Using HSQLDB (in-memory)");
    }
    
    private static void initMySQL() {
        System.out.println("[TestDatabaseProvider] Starting MySQL container...");
        mysqlContainer = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
        mysqlContainer.start();
        
        currentContext = new MysqlDbContext();
        
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(mysqlContainer.getJdbcUrl());
        ds.setUser(mysqlContainer.getUsername());
        ds.setPassword(mysqlContainer.getPassword());
        currentDataSource = ds;
        
        System.out.println("[TestDatabaseProvider] MySQL container started at: " + mysqlContainer.getJdbcUrl());
    }
    
    private static void initPostgres() {
        System.out.println("[TestDatabaseProvider] Starting PostgreSQL container...");
        postgresContainer = new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
        postgresContainer.start();
        
        currentContext = new PostgresDbContext();
        
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgresContainer.getJdbcUrl());
        ds.setUser(postgresContainer.getUsername());
        ds.setPassword(postgresContainer.getPassword());
        currentDataSource = ds;
        
        System.out.println("[TestDatabaseProvider] PostgreSQL container started at: " + postgresContainer.getJdbcUrl());
    }
    
    /**
     * Gets a fresh DataSource for testing. For HSQLDB, creates a new in-memory database.
     * For MySQL/PostgreSQL, drops all tables and returns the shared container connection.
     */
    public static DataSource getDataSource() {
        String database = System.getProperty(DATABASE_PROPERTY, "hsqldb").toLowerCase();
        
        if ("hsqldb".equals(database)) {
            // Create a unique in-memory HSQLDB database for each test
            String dbName = "testdb_" + (++dbCounter);
            JDBCDataSource dataSource = new JDBCDataSource();
            dataSource.setUrl("jdbc:hsqldb:mem:" + dbName);
            dataSource.setUser("SA");
            dataSource.setPassword("");
            return dataSource;
        }
        
        // For containerized databases, drop all tables first
        dropAllTables(currentDataSource, database);
        
        return currentDataSource;
    }
    
    /**
     * Drops all tables in the database.
     */
    private static void dropAllTables(DataSource ds, String database) {
        try (Connection conn = ds.getConnection()) {
            List<String> tables = getAllTables(conn, database);
            
            if (tables.isEmpty()) {
                return;
            }
            
            try (Statement stmt = conn.createStatement()) {
                // Disable foreign key checks for the drop operation
                if ("mysql".equals(database)) {
                    stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
                } else if ("postgres".equals(database) || "postgresql".equals(database)) {
                    // PostgreSQL: drop tables with CASCADE
                }
                
                for (String table : tables) {
                    String dropSql;
                    if ("postgres".equals(database) || "postgresql".equals(database)) {
                        dropSql = "DROP TABLE IF EXISTS \"" + table + "\" CASCADE";
                    } else {
                        dropSql = "DROP TABLE IF EXISTS `" + table + "`";
                    }
                    stmt.execute(dropSql);
                }
                
                // Re-enable foreign key checks
                if ("mysql".equals(database)) {
                    stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to drop tables", e);
        }
    }
    
    /**
     * Gets all table names in the current database.
     */
    private static List<String> getAllTables(Connection conn, String database) throws SQLException {
        List<String> tables = new ArrayList<>();
        
        String query;
        if ("mysql".equals(database)) {
            query = "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()";
        } else {
            // PostgreSQL
            query = "SELECT tablename FROM pg_tables WHERE schemaname = 'public'";
        }
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        
        return tables;
    }
    
    /**
     * Gets the DbContext for the current test database.
     */
    public static DbContext getDbContext() {
        return currentContext;
    }
    
    /**
     * Ensures the DbContext is set up. Call this from @BeforeClass in tests.
     */
    public static void initDbContext() {
        // Static initializer already did the work, this just ensures class is loaded
    }
    
    /**
     * Gets the name of the current database being used.
     */
    public static String getDatabaseName() {
        return System.getProperty(DATABASE_PROPERTY, "hsqldb");
    }
    
    /**
     * Checks if containers are available (Docker is running).
     */
    public static boolean isContainerDatabaseAvailable() {
        String database = System.getProperty(DATABASE_PROPERTY, "hsqldb").toLowerCase();
        if ("hsqldb".equals(database)) {
            return true;
        }
        
        try {
            if ("mysql".equals(database)) {
                return mysqlContainer != null && mysqlContainer.isRunning();
            } else if ("postgres".equals(database) || "postgresql".equals(database)) {
                return postgresContainer != null && postgresContainer.isRunning();
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
