package org.pojoquery.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;

/**
 * Tests for SchemaGeneratorNew - comparing output with old SchemaGenerator.
 */
public class TestSchemaGeneratorNew {

    @Table("users")
    public static class User {
        @Id
        Long id;
        String username;
        @FieldName("email_address")
        String email;
    }
    
    @Table("orders")
    public static class Order {
        @Id
        Long id;
        User customer;  // Foreign key reference
        Date orderDate;
    }
    
    public static class Address {
        String street;
        String city;
    }
    
    @Table("companies")
    public static class Company {
        @Id
        Long id;
        String name;
        @Embedded(prefix = "addr_")
        Address address;
    }
    
    @Table("articles")
    public static class Article {
        @Id
        Long id;
        String title;
        
        @Link(linktable = "article_tag")
        List<Tag> tags;
    }
    
    @Table("tags")
    public static class Tag {
        @Id
        Long id;
        String name;
    }
    
    @Table("authors")
    public static class Author {
        @Id
        Long id;
        String name;
        Book[] books;  // One-to-many
    }
    
    @Table("books")
    public static class Book {
        @Id
        Long id;
        String title;
    }

    @Test
    public void testSimpleEntity() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class);
        String sql = String.join("\n", sqlList);
        System.out.println("=== User (new) ===");
        System.out.println(sql);
        
        assertTrue(sql.contains("CREATE TABLE"));
        assertTrue(sql.contains("users"));
        assertTrue(sql.contains("id"));
        assertTrue(sql.contains("username"));
        assertTrue(sql.contains("email_address"));
        assertTrue(sql.contains("PRIMARY KEY"));
    }
    
    @Test
    public void testForeignKey() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Order.class);
        String sql = String.join("\n", sqlList);
        System.out.println("=== Order (new) ===");
        System.out.println(sql);
        
        assertTrue(sql.contains("customer_id"));
    }
    
    @Test
    public void testEmbedded() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Company.class);
        String sql = String.join("\n", sqlList);
        System.out.println("=== Company (new) ===");
        System.out.println(sql);
        
        assertTrue(sql.contains("addr_street"));
        assertTrue(sql.contains("addr_city"));
    }
    
    @Test
    public void testManyToMany() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Article.class);
        String sql = String.join("\n", sqlList);
        System.out.println("=== Article with many-to-many (new) ===");
        System.out.println(sql);
        
        // Should have articles table
        assertTrue(sql.contains("articles"));
        // Should NOT have tags column in articles table
        String articlesTable = sqlList.stream()
            .filter(s -> s.contains("CREATE TABLE") && s.contains("articles"))
            .findFirst()
            .orElse("");
        assertFalse(articlesTable.contains("tags"));
        
        // Should have link table
        assertTrue(sql.contains("article_tag"));
    }
    
    @Test
    public void testOneToMany() {
        // When we generate Author, it should infer FK column (authors_id) in Book table
        // Note: FK column name is derived from table name, so "authors" -> "authors_id"
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Author.class, Book.class);
        String sql = String.join("\n", sqlList);
        System.out.println("=== Author/Book one-to-many (new) ===");
        System.out.println(sql);
        
        // Book table should have authors_id FK (table name is "authors")
        String booksTable = sqlList.stream()
            .filter(s -> s.contains("CREATE TABLE") && s.contains("books"))
            .findFirst()
            .orElse("");
        assertTrue(booksTable.contains("authors_id"), "Book table should have authors_id FK column: " + booksTable);
    }
    

}
