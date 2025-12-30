package org.pojoquery.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.pojoquery.DbContext;
import org.pojoquery.DbContext.QuoteStyle;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Lob;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;

public class TestSchemaGenerator {

    @Table("users")
    public static class User {
        @Id
        Long id;
        String username;
        @FieldName("email_address")
        String email;
        int age;
        boolean active;
        Date createdAt;
    }
    
    @Table("products")
    public static class Product {
        @Id
        Long id;
        String name;
        BigDecimal price;
        String description;
    }
    
    @Table("orders")
    public static class Order {
        @Id
        Long id;
        User customer;  // Foreign key reference
        Date orderDate;
        BigDecimal total;
    }
    
    @Table("order_items")
    public static class OrderItem {
        @Id Long orderId;  // Composite key part 1
        @Id Long productId; // Composite key part 2
        int quantity;
        BigDecimal unitPrice;
    }
    
    public static class Address {
        String street;
        String city;
        @FieldName("zip_code")
        String zipCode;
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
        List<Tag> tags;  // Many-to-many, should not create column
    }
    
    @Table("blog_posts")
    public static class BlogPost {
        @Id
        Long id;
        String title;
        @Lob
        String content;  // Should be CLOB
        String summary;  // Should be VARCHAR
    }
    
    @Table("tags")
    public static class Tag {
        @Id
        Long id;
        String name;
    }
    
    // Test entities for inferred foreign key from collection fields
    @Table("authors")
    public static class Author {
        @Id
        Long id;
        String name;
        Book[] books;  // One-to-many: should infer author_id in Book
    }
    
    @Table("books")
    public static class Book {
        @Id
        Long id;
        String title;
        Chapter[] chapters;  // One-to-many: should infer book_id in Chapter
    }
    
    @Table("chapters")
    public static class Chapter {
        @Id
        Long id;
        String title;
        int pageNumber;
    }
    
    // Test entities for inferred foreign key from single entity reference with @Link(linkfield=...)
    @Table("event")
    public static class Event {
        @Id
        Long eventID;
        String title;
        Date date;
    }
    
    public static class EventWithFestival extends Event {
        @Link(linkfield="festivalID")
        Festival festival;
    }
    
    // Subclass that doesn't add any FK fields - used to test collection FK inference
    public static class EventSubclass extends Event {
        String additionalInfo;
    }
    
    @Table("festival")
    public static class Festival {
        @Id
        Long festivalId;
        String name;
    }
    
    // Festival with collection of subclass entities - should infer FK in Event (root) table
    @Table("festival")
    public static class FestivalWithEvents extends Festival {
        @Link(foreignlinkfield="festivalID")
        List<EventSubclass> events;
    }

