package org.pojoquery;

import static org.pojoquery.util.Strings.implode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.sql.DataSource;

/**
 * The `DB` interface provides a set of utility methods for interacting with a database.
 * It includes methods for querying, updating, inserting, and managing transactions.
 * The interface also defines nested classes and interfaces for handling database operations,
 * such as `Transaction`, `QueryType`, `ResultSetProcessor`, and custom exception handling.
 *
 * <p>Key features include:</p>
 * <ul>
 *   <li>Querying rows and columns from a database using SQL expressions.</li>
 *   <li>Streaming rows with a callback mechanism for large result sets.</li>
 *   <li>Performing updates, inserts, and insert-or-update operations.</li>
 *   <li>Executing DDL statements.</li>
 *   <li>Running transactions with automatic commit and rollback support.</li>
 * </ul>
 *
 * <p>Nested classes and interfaces:</p>
 * <ul>
 *   <li>`DatabaseException`: A custom runtime exception for handling SQL errors.</li>
 *   <li>`Transaction<T>`: Represents a database transaction with a generic return type.</li>
 *   <li>`QueryType`: An enumeration of query types (DDL, SELECT, UPDATE, INSERT).</li>
 *   <li>`ResultSetProcessor<T>`: A functional interface for processing result sets.</li>
 *   <li>`RowProcessor` and `ColumnProcessor`: Implementations of `ResultSetProcessor` for processing rows and columns.</li>
 *   <li>`Columns`: A utility class for accessing column data from query results.</li>
 * </ul>
 *
 * <p>Note: This interface assumes the use of `DataSource` and `Connection` for database connectivity,
 * and `SqlExpression` for encapsulating SQL queries and their parameters.</p>
 */
