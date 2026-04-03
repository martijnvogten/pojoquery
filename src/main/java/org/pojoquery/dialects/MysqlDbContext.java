package org.pojoquery.dialects;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.Map;

import org.pojoquery.DbContext;
import org.pojoquery.FieldMapping;
import org.pojoquery.pipeline.AQTSchemaGenerator.DDLColumnMetadata;
import org.pojoquery.pipeline.SimpleFieldMapping;

/**
 * DbContext implementation for MySQL/MariaDB databases.
 * Uses backtick quoting and MySQL-specific SQL syntax.
 */
public class MysqlDbContext implements DbContext {

    @Override
    public Dialect getDialect() {
        return Dialect.MYSQL;
    }

    @Override
    public QuoteStyle getQuoteStyle() {
        return QuoteStyle.MYSQL;
    }

    @Override
    public String quoteObjectNames(String... names) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                ret.append(".");
            }
            ret.append(getQuoteStyle().quote(names[i]));
        }
        return ret.toString();
    }

    @Override
    public String quoteAlias(String alias) {
        return getQuoteStyle().quote(alias);
    }

    @Override
    public FieldMapping getFieldMapping(Field f) {
        return new SimpleFieldMapping(f);
    }

	@Override
	public String getForeignKeyColumnType() {
		return "BIGINT";
	}

    @Override
    public String mapJavaTypeToSql(Class<?> type, DDLColumnMetadata colMeta) {

        if (type.equals(Long.class) || type.equals(long.class)) {
            return "BIGINT";
        }
        if (type.equals(Integer.class) || type.equals(int.class)) {
            return "INT";
        }
        if (type.equals(Short.class) || type.equals(short.class)) {
            return "SMALLINT";
        }
        if (type.equals(Byte.class) || type.equals(byte.class)) {
            return "TINYINT";
        }
        if (type.equals(Double.class) || type.equals(double.class)) {
            return "DOUBLE";
        }
        if (type.equals(Float.class) || type.equals(float.class)) {
            return "FLOAT";
        }
        if (type.equals(Boolean.class) || type.equals(boolean.class)) {
            return "TINYINT(1)";
        }
        if (type.equals(BigDecimal.class)) {
            int precision = (colMeta != null) ? colMeta.precision() : 19;
            int scale = (colMeta != null) ? colMeta.scale() : 4;
            return "DECIMAL(" + precision + "," + scale + ")";
        }
        if (type.equals(BigInteger.class)) {
            return "BIGINT";
        }

        if (type.equals(String.class)) {
            if (colMeta != null && colMeta.isLob()) {
                return "LONGTEXT";
            }
            int length = (colMeta != null) ? colMeta.length() : getDefaultVarcharLength();
            return "VARCHAR(" + length + ")";
        }

        if (type.equals(LocalDateTime.class) || type.equals(Instant.class) || type.equals(java.sql.Timestamp.class)) {
            return "DATETIME";
        }
        if (type.equals(Date.class) || type.equals(java.sql.Timestamp.class) || type.equals(Instant.class) || type.equals(LocalDateTime.class)) {
            return "TIMESTAMP";
        }
        if (type.equals(java.sql.Date.class) || type.equals(LocalDate.class)) {
            return "DATE";
        }
        if (type.equals(java.sql.Time.class) || type.equals(LocalTime.class)) {
            return "TIME";
        }

        if (type.equals(byte[].class)) {
            return "BLOB";
        }

        if (type.isEnum()) {
            return "VARCHAR(50)";
        }

        if (Map.class.isAssignableFrom(type)) {
            return "JSON";
        }

        throw new IllegalArgumentException("Cannot map Java type to SQL type: " + type);
    }

    @Override
    public String getAutoIncrementSyntax() {
        return " NOT NULL AUTO_INCREMENT";
    }

    @Override
    public String getCreateTableSuffix() {
        return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    public int getStreamingFetchSize() {
        return Integer.MIN_VALUE; // MySQL streaming mode
    }
}
