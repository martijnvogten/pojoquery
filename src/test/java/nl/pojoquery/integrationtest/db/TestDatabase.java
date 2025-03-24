package nl.pojoquery.integrationtest.db;

import javax.sql.DataSource;

import nl.pojoquery.DB;

public class TestDatabase {
	
	public static DataSource dropAndRecreate() {
		String schema = System.getProperty("pojoquery.integrationtest.schema", "pojoquery_integrationtest");
		String host = System.getProperty("pojoquery.integrationtest.host", "localhost");
		String username = System.getProperty("pojoquery.integrationtest.username", "root");
		String password = System.getProperty("pojoquery.integrationtest.password", "");
		
		DataSource db = getDataSource(host, null, username, password);

		DB.executeDDL(db, "DROP DATABASE IF EXISTS " + schema);
		DB.executeDDL(db, "CREATE DATABASE " + schema + " DEFAULT CHARSET utf8");

		return getDataSource(host, schema, username, password);
	}

	public static DataSource getDataSource(String host, String schema, String username, String password) {
		return MysqlDatabases.getMysqlDataSource(host, schema, username, password);
	}
}
