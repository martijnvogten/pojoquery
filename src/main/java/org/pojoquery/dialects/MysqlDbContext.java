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
import org.pojoquery.annotations.Lob;
import org.pojoquery.pipeline.AQTSchemaGenerator.DDLColumnMetadata;
import org.pojoquery.pipeline.SimpleFieldMapping;
import org.pojoquery.typemodel.FieldModel;

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
    public String mapJavaTypeToSql(FieldModel field, DDLColumnMetadata columnMetadata) {
        String type = field.getType().getQualifiedName();

        if (type.equals(Long.class.getName()) || type.equals(long.class.getName())) {
            return "BIGINT";
        }
        if (type.equals(Integer.class.getName()) || type.equals(int.class.getName())) {
            return "INT";
        }
        if (type.equals(Short.class.getName()) || type.equals(short.class.getName())) {
            return "SMALLINT";
        }
        if (type.equals(Byte.class.getName()) || type.equals(byte.class.getName())) {
            return "TINYINT";
        }
        if (type.equals(Double.class.getName()) || type.equals(double.class.getName())) {
            return "DOUBLE";
        }
        if (type.equals(Float.class.getName()) || type.equals(float.class.getName())) {
            return "FLOAT";
        }
        if (type.equals(Boolean.class.getName()) || type.equals(boolean.class.getName())) {
            return "TINYINT(1)";
        }
        if (type.equals(BigDecimal.class.getName())) {
            AnnotationHelper.ColumnMetadata colMeta = AnnotationHelper.getColumnMetadata(field);
            int precision = (colMeta != null) ? colMeta.precision : 19;
            int scale = (colMeta != null) ? colMeta.scale : 4;
            return "DECIMAL(" + precision + "," + scale + ")";
        }
        if (type.equals(BigInteger.class.getName())) {
            return "BIGINT";
        }

        if (type.equals(String.class.getName())) {
            if (field.hasAnnotation(Lob.class)) {
                return "LONGTEXT";
            }
            AnnotationHelper.ColumnMetadata colMeta = AnnotationHelper.getColumnMetadata(field);
            int length = (colMeta != null) ? colMeta.length : getDefaultVarcharLength();
            return "VARCHAR(" + length + ")";
        }

        if (type.equals(LocalDateTime.class.getName()) || type.equals(Instant.class.getName())) {
            return "DATETIME";
        }
        if (type.equals(Date.class.getName()) || type.equals(java.sql.Timestamp.class.getName()) || type.equals(Instant.class.getName())) {
            return "TIMESTAMP";
        }
        if (type.equals(java.sql.Date.class.getName()) || type.equals(LocalDate.class.getName())) {
            return "DATE";
        }
        if (type.equals(java.sql.Time.class.getName()) || type.equals(LocalTime.class.getName())) {
            return "TIME";
        }

        if (type.equals(byte[].class.getName())) {
            return "BLOB";
        }

        if (field.getType().isEnum()) {
            return "VARCHAR(50)";
        }

        if (field.getType().isMap()) {
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
