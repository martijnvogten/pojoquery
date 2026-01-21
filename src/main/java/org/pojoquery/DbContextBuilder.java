package org.pojoquery;

import java.lang.reflect.Field;

import org.pojoquery.DbContext.Dialect;
import org.pojoquery.DbContext.QuoteStyle;
import org.pojoquery.pipeline.SimpleFieldMapping;

/**
 * A builder class that provides a fluent API for configuring a DbContext.
 * This allows for easy configuration of database context settings.
 * 
 * <p>Example usage:</p>
 * <pre>
 * // Simple dialect-based context
 * DbContext ctx = DbContext.forDialect(Dialect.MYSQL);
 * 
 * // Customized context
 * DbContext ctx = DbContext.builder()
 *     .dialect(Dialect.MYSQL)
 *     .quoteObjectNames(false)
 *     .build();
 * </pre>
 */
public class DbContextBuilder {
    private Dialect dialect = Dialect.MYSQL;
    private QuoteStyle quoteStyle = null; // null means use dialect default
    private Boolean quoteObjects = null; // null means use dialect default
    private FieldMappingFactory fieldMappingFactory = SimpleFieldMapping::new;

    /**
     * Sets the SQL dialect. This determines the default settings for quoting,
     * type mapping, and SQL syntax.
     * @param dialect The SQL dialect to use
     * @return This builder for method chaining
     */
    public DbContextBuilder dialect(Dialect dialect) {
        this.dialect = dialect;
        return this;
    }

    /**
     * Sets the quote style for database identifiers.
     * This also sets the dialect to match:
     * - MYSQL quote style uses MYSQL dialect
     * - ANSI quote style uses POSTGRES dialect
     * - NONE quote style uses HSQLDB dialect
     * @param style The quote style to use
     * @return This builder for method chaining
     */
    public DbContextBuilder withQuoteStyle(QuoteStyle style) {
        this.quoteStyle = style;
        return this;
    }

    /**
     * Sets whether database object names should be quoted.
     * If not set, uses the dialect's default behavior.
     * @param quoteObjects true to quote object names, false otherwise
     * @return This builder for method chaining
     */
    public DbContextBuilder quoteObjectNames(boolean quoteObjects) {
        this.quoteObjects = quoteObjects;
        return this;
    }
    
    /**
     * Sets whether database object names should be quoted.
     * If not set, uses the dialect's default behavior.
     * @param quoteObjects true to quote object names, false otherwise
     * @return This builder for method chaining
     * @deprecated Use {@link #quoteObjectNames(boolean)} instead
     */
    @Deprecated
    public DbContextBuilder withQuotedObjectNames(boolean quoteObjects) {
        return quoteObjectNames(quoteObjects);
    }

    /**
     * Sets a custom field mapping factory.
     * @param factory The factory to use for creating field mappings
     * @return This builder for method chaining
     */
    public DbContextBuilder withFieldMappingFactory(FieldMappingFactory factory) {
        this.fieldMappingFactory = factory;
        return this;
    }

    /**
     * Builds and returns a new DbContext with the configured settings.
     * @return A new DbContext instance
     */
    public DbContext build() {
        DbContext baseContext = DbContext.forDialect(dialect);
        
        // If no customizations needed, return the base context
        boolean hasCustomQuoteStyle = quoteStyle != null && quoteStyle != baseContext.getQuoteStyle();
        boolean hasCustomQuoteObjects = quoteObjects != null;
        boolean hasCustomFieldMapping = !(fieldMappingFactory instanceof SimpleFieldMapping);
        
        if (!hasCustomQuoteStyle && !hasCustomQuoteObjects && !hasCustomFieldMapping) {
            return baseContext;
        }
        
        return new CustomDbContext(baseContext, quoteStyle, quoteObjects, fieldMappingFactory);
    }

    /**
     * Functional interface for creating field mappings.
     */
    @FunctionalInterface
    public interface FieldMappingFactory {
        FieldMapping create(Field field);
    }

    /**
     * Custom DbContext that wraps a base context with customizations.
     */
    private static class CustomDbContext implements DbContext {
        private final DbContext base;
        private final QuoteStyle quoteStyle;
        private final Boolean quoteObjects;
        private final FieldMappingFactory fieldMappingFactory;

        private CustomDbContext(DbContext base, QuoteStyle quoteStyle, Boolean quoteObjects, FieldMappingFactory fieldMappingFactory) {
            this.base = base;
            this.quoteStyle = quoteStyle;
            this.quoteObjects = quoteObjects;
            this.fieldMappingFactory = fieldMappingFactory;
        }

        @Override
        public Dialect getDialect() {
            return base.getDialect();
        }

        @Override
        public QuoteStyle getQuoteStyle() {
            return quoteStyle != null ? quoteStyle : base.getQuoteStyle();
        }

        @Override
        public String quoteObjectNames(String... names) {
            if (quoteObjects != null && !quoteObjects) {
                // Don't quote object names
                StringBuilder ret = new StringBuilder();
                for (int i = 0; i < names.length; i++) {
                    if (i > 0) {
                        ret.append(".");
                    }
                    ret.append(names[i]);
                }
                return ret.toString();
            }
            // Use potentially overridden quote style
            QuoteStyle style = getQuoteStyle();
            // If quoteObjects is explicitly true but the style is NONE, use ANSI quoting
            // This handles the case where HSQLDB dialect is used with quoteObjectNames(true)
            if (quoteObjects != null && quoteObjects && style == QuoteStyle.NONE) {
                style = QuoteStyle.ANSI;
            }
            StringBuilder ret = new StringBuilder();
            for (int i = 0; i < names.length; i++) {
                if (i > 0) {
                    ret.append(".");
                }
                ret.append(style.quote(names[i]));
            }
            return ret.toString();
        }

        @Override
        public String quoteAlias(String alias) {
            return getQuoteStyle().quote(alias);
        }

        @Override
        public FieldMapping getFieldMapping(Field f) {
            return fieldMappingFactory.create(f);
        }

        @Override
        public String mapJavaTypeToSql(Field field) {
            return base.mapJavaTypeToSql(field);
        }

        @Override
        public String getAutoIncrementSyntax() {
            return base.getAutoIncrementSyntax();
        }

        @Override
        public String getCreateTableSuffix() {
            return base.getCreateTableSuffix();
        }

        @Override
        public int getStreamingFetchSize() {
            return base.getStreamingFetchSize();
        }
        
        @Override
        public String getForeignKeyColumnType() {
            return base.getForeignKeyColumnType();
        }
        
        @Override
        public int getDefaultVarcharLength() {
            return base.getDefaultVarcharLength();
        }
    }
} 