    @Test
    public void testSimpleEntity() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class);
        String sql = String.join("\n", sqlList);
        System.out.println(sql);
        
        assertTrue(sql.contains("CREATE TABLE"));
        assertTrue(sql.contains("`users`"));
        assertTrue(sql.contains("`id`"));
        assertTrue(sql.contains("`username`"));
        assertTrue(sql.contains("`email_address`"));  // Uses @FieldName
        assertTrue(sql.contains("PRIMARY KEY"));
        assertTrue(sql.contains("BIGINT"));
        assertTrue(sql.contains("AUTO_INCREMENT"));
    }
    
    @Test
    public void testCompositeKey() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(OrderItem.class);
        String sql = String.join("\n", sqlList);
        System.out.println(sql);
        
        assertTrue(sql.contains("`orderId`"));
        assertTrue(sql.contains("`productId`"));
        assertTrue(sql.contains("PRIMARY KEY (`orderId`, `productId`)"));
    }
    
    @Test
    public void testForeignKey() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Order.class);
        String sql = String.join("\n", sqlList);
        System.out.println(sql);
        
        assertTrue(sql.contains("`customer_id`"));  // Foreign key column
    }
    
    @Test
    public void testEmbedded() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Company.class);
        String sql = String.join("\n", sqlList);
        System.out.println(sql);
        
        assertTrue(sql.contains("`addr_street`"));
        assertTrue(sql.contains("`addr_city`"));
        assertTrue(sql.contains("`addr_zip_code`"));
    }
    
    @Test
    public void testManyToManyDoesNotCreateColumn() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Article.class);
        String sql = String.join("\n", sqlList);
        System.out.println(sql);
        
        assertTrue(sql.contains("`id`"));
        assertTrue(sql.contains("`title`"));
        // Should NOT contain a tags column since it's a collection
        assertTrue(!sql.contains("tags"));
    }
    
    @Test
    public void testLobAnnotation() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(BlogPost.class);
        String sql = String.join("\n", sqlList);
        System.out.println(sql);
        
        assertTrue(sql.contains("`id`"));
        assertTrue(sql.contains("`title`"));
        assertTrue(sql.contains("`content`"));
        assertTrue(sql.contains("`summary`"));
        // content should be CLOB, summary should be VARCHAR
        assertTrue(sql.contains("`content` CLOB"));
        assertTrue(sql.contains("`summary` VARCHAR"));
    }
    
    @Test
    public void testWithCustomDbContext() {
        // Test with a custom DbContext that has different varchar length
        DbContext customContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.MYSQL)
            .build();
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class, customContext);
        String sql = String.join("\n", sqlList);
        System.out.println(sql);
        
        assertTrue(sql.contains("CREATE TABLE"));
        assertTrue(sql.contains("VARCHAR(255)"));
    }
    
    @Test
    public void testPostgreSQLDialect() {
        DbContext postgresContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.ANSI)
            .build();
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class, postgresContext);
        String sql = String.join("\n", sqlList);
        System.out.println(sql);
        
        assertTrue(sql.contains("\"users\""));  // PostgreSQL uses double quotes
    }
    
    @Test
    public void testMultipleEntities() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(User.class, Product.class, Tag.class);
        String sql = String.join("\n", sqlList);
        System.out.println(sql);
        
        assertTrue(sql.contains("`users`"));
        assertTrue(sql.contains("`products`"));
        assertTrue(sql.contains("`tags`"));
    }
    
    @Test
    public void testGenerateCreateTableMatchesManual() {
        // Test against the examples.events classes
        List<String> eventSqlList = SchemaGenerator.generateCreateTableStatements(examples.events.Event.class);
        String eventSql = String.join("\n", eventSqlList);
        System.out.println("Event:");
        System.out.println(eventSql);
        
        List<String> personSqlList = SchemaGenerator.generateCreateTableStatements(examples.events.PersonRecord.class);
        String personSql = String.join("\n", personSqlList);
        System.out.println("\nPersonRecord:");
        System.out.println(personSql);
        
        List<String> emailSqlList = SchemaGenerator.generateCreateTableStatements(examples.events.EmailAddress.class);
        String emailSql = String.join("\n", emailSqlList);
        System.out.println("\nEmailAddress:");
        System.out.println(emailSql);
        
        // Verify structure matches
        assertTrue(eventSql.contains("`event`"));
        assertTrue(eventSql.contains("`id`"));
        assertTrue(eventSql.contains("`title`"));
        assertTrue(eventSql.contains("`date`"));
        
        assertTrue(personSql.contains("`person`"));
        assertTrue(personSql.contains("`firstname`"));
        assertTrue(personSql.contains("`lastname`"));
        
        assertTrue(emailSql.contains("`emailaddress`"));
        assertTrue(emailSql.contains("`person_id`"));
        assertTrue(emailSql.contains("`email`"));
    }
    
    // Inheritance test classes
    @Table("room")
    @SubClasses({BedRoom.class, Kitchen.class})
    public static class Room {
        @Id
        Long room_id;
        BigDecimal area;
    }
    
    @Table("bedroom")
    public static class BedRoom extends Room {
        Integer numberOfBeds;
    }
    
    @Table("kitchen")
    public static class Kitchen extends Room {
        Boolean hasDishWasher;
    }
    
    @Test
    public void testInheritanceWithSubClasses() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Room.class);
        String sql = String.join("\n", sqlList);
        System.out.println("Inheritance hierarchy:");
        System.out.println(sql);
        
        // Should generate table for Room
        assertTrue(sql.contains("`room`"));
        assertTrue(sql.contains("`room_id`"));
        assertTrue(sql.contains("`area`"));
        
        // Should also generate tables for subclasses (BedRoom and Kitchen)
        assertTrue(sql.contains("`bedroom`"));
        assertTrue(sql.contains("`numberOfBeds`"));
        
        assertTrue(sql.contains("`kitchen`"));
        assertTrue(sql.contains("`hasDishWasher`"));
    }
    
    @Test
    public void testSubclassTableHasCorrectStructure() {
        // Generate just the BedRoom table (without going through parent)
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(BedRoom.class);
        String sql = String.join("\n", sqlList);
        System.out.println("BedRoom only:");
        System.out.println(sql);
        
        // BedRoom generates BOTH its parent table (room) AND its own table (bedroom)
        // This is the table-per-subclass inheritance pattern
        assertTrue(sql.contains("`room`"));
        assertTrue(sql.contains("`room_id`"));
        assertTrue(sql.contains("`area`"));  // In the room table
        
        assertTrue(sql.contains("`bedroom`"));
        assertTrue(sql.contains("`numberOfBeds`"));
    }
    
    @Test
    public void testInheritanceHierarchyGeneratesAllTables() {
        // Test with our own inheritance classes
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Room.class);
        String sql = String.join("\n", sqlList);
        System.out.println("Full inheritance hierarchy:");
        System.out.println(sql);
        
        // Count occurrences - room should only appear once, not multiple times
        int roomCount = countOccurrences(sql, "CREATE TABLE `room`");
        int bedroomCount = countOccurrences(sql, "CREATE TABLE `bedroom`");
        int kitchenCount = countOccurrences(sql, "CREATE TABLE `kitchen`");
        
        // Each table should be created exactly once
        assertTrue("Room table should be created once", roomCount >= 1);
        assertTrue("Bedroom table should be created once", bedroomCount == 1);
        assertTrue("Kitchen table should be created once", kitchenCount == 1);
    }
    
    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
    
    @Test
    public void testGenerateCreateTableStatementsList() {
        List<String> statements = SchemaGenerator.generateCreateTableStatements(Room.class);
        System.out.println("List of statements:");
        for (int i = 0; i < statements.size(); i++) {
            System.out.println("Statement " + (i + 1) + ":");
            System.out.println(statements.get(i));
            System.out.println();
        }
        
        // Room with subclasses should generate 3 statements
        assertEquals("Should generate 3 statements (Room, BedRoom, Kitchen)", 3, statements.size());
        
        // First statement should be Room
        assertTrue(statements.get(0).contains("`room`"));
        
        // Subsequent statements should be subclasses
        assertTrue(statements.get(1).contains("`bedroom`") || statements.get(1).contains("`kitchen`"));
        assertTrue(statements.get(2).contains("`bedroom`") || statements.get(2).contains("`kitchen`"));
    }
    
    @Test
    public void testGenerateCreateTableStatementsListForMultipleEntities() {
        List<String> statements = SchemaGenerator.generateCreateTableStatements(User.class, Product.class, Tag.class);
        
        assertEquals("Should generate 3 statements", 3, statements.size());
        assertTrue(statements.get(0).contains("`users`"));
        assertTrue(statements.get(1).contains("`products`"));
        assertTrue(statements.get(2).contains("`tags`"));
    }
    
    @Test
    public void testGenerateCreateTableStatementsMatchesJoined() {
        // Verify that joining the list produces the same result as previous generateCreateTable
        List<String> statements = SchemaGenerator.generateCreateTableStatements(Room.class);
        String joined = String.join("\n\n", statements);
        // For consistency, just check that joined is not empty and contains expected table
        assertTrue(joined.contains("CREATE TABLE"));
        assertTrue(joined.contains("`room`"));
    }
    
    @Test
    public void testInferredForeignKeyFromCollection() {
        // When generating tables for Author and Book together,
        // Book should have authors_id inferred from Author.books (using table name)
        List<String> statements = SchemaGenerator.generateCreateTableStatements(Author.class, Book.class);
        System.out.println("Inferred FK test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        assertEquals("Should generate 2 statements", 2, statements.size());
        
        // Find the books table statement
        String booksTable = statements.stream()
            .filter(s -> s.contains("`books`"))
            .findFirst()
            .orElse("");
        
        // Book table should have authors_id column inferred from Author.books (table name + "_id")
        assertTrue("Book table should have authors_id column", booksTable.contains("`authors_id`"));
    }
    
    @Test
    public void testInferredForeignKeyChain() {
        // Test a chain: Author -> Book -> Chapter
        // Book should have authors_id, Chapter should have books_id
        List<String> statements = SchemaGenerator.generateCreateTableStatements(Author.class, Book.class, Chapter.class);
        System.out.println("Inferred FK chain test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        assertEquals("Should generate 3 statements", 3, statements.size());
        
        String booksTable = statements.stream()
            .filter(s -> s.contains("`books`"))
            .findFirst()
            .orElse("");
        String chaptersTable = statements.stream()
            .filter(s -> s.contains("`chapters`"))
            .findFirst()
            .orElse("");
        
        assertTrue("Book table should have authors_id column", booksTable.contains("`authors_id`"));
        assertTrue("Chapter table should have books_id column", chaptersTable.contains("`books_id`"));
    }
    
    @Test
    public void testNoInferredForeignKeyForManyToMany() {
        // Many-to-many relationships (with linktable) should NOT add inferred FK
        List<String> statements = SchemaGenerator.generateCreateTableStatements(Article.class, Tag.class);
        
        String tagsTable = statements.stream()
            .filter(s -> s.contains("`tags`"))
            .findFirst()
            .orElse("");
        
        // Tags should NOT have article_id since Article.tags uses a linktable
        assertTrue("Tags table should not have article_id for many-to-many", !tagsTable.contains("article_id"));
    }
    
    @Test
    public void testInferredForeignKeyFromCollectionWithInheritance() {
        // FestivalWithEvents has List<EventSubclass> events with @Link(foreignlinkfield="festivalID")
        // The FK should be inferred in Event (root table), not just EventSubclass
        List<String> statements = SchemaGenerator.generateCreateTableStatements(Event.class, FestivalWithEvents.class);
        System.out.println("Inferred FK from collection with inheritance test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        String eventTable = statements.stream()
            .filter(s -> s.contains("`event`"))
            .findFirst()
            .orElse("");
        
        // Event table should have festivalID column inferred from FestivalWithEvents.events
        assertTrue("Event table should have festivalID column", eventTable.contains("`festivalID`"));
    }
    
    @Test
    public void testInferredForeignKeyFromLinkField() {
        // When EventWithFestival has @Link(linkfield="festivalID") Festival festival,
        // the Event table (parent table) should have festivalID inferred
        List<String> statements = SchemaGenerator.generateCreateTableStatements(EventWithFestival.class, Festival.class);
        System.out.println("Inferred FK from linkfield test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        assertEquals("Should generate 2 statements", 2, statements.size());
        
        String eventTable = statements.stream()
            .filter(s -> s.contains("`event`"))
            .findFirst()
            .orElse("");
        
        // Event table should have festivalID column inferred from EventWithFestival.festival
        assertTrue("Event table should have festivalID column", eventTable.contains("`festivalID`"));
    }
    
    // ========== Migration Tests ==========
    
    @Test
    public void testMigrationWithNewTable() {
        // Create empty schema info (simulating no existing tables)
        SchemaInfo emptySchema = new SchemaInfo();
        
        List<String> statements = SchemaGenerator.generateMigrationStatements(emptySchema, User.class);
        System.out.println("Migration with new table:");
        for (String stmt : statements) {
            System.out.println(stmt);
        }
        
        assertEquals("Should generate 1 CREATE TABLE statement", 1, statements.size());
        assertTrue("Should be CREATE TABLE", statements.get(0).startsWith("CREATE TABLE"));
        assertTrue("Should contain users table", statements.get(0).contains("`users`"));
    }
    
    @Test
    public void testMigrationWithExistingTableMissingColumns() {
        // Create schema info with existing table but missing columns
        SchemaInfo schemaInfo = new SchemaInfo();
        SchemaInfo.TableInfo usersTable = new SchemaInfo.TableInfo("users", null);
        usersTable.addColumn("id");
        usersTable.addColumn("username");
        // Missing: email_address, age, active, createdAt
        schemaInfo.addTableForTesting(null, "users", usersTable);
        
        List<String> statements = SchemaGenerator.generateMigrationStatements(schemaInfo, User.class);
        System.out.println("Migration with existing table missing columns:");
        for (String stmt : statements) {
            System.out.println(stmt);
        }
        
        assertEquals("Should generate 1 ALTER TABLE statement", 1, statements.size());
        assertTrue("Should be ALTER TABLE", statements.get(0).startsWith("ALTER TABLE"));
        assertTrue("Should contain ADD COLUMN for email_address", statements.get(0).contains("`email_address`"));
        assertTrue("Should contain ADD COLUMN for age", statements.get(0).contains("`age`"));
        assertTrue("Should contain ADD COLUMN for active", statements.get(0).contains("`active`"));
        assertTrue("Should contain ADD COLUMN for createdAt", statements.get(0).contains("`createdAt`"));
    }
    
    @Test
    public void testMigrationWithCompleteTable() {
        // Create schema info with table that has all columns
        SchemaInfo schemaInfo = new SchemaInfo();
        SchemaInfo.TableInfo usersTable = new SchemaInfo.TableInfo("users", null);
        usersTable.addColumn("id");
        usersTable.addColumn("username");
        usersTable.addColumn("email_address");
        usersTable.addColumn("age");
        usersTable.addColumn("active");
        usersTable.addColumn("createdAt");
        schemaInfo.addTableForTesting(null, "users", usersTable);
        
        List<String> statements = SchemaGenerator.generateMigrationStatements(schemaInfo, User.class);
        System.out.println("Migration with complete table:");
        System.out.println("Statements: " + statements.size());
        
        assertEquals("Should generate 0 statements (table is up-to-date)", 0, statements.size());
    }
    
    @Test
    public void testMigrationMultipleTables() {
        // Test with multiple entities - one new, one existing with missing columns
        SchemaInfo schemaInfo = new SchemaInfo();
        SchemaInfo.TableInfo usersTable = new SchemaInfo.TableInfo("users", null);
        usersTable.addColumn("id");
        usersTable.addColumn("username");
        schemaInfo.addTableForTesting(null, "users", usersTable);
        // products table doesn't exist
        
        List<String> statements = SchemaGenerator.generateMigrationStatements(schemaInfo, User.class, Product.class);
        System.out.println("Migration with multiple tables:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        assertEquals("Should generate 2 statements", 2, statements.size());
        
        // First should be ALTER TABLE for users (adding missing columns)
        assertTrue("First should be ALTER TABLE", statements.get(0).startsWith("ALTER TABLE"));
        assertTrue("Should be for users table", statements.get(0).contains("`users`"));
        
        // Second should be CREATE TABLE for products
        assertTrue("Second should be CREATE TABLE", statements.get(1).startsWith("CREATE TABLE"));
        assertTrue("Should be for products table", statements.get(1).contains("`products`"));
    }
}
