package system.db;

import static system.util.Strings.implode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;

public class DB {
	private enum QueryType {
		DDL, SELECT, UPDATE, INSERT
	}

	public interface ResultSetProcessor<T> {
		T process(ResultSet resultSet);
	}
	
	public static List<Map<String, Object>> queryRows(DataSource db, String sql, Object... params) {
		return execute(db, QueryType.SELECT, sql, Arrays.asList(params), new ResultSetProcessor<List<Map<String, Object>>>() {
			@Override
			public List<Map<String, Object>> process(ResultSet rs) {
				List<Map<String, Object>> result = new ArrayList<Map<String,Object>>();
				try {
					List<String> fieldNames = extractResultSetFieldNames(rs);
					while (rs.next()) {
						Map<String, Object> row = new HashMap<String, Object>(fieldNames.size());
						for (String fieldName : fieldNames) {
							row.put(fieldName, rs.getObject(fieldName));
						}
						result.add(row);
					}
					return result;
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});
	}
	
	public static Long insertOrUpdate(DataSource db, String tableName, Map<String, ? extends Object> values) {
		List<String> qmarks = new ArrayList<String>();
		List<String> quotedFields = new ArrayList<String>();
		List<Object> params = new ArrayList<Object>();
		List<String> updateList = new ArrayList<String>();
		
		for (String f : values.keySet()) {
			qmarks.add("?");
			String quotedField = "`" + f + "`";
			quotedFields.add(quotedField);
			params.add(values.get(f));
			updateList.add(quotedField + "=?");
		}
		params.addAll(new ArrayList<Object>(params));
		String sql = "INSERT INTO `" + tableName + "` (" + implode(",", quotedFields) + ")" + " VALUES (" + implode(",", qmarks) + ")" + " ON DUPLICATE KEY UPDATE " + implode(",", updateList);
		return execute(db, QueryType.INSERT, sql, params, null);
	}
	
	public static void executeDDL(DataSource db, String ddl) {
		execute(db, QueryType.DDL, ddl, null, null);
	}

	@SuppressWarnings("unchecked")
	private static <T> T execute(DataSource db, QueryType type, String sql, List<Object> params, ResultSetProcessor<T> processor) {
		Connection connection = null;
		try {
			connection = db.getConnection();
			PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			if (params != null) {
				int index = 1;
				for (Object val : params) {
					stmt.setObject(index++, val);
				}
			}
			boolean success = false;
			try {
				switch (type) {
				case DDL:
					stmt.executeUpdate();
					success = true;
					break;
				case SELECT:
					ResultSet resultSet = stmt.executeQuery();
					success = true;
					if (processor != null) {
						return processor.process(resultSet);
					}
					return (T)resultSet;
				case INSERT:
					stmt.executeUpdate();
					success = true;
					ResultSet keysResult = stmt.getGeneratedKeys();
					if (keysResult != null && keysResult.next()) {
						return (T)keysResult.getObject(1);
					} else {
						return null;
					}
				case UPDATE:
					Integer affectedRows = stmt.executeUpdate();
					success = true;
					return (T)affectedRows;
				}
			} finally {
				stmt.close();
				if (!success) {
					System.err.println("QUERY FAILED: " + sql);
				}
			}
			return null;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			try {
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	private static List<String> extractResultSetFieldNames(ResultSet result) throws SQLException {
		ResultSetMetaData metaData = result.getMetaData();
		List<String> fieldNames = new ArrayList<String>();
		for (int i = 0; i < metaData.getColumnCount(); i++) {
			fieldNames.add(metaData.getColumnLabel(i + 1));
		}
		return fieldNames;
	}
	
	public static DataSource getDataSource(String jdbcUrl, String user, String pass) {
		try {
			MysqlConnectionPoolDataSource dataSource = new MysqlConnectionPoolDataSource();
			dataSource.setUrl(jdbcUrl);
			dataSource.setUser(user);
			dataSource.setPassword(pass);
			dataSource.setCharacterEncoding("utf8");
			dataSource.setUseUnicode(true);
			return dataSource;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
