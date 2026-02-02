package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;
import org.pojoquery.schema.SchemaInfo;

/**
 * Integration test for SchemaGenerator's migration capabilities.
 * 
 * <p>Simulates a development workflow where entities evolve over time
 * and the SchemaGenerator automatically generates ALTER TABLE statements
 * to accommodate new fields.
 */
public class SchemaMigrationIT {

    // ========== Phase 1: Initial entity with basic fields ==========
    
    @Table("product")
    public static class ProductV1 {
        @Id
        Long id;
        
        String name;
    }
    
    // ========== Phase 2: Entity with additional fields ==========
    
    @Table("product")
    public static class ProductV2 {
        @Id
        Long id;
        
        String name;
        
        // New field added in V2
        String description;
        
        // Another new field
        Double price;
    }
    
    // ========== Phase 3: Entity with even more fields ==========
    
    @Table("product")
    public static class ProductV3 {
        @Id
        Long id;
        
        String name;
        
        String description;
        
        Double price;
        
        // New fields added in V3
        Integer stockQuantity;
        
        String category;
    }
    
    // ========== Phase 4: New OrderItem entity referencing Product ==========
    
    @Table("order_item")
    public static class OrderItem {
        @Id
        Long id;
        
        Long productId;
        
        Integer quantity;
        
        Double unitPrice;
    }
    
    // ========== Tests ==========
    
