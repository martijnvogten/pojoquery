package org.pojoquery.integrationtest;

import java.math.BigDecimal;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;
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
            PojoQuery.insert(db, account);
            Assert.fail("Should have thrown exception due to NOT NULL constraint on username");
        } catch (Exception e) {
            // Expected - NOT NULL constraint violated
            assertConstraintViolation(e, "username", "NOT NULL");
        }
    }
    
    @Test
    public void testNotNullConstraintAllowsNonNullInsert() {
        DataSource db = initAccountDatabase();
        
        Account account = new Account();
        account.username = "johndoe";
        account.email = "john@example.com";
        account.bio = null;  // This is OK - bio is nullable
        
        PojoQuery.insert(db, account);
        Assert.assertNotNull("Account should be inserted with generated ID", account.id);
        
        // Verify it was saved correctly
        Account loaded = PojoQuery.build(Account.class).findById(db, account.id);
        Assert.assertEquals("johndoe", loaded.username);
        Assert.assertEquals("john@example.com", loaded.email);
        Assert.assertNull(loaded.bio);
    }
    
    // ========== UNIQUE Constraint Tests ==========
    
    @Test
    public void testUniqueConstraintPreventsDuplicateInsert() {
        DataSource db = initAccountDatabase();
        
        // Insert first account
        Account account1 = new Account();
        account1.username = "uniqueuser";
        account1.email = "unique1@example.com";
        PojoQuery.insert(db, account1);
        
        // Try to insert second account with same username
        Account account2 = new Account();
        account2.username = "uniqueuser";  // Duplicate - should fail
        account2.email = "unique2@example.com";
        
        try {
            PojoQuery.insert(db, account2);
            Assert.fail("Should have thrown exception due to UNIQUE constraint on username");
        } catch (Exception e) {
            // Expected - UNIQUE constraint violated
            assertConstraintViolation(e, "username", "UNIQUE");
        }
    }
    
    @Test
    public void testUniqueConstraintAllowsDifferentValues() {
        DataSource db = initAccountDatabase();
        
        Account account1 = new Account();
        account1.username = "user1";
        account1.email = "user1@example.com";
        PojoQuery.insert(db, account1);
        
        Account account2 = new Account();
        account2.username = "user2";  // Different username - should succeed
        account2.email = "user2@example.com";
        PojoQuery.insert(db, account2);
        
        Assert.assertNotNull(account1.id);
        Assert.assertNotNull(account2.id);
        Assert.assertNotEquals(account1.id, account2.id);
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
            PojoQuery.insert(db, product);
            Assert.fail("Should have thrown exception due to FOREIGN KEY constraint");
        } catch (Exception e) {
            // Expected - FOREIGN KEY constraint violated
            assertConstraintViolation(e, "category", "FOREIGN KEY");
        }
    }
    
    @Test
    public void testForeignKeyConstraintAllowsValidReference() {
        DataSource db = initProductDatabase();
        
        // First insert a valid category
        Category category = new Category();
        category.name = "Electronics";
        PojoQuery.insert(db, category);
        Assert.assertNotNull(category.id);
        
        // Now insert product with valid category reference
        Product product = new Product();
        product.name = "Laptop";
        product.price = new BigDecimal("999.99");
        product.category = category;
        
        PojoQuery.insert(db, product);
        Assert.assertNotNull(product.id);
        
        // Verify it was saved with correct FK
        Product loaded = PojoQuery.build(Product.class).findById(db, product.id);
        Assert.assertEquals("Laptop", loaded.name);
        Assert.assertNotNull(loaded.category);
        Assert.assertEquals(category.id, loaded.category.id);
    }
    
    @Test
    public void testForeignKeyAllowsNullReference() {
        DataSource db = initProductDatabase();
        
        // Insert product without category (FK is nullable)
        Product product = new Product();
        product.name = "Uncategorized Product";
        product.price = new BigDecimal("9.99");
        product.category = null;
        
        PojoQuery.insert(db, product);
        Assert.assertNotNull(product.id);
        
        // Verify it was saved
        Product loaded = PojoQuery.build(Product.class).findById(db, product.id);
        Assert.assertEquals("Uncategorized Product", loaded.name);
    }
    
    @Test
    public void testRequiredForeignKeyPreventsNullReference() {
        DataSource db = initOrderItemDatabase();
        
        // Try to insert order item without product (FK is NOT NULL)
        OrderItem item = new OrderItem();
        item.product = null;  // Violates NOT NULL on FK
        item.quantity = 5;
        
        try {
            PojoQuery.insert(db, item);
            Assert.fail("Should have thrown exception due to NOT NULL on FK");
        } catch (Exception e) {
            // Expected - FK NOT NULL constraint violated
            assertConstraintViolation(e, "product_id", "NOT NULL");
        }
    }
    
    @Test
    public void testRequiredForeignKeyAllowsValidReference() {
        DataSource db = initOrderItemDatabase();
        
        // First insert a valid category and product
        Category category = new Category();
        category.name = "Electronics";
        PojoQuery.insert(db, category);
        
        Product product = new Product();
        product.name = "Laptop";
        product.price = new BigDecimal("999.99");
        product.category = category;
        PojoQuery.insert(db, product);
        
        // Now insert order item with valid product reference
        OrderItem item = new OrderItem();
        item.product = product;
        item.quantity = 2;
        
        PojoQuery.insert(db, item);
        Assert.assertNotNull(item.id);
        
        // Verify it was saved
        OrderItem loaded = PojoQuery.build(OrderItem.class).findById(db, item.id);
        Assert.assertEquals(2, loaded.quantity);
        Assert.assertNotNull(loaded.product);
    }
    
    // ========== Column Definition Tests ==========
    
    @Test
    public void testDecimalPrecisionScale() {
        DataSource db = initProductDatabase();
        
        Category category = new Category();
        category.name = "Test";
        PojoQuery.insert(db, category);
        
        Product product = new Product();
        product.name = "Precision Test";
        product.price = new BigDecimal("12345678.99");  // Within DECIMAL(10,2)
        product.category = category;
        
        PojoQuery.insert(db, product);
        
        Product loaded = PojoQuery.build(Product.class).findById(db, product.id);
        // Compare with scale consideration
        Assert.assertEquals(0, new BigDecimal("12345678.99").compareTo(loaded.price));
    }
    
    // ========== Combined Constraint Tests ==========
    
    @Test
    public void testNotNullAndUniqueTogetherPreventNullDuplicate() {
        DataSource db = initAccountDatabase();
        
        // First insert a valid account
        Account account1 = new Account();
        account1.username = "testuser";
        account1.email = "test@example.com";
        PojoQuery.insert(db, account1);
        
        // Try to insert with null username (violates NOT NULL before UNIQUE is checked)
        Account account2 = new Account();
        account2.username = null;
        account2.email = "another@example.com";
        
        try {
            PojoQuery.insert(db, account2);
            Assert.fail("Should have thrown exception due to NOT NULL constraint");
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
        
        Assert.assertTrue(
            "Expected constraint violation for " + constraintType + " on " + fieldHint + 
            ", but got: " + e.getMessage(),
            isConstraintViolation
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
