package nl.pojoquery.integrationtest.db;

import javax.sql.DataSource;

import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;

public class MysqlDatabases {

	public static DataSource getMysqlDataSource(String host, String schema, String username, String password) {
		String jdbcUrl = "jdbc:mysql://" + host + (schema != null ? "/" + schema : "");
		return getDataSource(jdbcUrl, username, password);
	}
	
	static DataSource getDataSource(String jdbcUrl, String user, String pass) {
		try {
			
			MysqlConnectionPoolDataSource dataSource = new MysqlConnectionPoolDataSource();
			dataSource.setUrl(jdbcUrl);
			dataSource.setUser(user);
			dataSource.setPassword(pass);
			dataSource.setCharacterEncoding("utf8");
//			dataSource.setUseUnicode(true);
//			dataSource.setSessionVariables("storage_engine=InnoDB");
			return dataSource;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
