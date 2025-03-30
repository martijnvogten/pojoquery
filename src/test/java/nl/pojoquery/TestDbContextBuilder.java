package nl.pojoquery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.junit.Test;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.QueryBuilder;
import nl.pojoquery.pipeline.SimpleFieldMapping;
import nl.pojoquery.pipeline.SqlQuery;

public class TestDbContextBuilder {

    @Table("test")
    static class TestEntity {
        @Id
        public Long id;
        public String name;
    }

    @Test
    public void testDefaultConfiguration() {
        DbContext context = DbContext.builder().build();
        
        // Test default quote style (MySQL)
        assertEquals(DbContext.QuoteStyle.MYSQL, context.getQuoteStyle());
        
        // Test default object quoting (true)
        assertEquals("`test`.`name`", context.quoteObjectNames("test", "name"));
        
        // Test default alias quoting
        assertEquals("`alias`", context.quoteAlias("alias"));
        
        // Test default field mapping
        assertTrue(context.getFieldMapping(getField("id")) instanceof SimpleFieldMapping);
    }

    @Test
    public void testCustomQuoteStyle() {
        DbContext context = DbContext.builder()
            .withQuoteStyle(DbContext.QuoteStyle.ANSI)
            .build();
        
        assertEquals(DbContext.QuoteStyle.ANSI, context.getQuoteStyle());
        assertEquals("\"test\".\"name\"", context.quoteObjectNames("test", "name"));
        assertEquals("\"alias\"", context.quoteAlias("alias"));
    }

    @Test
    public void testDisableObjectQuoting() {
        DbContext context = DbContext.builder()
            .withQuotedObjectNames(false)
            .build();
        
        assertEquals("test.name", context.quoteObjectNames("test", "name"));
        // Aliases should still be quoted
        assertEquals("`alias`", context.quoteAlias("alias"));
    }

    @Test
    public void testCustomFieldMapping() {
        DbContext context = DbContext.builder()
            .withFieldMappingFactory(field -> new CustomFieldMapping(field))
            .build();
        
        assertTrue(context.getFieldMapping(getField("id")) instanceof CustomFieldMapping);
    }

    @Test
    public void testCombinedConfiguration() {
        DbContext context = DbContext.builder()
            .withQuoteStyle(DbContext.QuoteStyle.ANSI)
            .withQuotedObjectNames(false)
            .withFieldMappingFactory(field -> new CustomFieldMapping(field))
            .build();
        
        assertEquals(DbContext.QuoteStyle.ANSI, context.getQuoteStyle());
        assertEquals("test.name", context.quoteObjectNames("test", "name"));
        assertEquals("\"alias\"", context.quoteAlias("alias"));
        assertTrue(context.getFieldMapping(getField("id")) instanceof CustomFieldMapping);
    }

    @Test
    public void testQueryGeneration() {
        DbContext context = DbContext.builder()
            .withQuoteStyle(DbContext.QuoteStyle.ANSI)
            .build();
        
        SqlQuery<?> query = QueryBuilder.from(context, TestEntity.class).getQuery();
        String sql = query.toStatement().getSql();
        
        assertTrue(sql.contains("\"test\"")); // Table name should be quoted with ANSI style
        assertTrue(sql.contains("\"id\"")); // Column names should be quoted with ANSI style
    }

    private static Field getField(String fieldName) {
        try {
            return TestEntity.class.getField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    // Custom field mapping for testing
    private static class CustomFieldMapping implements FieldMapping {
        
        public CustomFieldMapping(Field field) {
        }
        
        @Override
        public void apply(Object target, Object value) {
            // Not needed for testing
        }
    }
} 