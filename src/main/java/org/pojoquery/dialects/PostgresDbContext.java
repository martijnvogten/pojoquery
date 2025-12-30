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
 * DbContext implementation for PostgreSQL databases.
 * Uses ANSI double-quote quoting and PostgreSQL-specific SQL syntax.
 */
public class PostgresDbContext implements DbContext {

    @Override
    public Dialect getDialect() {
        return Dialect.POSTGRES;
    }

    @Override
    public QuoteStyle getQuoteStyle() {
        return QuoteStyle.ANSI;
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
            return "INTEGER";
        }
        if (type == Short.class || type == short.class) {
            return "SMALLINT";
        }
        if (type == Byte.class || type == byte.class) {
            return "SMALLINT"; // PostgreSQL doesn't have TINYINT
        }
        if (type == Double.class || type == double.class) {
            return "DOUBLE PRECISION";
        }
        if (type == Float.class || type == float.class) {
            return "REAL";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "BOOLEAN";
        }
        if (type == BigDecimal.class) {
            return "NUMERIC(19,4)";
        }
        if (type == BigInteger.class) {
            return "BIGINT";
        }

        if (type == String.class) {
            if (field.getAnnotation(Lob.class) != null) {
                return "TEXT";
            }
            return "VARCHAR(" + getDefaultVarcharLength() + ")";
        }

        if (type == Date.class || type == java.sql.Timestamp.class || type == LocalDateTime.class) {
            return "TIMESTAMP";
        }
        if (type == java.sql.Date.class || type == LocalDate.class) {
            return "DATE";
        }
        if (type == java.sql.Time.class || type == LocalTime.class) {
            return "TIME";
        }

        if (type == byte[].class) {
            return "BYTEA";
        }

        if (type.isEnum()) {
            return "VARCHAR(50)";
        }

        if (Map.class.isAssignableFrom(type)) {
            return "JSONB";
        }

        throw new IllegalArgumentException("Cannot map Java type to SQL type: " + type);
    }

    @Override
    public String getAutoIncrementSyntax() {
        return ""; // PostgreSQL uses SERIAL or GENERATED ALWAYS AS IDENTITY
    }
    
    @Override
    public String getForeignKeyColumnType() {
        return "BIGINT"; // For foreign key columns (non-auto-incrementing)
    }
    
    @Override
    public String getAutoIncrementKeyColumnType() {
        return "BIGSERIAL"; // Auto-incrementing primary key type in PostgreSQL
    }

    @Override
    public String getCreateTableSuffix() {
        return "";
    }

    @Override
    public int getStreamingFetchSize() {
        return 100; // Reasonable default for PostgreSQL
    }
}
