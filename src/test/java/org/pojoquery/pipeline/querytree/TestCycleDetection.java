package org.pojoquery.pipeline.querytree;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.internal.MappingException;

/**
 * Tests for cycle detection in the query tree pipeline.
 */
public class TestCycleDetection {

    // ========== Test entities with cycles ==========
    
    @Table("person_a")
    public static class PersonA {
        @Id
        public Long id;
        public String name;
        public AddressA address;  // PersonA → AddressA
    }
    
    @Table("address_a")
    public static class AddressA {
        @Id
        public Long id;
        public String street;
        public PersonA owner;  // AddressA → PersonA (cycle!)
    }
    
    // ========== Test entities with deeper cycle ==========
    
    @Table("node_a")
    public static class NodeA {
        @Id
        public Long id;
        public NodeB next;
    }
    
    @Table("node_b")
    public static class NodeB {
        @Id
        public Long id;
        public NodeC next;
    }
    
    @Table("node_c")
    public static class NodeC {
        @Id
        public Long id;
        public NodeA next;  // Cycle: A → B → C → A
    }
    
    // ========== Test entities with self-reference ==========
    
    @Table("employee")
    public static class Employee {
        @Id
        public Long id;
        public String name;
        public Employee manager;  // Self-reference cycle
    }
    
    // ========== Test entities WITHOUT cycles ==========
    
    @Table("author")
    public static class Author {
        @Id
        public Long id;
        public String name;
    }
    
    @Table("book")
    public static class Book {
        @Id
        public Long id;
        public String title;
        public Author author;  // No cycle - Author doesn't reference Book
    }
    
    @Table("chapter")
    public static class Chapter {
        @Id
        public Long id;
        public String title;
        public Book book;  // No cycle - chain ends at Author
    }
    
    // ========== Test entities with collection cycle ==========
    
    @Table("parent")
    public static class Parent {
        @Id
        public Long id;
        public String name;
        public List<Child> children;  // Parent → Child
    }
    
    @Table("child")
    public static class Child {
        @Id
        public Long id;
        public String name;
        public Parent parent;  // Child → Parent (cycle!)
    }
    
    // ========== Tests ==========
    
    @Test
    public void testDirectCycleIsDetected() {
        MappingException ex = assertThrows(MappingException.class, () -> {
            QueryTreeBuilder.from(PersonA.class);
        });
        
        assertTrue(ex.getMessage().contains("Cycle detected"));
        assertTrue(ex.getMessage().contains("PersonA"));
        assertTrue(ex.getMessage().contains("AddressA"));
    }
    
    @Test
    public void testDeepCycleIsDetected() {
        MappingException ex = assertThrows(MappingException.class, () -> {
            QueryTreeBuilder.from(NodeA.class);
        });
        
        assertTrue(ex.getMessage().contains("Cycle detected"));
        assertTrue(ex.getMessage().contains("NodeA"));
    }
    
    @Test
    public void testSelfReferenceIsDetected() {
        MappingException ex = assertThrows(MappingException.class, () -> {
            QueryTreeBuilder.from(Employee.class);
        });
        
        assertTrue(ex.getMessage().contains("Cycle detected"));
        assertTrue(ex.getMessage().contains("Employee"));
    }
    
    @Test
    public void testNonCyclicHierarchyIsAllowed() {
        // Should not throw
        assertDoesNotThrow(() -> {
            QueryTreeBuilder.from(Book.class);
        });
    }
    
    @Test
    public void testDeeperNonCyclicHierarchyIsAllowed() {
        // Chapter → Book → Author (no cycle)
        assertDoesNotThrow(() -> {
            QueryTreeBuilder.from(Chapter.class);
        });
    }
    
    @Test
    public void testCollectionCycleIsDetected() {
        MappingException ex = assertThrows(MappingException.class, () -> {
            QueryTreeBuilder.from(Parent.class);
        });
        
        assertTrue(ex.getMessage().contains("Cycle detected"));
        assertTrue(ex.getMessage().contains("Parent"));
        assertTrue(ex.getMessage().contains("Child"));
    }
}
