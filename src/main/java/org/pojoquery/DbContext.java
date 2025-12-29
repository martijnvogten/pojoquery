package org.pojoquery;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.Map;

import org.pojoquery.pipeline.SimpleFieldMapping;

public interface DbContext {
	
	public enum QuoteStyle {
		ANSI("\""),
		MYSQL("`");

		private final String quote;

		QuoteStyle(String quote) {
			this.quote = quote;
		}

		public String quote(String name) {
			return quote + name + quote;
		}
	}

	static DbContext DEFAULT = new DefaultDbContext();

	public String quoteObjectNames(String... names);
	public QuoteStyle getQuoteStyle();
	public String quoteAlias(String alias);
	public FieldMapping getFieldMapping(Field f);
	
	// Schema generation methods
	default String getDefaultVarcharLength() {
		return "255";
	}
	
	default String mapJavaTypeToSql(Class<?> type) {
		// Handle primitive types and wrappers
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
			return "DECIMAL(19,4)";
		}
		if (type == BigInteger.class) {
			return "BIGINT";
		}
		
		// Handle String
		if (type == String.class) {
			return "VARCHAR(" + getDefaultVarcharLength() + ")";
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
		return "TEXT";
	}
	
	default String getAutoIncrementSyntax() {
		return " NOT NULL AUTO_INCREMENT";
	}
	
	default String getTableSuffix() {
		return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
	}
	
	public class DefaultDbContext implements DbContext {
		private final QuoteStyle quoteStyle;
		private final boolean quoteObjects;

		public DefaultDbContext() {
			this(QuoteStyle.MYSQL, true);
		}

		public DefaultDbContext(QuoteStyle quoteStyle, boolean quoteObjects) {
			this.quoteStyle = quoteStyle;
			this.quoteObjects = quoteObjects;
		}

		@Override
		public String quoteObjectNames(String... names) {
			String ret = "";
			for (int i = 0, nl = names.length; i < nl; i++) {
				String name = names[i];
				if (i > 0) {
					ret += ".";
				}
				ret += quoteObjects ? quoteStyle.quote(name) : name;
			}
			return ret;
		}

		@Override
		public QuoteStyle getQuoteStyle() {
			return quoteStyle;
		}

		@Override
		public String quoteAlias(String alias) {
			// Always quote aliases
			return quoteStyle.quote(alias);
		}

		@Override
		public FieldMapping getFieldMapping(Field f) {
			return new SimpleFieldMapping(f);
		}
	}

	public static DbContext getDefault() {
		return DEFAULT;
	}

	/**
	 * Creates a new DbContextBuilder for configuring a custom DbContext.
	 * @return A new DbContextBuilder instance
	 */
	public static DbContextBuilder builder() {
		return new DbContextBuilder();
	}
}
