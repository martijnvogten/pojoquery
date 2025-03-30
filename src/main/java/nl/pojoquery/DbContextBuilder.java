package nl.pojoquery;

import java.lang.reflect.Field;
import nl.pojoquery.pipeline.SimpleFieldMapping;
import nl.pojoquery.DbContext.QuoteStyle;

/**
 * A builder class that provides a fluent API for configuring a DbContext.
 * This allows for easy configuration of database context settings like quote style
 * and field mappings.
 */
public class DbContextBuilder {
    private QuoteStyle quoteStyle = QuoteStyle.MYSQL;
    private boolean quoteObjects = true;
    private FieldMappingFactory fieldMappingFactory = SimpleFieldMapping::new;

    /**
     * Sets the quote style for database identifiers.
     * @param style The quote style to use (ANSI or MySQL)
     * @return This builder for method chaining
     */
    public DbContextBuilder withQuoteStyle(QuoteStyle style) {
        this.quoteStyle = style;
        return this;
    }

    /**
     * Sets whether database object names should be quoted.
     * @param quoteObjects true to quote object names, false otherwise
     * @return This builder for method chaining
     */
    public DbContextBuilder withQuotedObjectNames(boolean quoteObjects) {
        this.quoteObjects = quoteObjects;
        return this;
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
        return new CustomDbContext(quoteStyle, quoteObjects, fieldMappingFactory);
    }

    /**
     * Functional interface for creating field mappings.
     */
    @FunctionalInterface
    public interface FieldMappingFactory {
        FieldMapping create(Field field);
    }

    /**
     * Custom DbContext implementation that uses the builder's configuration.
     */
    private static class CustomDbContext implements DbContext {
        private final QuoteStyle quoteStyle;
        private final boolean quoteObjects;
        private final FieldMappingFactory fieldMappingFactory;

        private CustomDbContext(QuoteStyle quoteStyle, boolean quoteObjects, FieldMappingFactory fieldMappingFactory) {
            this.quoteStyle = quoteStyle;
            this.quoteObjects = quoteObjects;
            this.fieldMappingFactory = fieldMappingFactory;
        }

        @Override
        public String quoteObjectNames(String... names) {
            StringBuilder ret = new StringBuilder();
            for (int i = 0; i < names.length; i++) {
                if (i > 0) {
                    ret.append(".");
                }
                ret.append(quoteObjects ? quoteStyle.quote(names[i]) : names[i]);
            }
            return ret.toString();
        }

        @Override
        public QuoteStyle getQuoteStyle() {
            return quoteStyle;
        }

        @Override
        public String quoteAlias(String alias) {
            return quoteStyle.quote(alias);
        }

        @Override
        public FieldMapping getFieldMapping(Field f) {
            return fieldMappingFactory.create(f);
        }
    }
} 