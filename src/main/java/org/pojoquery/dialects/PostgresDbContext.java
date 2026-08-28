package org.pojoquery.dialects;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.pojoquery.DbContext;
import org.pojoquery.FieldMapping;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.AQTSchemaGenerator.DDLColumnMetadata;
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
    public String mapJavaTypeToSql(Class<?> type, DDLColumnMetadata colMeta) {
        if (type.equals(Long.class) || type.equals(long.class)) {
            return "BIGINT";
        }
        if (type.equals(Integer.class) || type.equals(int.class)) {
            return "INTEGER";
        }
        if (type.equals(Short.class) || type.equals(short.class)) {
            return "SMALLINT";
        }
        if (type.equals(Byte.class) || type.equals(byte.class)) {
            return "SMALLINT"; // PostgreSQL doesn't have TINYINT
        }
        if (type.equals(Double.class) || type.equals(double.class)) {
            return "DOUBLE PRECISION";
        }
        if (type.equals(Float.class) || type.equals(float.class)) {
            return "REAL";
        }
        if (type.equals(Boolean.class) || type.equals(boolean.class)) {
            return "BOOLEAN";
        }
        if (type.equals(BigDecimal.class)) {
            int precision = (colMeta != null) ? colMeta.precision() : 19;
            int scale = (colMeta != null) ? colMeta.scale() : 4;
            return "NUMERIC(" + precision + "," + scale + ")";
        }
        if (type.equals(BigInteger.class)) {
            return "BIGINT";
        }

        if (type.equals(String.class)) {
            if (colMeta != null && colMeta.isLob()) {
                return "TEXT";
            }
            int length = (colMeta != null) ? colMeta.length() : getDefaultVarcharLength();
            return "VARCHAR(" + length + ")";
        }

        if (type.equals(LocalDateTime.class) || type.equals(Instant.class) || type.equals(Date.class)) {
            return "TIMESTAMP";
        }
        if (type.equals(Date.class) || type.equals(java.sql.Timestamp.class) || type.equals(Instant.class)) {
            return "TIMESTAMPTZ";
        }
        if (type.equals(java.sql.Date.class) || type.equals(LocalDate.class)) {
            return "DATE";
        }
        if (type.equals(java.sql.Time.class) || type.equals(LocalTime.class)) {
            return "TIME";
        }

        if (type.equals(byte[].class)) {
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

    @Override
    public SqlExpression jsonObject(List<JsonProperty> properties) {
        List<SqlExpression> parts = new ArrayList<>();
        for (JsonProperty property : properties) {
            parts.add(SqlExpression.implode("", List.of(
                    SqlExpression.sql("'" + DbContext.escapeSqlStringLiteral(property.name()) + "', "),
                    property.value())));
        }
        return SqlExpression.implode("", List.of(
                SqlExpression.sql("JSONB_BUILD_OBJECT(\n  "),
                SqlExpression.implode(",\n  ", parts),
                SqlExpression.sql("\n )")));
    }

    /**
     * Polymorphic objects concatenate the base object with a CASE over the
     * variant objects: {@code (base || CASE WHEN ... THEN variant ... ELSE '{}'::jsonb END)}.
     * Variant properties with NULL values are stripped with jsonb_strip_nulls,
     * mirroring the absent-on-null behavior of the other dialects.
     */
    @Override
    public SqlExpression jsonObjectWithVariants(List<JsonProperty> baseProperties, List<JsonVariant> variants) {
        if (variants.isEmpty()) {
            return jsonObject(baseProperties);
        }
        List<SqlExpression> parts = new ArrayList<>();
        parts.add(SqlExpression.sql("("));
        parts.add(jsonObject(baseProperties));
        parts.add(SqlExpression.sql(" || CASE"));
        for (JsonVariant variant : variants) {
            parts.add(SqlExpression.implode("", List.of(
                    SqlExpression.sql(" WHEN "),
                    variant.condition(),
                    SqlExpression.sql(" THEN JSONB_STRIP_NULLS("),
                    jsonObject(variant.properties()),
                    SqlExpression.sql(")"))));
        }
        parts.add(SqlExpression.sql(" ELSE '{}'::jsonb END)"));
        return SqlExpression.implode("", parts);
    }

    @Override
    public SqlExpression castToStringExpression(SqlExpression value) {
        return SqlExpression.implode("", List.of(
                SqlExpression.sql("CAST("), value, SqlExpression.sql(" AS TEXT)")));
    }

    /**
     * {@code JSONB_BUILD_ARRAY} keeps NULL elements. The SQL/JSON
     * {@code JSON_ARRAY} of PostgreSQL 16+ defaults to ABSENT ON NULL, which
     * would shift every later slot, so it is deliberately not used.
     */
    @Override
    public SqlExpression jsonArray(List<SqlExpression> elements) {
        return SqlExpression.implode("", List.of(
                SqlExpression.sql("JSONB_BUILD_ARRAY(\n  "),
                SqlExpression.implode(",\n  ", elements),
                SqlExpression.sql("\n )")));
    }

    @Override
    public SqlExpression jsonArrayAgg(SqlExpression element) {
        return SqlExpression.implode("", List.of(
                SqlExpression.sql("JSONB_AGG(\n  "),
                element,
                SqlExpression.sql("\n )")));
    }

    @Override
    public SqlExpression emptyJsonArray() {
        return SqlExpression.sql("'[]'::jsonb");
    }
}
