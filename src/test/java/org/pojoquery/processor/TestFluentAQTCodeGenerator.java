package org.pojoquery.processor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;

/**
 * Tests for FluentAQTCodeGenerator.
 */
public class TestFluentAQTCodeGenerator {

    @Table("book")
    public static class Book {
        @Id
        public Long id;
        public String title;
        public Author author;
        public List<Review> reviews;  // One-to-many
        @Link(linktable = "book_category")
        public List<Category> categories;  // Many-to-many via join table
    }

    @Table("author")  
    public static class Author {
        @Id
        public Long id;
        public String name;
    }

    @Table("review")
    public static class Review {
        @Id
        public Long id;
        public String content;
        public Integer rating;
    }

    @Table("category")
    public static class Category {
        @Id
        public Long id;
        public String name;
    }

    @Test
    public void testGeneratesClassDeclaration() throws Exception {
        RootNode tree = AQTTransformer.buildQueryTreeForType(Book.class);
        FluentAQTCodeGenerator generator = new FluentAQTCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        System.out.println(code);
        
        assertTrue(code.contains("public class BookQuery extends FluentQuery<Book, BookQuery, BookQuery.Where, BookQuery.OrderBy, BookQuery.GroupBy> "),
            "Should generate correct class declaration");
    }

    @Test
    public void testGeneratesStaticOperatorFields() throws Exception {
        RootNode tree = AQTTransformer.buildQueryTreeForType(Book.class);
        FluentAQTCodeGenerator generator = new FluentAQTCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        assertTrue(code.contains("public final ConditionChainOperators<Terminator<BookQuery>, Long> id;"),
            "Should generate static operator field for id");
        assertTrue(code.contains("public final ConditionChainOperators<Terminator<BookQuery>, String> title;"),
            "Should generate static operator field for title");
    }

    @Test
    public void testGeneratesNestedStaticClass() throws Exception {
        RootNode tree = AQTTransformer.buildQueryTreeForType(Book.class);
        FluentAQTCodeGenerator generator = new FluentAQTCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        assertTrue(code.contains("public final StaticAuthor author;"),
            "Should generate static field for author entity");
        assertTrue(code.contains("public class StaticAuthor {"),
            "Should generate nested StaticAuthor class");
        assertTrue(code.contains("staticOp(\"author\", \"id\", Long.class)"),
            "Should generate staticOp for author.id");
        assertTrue(code.contains("staticOp(\"author\", \"name\", String.class)"),
            "Should generate staticOp for author.name");
    }

    @Test
    public void testGeneratesWhereClass() throws Exception {
        RootNode tree = AQTTransformer.buildQueryTreeForType(Book.class);
        FluentAQTCodeGenerator generator = new FluentAQTCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        assertTrue(code.contains("public class Where {"),
            "Should generate Where class");
        assertTrue(code.contains("chainOp(\"book\", \"id\", Long.class)"),
            "Should generate chainOp for id");
        assertTrue(code.contains("chainOp(\"book\", \"title\", String.class)"),
            "Should generate chainOp for title");
    }

    @Test
    public void testGeneratesNestedWhereClass() throws Exception {
        RootNode tree = AQTTransformer.buildQueryTreeForType(Book.class);
        FluentAQTCodeGenerator generator = new FluentAQTCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        assertTrue(code.contains("public class WhereAuthor {"),
            "Should generate nested WhereAuthor class");
        assertTrue(code.contains("chainOp(\"author\", \"id\", Long.class)"),
            "Should generate chainOp for author.id");
        assertTrue(code.contains("chainOp(\"author\", \"name\", String.class)"),
            "Should generate chainOp for author.name");
    }

    @Test
    public void testGeneratesConstructor() throws Exception {
        RootNode tree = AQTTransformer.buildQueryTreeForType(Book.class);
        FluentAQTCodeGenerator generator = new FluentAQTCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        assertTrue(code.contains("public BookQuery() {"),
            "Should generate constructor");
        assertTrue(code.contains("super(Book.class);"),
            "Should call super with correct arguments");
        assertTrue(code.contains("this.id = staticOp(\"book\", \"id\", Long.class);"),
            "Should initialize id in constructor");
        assertTrue(code.contains("this.author = new StaticAuthor();"),
            "Should initialize author in constructor");
    }

    @Test
    public void testGeneratesOneToManyCollection() throws Exception {
        RootNode tree = AQTTransformer.buildQueryTreeForType(Book.class);
        FluentAQTCodeGenerator generator = new FluentAQTCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        System.out.println(code);
        
        // Static class for reviews (one-to-many)
        assertTrue(code.contains("public final StaticReviews reviews;"),
            "Should generate static field for reviews collection");
        assertTrue(code.contains("public class StaticReviews {"),
            "Should generate nested StaticReviews class");
        assertTrue(code.contains("staticOp(\"reviews\", \"id\", Long.class)"),
            "Should generate staticOp for reviews.id");
        assertTrue(code.contains("staticOp(\"reviews\", \"content\", String.class)"),
            "Should generate staticOp for reviews.content");
        assertTrue(code.contains("staticOp(\"reviews\", \"rating\", Integer.class)"),
            "Should generate staticOp for reviews.rating");
        
        // Where class for reviews
        assertTrue(code.contains("public class WhereReviews {"),
            "Should generate nested WhereReviews class");
        assertTrue(code.contains("chainOp(\"reviews\", \"id\", Long.class)"),
            "Should generate chainOp for reviews.id");
    }

    @Test
    public void testGeneratesManyToManyJoinTable() throws Exception {
        RootNode tree = AQTTransformer.buildQueryTreeForType(Book.class);
        FluentAQTCodeGenerator generator = new FluentAQTCodeGenerator();
        
        StringWriter output = new StringWriter();
        generator.generate(tree, "test.pkg", "Book", "BookQuery", output);
        
        String code = output.toString();
        
        // Static class for categories (many-to-many via join table)
        assertTrue(code.contains("public final StaticCategories categories;"),
            "Should generate static field for categories collection");
        assertTrue(code.contains("public class StaticCategories {"),
            "Should generate nested StaticCategories class");
        assertTrue(code.contains("staticOp(\"categories\", \"id\", Long.class)"),
            "Should generate staticOp for categories.id");
        assertTrue(code.contains("staticOp(\"categories\", \"name\", String.class)"),
            "Should generate staticOp for categories.name");
        
        // Where class for categories
        assertTrue(code.contains("public class WhereCategories {"),
            "Should generate nested WhereCategories class");
        assertTrue(code.contains("chainOp(\"categories\", \"id\", Long.class)"),
            "Should generate chainOp for categories.id");
    }
}
