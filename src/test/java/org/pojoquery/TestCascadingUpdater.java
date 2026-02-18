package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.DbContext.QuoteStyle;
import org.pojoquery.annotations.Cascade;
import org.pojoquery.annotations.CascadeType;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.DbContextExtension;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Tests for the CascadingUpdater functionality.
 */
@ExtendWith(DbContextExtension.class)
public class TestCascadingUpdater {

    private DataSource dataSource;

    // === Test entities ===
    
    @Table("orders")
    public static class Order {
        @Id
        public Long id;
        
        public String orderNumber;
        
    }

	public static class OrderWithLineItems extends Order {
        @Cascade(CascadeType.ALL)
        public List<LineItem> lineItems;
        
        public OrderWithLineItems() {
			super();
            this.lineItems = new ArrayList<>();
        }
	}
    
    @Table("line_item")
    public static class LineItem {
        @Id
        public Long id;
        
		@FieldName("order_id")
        public Order order;
        
        public String productName;
        
        public Integer quantity;
        
        public LineItem() {}
        
        public LineItem(String productName, Integer quantity) {
            this.productName = productName;
            this.quantity = quantity;
        }
    }

    @BeforeEach
    void setup() {
        // Set up HSQLDB with ANSI quoting
        DbContext.setDefault(new DbContextBuilder()
                .dialect(Dialect.HSQLDB)
                .withQuoteStyle(QuoteStyle.ANSI)
                .quoteObjectNames(true)
                .build());

        // Create unique in-memory database
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:cascade_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;

        // Create tables
        SchemaGenerator.createTables(dataSource, Order.class, LineItem.class);
    }

    @Test
    public void testInsertWithCascade() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Create order with line items
            OrderWithLineItems order = new OrderWithLineItems();
            order.orderNumber = "ORD-001";
            order.lineItems.add(new LineItem("Widget", 5));
            order.lineItems.add(new LineItem("Gadget", 3));
            
            // Insert with cascade
            PojoQuery.insert(c, order);
            
            // Verify order was inserted
            assertNotNull(order.id);
            assertEquals("ORD-001", order.orderNumber);
            
            // Verify line items were inserted
            List<LineItem> items = PojoQuery.build(LineItem.class)
                    .addWhere("{order.id} = ?", order.id)
                    .execute(c);
            
