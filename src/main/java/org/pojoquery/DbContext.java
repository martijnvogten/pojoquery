package org.pojoquery;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.Map;

import org.pojoquery.annotations.Column;
import org.pojoquery.annotations.Lob;
import org.pojoquery.dialects.HsqldbDbContext;
import org.pojoquery.dialects.MysqlDbContext;
import org.pojoquery.dialects.PostgresDbContext;

/**
 * Defines database-specific behavior for PojoQuery operations.
 * 
 * <p>DbContext handles dialect-specific SQL generation including identifier quoting,
 * type mappings, and SQL syntax variations. PojoQuery provides built-in implementations
 * for MySQL, PostgreSQL, and HSQLDB.</p>
 * 
 * <h2>Setting the Default Context</h2>
 * <p>Set the default context at application startup:</p>
 * <pre>{@code
 * // For MySQL/MariaDB
 * DbContext.setDefault(DbContext.forDialect(Dialect.MYSQL));
 * 
 * // For PostgreSQL
 * DbContext.setDefault(DbContext.forDialect(Dialect.POSTGRES));
 * 
 * // For HSQLDB (testing)
 * DbContext.setDefault(DbContext.forDialect(Dialect.HSQLDB));
 * }</pre>
 * 
 * <h2>Custom DbContext</h2>
 * <p>For custom type mappings or behavior, extend an existing implementation:</p>
 * <pre>{@code
 * public class MyDbContext extends PostgresDbContext {
 *     @Override
 *     public String mapJavaTypeToSql(Field field) {
 *         if (field.getType() == UUID.class) {
 *             return \"UUID\";
 *         }
 *         return super.mapJavaTypeToSql(field);
 *     }
 * }
 * }</pre>
 * 
 * @see #forDialect(Dialect)
 * @see #setDefault(DbContext)
 * @see DbContextBuilder
 */
public interface DbContext {

	/**
	 * Defines how database identifiers (table and column names) are quoted.
	 */
	public enum QuoteStyle {
		ANSI("\""),
		MYSQL("`"),
		NONE("");

		private final String quote;

		QuoteStyle(String quote) {
			this.quote = quote;
		}

		public String quote(String name) {
			if (quote.isEmpty()) {
				return name;
			}
			return quote + name + quote;
		}
	}

	public enum Dialect {
		/** MySQL and MariaDB databases */
		MYSQL,
		/** HSQLDB (HyperSQL) - commonly used for testing */
		HSQLDB,
		/** PostgreSQL databases */
		POSTGRES,
		/** Generic ANSI SQL (falls back to MySQL behavior) */
		ANSI
	}

	/**
	 * Returns the SQL dialect for this database context.
	 * Used to generate dialect-specific SQL syntax (e.g., upsert statements).
	 * 
	 * @return the SQL dialect
	 */
	default Dialect getDialect() {
		return Dialect.MYSQL;
	}

	// Holder class to allow mutable default context
	class DefaultHolder {
		static DbContext instance = new MysqlDbContext();
	}

	// Keep for backward compatibility - references DefaultHolder.instance
	static DbContext DEFAULT = DefaultHolder.instance;

	/**
	 * Returns the current default DbContext.
	 * 
	 * @return the default DbContext
	 */
	static DbContext getDefault() {
		return DefaultHolder.instance;
	}

	/**
	 * Sets the default DbContext for all PojoQuery operations.
	 * 
	 * <p>Call this at application startup to configure for your database:</p>
	 * <pre>{@code
	 * DbContext.setDefault(DbContext.forDialect(Dialect.POSTGRES));
	 * }</pre>
	 * 
	 * @param context the DbContext to use as the default
	 */
	static void setDefault(DbContext context) {
		DefaultHolder.instance = context;
	}

	/**
	 * Creates a DbContextBuilder for customizing context settings.
	 * 
	 * @return a new DbContextBuilder
	 * @see DbContextBuilder
	 */
	static DbContextBuilder builder() {
		return new DbContextBuilder();
	}

	/**
	 * Creates a DbContext for the specified SQL dialect with sensible defaults.
	 * This is the recommended way to get a DbContext for a specific database.
	 * 
	 * <p>Example usage:</p>
	 * <pre>
	 * // For MySQL/MariaDB
	 * DbContext.setDefault(DbContext.forDialect(Dialect.MYSQL));
	 * 
	 * // For PostgreSQL
	 * DbContext.setDefault(DbContext.forDialect(Dialect.POSTGRES));
	 * 
	 * // For HSQLDB (testing)
	 * DbContext.setDefault(DbContext.forDialect(Dialect.HSQLDB));
	 * </pre>
	 * 
	 * @param dialect the SQL dialect
	 * @return a pre-configured DbContext for the dialect
	 */
	static DbContext forDialect(Dialect dialect) {
		switch (dialect) {
			case MYSQL:
				return new MysqlDbContext();
			case HSQLDB:
				return new HsqldbDbContext();
			case POSTGRES:
				return new PostgresDbContext();
			case ANSI:
			default:
				return new MysqlDbContext(); // Fall back to MySQL for ANSI
		}
	}