    /**
     * Tests the complete development workflow:
     * 1. Create initial table with ProductV1
     * 2. Insert some data
     * 3. "Evolve" entity to ProductV2 (add description, price)
     * 4. Call SchemaGenerator to generate ALTER TABLE statements
     * 5. Verify the new columns exist and can be used
     * 6. "Evolve" entity to ProductV3 (add stockQuantity, category)
     * 7. Generate more ALTER TABLE statements
     * 8. Verify all columns work correctly
     * 9. Add a new OrderItem entity that references Product
     * 10. Verify the new table is created and works with existing data
     */
    @Test
    public void testIncrementalSchemaMigration() {
        DataSource db = TestDatabaseProvider.getDataSource();
        
        // Phase 1: Create initial table
        SchemaGenerator.createTables(db, ProductV1.class);
        
        DB.withConnection(db, (Connection c) -> {
            // Insert initial data with V1 entity
            ProductV1 product1 = new ProductV1();
            product1.name = "Widget";
            PojoQuery.insert(c, product1);
            assertNotNull(product1.id, "ID should be auto-generated");
            
            // Verify we can query the data
            ProductV1 loaded = PojoQuery.build(ProductV1.class).findById(c, product1.id).orElseThrow();
            assertEquals("Widget", loaded.name);
        });
        
        // Phase 2: Migrate to V2 (add description and price columns)
        SchemaInfo schemaInfo = SchemaInfo.fromDataSource(db);
        List<String> migrationStatements = SchemaGenerator.generateMigrationStatements(
            schemaInfo, ProductV2.class);
        
        // Verify that ALTER TABLE statements are generated
        assertFalse(migrationStatements.isEmpty(), "Should generate migration statements for new columns");
        
        String migrationSql = String.join("\n", migrationStatements);
        System.out.println("V1 -> V2 Migration SQL:\n" + migrationSql);
        assertTrue(migrationSql.toLowerCase().contains("description"), "Should add description column");
        assertTrue(migrationSql.toLowerCase().contains("price"), "Should add price column");
        
        // Execute the migration
        DB.withConnection(db, (Connection c) -> {
            for (String ddl : migrationStatements) {
                DB.executeDDL(c, ddl);
            }
        });
        
        DB.withConnection(db, (Connection c) -> {
            // Now we can use the V2 entity with new fields
            ProductV2 product2 = new ProductV2();
            product2.name = "Gadget";
            product2.description = "A useful gadget";
            product2.price = 29.99;
            PojoQuery.insert(c, product2);
            
            // Query using V2 entity
            ProductV2 loaded = PojoQuery.build(ProductV2.class).findById(c, product2.id).orElseThrow();
            assertEquals("Gadget", loaded.name);
            assertEquals("A useful gadget", loaded.description);
            assertEquals(29.99, loaded.price, 0.001);
            
            // Update the original product with new fields
            ProductV2 original = PojoQuery.build(ProductV2.class).findById(c, 1L).orElseThrow();
            original.description = "The original widget";
            original.price = 19.99;
            PojoQuery.update(c, original);
            
            // Verify update
            ProductV2 updated = PojoQuery.build(ProductV2.class).findById(c, 1L).orElseThrow();
            assertEquals("Widget", updated.name);
            assertEquals("The original widget", updated.description);
            assertEquals(19.99, updated.price, 0.001);
        });
        
        // Phase 3: Migrate to V3 (add stockQuantity and category columns)
        SchemaInfo schemaInfo2 = SchemaInfo.fromDataSource(db);
        List<String> migrationStatements2 = SchemaGenerator.generateMigrationStatements(
            schemaInfo2, ProductV3.class);
        
        assertFalse(migrationStatements2.isEmpty(), "Should generate migration statements for V3 columns");
        
        String migrationSql2 = String.join("\n", migrationStatements2);
        System.out.println("V2 -> V3 Migration SQL:\n" + migrationSql2);
        assertTrue(migrationSql2.toLowerCase().contains("stockquantity"), "Should add stockQuantity column");
        assertTrue(migrationSql2.toLowerCase().contains("category"), "Should add category column");
        
        // Execute the second migration
        DB.withConnection(db, (Connection c) -> {
            for (String ddl : migrationStatements2) {
                DB.executeDDL(c, ddl);
            }
        });
        
        DB.withConnection(db, (Connection c) -> {
            // Use V3 entity with all fields
            ProductV3 product3 = new ProductV3();
            product3.name = "Super Widget";
            product3.description = "The best widget ever";
            product3.price = 99.99;
            product3.stockQuantity = 100;
            product3.category = "Electronics";
            PojoQuery.insert(c, product3);
            
            // Query all products using V3 entity
            List<ProductV3> allProducts = PojoQuery.build(ProductV3.class).execute(c);
            assertEquals(3, allProducts.size());
            
            // Find the new product
            ProductV3 superWidget = PojoQuery.build(ProductV3.class).findById(c, product3.id).orElseThrow();
            assertEquals("Super Widget", superWidget.name);
            assertEquals("The best widget ever", superWidget.description);
            assertEquals(99.99, superWidget.price, 0.001);
            assertEquals(Integer.valueOf(100), superWidget.stockQuantity);
            assertEquals("Electronics", superWidget.category);
        });
        
        // Phase 4: Add a completely new entity (OrderItem) that references Product
        SchemaInfo schemaInfo3 = SchemaInfo.fromDataSource(db);
        List<String> migrationStatements3 = SchemaGenerator.generateMigrationStatements(
            schemaInfo3, ProductV3.class, OrderItem.class);
        
        assertFalse(migrationStatements3.isEmpty(), "Should generate migration statements for OrderItem table");
        
        String migrationSql3 = String.join("\n", migrationStatements3);
        System.out.println("V3 + OrderItem Migration SQL:\n" + migrationSql3);
        assertTrue(migrationSql3.toLowerCase().contains("order_item"), "Should create order_item table");
        assertTrue(migrationSql3.toLowerCase().contains("productid"), "Should have productId column");
        assertTrue(migrationSql3.toLowerCase().contains("quantity"), "Should have quantity column");
        
        // Execute the third migration
        DB.withConnection(db, (Connection c) -> {
            for (String ddl : migrationStatements3) {
                DB.executeDDL(c, ddl);
            }
        });
        
        DB.withConnection(db, (Connection c) -> {
            // Create order items referencing existing products
            OrderItem item1 = new OrderItem();
            item1.productId = 1L; // Widget
            item1.quantity = 5;
            item1.unitPrice = 19.99;
            PojoQuery.insert(c, item1);
            
            OrderItem item2 = new OrderItem();
            item2.productId = 2L; // Gadget
            item2.quantity = 2;
            item2.unitPrice = 29.99;
            PojoQuery.insert(c, item2);
            
            OrderItem item3 = new OrderItem();
            item3.productId = 3L; // Super Widget
            item3.quantity = 1;
            item3.unitPrice = 99.99;
            PojoQuery.insert(c, item3);
            
            // Query all order items
            List<OrderItem> allItems = PojoQuery.build(OrderItem.class).execute(c);
            assertEquals(3, allItems.size());
            
            // Verify order item data
            OrderItem loadedItem1 = PojoQuery.build(OrderItem.class).findById(c, item1.id).orElseThrow();
            assertEquals(Long.valueOf(1L), loadedItem1.productId);
            assertEquals(Integer.valueOf(5), loadedItem1.quantity);
            assertEquals(19.99, loadedItem1.unitPrice, 0.001);
            
            // Verify we still have all products
            List<ProductV3> allProducts = PojoQuery.build(ProductV3.class).execute(c);
            assertEquals(3, allProducts.size());
        });
    }
    
