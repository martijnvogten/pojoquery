package org.pojoquery.integrationtest;

import java.math.BigDecimal;
import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Column;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Integration tests for database constraints:
 * - NOT NULL constraints (@Column(nullable = false))
 * - UNIQUE constraints (@Column(unique = true))
 * - FOREIGN KEY constraints (@Link)
 * - Column length constraints (@Column(length = ...))
 */
@ExtendWith(DbContextExtension.class)
public class ConstraintsIT {

    // ========== Test Entities ==========
    
    @Table("account")
    public static class Account {
        @Id
        Long id;
        
        @Column(nullable = false, unique = true)
        String username;
        
        @Column(length = 100, nullable = false)
        String email;
        
        String bio;  // Nullable
    }
    
    @Table("category")
    public static class Category {
        @Id
        Long id;
        
        @Column(nullable = false)
        String name;
    }
    
    @Table("product")
    public static class Product {
        @Id
        Long id;
        
        @Column(nullable = false)
        String name;
        
        @Column(precision = 10, scale = 2)
        BigDecimal price;
        
        @Link(linkfield = "category_id")
        Category category;  // Nullable FK
    }
    
    // Entity with required FK using @Link(nullable = false)
    @Table("order_item")
    public static class OrderItem {
        @Id
        Long id;
        
        @Link(linkfield = "product_id", nullable = false)
        Product product;  // Required FK
        
        int quantity;
    }
    
    // ========== NOT NULL Constraint Tests ==========
    
    @Test
    public void testNotNullConstraintPreventsNullInsert() {
        DataSource db = initAccountDatabase();
        
        Account account = new Account();
        account.username = null;  // Violates NOT NULL
        account.email = "test@example.com";
        
        try {
            DB.withConnection(db, (Connection c) -> {
                PojoQuery.insert(c, account);
            });
            Assertions.fail("Should have thrown exception due to NOT NULL constraint on username");
        } catch (Exception e) {
            // Expected - NOT NULL constraint violated
            assertConstraintViolation(e, "username", "NOT NULL");
        }
    }
    
    @Test
    public void testNotNullConstraintAllowsNonNullInsert() {
        DataSource db = initAccountDatabase();
        
        DB.withConnection(db, (Connection c) -> {
            Account account = new Account();
            account.username = "johndoe";
            account.email = "john@example.com";
            account.bio = null;  // This is OK - bio is nullable
            
            PojoQuery.insert(c, account);
            Assertions.assertNotNull(account.id, "Account should be inserted with generated ID");
            
            // Verify it was saved correctly
            Account loaded = PojoQuery.build(Account.class).findById(c, account.id).orElseThrow();
            Assertions.assertEquals("johndoe", loaded.username);
            Assertions.assertEquals("john@example.com", loaded.email);
            Assertions.assertNull(loaded.bio);
        });
    }
    
    // ========== UNIQUE Constraint Tests ==========
    
    @Test
    public void testUniqueConstraintPreventsDuplicateInsert() {
        DataSource db = initAccountDatabase();
        
        // Insert first account
        DB.withConnection(db, (Connection c) -> {
            Account account1 = new Account();
            account1.username = "uniqueuser";
            account1.email = "unique1@example.com";
            PojoQuery.insert(c, account1);
        });
        
        // Try to insert second account with same username
        Account account2 = new Account();
        account2.username = "uniqueuser";  // Duplicate - should fail
        account2.email = "unique2@example.com";
        
        try {
            DB.withConnection(db, (Connection c) -> {
                PojoQuery.insert(c, account2);
            });
            Assertions.fail("Should have thrown exception due to UNIQUE constraint on username");
        } catch (Exception e) {
            // Expected - UNIQUE constraint violated
            assertConstraintViolation(e, "username", "UNIQUE");
        }
    }
    