	/**
	 * Quotes database object names (tables, columns) using the appropriate quote style.
	 * 
	 * <p>Example: for MySQL, {@code quoteObjectNames("users", "name")} returns {@code `users`.`name`}</p>
	 * 
	 * @param names the object names to quote
	 * @return the quoted and dot-joined names
	 */
	public String quoteObjectNames(String... names);

	/**
	 * Returns the quote style used for this database.
	 * 
	 * @return the quote style
	 */
	public QuoteStyle getQuoteStyle();

	/**
	 * Quotes a column or table alias.
	 * 
	 * @param alias the alias to quote
	 * @return the quoted alias
	 */
	public String quoteAlias(String alias);

	public FieldMapping getFieldMapping(Field f);

	// Schema generation methods
	default int getDefaultVarcharLength() {
		return 255;
	}

	/**
	 * Returns the SQL type for foreign key columns that reference other tables.
	 * This should NOT be an auto-incrementing type.
	 * @return the SQL type for foreign key columns (e.g., "BIGINT")
	 */
	public String getForeignKeyColumnType();
	
	/**
	 * Returns the SQL type for auto-incrementing primary key columns.
	 * For databases like PostgreSQL, this may return a special type like "BIGSERIAL".
	 * Default implementation returns getKeyColumnType() for backward compatibility.
	 * @return the SQL type for auto-incrementing primary key columns
	 */
	default String getAutoIncrementKeyColumnType() {
		return getForeignKeyColumnType();
	}

	/**
	 * Maps a Java field to its SQL type, considering any annotations on the field.
	 * 
	 * @param field the field to map (may be null for non-field contexts like FK
	 *              columns)
	 * @return the SQL type string
	 */
	default String mapJavaTypeToSql(Field field) {
		Class<?> type = field.getType();

		if (type == Long.class || type == long.class) {
			return "BIGINT";
		}
		if (type == Integer.class || type == int.class) {
			return "INT";
		}
		if (type == Short.class || type == short.class) {
			return "SMALLINT";
		}
		if (type == Byte.class || type == byte.class) {
			return "TINYINT";
		}
		if (type == Double.class || type == double.class) {
			return "DOUBLE";
		}
		if (type == Float.class || type == float.class) {
			return "FLOAT";
		}
		if (type == Boolean.class || type == boolean.class) {
			return "TINYINT(1)";
		}
		if (type == BigDecimal.class) {
			Column colAnn = field.getAnnotation(Column.class);
			int precision = (colAnn != null) ? colAnn.precision() : 19;
			int scale = (colAnn != null) ? colAnn.scale() : 4;
			return "DECIMAL(" + precision + "," + scale + ")";
		}
		if (type == BigInteger.class) {
			return "BIGINT";
		}

		// Handle String
		if (type == String.class) {
			if (field.getAnnotation(Lob.class) != null) {
				return "CLOB";
			}
			Column colAnn = field.getAnnotation(Column.class);
			int length = (colAnn != null) ? colAnn.length() : getDefaultVarcharLength();
			return "VARCHAR(" + length + ")";
		}

		// Handle date/time types
		if (type == Date.class || type == java.sql.Timestamp.class || type == LocalDateTime.class) {
			return "DATETIME";
		}
		if (type == java.sql.Date.class || type == LocalDate.class) {
			return "DATE";
		}
		if (type == java.sql.Time.class || type == LocalTime.class) {
			return "TIME";
		}

		// Handle byte array (binary data)
		if (type == byte[].class) {
			return "BLOB";
		}

		// Handle enums
		if (type.isEnum()) {
			return "VARCHAR(50)";
		}

		// Handle Map (for @Other annotation)
		if (Map.class.isAssignableFrom(type)) {
			return "JSON";
		}

		// Default to TEXT for unknown types
		throw new IllegalArgumentException("Cannot map Java type to SQL type: " + type);
	}

	default String getAutoIncrementSyntax() {
		return " NOT NULL AUTO_INCREMENT";
	}

	/**
	 * Returns the fetch size to use for streaming result sets.
	 * MySQL uses Integer.MIN_VALUE to enable streaming mode.
	 * Other databases typically use a positive value like 100 or 0 for default.
	 * 
	 * @return the fetch size for streaming queries
	 */
	default int getStreamingFetchSize() {
		return Integer.MIN_VALUE; // MySQL default
	}

	/**
	 * Returns the suffix to append to CREATE TABLE statements.
	 * For MySQL, this typically includes engine and charset settings.
	 * 
	 * @return the CREATE TABLE suffix
	 */
	default String getCreateTableSuffix() {
		return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
	}
}