public interface DB {
	public static final class DatabaseException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public DatabaseException(SQLException e) {
			super(e);
		}

	}

	public interface Transaction<T> {
		public T run(Connection connection);
	}

	public enum QueryType {
		DDL, SELECT, UPDATE, INSERT
	}

	public interface ResultSetProcessor<T> {
		T process(ResultSet resultSet);
	}

	/**
	 * Queries rows from the database using a connection and a SQL expression.
	 * 
	 * @param connection the database connection
	 * @param queryStatement the SQL expression to execute
	 * @return a list of rows as maps of column names to values
	 */
	public static List<Map<String, Object>> queryRows(Connection connection, SqlExpression queryStatement) {
		return execute(connection, QueryType.SELECT, queryStatement.getSql(), queryStatement.getParameters(), new RowProcessor() );
	}

	/**
	 * Queries rows from the database using a data source and a SQL expression.
	 * 
	 * @param db the data source
	 * @param queryStatement the SQL expression to execute
	 * @return a list of rows as maps of column names to values
	 */
	public static List<Map<String, Object>> queryRows(DataSource db, SqlExpression queryStatement) {
		return execute(db, QueryType.SELECT, queryStatement.getSql(), queryStatement.getParameters(), new RowProcessor() );
	}
	
	/**
     * Queries rows from the database using a connection, SQL, and parameters.
     * 
     * @param connection the database connection
     * @param sql the SQL query to execute
     * @param params the parameters for the query
     * @return a list of rows as maps of column names to values
     */
    public static List<Map<String, Object>> queryRows(Connection connection, String sql, Object... params) {
        return execute(connection, QueryType.SELECT, sql, Arrays.asList(params), new RowProcessor() );
    }

    /**
     * Queries rows from the database using a data source, SQL, and parameters.
     * 
     * @param db the data source
     * @param sql the SQL query to execute
     * @param params the parameters for the query
     * @return a list of rows as maps of column names to values
     */
    public static List<Map<String, Object>> queryRows(DataSource db, String sql, Object... params) {
        return execute(db, QueryType.SELECT, sql, Arrays.asList(params), new RowProcessor() );
    }
	
	/**
     * Streams rows from the database using a connection and a SQL expression.
     * 
     * @param conn the database connection
     * @param queryStatement the SQL expression to execute
     * @param rowCallback the callback to process each row
     */
    public static void queryRowsStreaming(Connection conn, SqlExpression queryStatement, Consumer<Map<String,Object>> rowCallback) {
        try (PreparedStatement stmt = conn.prepareStatement(queryStatement.getSql());) {
			applyParameters(queryStatement.getParameters(), stmt);
			stmt.setFetchSize(Integer.MIN_VALUE);
			try (ResultSet rs = stmt.executeQuery()) {
				List<String> fieldNames = extractResultSetFieldNames(rs);
				while (rs.next()) {
					Map<String, Object> row = new HashMap<String, Object>(fieldNames.size());
					for (String fieldName : fieldNames) {
						row.put(fieldName, rs.getObject(fieldName));
					}
					rowCallback.accept(row);
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException(e);
			}
    }

    /**
     * Streams rows from the database using a data source and a SQL expression.
     * 
     * @param db the data source
     * @param queryStatement the SQL expression to execute
     * @param rowCallback the callback to process each row
     */
    public static void queryRowsStreaming(DataSource db, SqlExpression queryStatement, Consumer<Map<String,Object>> rowCallback) {
        Connection connection = null;
		try {
			connection = db.getConnection();
			queryRowsStreaming(connection, queryStatement, rowCallback);
		} catch (SQLException e) {
			throw new DatabaseException(e);
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
	
	/**
     * Queries columns from the database using a connection, SQL, and parameters.
     * 
     * @param conn the database connection
     * @param sql the SQL query to execute
     * @param params the parameters for the query
     * @return the queried columns
     */
    public static Columns queryColumns(Connection conn, String sql, Object... params) {
        return new Columns(execute(conn, QueryType.SELECT, sql, Arrays.asList(params), new ColumnProcessor()));
    }

    /**
     * Queries columns from the database using a data source, SQL, and parameters.
     * 
     * @param db the data source
     * @param sql the SQL query to execute
     * @param params the parameters for the query
     * @return the queried columns
     */
    public static Columns queryColumns(DataSource db, String sql, Object... params) {
        return new Columns(execute(db, QueryType.SELECT, sql, Arrays.asList(params), new ColumnProcessor()));
    }
	
	public static class ColumnProcessor implements ResultSetProcessor<List<List<Object>>> {
		public List<List<Object>> process(ResultSet rs) {
			List<List<Object>> result = new ArrayList<>();
			try {
				int columnCount = rs.getMetaData().getColumnCount();
				for(int i = 0; i < columnCount; i++) {
					result.add(new ArrayList<Object>());
				}
				while (rs.next()) {
					for(int i = 0; i < columnCount; i++) {
						result.get(i).add(rs.getObject(i + 1));
					}
				}
				return result;
			} catch (SQLException e) {
				throw new DatabaseException(e);
			}
		}
	}
	
	public static class RowProcessor implements ResultSetProcessor<List<Map<String, Object>>> {
		public List<Map<String, Object>> process(ResultSet rs) {
			List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
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
			} catch (SQLException e) {
				throw new DatabaseException(e);
			}
		}
	}

	/**
     * Updates records in the database using a schema name.
     * 
     * @param db the data source
     * @param schemaName the schema name
     * @param tableName the name of the table
     * @param values the values to update
     * @param ids the identifiers of the records to update
     * @return the number of rows affected
     */
    public static int update(DataSource db, String schemaName, String tableName, Map<String, Object> values, Map<String, Object> ids) {
        SqlExpression updateSql = buildUpdate(DbContext.getDefault(), schemaName, tableName, values, ids);
        return (Integer)execute(db, QueryType.UPDATE, updateSql.getSql(), updateSql.getParameters(), null);
    }

    /**
     * Updates records in the database without a schema name.
     * 
     * @param db the data source
     * @param tableName the name of the table
     * @param values the values to update
     * @param ids the identifiers of the records to update
     * @return the number of rows affected
     */
    public static int update(DataSource db, String tableName, Map<String, Object> values, Map<String, Object> ids) {
        SqlExpression updateSql = buildUpdate(DbContext.getDefault(), null, tableName, values, ids);
        return (Integer)execute(db, QueryType.UPDATE, updateSql.getSql(), updateSql.getParameters(), null);
    }
	
	/**
     * Updates records in the database using a connection without a schema name.
     * 
     * @param connection the database connection
     * @param tableName the name of the table
     * @param values the values to update
     * @param ids the identifiers of the records to update
     * @return the number of rows affected
     */
    public static int update(Connection connection, String tableName, Map<String, Object> values, Map<String, Object> ids) {
        SqlExpression updateSql = buildUpdate(DbContext.getDefault(), null, tableName, values, ids);
        return (Integer)execute(connection, QueryType.UPDATE, updateSql.getSql(), updateSql.getParameters(), null);
    }
	
	/**
     * Updates records in the database using a connection and schema name.
     * 
     * @param connection the database connection
     * @param schemaName the schema name
     * @param tableName the name of the table
     * @param values the values to update
     * @param ids the identifiers of the records to update
     * @return the number of rows affected
     */
    public static int update(Connection connection, String schemaName, String tableName, Map<String, Object> values, Map<String, Object> ids) {
        SqlExpression updateSql = buildUpdate(DbContext.getDefault(), schemaName, tableName, values, ids);
        return (Integer)execute(connection, QueryType.UPDATE, updateSql.getSql(), updateSql.getParameters(), null);
    }

	/**
     * Updates records in the database using a SQL expression.
     * 
     * @param db the data source
     * @param update the SQL expression for the update
     * @return the number of rows affected
     */
    public static int update(DataSource db, SqlExpression update) {
        return (Integer)execute(db, QueryType.UPDATE, update.getSql(), update.getParameters(), null);
    }

	/**
     * Updates records in the database using a connection and a SQL expression.
     * 
     * @param conn the database connection
     * @param update the SQL expression for the update
     * @return the number of rows affected
     */
    public static int update(Connection conn, SqlExpression update) {
        return (Integer)execute(conn, QueryType.UPDATE, update.getSql(), update.getParameters(), null);
    }

	/**
	 * Inserts a record into the database.
	 * 
	 * @param <PK> the type of the primary key
	 * @param db the data source
	 * @param tableName the name of the table
	 * @param values the values to insert
	 * @return the generated primary key
	 */
	public static <PK> PK insert(DataSource db, String schemaName, String tableName, Map<String, ? extends Object> values) {
		SqlExpression insertSql = buildInsertOrUpdate(DbContext.getDefault(), schemaName, tableName, values, false);
		return execute(db, QueryType.INSERT, insertSql.getSql(), insertSql.getParameters(), null);
	}

	/**
	 * Inserts a new record into the specified table in the database.
	 *
	 * @param <PK>       The type of the primary key that will be returned.
	 * @param db         The {@link DataSource} representing the database connection.
	 * @param tableName  The name of the table where the record will be inserted.
	 * @param values     A map containing the column names as keys and their corresponding values.
	 * @return           The primary key of the newly inserted record.
	 */
	public static <PK> PK insert(DataSource db, String tableName, Map<String, ? extends Object> values) {
		SqlExpression insertSql = buildInsertOrUpdate(DbContext.getDefault(), null, tableName, values, false);
		return execute(db, QueryType.INSERT, insertSql.getSql(), insertSql.getParameters(), null);
	}
	
	public static <PK> PK insert(Connection connection, String schemaName, String tableName, Map<String, ? extends Object> values) {
		SqlExpression insertSql = buildInsertOrUpdate(DbContext.getDefault(), schemaName, tableName, values, false);
		return execute(connection, QueryType.INSERT, insertSql.getSql(), insertSql.getParameters(), null);
	}
	
	public static <PK> PK insert(Connection connection, String tableName, Map<String, ? extends Object> values) {
		SqlExpression insertSql = buildInsertOrUpdate(DbContext.getDefault(), null, tableName, values, false);
		return execute(connection, QueryType.INSERT, insertSql.getSql(), insertSql.getParameters(), null);
	}
	
	public static <PK> PK insertOrUpdate(DataSource db, String tableName, Map<String, ? extends Object> values) {
		SqlExpression insertSql = buildInsertOrUpdate(DbContext.getDefault(), null, tableName, values, true);
		return execute(db, QueryType.INSERT, insertSql.getSql(), insertSql.getParameters(), null);
	}
	
	/**
     * Inserts or updates a record in the database using a schema name.
     * 
     * @param <PK> the type of the primary key
     * @param db the data source
     * @param schemaName the schema name
     * @param tableName the name of the table
     * @param values the values to insert or update
     * @return the generated primary key
     */
    public static <PK> PK insertOrUpdate(DataSource db, String schemaName, String tableName, Map<String, ? extends Object> values) {
        SqlExpression insertSql = buildInsertOrUpdate(DbContext.getDefault(), schemaName, tableName, values, true);
        return execute(db, QueryType.INSERT, insertSql.getSql(), insertSql.getParameters(), null);
    }

	/**
     * Inserts or updates a record in the database using a connection and schema name.
     * 
     * @param <PK> the type of the primary key
     * @param connection the database connection
     * @param schemaName the schema name
     * @param tableName the name of the table
     * @param values the values to insert or update
     * @return the generated primary key
     */
    public static <PK> PK insertOrUpdate(Connection connection, String schemaName, String tableName, Map<String, ? extends Object> values) {
        SqlExpression insertSql = buildInsertOrUpdate(DbContext.getDefault(), schemaName, tableName, values, true);
        return execute(connection, QueryType.INSERT, insertSql.getSql(), insertSql.getParameters(), null);
    }

	/**
     * Inserts or updates a record in the database using a connection without a schema name.
     * 
     * @param <PK> the type of the primary key
     * @param connection the database connection
     * @param tableName the name of the table
     * @param values the values to insert or update
     * @return the generated primary key
     */
    public static <PK> PK insertOrUpdate(Connection connection, String tableName, Map<String, ? extends Object> values) {
        SqlExpression insertSql = buildInsertOrUpdate(DbContext.getDefault(), null, tableName, values, true);
        return execute(connection, QueryType.INSERT, insertSql.getSql(), insertSql.getParameters(), null);
    }

	/**
     * Builds an SQL expression for inserting or updating a record.
     * 
     * @param context the database context
     * @param schemaName the schema name (optional)
     * @param tableName the table name
     * @param values the values to insert or update
     * @param addOnDuplicateKeyClause whether to add an ON DUPLICATE KEY clause
     * @return the SQL expression
     */
	private static SqlExpression buildInsertOrUpdate(DbContext context, String schemaName, String tableName, Map<String, ? extends Object> values, boolean addOnDuplicateKeyClause) {
		List<String> qmarks = new ArrayList<String>();
		List<String> quotedFields = new ArrayList<String>();
		List<Object> params = new ArrayList<Object>();
		List<String> updateList = new ArrayList<String>();

		for (String f : values.keySet()) {
			final String quotedField = context.quoteObjectNames(f);

			qmarks.add("?");
			quotedFields.add(quotedField);
			params.add(values.get(f));
			updateList.add(quotedField + "=?");
		}
		if (addOnDuplicateKeyClause) {
			params.addAll(new ArrayList<Object>(params));
		}
		String sql = "INSERT INTO " + prefixAndQuoteTableName(context, schemaName, tableName) + " (" + implode(",", quotedFields) + ")" + " VALUES (" + implode(",", qmarks) + ")";
		
		if (addOnDuplicateKeyClause) {
			sql += " ON DUPLICATE KEY UPDATE " + implode(",", updateList);
		}
		
		return new SqlExpression(sql, params);
	}

	/**
     * Quotes and prefixes a table name with a schema name if provided.
     * 
     * @param context the database context
     * @param schemaName the schema name (optional)
     * @param tableName the table name
     * @return the fully qualified table name
     */
	public static String prefixAndQuoteTableName(DbContext context, String schemaName, String tableName) {
		if (schemaName == null) {
			return context.quoteObjectNames(tableName);
		}
		return context.quoteObjectNames(schemaName, tableName);
	}

	/**
	 * Builds and executes an SQL update statement.
	 * 
	 * @param context the database context
	 * @param schemaName the schema name
	 * @param tableName the table name
	 * @param values the values to update
	 * @param ids the identifiers of the records to update
	 * @return the SQL expression for the update
	 */
	private static SqlExpression buildUpdate(DbContext context, String schemaName, String tableName, Map<String, ? extends Object> values, Map<String, ? extends Object> ids) {
		List<String> qmarks = new ArrayList<String>();
		List<String> assignments = new ArrayList<String>();
		List<Object> params = new ArrayList<Object>();
		List<String> wheres = new ArrayList<String>();

		for (String f : values.keySet()) {
			qmarks.add("?");
			assignments.add(String.format("%s=?", context.quoteObjectNames(f)));
			params.add(values.get(f));
		}
		
		for (String idField : ids.keySet()) {
			qmarks.add("?");
			wheres.add(String.format("%s=?", context.quoteObjectNames(idField)));
			params.add(ids.get(idField));
		}
		
		String sql = "UPDATE " + prefixAndQuoteTableName(context, schemaName, tableName) + " SET " + implode(", ", assignments) + " WHERE " + implode(" AND ", wheres);
		return new SqlExpression(sql, params);
	}

	/**
	 * Executes a DDL statement on the database.
	 * 
	 * @param db the data source
	 * @param ddl the DDL statement to execute
	 */
	public static void executeDDL(DataSource db, String ddl) {
		execute(db, QueryType.DDL, ddl, null, null);
	}

	/**
	 * Executes a DDL statement on the database using a connection.
	 * 
	 * @param connection the database connection
	 * @param ddl the DDL statement to execute
	 */
	public static void executeDDL(Connection connection, String ddl) {
		execute(connection, QueryType.DDL, ddl, null, null);
	}

	/**
     * Executes a query and processes the result set.
     * 
     * @param <T> the type of the result
     * @param db the data source
     * @param type the type of query (e.g., SELECT, INSERT)
     * @param sql the SQL query to execute
     * @param params the parameters for the query
     * @param processor the processor to handle the result set
     * @return the processed result
     */
	public static <T> T execute(DataSource db, QueryType type, String sql, Iterable<Object> params, ResultSetProcessor<T> processor) {
		Connection connection = null;
		try {
			connection = db.getConnection();
			return execute(connection, type, sql, params, processor);
		} catch (SQLException e) {
			throw new DatabaseException(e);
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

	/**
     * Executes a query and processes the result set using a connection.
     * 
     * @param <T> the type of the result
     * @param connection the database connection
     * @param type the type of query (e.g., SELECT, INSERT)
     * @param sql the SQL query to execute
     * @param params the parameters for the query
     * @param processor the processor to handle the result set
     * @return the processed result
     */
	@SuppressWarnings("unchecked")
	public static <T> T execute(Connection connection, QueryType type, String sql, Iterable<Object> params, ResultSetProcessor<T> processor) {
		try (PreparedStatement stmt = connection.prepareStatement(
				sql,
				type == QueryType.INSERT
					? Statement.RETURN_GENERATED_KEYS
					: Statement.NO_GENERATED_KEYS
			)) {
			if (params != null) {
				applyParameters(params, stmt);
			}
			boolean success = false;
			try {
				switch (type) {
				case DDL:
					stmt.executeUpdate();
					success = true;
					break;
				case SELECT:
					try (ResultSet resultSet = stmt.executeQuery()) {
						success = true;
						return processor.process(resultSet);
					}
				case INSERT:
					stmt.executeUpdate();
					success = true;
					try (ResultSet keysResult = stmt.getGeneratedKeys()) {
						if (keysResult != null && keysResult.next()) {
							return (T) keysResult.getObject(1);
						} else {
							return null;
						}
					}
				case UPDATE:
					Integer affectedRows = stmt.executeUpdate();
					success = true;
					return (T) affectedRows;
				}
			} finally {
				if (!success) {
					System.err.println("QUERY FAILED: " + sql);
				}
			}
			return null;
		} catch (SQLException e) {
			System.err.println(e.getMessage());
			throw new DatabaseException(e);
		}
	}

	/**
	 * Applies parameters to a prepared statement.
	 * 
	 * @param params the parameters to apply
	 * @param stmt the prepared statement
	 * @throws SQLException if an SQL error occurs
	 */
	private static void applyParameters(Iterable<Object> params, PreparedStatement stmt) throws SQLException {
		int index = 1;
		for (Object val : params) {
			if (val != null && val instanceof LocalDate) {
				LocalDate localDate = (LocalDate)val;
				String dateAsString = localDate.getYear() + "-" + localDate.getMonthValue() + "-" + localDate.getDayOfMonth();
				stmt.setObject(index++, dateAsString);
			} else if (val != null && val.getClass().isEnum()) {
				stmt.setObject(index++, ((Enum<?>)val).name());
			} else {
				stmt.setObject(index++, val);
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

	/**
	 * Runs a transaction on the database using a connection.
	 * 
	 * @param <T> the type of the result
	 * @param connection the database connection
	 * @param transaction the transaction to execute
	 * @return the result of the transaction
	 */
	public static <T> T runInTransaction(Connection connection, Transaction<T> transaction) {
		boolean success = false;
		try {
			connection.setAutoCommit(false);
			T result = transaction.run(connection);
			connection.commit();
			success = true;
			return result;
		} catch (SQLException e) {
			throw new DatabaseException(e);
		} finally {
			if (!success) {
				try {
					connection.rollback();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Runs a transaction on the database using a data source.
	 * 
	 * @param <T> the type of the result
	 * @param dataSource the data source
	 * @param transaction the transaction to execute
	 * @return the result of the transaction
	 */
	public static <T> T runInTransaction(DataSource dataSource, Transaction<T> transaction) {
		Connection connection = null;
		try {
			connection = dataSource.getConnection();
			return runInTransaction(connection, transaction);
		} catch (SQLException e) {
			throw new DatabaseException(e);
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

	public static class Columns {

		private List<List<Object>> data;

		public Columns(List<List<Object>> data) {
			this.data = data;
		}
		
		@SuppressWarnings("unchecked")
		public <T> List<T> get(int index) {
			return (List<T>) data.get(index);
		}

	}

}
