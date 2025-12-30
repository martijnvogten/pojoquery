package org.pojoquery.dialects;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.Map;

import org.pojoquery.DbContext;
import org.pojoquery.FieldMapping;
import org.pojoquery.annotations.Lob;
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
    public String mapJavaTypeToSql(Field field) {
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
            return "DECIMAL(19,4)";
        }
        if (type == BigInteger.class) {
            return "BIGINT";
        }

        if (type == String.class) {
            if (field.getAnnotation(Lob.class) != null) {
                return "LONGTEXT";
            }
            return "VARCHAR(" + getDefaultVarcharLength() + ")";
        }

        if (type == Date.class || type == java.sql.Timestamp.class || type == LocalDateTime.class) {
            return "DATETIME";
        }
        if (type == java.sql.Date.class || type == LocalDate.class) {
            return "DATE";
        }
        if (type == java.sql.Time.class || type == LocalTime.class) {
            return "TIME";
        }

        if (type == byte[].class) {
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
    public String getTableSuffix() {
        return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    public int getStreamingFetchSize() {
        return Integer.MIN_VALUE; // MySQL streaming mode
    }
}