    @Test
    public void testUniqueConstraintAllowsDifferentValues() {
        DataSource db = initAccountDatabase();
        
        DB.withConnection(db, (Connection c) -> {
            Account account1 = new Account();
            account1.username = "user1";
            account1.email = "user1@example.com";
            PojoQuery.insert(c, account1);
            
            Account account2 = new Account();
            account2.username = "user2";  // Different username - should succeed
            account2.email = "user2@example.com";
            PojoQuery.insert(c, account2);
            
            Assertions.assertNotNull(account1.id);
            Assertions.assertNotNull(account2.id);
            Assertions.assertNotEquals(account1.id, account2.id);
        });
    }
    
    // ========== FOREIGN KEY Constraint Tests ==========
    
    @Test
    public void testForeignKeyConstraintPreventsInvalidReference() {
        DataSource db = initProductDatabase();
        
        // Try to insert product with non-existent category
        Product product = new Product();
        product.name = "Test Product";
        product.price = new BigDecimal("19.99");
        product.category = new Category();
        product.category.id = 99999L;  // Non-existent category ID
        
        try {
            DB.withConnection(db, (Connection c) -> {
                PojoQuery.insert(c, product);
            });
            Assertions.fail("Should have thrown exception due to FOREIGN KEY constraint");
        } catch (Exception e) {
            // Expected - FOREIGN KEY constraint violated
            assertConstraintViolation(e, "category", "FOREIGN KEY");
        }
    }
    
    @Test
    public void testForeignKeyConstraintAllowsValidReference() {
        DataSource db = initProductDatabase();
        
        DB.withConnection(db, (Connection c) -> {
            // First insert a valid category
            Category category = new Category();
            category.name = "Electronics";
            PojoQuery.insert(c, category);
            Assertions.assertNotNull(category.id);
            
            // Now insert product with valid category reference
            Product product = new Product();
            product.name = "Laptop";
            product.price = new BigDecimal("999.99");
            product.category = category;
            
            PojoQuery.insert(c, product);
            Assertions.assertNotNull(product.id);
            
            // Verify it was saved with correct FK
            Product loaded = PojoQuery.build(Product.class).findById(c, product.id).orElseThrow();
            Assertions.assertEquals("Laptop", loaded.name);
            Assertions.assertNotNull(loaded.category);
            Assertions.assertEquals(category.id, loaded.category.id);
        });
    }
    
    @Test
    public void testForeignKeyAllowsNullReference() {
        DataSource db = initProductDatabase();
        
        DB.withConnection(db, (Connection c) -> {
            // Insert product without category (FK is nullable)
            Product product = new Product();
            product.name = "Uncategorized Product";
            product.price = new BigDecimal("9.99");
            product.category = null;
            
            PojoQuery.insert(c, product);
            Assertions.assertNotNull(product.id);
            
            // Verify it was saved
            Product loaded = PojoQuery.build(Product.class).findById(c, product.id).orElseThrow();
            Assertions.assertEquals("Uncategorized Product", loaded.name);
        });
    }
    
    @Test
    public void testRequiredForeignKeyPreventsNullReference() {
        DataSource db = initOrderItemDatabase();
        
        // Try to insert order item without product (FK is NOT NULL)
        OrderItem item = new OrderItem();
        item.product = null;  // Violates NOT NULL on FK
        item.quantity = 5;
        
        try {
            DB.withConnection(db, (Connection c) -> {
                PojoQuery.insert(c, item);
            });
            Assertions.fail("Should have thrown exception due to NOT NULL on FK");
        } catch (Exception e) {
            // Expected - FK NOT NULL constraint violated
            assertConstraintViolation(e, "product_id", "NOT NULL");
        }
    }
    
