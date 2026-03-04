package org.pojoquery.processor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.QueryTreeBuilder;

/**
 * Tests for QueryClassCodeGenerator.
 * 
 * <p>These tests verify that code generation works correctly at runtime
 * using ReflectionTypeModel, enabling fast iteration on code generation
 * logic without needing to compile generated sources.</p>
 */
public class TestQueryClassCodeGenerator {

    @Table("book")
    public static class Book {
        @Id
        public Long id;
        public String title;
        public Author author;
    }

    @Table("author")  
    public static class Author {
        @Id
        public Long id;
        public String name;
        public String email;
    }

    @Test
    public void testGeneratesClassDeclaration() throws Exception {
        QueryTree tree = QueryTreeBuilder.from(Book.class);
        QueryClassCodeGenerator generator = new QueryClassCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        assertTrue(code.contains("public class BookQuery extends TypedQuery<Book, Long, BookQuery>"),
            "Should generate class extending TypedQuery with correct type parameters");
    }

    @Test
    public void testGeneratesConditionBuilderFields() throws Exception {
        QueryTree tree = QueryTreeBuilder.from(Book.class);
        QueryClassCodeGenerator generator = new QueryClassCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        // Main entity fields
        assertTrue(code.contains("public final ComparableConditionBuilderField<Long, BookQueryStaticConditionChain> id"),
            "Should generate ComparableConditionBuilderField for id (Long)");
        assertTrue(code.contains("public final ComparableConditionBuilderField<String, BookQueryStaticConditionChain> title"),
            "Should generate ComparableConditionBuilderField for title (String)");
    }

    @Test
    public void testGeneratesNestedRelationshipFields() throws Exception {
        QueryTree tree = QueryTreeBuilder.from(Book.class);
        QueryClassCodeGenerator generator = new QueryClassCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        // Should have author relationship class
        assertTrue(code.contains("public final AuthorFields author = new AuthorFields()"),
            "Should generate author relationship field");
        assertTrue(code.contains("public class AuthorFields"),
            "Should generate AuthorFields inner class");
        
        // Author fields within the nested class
        assertTrue(code.contains("\"author\", \"id\""),
            "Should reference author.id field");
        assertTrue(code.contains("\"author\", \"name\""),
            "Should reference author.name field");
    }

    @Test
    public void testGeneratesProcessRowsMethod() throws Exception {
        QueryTree tree = QueryTreeBuilder.from(Book.class);
        QueryClassCodeGenerator generator = new QueryClassCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        assertTrue(code.contains("protected List<Book> processRows(List<Map<String, Object>> rows)"),
            "Should generate processRows method");
        assertTrue(code.contains("Map<Object, Book> bookById = new HashMap<>()"),
            "Should generate entity deduplication map");
        assertTrue(code.contains("Map<Object, Author> authorById = new HashMap<>()"),
            "Should generate related entity deduplication map");
    }

    @Test
    public void testGeneratesWhereBuilderClass() throws Exception {
        QueryTree tree = QueryTreeBuilder.from(Book.class);
        QueryClassCodeGenerator generator = new QueryClassCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        assertTrue(code.contains("public class BookQueryWhereBuilder"),
            "Should generate WhereBuilder class");
        assertTrue(code.contains("public BookQueryWhereBuilder where()"),
            "Should generate where() method");
    }

    @Test
    public void testGeneratesOrderByBuilder() throws Exception {
        QueryTree tree = QueryTreeBuilder.from(Book.class);
        QueryClassCodeGenerator generator = new QueryClassCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        assertTrue(code.contains("public class BookQueryOrderByBuilder"),
            "Should generate OrderByBuilder class");
        assertTrue(code.contains("public final OrderByField<BookQuery> title"),
            "Should generate OrderByField for title");
    }

    @Test
    public void testGeneratesStaticConditionChain() throws Exception {
        QueryTree tree = QueryTreeBuilder.from(Book.class);
        QueryClassCodeGenerator generator = new QueryClassCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        assertTrue(code.contains("public class BookQueryStaticConditionChain"),
            "Should generate StaticConditionChain class");
        assertTrue(code.contains("implements ConditionChain<BookQueryStaticConditionChain>, Supplier<SqlExpression>"),
            "StaticConditionChain should implement correct interfaces");
    }

    @Test
    public void testGeneratesSqlFunctionMethods() throws Exception {
        QueryTree tree = QueryTreeBuilder.from(Book.class);
        QueryClassCodeGenerator generator = new QueryClassCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        assertTrue(code.contains("public ChainableExpression<String, BookQueryStaticConditionChain> concat(Object... parts)"),
            "Should generate concat method");
        assertTrue(code.contains("public ChainableExpression<String, BookQueryStaticConditionChain> lower(Object part)"),
            "Should generate lower method");
        assertTrue(code.contains("public ChainableExpression<String, BookQueryStaticConditionChain> upper(Object part)"),
            "Should generate upper method");
    }

    @Test
    public void testGeneratesInitializeQuery() throws Exception {
        QueryTree tree = QueryTreeBuilder.from(Book.class);
        QueryClassCodeGenerator generator = new QueryClassCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        assertTrue(code.contains("protected void initializeQuery()"),
            "Should generate initializeQuery method");
        assertTrue(code.contains("query.setTable(null, \"book\")"),
            "Should set table in initializeQuery");
    }

    @Test 
    public void testExtractMetadata() {
        QueryTree tree = QueryTreeBuilder.from(Book.class);
        QueryClassCodeGenerator generator = new QueryClassCodeGenerator();
        
        QueryClassCodeGenerator.QueryMetadata metadata = generator.extractMetadata(tree);
        
        // Verify basic metadata
        assertTrue(metadata.tableName.equals("book"), "Table name should be 'book'");
        assertTrue(metadata.resultType.getSimpleName().equals("Book"), "Result type should be Book");
        
        // Verify fields are extracted
        assertTrue(metadata.fields.size() > 0, "Should have fields");
        boolean hasIdField = metadata.fields.stream()
            .anyMatch(f -> f.alias.equals("book.id"));
        assertTrue(hasIdField, "Should have book.id field");
        
        // Verify joins are extracted for author relationship
        assertTrue(metadata.joins.size() > 0, "Should have joins for author relationship");
    }

    @Test
    public void testCodeCompilationReadiness() throws Exception {
        // This test generates code and checks it has all required import statements
        // for the generated code to compile
        QueryTree tree = QueryTreeBuilder.from(Book.class);
        QueryClassCodeGenerator generator = new QueryClassCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        // Required imports
        assertTrue(code.contains("import java.sql.Connection;"), "Should import Connection");
        assertTrue(code.contains("import java.util.List;"), "Should import List");
        assertTrue(code.contains("import java.util.Map;"), "Should import Map");
        assertTrue(code.contains("import org.pojoquery.SqlExpression;"), "Should import SqlExpression");
        assertTrue(code.contains("import org.pojoquery.typedquery.TypedQuery;"), "Should import TypedQuery");
        assertTrue(code.contains("import org.pojoquery.typedquery.ComparableConditionBuilderField;"), 
            "Should import ComparableConditionBuilderField");
    }
}
