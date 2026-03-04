package org.pojoquery.integrationtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.QueryTreeBuilder;
import org.pojoquery.schema.QueryTreeSchemaGenerator;

/**
 * Integration tests for QueryTree-based schema generation and queries.
 * 
 * <p>Uses {@link QueryTreeSchemaGenerator} to create tables from QueryTree
 * rather than from POJO annotations directly.</p>
 */
public class QueryTreeBasicsIT {

    // ========== Entity Classes ==========
    
    @Table("person")
    public static class Person {
        @Id
        public Long id;
        public String firstName;
        public String lastName;
    }
    
    public static class Author extends Person {
        public String penName;
    }
    
    @Table("book")
    public static class Book {
        @Id
        public Long id;
        public String title;
        public Author author;
    }
    
    @Table("publisher")
    public static class Publisher {
        @Id
        public Long id;
        public String name;
    }
    
    @Table("category")
    public static class Category {
        @Id
        public Long id;
        public String name;
    }
    
    @Table("article")
    public static class Article {
        @Id
        public Long id;
        public String title;
        public String content;
        public Author author;
        public List<Category> categories;
    }
    
    public static class Address {
        public String street;
        public String city;
        public String zipCode;
    }
    
    @Table("customer")
    public static class Customer {
        @Id
        public Long id;
        public String name;
        @Embedded(prefix = "addr_")
        public Address address;
    }
    
    // ========== Basic Tests ==========
    
    @Test
    public void testSimplePersonQuery() {
        // QueryTree tree = QueryTreeBuilder.from(Person.class);
        DataSource db = initDatabaseFromClasses(Person.class);
        
        DB.withConnection(db, (Connection c) -> {
            // Insert a person
            Long personId = DB.insert(c, "person", Map.of(
                "firstName", "John",
                "lastName", "Doe"
            ));
            
            // Query using PojoQuery
            List<Person> persons = PojoQuery.build(Person.class).execute(c);
            
            assertEquals(1, persons.size());
            assertEquals("John", persons.get(0).firstName);
            assertEquals("Doe", persons.get(0).lastName);
            assertEquals(personId, persons.get(0).id);
        });
    }
    
    @Test
    public void testBookWithAuthorJoin() {
        DataSource db = initDatabaseFromClasses(Book.class, Author.class);
        
        DB.withConnection(db, (Connection c) -> {
            // Insert author
            Long authorId = DB.insert(c, "person", Map.of(
                "firstName", "Jane",
                "lastName", "Austen",
                "penName", "J. Austen"
            ));
            
            // Insert book
            DB.insert(c, "book", Map.of(
                "title", "Pride and Prejudice",
                "author_id", authorId
            ));
            
            // Query book with author
            List<Book> books = PojoQuery.build(Book.class).execute(c);
            
            assertEquals(1, books.size());
            Book book = books.get(0);
            assertEquals("Pride and Prejudice", book.title);
            assertNotNull(book.author);
            assertEquals("Jane", book.author.firstName);
            assertEquals("Austen", book.author.lastName);
        });
    }
    
    @Test
    public void testEmbeddedFields() {
        QueryTree tree = QueryTreeBuilder.from(Customer.class);
        
        // Debug: print tree and DDL
        System.out.println("Customer tree:");
        System.out.println(tree.root());
        List<String> ddl = QueryTreeSchemaGenerator.generateCreateTableStatements(tree, DbContext.getDefault());
        System.out.println("DDL:");
        for (String stmt : ddl) {
            System.out.println(stmt);
        }
        
        DataSource db = initDatabaseFromTree(tree);
        
        DB.withConnection(db, (Connection c) -> {
            // Insert customer with embedded address
            DB.insert(c, "customer", Map.of(
                "name", "Acme Corp",
                "addr_street", "123 Main St",
                "addr_city", "Springfield",
                "addr_zipCode", "12345"
            ));
            
            // Query customer
            List<Customer> customers = PojoQuery.build(Customer.class).execute(c);
            
            assertEquals(1, customers.size());
            Customer customer = customers.get(0);
            assertEquals("Acme Corp", customer.name);
            assertNotNull(customer.address);
            assertEquals("123 Main St", customer.address.street);
            assertEquals("Springfield", customer.address.city);
            assertEquals("12345", customer.address.zipCode);
        });
    }
    