    @Test
    public void testRequiredForeignKeyAllowsValidReference() {
        DataSource db = initOrderItemDatabase();
        
        DB.withConnection(db, (Connection c) -> {
            // First insert a valid category and product
            Category category = new Category();
            category.name = "Electronics";
            PojoQuery.insert(c, category);
            
            Product product = new Product();
            product.name = "Laptop";
            product.price = new BigDecimal("999.99");
            product.category = category;
            PojoQuery.insert(c, product);
            
            // Now insert order item with valid product reference
            OrderItem item = new OrderItem();
            item.product = product;
            item.quantity = 2;
            
            PojoQuery.insert(c, item);
            Assertions.assertNotNull(item.id);
            
            // Verify it was saved
            OrderItem loaded = PojoQuery.build(OrderItem.class).findById(c, item.id).orElseThrow();
            Assertions.assertEquals(2, loaded.quantity);
            Assertions.assertNotNull(loaded.product);
        });
    }
    
    // ========== Column Definition Tests ==========
    
    @Test
    public void testDecimalPrecisionScale() {
        DataSource db = initProductDatabase();
        
        DB.withConnection(db, (Connection c) -> {
            Category category = new Category();
            category.name = "Test";
            PojoQuery.insert(c, category);
            
            Product product = new Product();
            product.name = "Precision Test";
            product.price = new BigDecimal("12345678.99");  // Within DECIMAL(10,2)
            product.category = category;
            
            PojoQuery.insert(c, product);
            
            Product loaded = PojoQuery.build(Product.class).findById(c, product.id).orElseThrow();
            // Compare with scale consideration
            Assertions.assertEquals(0, new BigDecimal("12345678.99").compareTo(loaded.price));
        });
    }
    
    // ========== Combined Constraint Tests ==========
    
    @Test
    public void testNotNullAndUniqueTogetherPreventNullDuplicate() {
        DataSource db = initAccountDatabase();
        
        // First insert a valid account
        DB.withConnection(db, (Connection c) -> {
            Account account1 = new Account();
            account1.username = "testuser";
            account1.email = "test@example.com";
            PojoQuery.insert(c, account1);
        });
        
        // Try to insert with null username (violates NOT NULL before UNIQUE is checked)
        Account account2 = new Account();
        account2.username = null;
        account2.email = "another@example.com";
        
        try {
            DB.withConnection(db, (Connection c) -> {
                PojoQuery.insert(c, account2);
            });
            Assertions.fail("Should have thrown exception due to NOT NULL constraint");
        } catch (Exception e) {
            // Expected
            assertConstraintViolation(e, "username", "NOT NULL");
        }
    }
    
    // ========== Helper Methods ==========
    
    private static DataSource initAccountDatabase() {
        DataSource db = TestDatabaseProvider.getDataSource();
        SchemaGenerator.createTables(db, Account.class);
        return db;
    }
    
    private static DataSource initProductDatabase() {
        DataSource db = TestDatabaseProvider.getDataSource();
        // Category must be created first for FK to work
        SchemaGenerator.createTables(db, Category.class, Product.class);
        return db;
    }
    
    private static DataSource initOrderItemDatabase() {
        DataSource db = TestDatabaseProvider.getDataSource();
        // Create in dependency order: Category -> Product -> OrderItem
        SchemaGenerator.createTables(db, Category.class, Product.class, OrderItem.class);
        return db;
    }
    
    /**
     * Helper to assert that an exception is related to a constraint violation.
     * Different databases report constraint violations differently, so we do
     * a best-effort check on the exception message.
     */
    private static void assertConstraintViolation(Exception e, String fieldHint, String constraintType) {
        String message = getFullExceptionMessage(e).toLowerCase();
        
        // Check that it's some kind of SQL/constraint exception
        boolean isConstraintViolation = 
            message.contains("constraint") ||
            message.contains("null") ||
            message.contains("unique") ||
            message.contains("duplicate") ||
            message.contains("foreign key") ||
            message.contains("referential integrity") ||
            message.contains("violation") ||
            message.contains("integrity");
        
        Assertions.assertTrue(
            isConstraintViolation,
            "Expected constraint violation for " + constraintType + " on " + fieldHint + 
            ", but got: " + e.getMessage()
        );
    }
    
    private static String getFullExceptionMessage(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        while (current != null) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(" ");
            }
            current = current.getCause();
        }
        return sb.toString();
    }
}