            assertEquals(2, items.size());
            assertTrue(items.stream().anyMatch(i -> "Widget".equals(i.productName) && i.quantity == 5));
            assertTrue(items.stream().anyMatch(i -> "Gadget".equals(i.productName) && i.quantity == 3));
        });
    }

    @Test
    public void testUpdateWithCascadeAddNew() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Setup: Create initial order with one line item
            OrderWithLineItems order = new OrderWithLineItems();
            order.orderNumber = "ORD-002";
            order.lineItems.add(new LineItem("Original", 1));
            PojoQuery.insert(c, order);
            
            Long orderId = order.id;
            Long originalItemId = order.lineItems.get(0).id;
            
            // Reload order (simulating real-world scenario)
            Order baseOrder = PojoQuery.build(Order.class).findById(c, orderId).orElseThrow();
            order = new OrderWithLineItems();
            order.id = baseOrder.id;
            order.orderNumber = baseOrder.orderNumber;
            order.lineItems = new ArrayList<>(PojoQuery.build(LineItem.class)
                    .addWhere("{order.id} = ?", orderId)
                    .execute(c));
            
            // Add a new line item
            order.lineItems.add(new LineItem("NewItem", 10));
            
            // Update with cascade
            PojoQuery.update(c, order);
            
            // Verify both items exist
            List<LineItem> items = PojoQuery.build(LineItem.class)
                    .addWhere("{order.id} = ?", orderId)
                    .execute(c);
            
            assertEquals(2, items.size());
            assertTrue(items.stream().anyMatch(i -> i.id.equals(originalItemId)));
            assertTrue(items.stream().anyMatch(i -> "NewItem".equals(i.productName)));
        });
    }

    @Test
    public void testUpdateWithCascadeRemoveItem() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Setup: Create order with two line items
            OrderWithLineItems order = new OrderWithLineItems();
            order.orderNumber = "ORD-003";
            order.lineItems.add(new LineItem("Keep", 1));
            order.lineItems.add(new LineItem("Remove", 2));
            PojoQuery.insert(c, order);
            
            Long orderId = order.id;
            
            // Reload order
            Order baseOrder = PojoQuery.build(Order.class).findById(c, orderId).orElseThrow();
            order = new OrderWithLineItems();
            order.id = baseOrder.id;
            order.orderNumber = baseOrder.orderNumber;
            order.lineItems = new ArrayList<>(PojoQuery.build(LineItem.class)
                    .addWhere("{order.id} = ?", orderId)
                    .execute(c));
            
            assertEquals(2, order.lineItems.size());
            
            // Remove the "Remove" item
            order.lineItems.removeIf(i -> "Remove".equals(i.productName));
            assertEquals(1, order.lineItems.size());
            
            // Update with cascade
            PojoQuery.update(c, order);
            
            // Verify only "Keep" item remains (orphan removal)
            List<LineItem> items = PojoQuery.build(LineItem.class)
                    .addWhere("{order.id} = ?", orderId)
                    .execute(c);
            
            assertEquals(1, items.size());
            assertEquals("Keep", items.get(0).productName);
        });
    }

    @Test
    public void testUpdateWithCascadeModifyExisting() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Setup: Create order with one line item
            OrderWithLineItems order = new OrderWithLineItems();
            order.orderNumber = "ORD-004";
            order.lineItems.add(new LineItem("Product", 5));
            PojoQuery.insert(c, order);
            
            Long orderId = order.id;
            
            // Reload order
            Order baseOrder = PojoQuery.build(Order.class).findById(c, orderId).orElseThrow();
            order = new OrderWithLineItems();
            order.id = baseOrder.id;
            order.orderNumber = baseOrder.orderNumber;
            order.lineItems = new ArrayList<>(PojoQuery.build(LineItem.class)
                    .addWhere("{order.id} = ?", orderId)
                    .execute(c));
            
            // Modify the line item
            order.lineItems.get(0).quantity = 100;
            order.lineItems.get(0).productName = "ModifiedProduct";
            
            // Update with cascade
            PojoQuery.update(c, order);
            
            // Verify modification
            List<LineItem> items = PojoQuery.build(LineItem.class)
                    .addWhere("{order.id} = ?", orderId)
                    .execute(c);
            
            assertEquals(1, items.size());
            assertEquals("ModifiedProduct", items.get(0).productName);
            assertEquals(100, items.get(0).quantity);
        });
    }

    @Test
    public void testDeleteWithCascade() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Setup: Create order with line items
            OrderWithLineItems order = new OrderWithLineItems();
            order.orderNumber = "ORD-005";
            order.lineItems.add(new LineItem("Item1", 1));
            order.lineItems.add(new LineItem("Item2", 2));
            PojoQuery.insert(c, order);
            
            Long orderId = order.id;
            
            // Verify items exist
            List<LineItem> itemsBefore = PojoQuery.build(LineItem.class)
                    .addWhere("{order.id} = ?", orderId)
                    .execute(c);
            assertEquals(2, itemsBefore.size());
            
            // Delete with cascade
            PojoQuery.deleteCascading(c, order);
            
            // Verify order is deleted
            Optional<Order> deletedOrder = PojoQuery.build(Order.class).findById(c, orderId);
            assertTrue(deletedOrder.isEmpty(), "Order should be deleted");
            
            // Verify line items are deleted
            List<LineItem> itemsAfter = PojoQuery.build(LineItem.class)
                    .addWhere("{order.id} = ?", orderId)
                    .execute(c);
            assertEquals(0, itemsAfter.size());
        });
    }

    @Test
    public void testUpdateEmptyCollection() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Setup: Create order with line items
            OrderWithLineItems order = new OrderWithLineItems();
            order.orderNumber = "ORD-006";
            order.lineItems.add(new LineItem("Item1", 1));
            order.lineItems.add(new LineItem("Item2", 2));
            PojoQuery.insert(c, order);
            
            Long orderId = order.id;
            
            // Reload order
            Order baseOrder = PojoQuery.build(Order.class).findById(c, orderId).orElseThrow();
            order = new OrderWithLineItems();
            order.id = baseOrder.id;
            order.orderNumber = baseOrder.orderNumber;
            order.lineItems = new ArrayList<>();  // Empty collection
            
            // Update with cascade (should delete all items)
            PojoQuery.update(c, order);
            
            // Verify all items are deleted
            List<LineItem> items = PojoQuery.build(LineItem.class)
                    .addWhere("{order.id} = ?", orderId)
                    .execute(c);
            
            assertEquals(0, items.size());
        });
    }

    @Test
    public void testUpdateNullCollection() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Setup: Create order with line items
            OrderWithLineItems order = new OrderWithLineItems();
            order.orderNumber = "ORD-007";
            order.lineItems.add(new LineItem("Item1", 1));
            PojoQuery.insert(c, order);
            
            Long orderId = order.id;
            
            // Reload order
            Order baseOrder = PojoQuery.build(Order.class).findById(c, orderId).orElseThrow();
            order = new OrderWithLineItems();
            order.id = baseOrder.id;
            order.orderNumber = baseOrder.orderNumber;
            order.lineItems = null;  // Null collection
            
            // Update with cascade (should delete all items)
            PojoQuery.update(c, order);
            
            // Verify all items are deleted
            List<LineItem> items = PojoQuery.build(LineItem.class)
                    .addWhere("{order.id} = ?", orderId)
                    .execute(c);
            
            assertEquals(0, items.size());
        });
    }
    
    @Test
    public void testComplexScenario() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Setup: Create order with 3 items
            OrderWithLineItems order = new OrderWithLineItems();
            order.orderNumber = "ORD-008";
            order.lineItems.add(new LineItem("Keep", 1));
            order.lineItems.add(new LineItem("Modify", 2));
            order.lineItems.add(new LineItem("Delete", 3));
            PojoQuery.insert(c, order);
            
            Long orderId = order.id;
            
            // Reload order
            Order baseOrder = PojoQuery.build(Order.class).findById(c, orderId).orElseThrow();
            order = new OrderWithLineItems();
            order.id = baseOrder.id;
            order.orderNumber = baseOrder.orderNumber;
            order.lineItems = new ArrayList<>(PojoQuery.build(LineItem.class)
                    .addWhere("{order.id} = ?", orderId)
                    .addOrderBy("{line_item.id} ASC")
                    .execute(c));
            
            // Complex operations:
            // 1. Keep "Keep" unchanged
            // 2. Modify "Modify"
            // 3. Delete "Delete"
            // 4. Add new item
            
            order.lineItems.removeIf(i -> "Delete".equals(i.productName));
            order.lineItems.stream()
                    .filter(i -> "Modify".equals(i.productName))
                    .forEach(i -> { i.productName = "Modified"; i.quantity = 99; });
            order.lineItems.add(new LineItem("New", 10));
            
            // Update
            PojoQuery.update(c, order);
            
            // Verify results
            List<LineItem> items = PojoQuery.build(LineItem.class)
                    .addWhere("{order.id} = ?", orderId)
                    .execute(c);
            
            assertEquals(3, items.size());
            assertTrue(items.stream().anyMatch(i -> "Keep".equals(i.productName) && i.quantity == 1));
            assertTrue(items.stream().anyMatch(i -> "Modified".equals(i.productName) && i.quantity == 99));
            assertTrue(items.stream().anyMatch(i -> "New".equals(i.productName) && i.quantity == 10));
            assertFalse(items.stream().anyMatch(i -> "Delete".equals(i.productName)));
        });
    }
}