    @Test
    public void testMultipleRecords() {
        QueryTree tree = QueryTreeBuilder.from(Person.class);
        DataSource db = initDatabaseFromTree(tree);
        
        DB.withConnection(db, (Connection c) -> {
            // Insert multiple persons
            DB.insert(c, "person", Map.of("firstName", "Alice", "lastName", "Smith"));
            DB.insert(c, "person", Map.of("firstName", "Bob", "lastName", "Jones"));
            DB.insert(c, "person", Map.of("firstName", "Charlie", "lastName", "Brown"));
            
            // Query all
            List<Person> persons = PojoQuery.build(Person.class)
                .addOrderBy("{person.firstName}")
                .execute(c);
            
            assertEquals(3, persons.size());
            assertEquals("Alice", persons.get(0).firstName);
            assertEquals("Bob", persons.get(1).firstName);
            assertEquals("Charlie", persons.get(2).firstName);
        });
    }
    
    @Test
    public void testQueryWithWhere() {
        QueryTree tree = QueryTreeBuilder.from(Person.class);
        DataSource db = initDatabaseFromTree(tree);
        
        DB.withConnection(db, (Connection c) -> {
            DB.insert(c, "person", Map.of("firstName", "Alice", "lastName", "Smith"));
            DB.insert(c, "person", Map.of("firstName", "Bob", "lastName", "Smith"));
            DB.insert(c, "person", Map.of("firstName", "Charlie", "lastName", "Brown"));
            
            // Query with WHERE clause
            List<Person> smiths = PojoQuery.build(Person.class)
                .addWhere("{person.lastName} = ?", "Smith")
                .addOrderBy("{person.firstName}")
                .execute(c);
            
            assertEquals(2, smiths.size());
            assertEquals("Alice", smiths.get(0).firstName);
            assertEquals("Bob", smiths.get(1).firstName);
        });
    }
    
    @Test
    public void testFindById() {
        QueryTree tree = QueryTreeBuilder.from(Person.class);
        DataSource db = initDatabaseFromTree(tree);
        
        DB.withConnection(db, (Connection c) -> {
            Long id1 = DB.insert(c, "person", Map.of("firstName", "Alice", "lastName", "Smith"));
            Long id2 = DB.insert(c, "person", Map.of("firstName", "Bob", "lastName", "Jones"));
            
            // Find by ID
            Person alice = PojoQuery.build(Person.class).findById(c, id1).orElseThrow();
            assertEquals("Alice", alice.firstName);
            
            Person bob = PojoQuery.build(Person.class).findById(c, id2).orElseThrow();
            assertEquals("Bob", bob.firstName);
        });
    }
    
    @Test
    public void testSchemaGenerationOutput() {
        QueryTree tree = QueryTreeBuilder.from(Book.class);
        
        // Print tree structure for debugging
        System.out.println("QueryTree structure:");
        System.out.println(tree.root());
        System.out.println();
        System.out.println("Children count: " + tree.root().children().size());
        for (QueryNode child : tree.root().children()) {
            System.out.println("Child type: " + child.getClass().getSimpleName());
            if (child instanceof JoinedNode joined) {
                System.out.println("  Table: " + joined.tableInfo().tableName());
            }
        }
        
        List<String> ddl = QueryTreeSchemaGenerator.generateCreateTableStatements(tree, DbContext.getDefault());
        
        // Print DDL for inspection
        System.out.println("Generated DDL count: " + ddl.size());
        for (String stmt : ddl) {
            System.out.println(stmt);
            System.out.println();
        }
        
        // Check actual count
        assertEquals(2, ddl.size(), "Should generate DDL for book and person tables");
    }
    
    // ========== Helper Methods ==========
    
    private static DataSource initDatabaseFromTree(QueryTree tree) {
        DataSource db = TestDatabaseProvider.getDataSource();
        QueryTreeSchemaGenerator.createTables(db, tree);
        return db;
    }
    
    private static DataSource initDatabaseFromClasses(Class<?>... classes) {
        DataSource db = TestDatabaseProvider.getDataSource();
        QueryTreeSchemaGenerator.createTables(db, Arrays.stream(classes).map(QueryTreeBuilder::from).toList());
        return db;
    }
}
