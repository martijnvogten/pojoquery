package org.pojoquery;

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
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.pojoquery.internal.SqlStatementBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li>{@code DatabaseException}: A custom runtime exception for handling SQL errors.</li>
 *   <li>{@code Transaction<T>}: Represents a database transaction with a generic return type.</li>
 *   <li>{@code QueryType}: An enumeration of query types (DDL, SELECT, UPDATE, INSERT).</li>
 *   <li>{@code ResultSetProcessor<T>}: A functional interface for processing result sets.</li>
 *   <li>{@code RowProcessor} and {@code ColumnProcessor}: Implementations of {@code ResultSetProcessor} for processing rows and columns.</li>
 *   <li>{@code Columns}: A utility class for accessing column data from query results.</li>
 * </ul>
 *
 * <p>Note: This interface assumes the use of {@code DataSource} and {@code Connection} for database connectivity,
 * and {@code SqlExpression} for encapsulating SQL queries and their parameters.</p>
 */
public interface DB {
	Logger LOG = LoggerFactory.getLogger(DB.class);
	
	public static final class DatabaseException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public DatabaseException(SQLException e) {
			super(e);
		}

	}

	public interface Transaction<T> {
		public T run(Connection connection);
	}

	/**
	 * Functional interface for transactions that don't return a value.
	 */
	public interface TransactionReturningVoid {
		public void run(Connection connection);
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
        queryRowsStreaming(DbContext.getDefault(), conn, queryStatement, rowCallback);
    }

    /**
     * Streams rows from the database using a connection and a SQL expression with explicit DbContext.
     * 
     * @param context the database context
     * @param conn the database connection
     * @param queryStatement the SQL expression to execute
     * @param rowCallback the callback to process each row
     */
    public static void queryRowsStreaming(DbContext context, Connection conn, SqlExpression queryStatement, Consumer<Map<String,Object>> rowCallback) {
        String sql = queryStatement.getSql();
        String connId = null;
        if (LOG.isDebugEnabled()) {
            connId = getConnectionId(conn);
            LOG.debug("[{}] SELECT (streaming): {}", connId, sql.replace("\n", " ").replaceAll("\\s+", " ").trim());
            if (LOG.isTraceEnabled()) {
                LOG.trace("[{}] Parameters: {}", connId, queryStatement.getParameters());
            }
        }
        try (PreparedStatement stmt = conn.prepareStatement(sql);) {
			applyParameters(context, queryStatement.getParameters(), stmt);
			int fetchSize = context.getStreamingFetchSize();
			if (fetchSize != 0) {
				stmt.setFetchSize(fetchSize);
			}
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
        queryRowsStreaming(DbContext.getDefault(), db, queryStatement, rowCallback);
    }

    /**
     * Streams rows from the database using a data source and a SQL expression with explicit DbContext.
     * 
     * @param context the database context
     * @param db the data source
     * @param queryStatement the SQL expression to execute
     * @param rowCallback the callback to process each row
     */
    public static void queryRowsStreaming(DbContext context, DataSource db, SqlExpression queryStatement, Consumer<Map<String,Object>> rowCallback) {
		try (Connection connection = db.getConnection()) {
			queryRowsStreaming(context, connection, queryStatement, rowCallback);
		} catch (SQLException e) {
			throw new DatabaseException(e);
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
	
	/**
	 * ResultSet processor that extracts columns as lists of values.
	 */
	public static class ColumnProcessor implements ResultSetProcessor<List<List<Object>>> {
		/**
		 * Processes the ResultSet and returns columns as lists of values.
		 * @param rs the ResultSet to process
		 * @return a list of columns, each column being a list of values
		 */
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
	
	/**
	 * ResultSet processor that extracts rows as maps of column name to value.
	 */
	public static class RowProcessor implements ResultSetProcessor<List<Map<String, Object>>> {
		/**
		 * Processes the ResultSet and returns rows as maps.
		 * @param rs the ResultSet to process
		 * @return a list of rows, each row being a map from column name to value
		 */
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
     * @deprecated Use {@link #runInTransaction(DataSource, Transaction)} with the Connection-based overload instead.
     *             DataSource methods auto-commit each operation, which can lead to inconsistent data.
     */
    @Deprecated
    public static int update(DataSource db, String schemaName, String tableName, Map<String, Object> values, Map<String, Object> ids) {
        return update(DbContext.getDefault(), db, schemaName, tableName, values, ids);
    }

    /**
     * Updates records in the database using a schema name with explicit DbContext.
     * 
     * @param context the database context
     * @param db the data source
     * @param schemaName the schema name
     * @param tableName the name of the table
     * @param values the values to update
     * @param ids the identifiers of the records to update
     * @return the number of rows affected
     * @deprecated Use {@link #runInTransaction(DataSource, Transaction)} with the Connection-based overload instead.
     *             DataSource methods auto-commit each operation, which can lead to inconsistent data.
     */
    @Deprecated
    public static int update(DbContext context, DataSource db, String schemaName, String tableName, Map<String, Object> values, Map<String, Object> ids) {
        SqlExpression updateSql = SqlStatementBuilder.buildUpdate(context, schemaName, tableName, values, ids);
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
     * @deprecated Use {@link #runInTransaction(DataSource, Transaction)} with the Connection-based overload instead.
     *             DataSource methods auto-commit each operation, which can lead to inconsistent data.
     */
    @Deprecated
    public static int update(DataSource db, String tableName, Map<String, Object> values, Map<String, Object> ids) {
        return update(DbContext.getDefault(), db, null, tableName, values, ids);
    }

    /**
     * Updates records in the database without a schema name with explicit DbContext.
     * 
     * @param context the database context
     * @param db the data source
     * @param tableName the name of the table
     * @param values the values to update
     * @param ids the identifiers of the records to update
     * @return the number of rows affected
     * @deprecated Use {@link #runInTransaction(DataSource, Transaction)} with the Connection-based overload instead.
     *             DataSource methods auto-commit each operation, which can lead to inconsistent data.
     */
    @Deprecated
    public static int update(DbContext context, DataSource db, String tableName, Map<String, Object> values, Map<String, Object> ids) {
        return update(context, db, null, tableName, values, ids);
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
        return update(DbContext.getDefault(), connection, null, tableName, values, ids);
    }

    /**
     * Updates records in the database using a connection without a schema name with explicit DbContext.
     * 
     * @param context the database context
     * @param connection the database connection
     * @param tableName the name of the table
     * @param values the values to update
     * @param ids the identifiers of the records to update
     * @return the number of rows affected
     */
    public static int update(DbContext context, Connection connection, String tableName, Map<String, Object> values, Map<String, Object> ids) {
        return update(context, connection, null, tableName, values, ids);
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
        return update(DbContext.getDefault(), connection, schemaName, tableName, values, ids);
    }

    /**
     * Updates records in the database using a connection and schema name with explicit DbContext.
     * 
     * @param context the database context
     * @param connection the database connection
     * @param schemaName the schema name
     * @param tableName the name of the table
     * @param values the values to update
     * @param ids the identifiers of the records to update
     * @return the number of rows affected
     */
    public static int update(DbContext context, Connection connection, String schemaName, String tableName, Map<String, Object> values, Map<String, Object> ids) {
        SqlExpression updateSql = SqlStatementBuilder.buildUpdate(context, schemaName, tableName, values, ids);
        return (Integer)execute(connection, QueryType.UPDATE, updateSql.getSql(), updateSql.getParameters(), null);
    }

	/**
     * Updates records in the database using a SQL expression.
     * 
     * @param db the data source
     * @param update the SQL expression for the update
     * @return the number of rows affected
     * @deprecated Use {@link #runInTransaction(DataSource, Transaction)} with the Connection-based overload instead.
     *             DataSource methods auto-commit each operation, which can lead to inconsistent data.
     */
    @Deprecated
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
	 * @param schemaName the schema name
	 * @param tableName the name of the table
	 * @param values the values to insert
	 * @return the generated primary key
	 * @deprecated Use {@link #runInTransaction(DataSource, Transaction)} with the Connection-based overload instead.
	 *             DataSource methods auto-commit each operation, which can lead to inconsistent data.
	 */
	@Deprecated
	public static <PK> PK insert(DataSource db, String schemaName, String tableName, Map<String, ? extends Object> values) {
		return insert(DbContext.getDefault(), db, schemaName, tableName, values);
	}

	/**
	 * Inserts a record into the database with explicit DbContext.
	 * 
	 * @param <PK> the type of the primary key
	 * @param context the database context
	 * @param db the data source
	 * @param schemaName the schema name
	 * @param tableName the name of the table
	 * @param values the values to insert
	 * @return the generated primary key
	 * @deprecated Use {@link #runInTransaction(DataSource, Transaction)} with the Connection-based overload instead.
	 *             DataSource methods auto-commit each operation, which can lead to inconsistent data.
	 */
	@Deprecated
	public static <PK> PK insert(DbContext context, DataSource db, String schemaName, String tableName, Map<String, ? extends Object> values) {
		SqlExpression insertSql = SqlStatementBuilder.buildInsert(context, schemaName, tableName, values);
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
	 * @deprecated Use {@link #runInTransaction(DataSource, Transaction)} with the Connection-based overload instead.
	 *             DataSource methods auto-commit each operation, which can lead to inconsistent data.
	 */
	@Deprecated
	public static <PK> PK insert(DataSource db, String tableName, Map<String, ? extends Object> values) {
		return insert(DbContext.getDefault(), db, null, tableName, values);
	}

	/**
	 * Inserts a new record into the specified table in the database with explicit DbContext.
	 *
	 * @param <PK>       The type of the primary key that will be returned.
	 * @param context    The database context.
	 * @param db         The {@link DataSource} representing the database connection.
	 * @param tableName  The name of the table where the record will be inserted.
	 * @param values     A map containing the column names as keys and their corresponding values.
	 * @return           The primary key of the newly inserted record.
	 * @deprecated Use {@link #runInTransaction(DataSource, Transaction)} with the Connection-based overload instead.
	 *             DataSource methods auto-commit each operation, which can lead to inconsistent data.
	 */
	@Deprecated
	public static <PK> PK insert(DbContext context, DataSource db, String tableName, Map<String, ? extends Object> values) {
		return insert(context, db, null, tableName, values);
	}
	
	/**
	 * Inserts a new record into the specified table using a connection and schema name.
	 *
	 * @param <PK>       The type of the primary key that will be returned.
	 * @param connection The database connection.
	 * @param schemaName The schema name.
	 * @param tableName  The name of the table where the record will be inserted.
	 * @param values     A map containing the column names as keys and their corresponding values.
	 * @return           The primary key of the newly inserted record.
	 */
	public static <PK> PK insert(Connection connection, String schemaName, String tableName, Map<String, ? extends Object> values) {
		return insert(DbContext.getDefault(), connection, schemaName, tableName, values);
	}

	/**
	 * Inserts a new record into the specified table using a connection and schema name with explicit DbContext.
	 *
	 * @param <PK>       The type of the primary key that will be returned.
	 * @param context    The database context.
	 * @param connection The database connection.
	 * @param schemaName The schema name.
	 * @param tableName  The name of the table where the record will be inserted.
	 * @param values     A map containing the column names as keys and their corresponding values.
	 * @return           The primary key of the newly inserted record.
	 */
	public static <PK> PK insert(DbContext context, Connection connection, String schemaName, String tableName, Map<String, ? extends Object> values) {
		SqlExpression insertSql = SqlStatementBuilder.buildInsert(context, schemaName, tableName, values);
		return execute(connection, QueryType.INSERT, insertSql.getSql(), insertSql.getParameters(), null);
	}
	
	/**
	 * Inserts a new record into the specified table using a connection.
	 *
	 * @param <PK>       The type of the primary key that will be returned.
	 * @param connection The database connection.
	 * @param tableName  The name of the table where the record will be inserted.
	 * @param values     A map containing the column names as keys and their corresponding values.
	 * @return           The primary key of the newly inserted record.
	 */
	public static <PK> PK insert(Connection connection, String tableName, Map<String, ? extends Object> values) {
		return insert(DbContext.getDefault(), connection, null, tableName, values);
	}

	/**
	 * Inserts a new record into the specified table using a connection with explicit DbContext.
	 *
	 * @param <PK>       The type of the primary key that will be returned.
	 * @param context    The database context.
	 * @param connection The database connection.
	 * @param tableName  The name of the table where the record will be inserted.
	 * @param values     A map containing the column names as keys and their corresponding values.
	 * @return           The primary key of the newly inserted record.
	 */
	public static <PK> PK insert(DbContext context, Connection connection, String tableName, Map<String, ? extends Object> values) {
		return insert(context, connection, null, tableName, values);
	}
	
	/**
     * Upserts (inserts or updates on conflict) a record in the database.
     * 
     * @param <PK> the type of the primary key
     * @param db the data source
     * @param tableName the name of the table
     * @param values the values to insert or update
     * @param idFields the names of the primary key columns used for conflict detection
     * @return the generated primary key or null if the operation didn't generate keys (e.g., update)
     * @deprecated Use {@link #runInTransaction(DataSource, Transaction)} with the Connection-based overload instead.
     *             DataSource methods auto-commit each operation, which can lead to inconsistent data.
     */
    @Deprecated
	public static <PK> PK upsert(DataSource db, String tableName, Map<String, ? extends Object> values, List<String> idFields) {
		return upsert(DbContext.getDefault(), db, null, tableName, values, idFields);
	}

	/**
     * Upserts (inserts or updates on conflict) a record in the database with explicit DbContext.
     * 
     * @param <PK> the type of the primary key
     * @param context the database context
     * @param db the data source
     * @param tableName the name of the table
     * @param values the values to insert or update
     * @param idFields the names of the primary key columns used for conflict detection
     * @return the generated primary key or null if the operation didn't generate keys (e.g., update)
     * @deprecated Use {@link #runInTransaction(DataSource, Transaction)} with the Connection-based overload instead.
     *             DataSource methods auto-commit each operation, which can lead to inconsistent data.
     */
    @Deprecated
	public static <PK> PK upsert(DbContext context, DataSource db, String tableName, Map<String, ? extends Object> values, List<String> idFields) {
		return upsert(context, db, null, tableName, values, idFields);
	}
	
	/**
     * Upserts (inserts or updates on conflict) a record in the database using a schema name.
     * 
     * @param <PK> the type of the primary key
     * @param db the data source
     * @param schemaName the schema name
     * @param tableName the name of the table
     * @param values the values to insert or update
     * @param idFields the names of the primary key columns used for conflict detection
     * @return the generated primary key
     * @deprecated Use {@link #runInTransaction(DataSource, Transaction)} with the Connection-based overload instead.
     *             DataSource methods auto-commit each operation, which can lead to inconsistent data.
     */
    @Deprecated
    public static <PK> PK upsert(DataSource db, String schemaName, String tableName, Map<String, ? extends Object> values, List<String> idFields) {
        return upsert(DbContext.getDefault(), db, schemaName, tableName, values, idFields);
    }

    /**
     * Upserts (inserts or updates on conflict) a record in the database using a schema name with explicit DbContext.
     * 
     * @param <PK> the type of the primary key
     * @param context the database context
     * @param db the data source
     * @param schemaName the schema name
     * @param tableName the name of the table
     * @param values the values to insert or update
     * @param idFields the names of the primary key columns used for conflict detection
     * @return the generated primary key
     * @deprecated Use {@link #runInTransaction(DataSource, Transaction)} with the Connection-based overload instead.
     *             DataSource methods auto-commit each operation, which can lead to inconsistent data.
     */
    @Deprecated
    public static <PK> PK upsert(DbContext context, DataSource db, String schemaName, String tableName, Map<String, ? extends Object> values, List<String> idFields) {
        SqlExpression upsertSql = SqlStatementBuilder.buildUpsert(context, schemaName, tableName, values, idFields);
        return execute(db, QueryType.INSERT, upsertSql.getSql(), upsertSql.getParameters(), null);
    }

	/**
     * Upserts (inserts or updates on conflict) a record in the database using a connection and schema name.
     * 
     * @param <PK> the type of the primary key
     * @param connection the database connection
     * @param schemaName the schema name
     * @param tableName the name of the table
     * @param values the values to insert or update
     * @param idFields the names of the primary key columns used for conflict detection
     * @return the generated primary key
     */
    public static <PK> PK upsert(Connection connection, String schemaName, String tableName, Map<String, ? extends Object> values, List<String> idFields) {
        return upsert(DbContext.getDefault(), connection, schemaName, tableName, values, idFields);
    }

    /**
     * Upserts (inserts or updates on conflict) a record in the database using a connection and schema name with explicit DbContext.
     * 
     * @param <PK> the type of the primary key
     * @param context the database context
     * @param connection the database connection
     * @param schemaName the schema name
     * @param tableName the name of the table
     * @param values the values to insert or update
     * @param idFields the names of the primary key columns used for conflict detection
     * @return the generated primary key
     */
    public static <PK> PK upsert(DbContext context, Connection connection, String schemaName, String tableName, Map<String, ? extends Object> values, List<String> idFields) {
        SqlExpression upsertSql = SqlStatementBuilder.buildUpsert(context, schemaName, tableName, values, idFields);
        return execute(connection, QueryType.INSERT, upsertSql.getSql(), upsertSql.getParameters(), null);
    }

	/**
     * Upserts (inserts or updates on conflict) a record in the database using a connection.
     * 
     * @param <PK> the type of the primary key
     * @param connection the database connection
     * @param tableName the name of the table
     * @param values the values to insert or update
     * @param idFields the names of the primary key columns used for conflict detection
     * @return the generated primary key
     */
    public static <PK> PK upsert(Connection connection, String tableName, Map<String, ? extends Object> values, List<String> idFields) {
        return upsert(DbContext.getDefault(), connection, null, tableName, values, idFields);
    }

    /**
     * Upserts (inserts or updates on conflict) a record in the database using a connection with explicit DbContext.
     * 
     * @param <PK> the type of the primary key
     * @param context the database context
     * @param connection the database connection
     * @param tableName the name of the table
     * @param values the values to insert or update
     * @param idFields the names of the primary key columns used for conflict detection
     * @return the generated primary key
     */
    public static <PK> PK upsert(DbContext context, Connection connection, String tableName, Map<String, ? extends Object> values, List<String> idFields) {
        return upsert(context, connection, null, tableName, values, idFields);
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
		return SqlStatementBuilder.prefixAndQuoteTableName(context, schemaName, tableName);
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
		return execute(DbContext.getDefault(), db, type, sql, params, processor);
	}

	/**
     * Executes a query and processes the result set with explicit DbContext.
     * 
     * @param <T> the type of the result
     * @param context the database context
     * @param db the data source
     * @param type the type of query (e.g., SELECT, INSERT)
     * @param sql the SQL query to execute
     * @param params the parameters for the query
     * @param processor the processor to handle the result set
     * @return the processed result
     */
	public static <T> T execute(DbContext context, DataSource db, QueryType type, String sql, Iterable<Object> params, ResultSetProcessor<T> processor) {
		try (Connection connection = db.getConnection()) {
			return execute(context, connection, type, sql, params, processor);
		} catch (SQLException e) {
			throw new DatabaseException(e);
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
	public static <T> T execute(Connection connection, QueryType type, String sql, Iterable<Object> params, ResultSetProcessor<T> processor) {
		return execute(DbContext.getDefault(), connection, type, sql, params, processor);
	}

	/**
     * Executes a query and processes the result set using a connection with explicit DbContext.
     * 
     * @param <T> the type of the result
     * @param context the database context
     * @param connection the database connection
     * @param type the type of query (e.g., SELECT, INSERT)
     * @param sql the SQL query to execute
     * @param params the parameters for the query
     * @param processor the processor to handle the result set
     * @return the processed result
     */
	@SuppressWarnings("unchecked")
	public static <T> T execute(DbContext context, Connection connection, QueryType type, String sql, Iterable<Object> params, ResultSetProcessor<T> processor) {
		long startTime = 0;
		String connId = null;
		if (LOG.isDebugEnabled()) {
			connId = getConnectionId(connection);
			LOG.debug("[{}] {}: {}", connId, type, sql.replace("\n", " ").replaceAll("\\s+", " ").trim());
			if (params != null && LOG.isTraceEnabled()) {
				LOG.trace("[{}] Parameters: {}", connId, params);
			}
			startTime = System.nanoTime();
		}
		try (PreparedStatement stmt = connection.prepareStatement(
				sql,
				type == QueryType.INSERT
					? Statement.RETURN_GENERATED_KEYS
					: Statement.NO_GENERATED_KEYS
			)) {
			if (params != null) {
				applyParameters(context, params, stmt);
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
						if (keysResult != null) {
							try {
								if (keysResult.next()) {
									Object key = keysResult.getObject(1);
									// MySQL returns BigInteger for auto-generated keys, normalize to Long
									if (key instanceof Number) {
										return (T) Long.valueOf(((Number) key).longValue());
									}
									return (T) key;
								}
							} catch (SQLException e) {
								// Some databases (e.g., HSQLDB) throw exception when no keys were generated
								// This is normal for tables without auto-increment columns
							}
						}
						return null;
					}
				case UPDATE:
					Integer affectedRows = stmt.executeUpdate();
					success = true;
					return (T) affectedRows;
				}
			} finally {
				if (LOG.isDebugEnabled() && success) {
					long duration = (System.nanoTime() - startTime) / 1_000_000;
					LOG.debug("[{}] {} completed in {} ms", connId, type, duration);
				}
				if (!success) {
					LOG.error("Query failed: {}", sql);
				}
			}
			return null;
		} catch (SQLException e) {
			LOG.error("SQL error: {}", e.getMessage());
			throw new DatabaseException(e);
		}
	}

	/**
	 * Applies parameters to a prepared statement.
	 * 
	 * @param context the database context for type conversion
	 * @param params the parameters to apply
	 * @param stmt the prepared statement
	 * @throws SQLException if an SQL error occurs
	 */
	private static void applyParameters(DbContext context, Iterable<Object> params, PreparedStatement stmt) throws SQLException {
		int index = 1;
		for (Object val : params) {
			Object converted = context.convertParameterForJdbc(val);
			stmt.setObject(index++, converted);
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
	 * Extracts a meaningful connection identifier for logging.
	 * Uses the connection's toString() which typically includes driver-specific
	 * identifiers (e.g., HikariProxyConnection@xxx, PgConnection@xxx).
	 * Falls back to identity hash code if toString() is not informative.
	 */
	private static String getConnectionId(Connection connection) {
		String str = connection.toString();
		// Extract class name and hash from typical toString() format: "ClassName@hexhash"
		int atIndex = str.lastIndexOf('@');
		if (atIndex > 0 && atIndex < str.length() - 1) {
			// Get simple class name
			String className = str.substring(0, atIndex);
			int dotIndex = className.lastIndexOf('.');
			if (dotIndex >= 0) {
				className = className.substring(dotIndex + 1);
			}
			// Truncate long class names (e.g., HikariProxyConnection -> HikariProxy)
			if (className.length() > 12) {
				className = className.substring(0, 12);
			}
			return className + str.substring(atIndex);
		}
		// Fallback to identity hash code
		return String.format("conn@%08x", System.identityHashCode(connection));
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
		try {
			connection.setAutoCommit(false);
			T result = transaction.run(connection);
			connection.commit();
			return result;
		} catch (SQLException e) {
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				e.addSuppressed(rollbackEx);
			}
			throw new DatabaseException(e);
		} catch (RuntimeException e) {
			try {
				connection.rollback();
			} catch (SQLException rollbackEx) {
				e.addSuppressed(rollbackEx);
			}
			throw e;
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
		try (Connection connection = dataSource.getConnection()) {
			return runInTransaction(connection, transaction);
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Runs a transaction on the database using a connection.
	 * Use this overload when the transaction doesn't need to return a value.
	 * 
	 * @param connection the database connection
	 * @param transaction the transaction to execute
	 */
	public static void runInTransaction(Connection connection, TransactionReturningVoid transaction) {
		runInTransaction(connection, c -> {
			transaction.run(c);
			return null;
		});
	}

	/**
	 * Runs a transaction on the database using a data source.
	 * Use this overload when the transaction doesn't need to return a value.
	 * 
	 * @param dataSource the data source
	 * @param transaction the transaction to execute
	 */
	public static void runInTransaction(DataSource dataSource, TransactionReturningVoid transaction) {
		runInTransaction(dataSource, c -> {
			transaction.run(c);
			return null;
		});
	}

	/**
	 * Executes work with a connection from the data source, using autocommit mode.
	 * The connection is automatically closed after the work completes.
	 * Use this for read-only queries, single statements, or DDL operations
	 * that don't require transactional guarantees.
	 * 
	 * @param <T> the type of the result
	 * @param dataSource the data source
	 * @param work the work to execute with the connection
	 * @return the result of the work
	 */
	public static <T> T withConnection(DataSource dataSource, Transaction<T> work) {
		try (Connection connection = dataSource.getConnection()) {
			return work.run(connection);
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Executes work with a connection from the data source, using autocommit mode.
	 * The connection is automatically closed after the work completes.
	 * Use this overload when the work doesn't need to return a value.
	 * 
	 * @param dataSource the data source
	 * @param work the work to execute with the connection
	 */
	public static void withConnection(DataSource dataSource, TransactionReturningVoid work) {
		withConnection(dataSource, c -> {
			work.run(c);
			return null;
		});
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
