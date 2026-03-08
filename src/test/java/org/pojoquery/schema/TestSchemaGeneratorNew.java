package org.pojoquery.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    
    @Test
    public void compareWithOld() {
        // Compare outputs for User class
        List<String> oldSql = SchemaGeneratorOld.generateCreateTableStatements(User.class);
        List<String> newSql = SchemaGenerator.generateCreateTableStatements(User.class);
        
        System.out.println("=== Old SchemaGenerator (User) ===");
        oldSql.forEach(System.out::println);
        System.out.println("\n=== New SchemaGeneratorNew (User) ===");
        newSql.forEach(System.out::println);
        
        // Both should have same number of statements
        assertEquals(oldSql.size(), newSql.size(), "Should generate same number of statements for User");
    }
    
    @Test
    public void compareWithOldForeignKey() {
        // Compare outputs for Order class (has FK)
        List<String> oldSql = SchemaGeneratorOld.generateCreateTableStatements(Order.class);
        List<String> newSql = SchemaGenerator.generateCreateTableStatements(Order.class);
        
        System.out.println("=== Old SchemaGenerator (Order) ===");
        oldSql.forEach(System.out::println);
        System.out.println("\n=== New SchemaGeneratorNew (Order) ===");
        newSql.forEach(System.out::println);
        
        // Both should have same number of statements
        assertEquals(oldSql.size(), newSql.size(), "Should generate same number of statements for Order");
        
        // Both should have customer_id column
        String oldCreate = oldSql.get(0);
        String newCreate = newSql.get(0);
        assertTrue(oldCreate.contains("customer_id"), "Old should have customer_id");
        assertTrue(newCreate.contains("customer_id"), "New should have customer_id");
    }
    
    @Test
    public void compareWithOldEmbedded() {
        // Compare outputs for Company class (has embedded)
        List<String> oldSql = SchemaGeneratorOld.generateCreateTableStatements(Company.class);
        List<String> newSql = SchemaGenerator.generateCreateTableStatements(Company.class);
        
        System.out.println("=== Old SchemaGenerator (Company) ===");
        oldSql.forEach(System.out::println);
        System.out.println("\n=== New SchemaGeneratorNew (Company) ===");
        newSql.forEach(System.out::println);
        
        // Both should have same number of statements
        assertEquals(oldSql.size(), newSql.size(), "Should generate same number of statements for Company");
        
        // Both should have embedded columns
        String oldCreate = oldSql.get(0);
        String newCreate = newSql.get(0);
        assertTrue(oldCreate.contains("addr_street"), "Old should have addr_street");
        assertTrue(newCreate.contains("addr_street"), "New should have addr_street");
        assertTrue(oldCreate.contains("addr_city"), "Old should have addr_city");
        assertTrue(newCreate.contains("addr_city"), "New should have addr_city");
    }
    
    @Test
    public void compareWithOldManyToMany() {
        // Compare outputs for Article class (has many-to-many)
        List<String> oldSql = SchemaGeneratorOld.generateCreateTableStatements(Article.class);
        List<String> newSql = SchemaGenerator.generateCreateTableStatements(Article.class);
        
        System.out.println("=== Old SchemaGenerator (Article) ===");
        oldSql.forEach(System.out::println);
        System.out.println("\n=== New SchemaGeneratorNew (Article) ===");
        newSql.forEach(System.out::println);
        
        // Both should generate: CREATE TABLE articles, CREATE TABLE article_tag, 2x ALTER TABLE for FKs
        assertEquals(oldSql.size(), newSql.size(), "Should generate same number of statements for Article");
        
        // Both should have link table
        assertTrue(String.join("", oldSql).contains("article_tag"), "Old should have article_tag");
        assertTrue(String.join("", newSql).contains("article_tag"), "New should have article_tag");
    }
}
