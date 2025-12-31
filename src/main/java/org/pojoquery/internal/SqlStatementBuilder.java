package org.pojoquery.internal;

import static org.pojoquery.util.Strings.implode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;

/**
 * Internal utility class for building SQL statements.
 * This class handles the construction of INSERT, UPDATE, and UPSERT statements
 * with proper dialect-specific syntax.
 * 
 * <p>This class is not part of the public API and may change without notice.</p>
 */
public final class SqlStatementBuilder {
	
	private SqlStatementBuilder() {
		// Utility class - prevent instantiation
	}

	/**
	 * Builds an SQL INSERT statement.
	 * 
	 * @param context the database context for dialect-specific syntax
	 * @param schemaName the schema name (optional, may be null)
	 * @param tableName the table name
	 * @param values the column-value pairs to insert
	 * @return the SQL expression with parameters
	 */
	public static SqlExpression buildInsert(DbContext context, String schemaName, String tableName, Map<String, ? extends Object> values) {
		String qualifiedTableName = prefixAndQuoteTableName(context, schemaName, tableName);
		
		// Handle empty values (e.g., when only auto-generated ID exists)
		if (values.isEmpty()) {
			String sql;
			switch (context.getDialect()) {
			case MYSQL:
				sql = "INSERT INTO " + qualifiedTableName + " () VALUES ()";
				break;
			case POSTGRES:
			case HSQLDB:
			case ANSI:
			default:
				sql = "INSERT INTO " + qualifiedTableName + " DEFAULT VALUES";
				break;
			}
			return new SqlExpression(sql, new ArrayList<>());
		}
		
		List<String> qmarks = new ArrayList<>();
		List<String> quotedFields = new ArrayList<>();
		List<Object> params = new ArrayList<>();

		for (String f : values.keySet()) {
			qmarks.add("?");
			quotedFields.add(context.quoteObjectNames(f));
			params.add(values.get(f));
		}
		
		String sql = "INSERT INTO " + qualifiedTableName + " (" + implode(",", quotedFields) + ")" 
			+ " VALUES (" + implode(",", qmarks) + ")";
		
		return new SqlExpression(sql, params);
	}

	/**
	 * Builds an SQL UPDATE statement.
	 * 
	 * @param context the database context for dialect-specific syntax
	 * @param schemaName the schema name (optional, may be null)
	 * @param tableName the table name
	 * @param values the column-value pairs to update
	 * @param ids the column-value pairs identifying rows to update
	 * @return the SQL expression with parameters
	 */
	public static SqlExpression buildUpdate(DbContext context, String schemaName, String tableName, Map<String, ? extends Object> values, Map<String, ? extends Object> ids) {
		List<String> assignments = new ArrayList<>();
		List<Object> params = new ArrayList<>();
		List<String> wheres = new ArrayList<>();

		for (String f : values.keySet()) {
			assignments.add(String.format("%s=?", context.quoteObjectNames(f)));
			params.add(values.get(f));
		}
		
		for (String idField : ids.keySet()) {
			wheres.add(String.format("%s=?", context.quoteObjectNames(idField)));
			params.add(ids.get(idField));
		}
		
		String sql = "UPDATE " + prefixAndQuoteTableName(context, schemaName, tableName) 
			+ " SET " + implode(", ", assignments) 
			+ " WHERE " + implode(" AND ", wheres);
		return new SqlExpression(sql, params);
	}

