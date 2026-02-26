package org.pojoquery.dialects;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.DbContext;
import org.pojoquery.FieldMapping;
import org.pojoquery.pipeline.SimpleFieldMapping;
import org.pojoquery.typemodel.FieldModel;

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
    public String mapJavaTypeToSql(FieldModel field) {
        String type = field.getType().getQualifiedName();

        if (type.equals(Long.class.getName()) || type.equals(long.class.getName())) {
            return "BIGINT";
        }
        if (type.equals(Integer.class.getName()) || type.equals(int.class.getName())) {
            return "INTEGER";
        }
        if (type.equals(Short.class.getName()) || type.equals(short.class.getName())) {
            return "SMALLINT";
        }
        if (type.equals(Byte.class.getName()) || type.equals(byte.class.getName())) {
            return "SMALLINT"; // PostgreSQL doesn't have TINYINT
        }
        if (type.equals(Double.class.getName()) || type.equals(double.class.getName())) {
            return "DOUBLE PRECISION";
        }
        if (type.equals(Float.class.getName()) || type.equals(float.class.getName())) {
            return "REAL";
        }
        if (type.equals(Boolean.class.getName()) || type.equals(boolean.class.getName())) {
            return "BOOLEAN";
        }
        if (type.equals(BigDecimal.class.getName())) {
            AnnotationHelper.ColumnMetadata colMeta = AnnotationHelper.getColumnMetadata(field);
            int precision = (colMeta != null) ? colMeta.precision : 19;
            int scale = (colMeta != null) ? colMeta.scale : 4;
            return "NUMERIC(" + precision + "," + scale + ")";
        }
        if (type.equals(BigInteger.class.getName())) {
            return "BIGINT";
        }

        if (type.equals(String.class.getName())) {
            if (AnnotationHelper.isLob(field)) {
                return "TEXT";
            }
            AnnotationHelper.ColumnMetadata colMeta = AnnotationHelper.getColumnMetadata(field);
            int length = (colMeta != null) ? colMeta.length : getDefaultVarcharLength();
            return "VARCHAR(" + length + ")";
        }

        if (type.equals(LocalDateTime.class.getName())) {
            return "TIMESTAMP";
        }
        if (type.equals(Date.class.getName()) || type.equals(java.sql.Timestamp.class.getName()) || type.equals(Instant.class.getName())) {
            return "TIMESTAMPTZ";
        }
        if (type.equals(java.sql.Date.class.getName()) || type.equals(LocalDate.class.getName())) {
            return "DATE";
        }
        if (type.equals(java.sql.Time.class.getName()) || type.equals(LocalTime.class.getName())) {
            return "TIME";
        }

        if (type.equals(byte[].class.getName())) {
            return "BYTEA";
        }

        if (field.getType().isEnum()) {
            return "VARCHAR(50)";
        }

        if (field.getType().isMap()) {
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