    /**
     * Tests that calling generateMigrationStatements when no migration is needed
     * returns an empty list (idempotent behavior).
     */
    @Test
    public void testNoMigrationNeededWhenSchemaUpToDate() {
        DataSource db = TestDatabaseProvider.getDataSource();
        
        // Create table with all fields from the start
        SchemaGenerator.createTables(db, ProductV3.class);
        
        // Query schema and generate migration statements
        SchemaInfo schemaInfo = SchemaInfo.fromDataSource(db);
        List<String> migrationStatements = SchemaGenerator.generateMigrationStatements(
            schemaInfo, ProductV3.class);
        
        // Should be empty or contain no actual ALTER TABLE ADD COLUMN statements
        // (might contain FK constraints if any)
        for (String statement : migrationStatements) {
            assertFalse(statement.toLowerCase().contains("add column"),
                "Should not have ADD COLUMN when schema is up to date");
        }
    }
    
    /**
     * Tests migration with multiple related entities.
     */
    @Test
    public void testMigrationWithRelatedEntities() {
        DataSource db = TestDatabaseProvider.getDataSource();
        
        // Phase 1: Create initial tables
        SchemaGenerator.createTables(db, CategoryV1.class, ItemV1.class);
        
        DB.withConnection(db, (Connection c) -> {
            CategoryV1 cat = new CategoryV1();
            cat.name = "Books";
            PojoQuery.insert(c, cat);
            
            ItemV1 item = new ItemV1();
            item.title = "Java Programming";
            item.categoryId = cat.id;
            PojoQuery.insert(c, item);
        });
        
        // Phase 2: Evolve both entities
        SchemaInfo schemaInfo = SchemaInfo.fromDataSource(db);
        List<String> migrationStatements = SchemaGenerator.generateMigrationStatements(
            schemaInfo, CategoryV2.class, ItemV2.class);
        
        System.out.println("Multi-entity Migration SQL:\n" + String.join("\n", migrationStatements));
        
        // Execute migration
        DB.withConnection(db, (Connection c) -> {
            for (String ddl : migrationStatements) {
                DB.executeDDL(c, ddl);
            }
        });
        
        // Verify both entities work with new fields
        DB.withConnection(db, (Connection c) -> {
            CategoryV2 cat = PojoQuery.build(CategoryV2.class).findById(c, 1L).orElseThrow();
            cat.displayOrder = 1;
            PojoQuery.update(c, cat);
            
            ItemV2 item = PojoQuery.build(ItemV2.class).findById(c, 1L).orElseThrow();
            item.author = "Some Author";
            item.pageCount = 500;
            PojoQuery.update(c, item);
            
            // Verify
            CategoryV2 loadedCat = PojoQuery.build(CategoryV2.class).findById(c, 1L).orElseThrow();
            assertEquals(Integer.valueOf(1), loadedCat.displayOrder);
            
            ItemV2 loadedItem = PojoQuery.build(ItemV2.class).findById(c, 1L).orElseThrow();
            assertEquals("Some Author", loadedItem.author);
            assertEquals(Integer.valueOf(500), loadedItem.pageCount);
        });
    }
    
    // ========== Related entity test classes ==========
    
    @Table("category")
    public static class CategoryV1 {
        @Id
        Long id;
        
        String name;
    }
    
    @Table("category")
    public static class CategoryV2 {
        @Id
        Long id;
        
        String name;
        
        Integer displayOrder;
    }
    
    @Table("item")
    public static class ItemV1 {
        @Id
        Long id;
        
        String title;
        
        Long categoryId;
    }
    
    @Table("item")
    public static class ItemV2 {
        @Id
        Long id;
        
        String title;
        
        Long categoryId;
        
        String author;
        
        Integer pageCount;
    }
}
