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
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
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
        @Cascade
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

    // === Link table (many-to-many) tests ===

    @Table("article")
    public static class Article {
        @Id public Long id;
        public String title;
        @Link(linktable = "article_tag")
        public List<Tag> tags;
        
        public Article() { this.tags = new ArrayList<>(); }
        public Article(String title) { this(); this.title = title; }
    }

    @Table("tag")
    public static class Tag {
        @Id public Long id;
        public String name;
        
        public Tag() {}
        public Tag(String name) { this.name = name; }
    }

    @Test
    public void testLinkTableSync() {
        // Create tables
        SchemaGenerator.createTables(dataSource, Article.class, Tag.class);

        DB.withConnection(dataSource, (Connection c) -> {
            // Create tags first (reference data)
            Tag java = new Tag("java");
            Tag sql = new Tag("sql");
            Tag python = new Tag("python");
            PojoQuery.insert(c, java);
            PojoQuery.insert(c, sql);
            PojoQuery.insert(c, python);

            // Insert article with tags
            Article article = new Article("PojoQuery Tutorial");
            article.tags.add(java);
            article.tags.add(sql);
            PojoQuery.insert(c, article);

            // Verify link table has 2 rows
            Article loaded = PojoQuery.build(Article.class).findById(c, article.id).get();
            assertEquals(2, loaded.tags.size());

            // Update: remove sql, add python
            loaded.tags.removeIf(t -> "sql".equals(t.name));
            loaded.tags.add(python);
            PojoQuery.update(c, loaded);

            // Verify link table updated
            Article reloaded = PojoQuery.build(Article.class).findById(c, article.id).get();
            assertEquals(2, reloaded.tags.size());
            assertTrue(reloaded.tags.stream().anyMatch(t -> "java".equals(t.name)));
            assertTrue(reloaded.tags.stream().anyMatch(t -> "python".equals(t.name)));
            assertFalse(reloaded.tags.stream().anyMatch(t -> "sql".equals(t.name)));
        });
    }

    // === Link table with custom column names ===

    @Table("project")
    public static class Project {
        @Id public Long id;
        public String name;
        
        // Custom link table name and custom linkfield (parent FK column)
        @Link(linktable = "project_members", linkfield = "proj_id")
        public List<Member> members;
        
        public Project() { this.members = new ArrayList<>(); }
        public Project(String name) { this(); this.name = name; }
    }

    @Table("member")
    public static class Member {
        @Id public Long id;
        public String username;
        
        public Member() {}
        public Member(String username) { this.username = username; }
    }

    @Table("team")
    public static class Team {
        @Id public Long id;
        public String teamName;
        
        // Custom foreignlinkfield (foreign entity FK column)
        @Link(linktable = "team_developers", foreignlinkfield = "dev_id")
        public List<Developer> developers;
        
        public Team() { this.developers = new ArrayList<>(); }
        public Team(String teamName) { this(); this.teamName = teamName; }
    }

    @Table("developer")
    public static class Developer {
        @Id public Long id;
        public String devName;
        
        public Developer() {}
        public Developer(String devName) { this.devName = devName; }
    }

    @Table("account")
    public static class Account {
        @Id public Long id;
        public String accountName;
        
        // Custom both linkfield and foreignlinkfield
        @Link(linktable = "account_permission_map", linkfield = "acct_id", foreignlinkfield = "perm_id")
        public List<Permission> permissions;
        
        public Account() { this.permissions = new ArrayList<>(); }
        public Account(String accountName) { this(); this.accountName = accountName; }
    }

    @Table("permission")
    public static class Permission {
        @Id public Long id;
        public String permName;
        
        public Permission() {}
        public Permission(String permName) { this.permName = permName; }
    }

    public enum AccessLevel {
        READ, WRITE, ADMIN
    }

    @Table("resource")
    public static class Resource {
        @Id public Long id;
        public String resourceName;
        
        // fetchColumn for value collection (enum) - use default linkfield naming for query compatibility
        @Link(linktable = "resource_access", fetchColumn = "access_level")
        public List<AccessLevel> accessLevels;
        
        public Resource() { this.accessLevels = new ArrayList<>(); }
        public Resource(String resourceName) { this(); this.resourceName = resourceName; }
    }

    @Test
    public void testLinkTableWithCustomLinkField() {
        // Custom linkfield (parent FK column name)
        // SchemaGenerator should pick up the custom column name from @Link(linkfield="proj_id")
        SchemaGenerator.createTables(dataSource, Project.class, Member.class);
        
        DB.withConnection(dataSource, (Connection c) -> {
            Member alice = new Member("alice");
            Member bob = new Member("bob");
            PojoQuery.insert(c, alice);
            PojoQuery.insert(c, bob);
            
            Project project = new Project("Backend");
            project.members.add(alice);
            project.members.add(bob);
            PojoQuery.insert(c, project);
            
            // Verify the link table was populated correctly
            Project loaded = PojoQuery.build(Project.class).findById(c, project.id).get();
            assertEquals(2, loaded.members.size());
            assertTrue(loaded.members.stream().anyMatch(m -> "alice".equals(m.username)));
            assertTrue(loaded.members.stream().anyMatch(m -> "bob".equals(m.username)));
        });
    }

    @Test
    public void testLinkTableWithCustomForeignLinkField() {
        // Custom foreignlinkfield (foreign entity FK column name)
        // SchemaGenerator should pick up the custom column name from @Link(foreignlinkfield="dev_id")
        SchemaGenerator.createTables(dataSource, Team.class, Developer.class);
        
        DB.withConnection(dataSource, (Connection c) -> {
            Developer dev1 = new Developer("John");
            Developer dev2 = new Developer("Jane");
            PojoQuery.insert(c, dev1);
            PojoQuery.insert(c, dev2);
            
            Team team = new Team("Core");
            team.developers.add(dev1);
            team.developers.add(dev2);
            PojoQuery.insert(c, team);
            
            // Verify the link table was populated correctly
            Team loaded = PojoQuery.build(Team.class).findById(c, team.id).get();
            assertEquals(2, loaded.developers.size());
            assertTrue(loaded.developers.stream().anyMatch(d -> "John".equals(d.devName)));
            assertTrue(loaded.developers.stream().anyMatch(d -> "Jane".equals(d.devName)));
        });
    }

    @Test
    public void testLinkTableWithBothCustomFields() {
        // Both linkfield and foreignlinkfield customized
        // SchemaGenerator should pick up both custom column names
        SchemaGenerator.createTables(dataSource, Account.class, Permission.class);
        
        DB.withConnection(dataSource, (Connection c) -> {
            Permission read = new Permission("read");
            Permission write = new Permission("write");
            Permission delete = new Permission("delete");
            PojoQuery.insert(c, read);
            PojoQuery.insert(c, write);
            PojoQuery.insert(c, delete);
            
            Account account = new Account("admin");
            account.permissions.add(read);
            account.permissions.add(write);
            PojoQuery.insert(c, account);
            
            // Verify the link table was populated correctly
            Account loaded = PojoQuery.build(Account.class).findById(c, account.id).get();
            assertEquals(2, loaded.permissions.size());
            
            // Test update: add delete, remove read
            loaded.permissions.add(delete);
            loaded.permissions.removeIf(p -> "read".equals(p.permName));
            PojoQuery.update(c, loaded);
            
            Account reloaded = PojoQuery.build(Account.class).findById(c, account.id).get();
            assertEquals(2, reloaded.permissions.size());
            assertTrue(reloaded.permissions.stream().anyMatch(p -> "write".equals(p.permName)));
            assertTrue(reloaded.permissions.stream().anyMatch(p -> "delete".equals(p.permName)));
            assertFalse(reloaded.permissions.stream().anyMatch(p -> "read".equals(p.permName)));
        });
    }

    @Test
    public void testLinkTableWithFetchColumnForValueCollection() {
        // fetchColumn for enum/value collection
        // SchemaGenerator doesn't create link tables for value collections (no entity class),
        // so we create the link table manually
        SchemaGenerator.createTables(dataSource, Resource.class);
        DB.withConnection(dataSource, (Connection c) -> {
            // Create the link table for value collection manually (SchemaGenerator doesn't handle fetchColumn)
            DB.update(c, new SqlExpression("CREATE TABLE \"resource_access\" (\"resource_id\" BIGINT, \"access_level\" VARCHAR(50), PRIMARY KEY (\"resource_id\", \"access_level\"))"));
            
            Resource resource = new Resource("document.pdf");
            resource.accessLevels.add(AccessLevel.READ);
            resource.accessLevels.add(AccessLevel.WRITE);
            PojoQuery.insert(c, resource);
            
            Resource loaded = PojoQuery.build(Resource.class).findById(c, resource.id).get();
            assertEquals(2, loaded.accessLevels.size());
            assertTrue(loaded.accessLevels.contains(AccessLevel.READ));
            assertTrue(loaded.accessLevels.contains(AccessLevel.WRITE));
            
            // Update: add ADMIN, remove WRITE
            loaded.accessLevels.add(AccessLevel.ADMIN);
            loaded.accessLevels.remove(AccessLevel.WRITE);
            PojoQuery.update(c, loaded);
            
            Resource reloaded = PojoQuery.build(Resource.class).findById(c, resource.id).get();
            assertEquals(2, reloaded.accessLevels.size());
            assertTrue(reloaded.accessLevels.contains(AccessLevel.READ));
            assertTrue(reloaded.accessLevels.contains(AccessLevel.ADMIN));
            assertFalse(reloaded.accessLevels.contains(AccessLevel.WRITE));
        });
    }
}