	/**
	 * Builds an SQL UPSERT (INSERT or UPDATE on conflict) statement.
	 * The syntax varies by database dialect:
	 * <ul>
	 *   <li>MySQL: INSERT ... ON DUPLICATE KEY UPDATE</li>
	 *   <li>PostgreSQL: INSERT ... ON CONFLICT DO UPDATE</li>
	 *   <li>HSQLDB: MERGE INTO ... USING ... WHEN MATCHED/NOT MATCHED</li>
	 * </ul>
	 * 
	 * @param context the database context for dialect-specific syntax
	 * @param schemaName the schema name (optional, may be null)
	 * @param tableName the table name
	 * @param values the column-value pairs to insert or update
	 * @param idFields the names of the primary key columns used for conflict detection
	 * @return the SQL expression with parameters
	 */
	public static SqlExpression buildUpsert(DbContext context, String schemaName, String tableName, Map<String, ? extends Object> values, List<String> idFields) {
		String qualifiedTableName = prefixAndQuoteTableName(context, schemaName, tableName);
		
		// Handle empty values - upsert with no values doesn't make sense, fall back to plain insert
		if (values.isEmpty()) {
			return buildInsert(context, schemaName, tableName, values);
		}
		
		// Quote the ID field names for the conflict clause
		List<String> quotedIdFields = new ArrayList<>();
		for (String idField : idFields) {
			quotedIdFields.add(context.quoteObjectNames(idField));
		}
		String conflictColumns = implode(", ", quotedIdFields);
		
		List<String> qmarks = new ArrayList<>();
		List<String> quotedFields = new ArrayList<>();
		List<Object> params = new ArrayList<>();
		List<String> updateList = new ArrayList<>();

		for (String f : values.keySet()) {
			final String quotedField = context.quoteObjectNames(f);
			qmarks.add("?");
			quotedFields.add(quotedField);
			params.add(values.get(f));
			updateList.add(quotedField + "=?");
		}
		
		String sql;
		
		switch (context.getDialect()) {
		case MYSQL:
			// MySQL: INSERT ... ON DUPLICATE KEY UPDATE
			params.addAll(new ArrayList<>(params));
			sql = "INSERT INTO " + qualifiedTableName + " (" + implode(",", quotedFields) + ")" 
				+ " VALUES (" + implode(",", qmarks) + ")"
				+ " ON DUPLICATE KEY UPDATE " + implode(",", updateList);
			break;
		case POSTGRES:
			// PostgreSQL: INSERT ... ON CONFLICT (pk) DO UPDATE
			params.addAll(new ArrayList<>(params));
			sql = "INSERT INTO " + qualifiedTableName + " (" + implode(",", quotedFields) + ")"
				+ " VALUES (" + implode(",", qmarks) + ")"
				+ " ON CONFLICT (" + conflictColumns + ") DO UPDATE SET " + implode(",", updateList);
			break;
		case HSQLDB:
			// HSQLDB: MERGE INTO ... USING ... ON ... WHEN MATCHED/NOT MATCHED
			// Build the ON clause for composite keys
			List<String> onConditions = new ArrayList<>();
			for (String idField : idFields) {
				String quotedIdField = context.quoteObjectNames(idField);
				onConditions.add("t." + quotedIdField + " = vals." + quotedIdField);
			}
			// Reference vals.column instead of new placeholders to avoid duplicate params
			List<String> valsUpdateList = new ArrayList<>();
			List<String> valsFieldRefs = new ArrayList<>();
			for (String f : values.keySet()) {
				final String quotedField = context.quoteObjectNames(f);
				valsUpdateList.add(quotedField + "=vals." + quotedField);
				valsFieldRefs.add("vals." + quotedField);
			}
			sql = "MERGE INTO " + qualifiedTableName + " t"
				+ " USING (VALUES(" + implode(",", qmarks) + ")) AS vals(" + implode(",", quotedFields) + ")"
				+ " ON " + implode(" AND ", onConditions)
				+ " WHEN MATCHED THEN UPDATE SET " + implode(",", valsUpdateList)
				+ " WHEN NOT MATCHED THEN INSERT (" + implode(",", quotedFields) + ") VALUES (" + implode(",", valsFieldRefs) + ")";
			break;
		case ANSI:
		default:
			// Fallback: just do a plain INSERT (no upsert support)
			sql = "INSERT INTO " + qualifiedTableName + " (" + implode(",", quotedFields) + ")" 
				+ " VALUES (" + implode(",", qmarks) + ")";
			break;
		}
		
		return new SqlExpression(sql, params);
	}

	/**
	 * Quotes and prefixes a table name with a schema name if provided.
	 * 
	 * @param context the database context
	 * @param schemaName the schema name (optional, may be null)
	 * @param tableName the table name
	 * @return the fully qualified and quoted table name
	 */
	public static String prefixAndQuoteTableName(DbContext context, String schemaName, String tableName) {
		if (schemaName == null) {
			return context.quoteObjectNames(tableName);
		}
		return context.quoteObjectNames(schemaName, tableName);
	}
}
