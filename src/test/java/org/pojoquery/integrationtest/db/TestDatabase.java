package org.pojoquery.integrationtest.db;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DbContext;

public class TestDatabase {
	
	private static int dbCounter = 0;
	private static final HsqldbDbContext HSQLDB_CONTEXT = new HsqldbDbContext();
	
	static {
		// Set HSQLDB context as default for all PojoQuery operations
		DbContext.setDefault(HSQLDB_CONTEXT);
	}
	
	/**
	 * Ensures the DbContext is set up. Call this from @BeforeClass in tests
	 * that need the DbContext but don't call dropAndRecreate().
	 */
	public static void initDbContext() {
		// Static initializer already did the work, this just ensures class is loaded
	}
	
	public static DataSource dropAndRecreate() {
		// Create a unique in-memory HSQLDB database for each test
		String dbName = "testdb_" + (++dbCounter);
		JDBCDataSource dataSource = new JDBCDataSource();
		dataSource.setUrl("jdbc:hsqldb:mem:" + dbName);
		dataSource.setUser("SA");
		dataSource.setPassword("");
		return dataSource;
	}

	public static DbContext getDbContext() {
		return HSQLDB_CONTEXT;
	}
}