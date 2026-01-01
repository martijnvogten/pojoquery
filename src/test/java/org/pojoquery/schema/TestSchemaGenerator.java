package org.pojoquery.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext;
import org.pojoquery.DbContext.QuoteStyle;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Lob;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.dialects.PostgresDbContext;

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
    
    // Test entity for self-referencing many-to-many relationship
    @Table("person")
    public static class Person {
        @Id
        Long id;
        String name;
        
        @Link(linktable = "person_friends")
        List<Person> friends;  // Self-referencing many-to-many
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
        // Article has @Link(linktable="article_tag") List<Tag> tags
        // Creates: Article table, link table, 2 ALTER TABLE for FK constraints
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Article.class);
        String sql = String.join("\n", sqlList);
        System.out.println(sql);
        
        assertTrue(sql.contains("`id`"));
        assertTrue(sql.contains("`title`"));
        // Should NOT contain a tags column in the articles table since it's a collection
        // But should generate a link table for many-to-many
        String articlesTable = sqlList.stream()
            .filter(s -> s.contains("CREATE TABLE") && s.contains("`articles`"))
            .findFirst()
            .orElse("");
        assertTrue(!articlesTable.contains("tags")); // No tags column in articles table
        
        // Should generate: Article CREATE + link table CREATE + 2 ALTER TABLE for FKs
        assertEquals(4, sqlList.size(), "Should generate 4 statements (Article + link table + 2 FK constraints)");
        String linkTable = sqlList.stream()
            .filter(s -> s.contains("CREATE TABLE") && s.contains("`article_tag`"))
            .findFirst()
            .orElse("");
        assertTrue(linkTable.contains("`article_tag`"), "Link table should be article_tag");
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
        // content should be LONGTEXT (MySQL's LOB type), summary should be VARCHAR
        assertTrue(sql.contains("`content` LONGTEXT"));
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
        int roomCount = countOccurrences(sql, "`room`");
        int bedroomCount = countOccurrences(sql, "`bedroom`");
        int kitchenCount = countOccurrences(sql, "`kitchen`");
        
        // Each table should be created exactly once (table name appears once in CREATE TABLE)
        assertTrue(roomCount == 1, "Room table should be created once");
        assertTrue(bedroomCount == 1, "Bedroom table should be created once");
        assertTrue(kitchenCount == 1, "Kitchen table should be created once");
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
        assertEquals(3, statements.size(), "Should generate 3 statements (Room, BedRoom, Kitchen)");
        
        // First statement should be Room
        assertTrue(statements.get(0).contains("`room`"));
        
        // Subsequent statements should be subclasses
        assertTrue(statements.get(1).contains("`bedroom`") || statements.get(1).contains("`kitchen`"));
        assertTrue(statements.get(2).contains("`bedroom`") || statements.get(2).contains("`kitchen`"));
    }
    
    @Test
    public void testGenerateCreateTableStatementsListForMultipleEntities() {
        List<String> statements = SchemaGenerator.generateCreateTableStatements(User.class, Product.class, Tag.class);
        
        assertEquals(3, statements.size(), "Should generate 3 statements");
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
        // Plus ALTER TABLE for FK constraint
        List<String> statements = SchemaGenerator.generateCreateTableStatements(Author.class, Book.class);
        System.out.println("Inferred FK test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        // 2 CREATE TABLEs + 1 ALTER TABLE for FK
        assertEquals(3, statements.size(), "Should generate 3 statements");
        
        // Find the books table statement
        String booksTable = statements.stream()
            .filter(s -> s.contains("CREATE TABLE") && s.contains("`books`"))
            .findFirst()
            .orElse("");
        
        // Book table should have authors_id column inferred from Author.books (table name + "_id")
        assertTrue(booksTable.contains("`authors_id`"), "Book table should have authors_id column");
    }
    
    @Test
    public void testInferredForeignKeyChain() {
        // Test a chain: Author -> Book -> Chapter
        // Book should have authors_id, Chapter should have books_id
        // Plus 2 ALTER TABLE for FK constraints
        List<String> statements = SchemaGenerator.generateCreateTableStatements(Author.class, Book.class, Chapter.class);
        System.out.println("Inferred FK chain test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        // 3 CREATE TABLEs + 2 ALTER TABLEs for FKs
        assertEquals(5, statements.size(), "Should generate 5 statements");
        
        String booksTable = statements.stream()
            .filter(s -> s.contains("CREATE TABLE") && s.contains("`books`"))
            .findFirst()
            .orElse("");
        String chaptersTable = statements.stream()
            .filter(s -> s.contains("CREATE TABLE") && s.contains("`chapters`"))
            .findFirst()
            .orElse("");
        
        assertTrue(booksTable.contains("`authors_id`"), "Book table should have authors_id column");
        assertTrue(chaptersTable.contains("`books_id`"), "Chapter table should have books_id column");
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
        assertTrue(!tagsTable.contains("article_id"), "Tags table should not have article_id for many-to-many");
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
            .filter(s -> s.contains("CREATE TABLE") && s.contains("`event`"))
            .findFirst()
            .orElse("");
        
        // Event table should have festivalID column inferred from FestivalWithEvents.events
        assertTrue(eventTable.contains("`festivalID`"), "Event table should have festivalID column");
    }
    
    @Test
    public void testInferredForeignKeyFromLinkField() {
        // When EventWithFestival has @Link(linkfield="festivalID") Festival festival,
        // the Event table (parent table) should have festivalID inferred
        // Plus ALTER TABLE for FK constraint
        List<String> statements = SchemaGenerator.generateCreateTableStatements(EventWithFestival.class, Festival.class);
        System.out.println("Inferred FK from linkfield test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        // 2 CREATE TABLEs + 1 ALTER TABLE for FK
        assertEquals(3, statements.size(), "Should generate 3 statements");
        
        String eventTable = statements.stream()
            .filter(s -> s.contains("CREATE TABLE") && s.contains("`event`"))
            .findFirst()
            .orElse("");
        
        // Event table should have festivalID column inferred from EventWithFestival.festival
        assertTrue(eventTable.contains("`festivalID`"), "Event table should have festivalID column");
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
        
        assertEquals(1, statements.size(), "Should generate 1 CREATE TABLE statement");
        assertTrue(statements.get(0).startsWith("CREATE TABLE"), "Should be CREATE TABLE");
        assertTrue(statements.get(0).contains("`users`"), "Should contain users table");
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
        
        assertEquals(1, statements.size(), "Should generate 1 ALTER TABLE statement");
        assertTrue(statements.get(0).startsWith("ALTER TABLE"), "Should be ALTER TABLE");
        assertTrue(statements.get(0).contains("`email_address`"), "Should contain ADD COLUMN for email_address");
        assertTrue(statements.get(0).contains("`age`"), "Should contain ADD COLUMN for age");
        assertTrue(statements.get(0).contains("`active`"), "Should contain ADD COLUMN for active");
        assertTrue(statements.get(0).contains("`createdAt`"), "Should contain ADD COLUMN for createdAt");
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
        
        assertEquals(0, statements.size(), "Should generate 0 statements (table is up-to-date)");
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
        
        assertEquals(2, statements.size(), "Should generate 2 statements");
        
        // First should be ALTER TABLE for users (adding missing columns)
        assertTrue(statements.get(0).startsWith("ALTER TABLE"), "First should be ALTER TABLE");
        assertTrue(statements.get(0).contains("`users`"), "Should be for users table");
        
        // Second should be CREATE TABLE for products
        assertTrue(statements.get(1).startsWith("CREATE TABLE"), "Second should be CREATE TABLE");
        assertTrue(statements.get(1).contains("`products`"), "Should be for products table");
    }
    
    @Test
    public void testPostgresForeignKeyUsesBigintNotBigserial() {
        // PostgreSQL FK columns should use BIGINT (non-auto-increment), not BIGSERIAL
        PostgresDbContext postgresContext = new PostgresDbContext();
        List<String> statements = SchemaGenerator.generateCreateTableStatements(postgresContext, EventWithFestival.class, Festival.class);
        
        System.out.println("PostgreSQL FK column type test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        String eventTable = statements.stream()
            .filter(s -> s.contains("\"event\""))
            .findFirst()
            .orElse("");
        
        // The FK column (festivalID) should use BIGINT, not BIGSERIAL
        assertTrue(eventTable.contains("\"festivalID\""), "Event table should have festivalID column");
        assertTrue(eventTable.contains("\"festivalID\" BIGINT"), "FK column festivalID should use BIGINT");
        assertFalse(eventTable.contains("\"festivalID\" BIGSERIAL"), "FK column festivalID should NOT use BIGSERIAL");
        
        // But the PK column (eventID) should use BIGSERIAL for auto-increment
        assertTrue(eventTable.contains("\"eventID\" BIGSERIAL"), "PK column eventID should use BIGSERIAL");
    }
    
    // Test entity for composite key with linked entities (like EventPersonLink)
    @Table("event_person")
    public static class EventPersonLink {
        @Id
        Long event_id;
        
        @Id
        Long person_id;
        
        // These linked entities would normally generate FK columns,
        // but they should NOT duplicate the existing @Id columns
        Event event;
        Festival person; // Using Festival as a stand-in for Person
    }
    
    @Test
    public void testCompositeKeyWithLinkedEntitiesNoDuplicateColumns() {
        // When an entity has both @Id fields and linked entities that would
        // generate FK columns with the same name, the columns should not be duplicated
        // Creates: event_person CREATE + 2 ALTER TABLEs for FKs
        List<String> statements = SchemaGenerator.generateCreateTableStatements(EventPersonLink.class);
        
        System.out.println("Composite key with linked entities test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        // 1 CREATE TABLE + 2 ALTER TABLEs for FK constraints
        assertEquals(3, statements.size(), "Should generate 3 statements");
        String sql = statements.stream()
            .filter(s -> s.contains("CREATE TABLE"))
            .findFirst()
            .orElse("");
        
        // Count occurrences of column definitions (with BIGINT type) - should appear only once each
        int personIdColCount = countOccurrences(sql, "`person_id` BIGINT");
        assertEquals(1, personIdColCount, "person_id column definition should appear exactly once");
        
        int eventIdColCount = countOccurrences(sql, "`event_id` BIGINT");
        assertEquals(1, eventIdColCount, "event_id column definition should appear exactly once");
        
        // Should have composite primary key
        assertTrue(sql.contains("PRIMARY KEY (`event_id`, `person_id`)"), "Should have composite PRIMARY KEY");
    }
    
    // ========== Foreign Key Constraint Tests ==========
    
    @Test
    public void testForeignKeyConstraintForLinkedEntity() {
        // Order has a 'customer' field that references User - should generate FK constraint
        // FK constraints are generated as separate ALTER TABLE statements for compatibility
        List<String> statements = SchemaGenerator.generateCreateTableStatements(Order.class, User.class);
        
        System.out.println("FK constraint for linked entity test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        String ordersTable = statements.stream()
            .filter(s -> s.contains("CREATE TABLE") && s.contains("`orders`"))
            .findFirst()
            .orElse("");
        
        // Should have FK column
        assertTrue(ordersTable.contains("`customer_id`"), "Orders should have customer_id column");
        
        // FK constraint should be in a separate ALTER TABLE statement
        String sql = String.join("\n", statements);
        assertTrue(sql.contains("ALTER TABLE `orders` ADD FOREIGN KEY (`customer_id`) REFERENCES `users`(`id`)"), 
            "Should have FK constraint referencing users (via ALTER TABLE)");
    }
    
    @Test
    public void testForeignKeyConstraintForInferredFK() {
        // Author has Book[] books - Book should have inferred authors_id column with FK constraint
        // FK constraints are generated as separate ALTER TABLE statements for compatibility
        List<String> statements = SchemaGenerator.generateCreateTableStatements(Author.class, Book.class);
        
        System.out.println("FK constraint for inferred FK test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        String booksTable = statements.stream()
            .filter(s -> s.contains("CREATE TABLE") && s.contains("`books`"))
            .findFirst()
            .orElse("");
        
        // Should have inferred FK column
        assertTrue(booksTable.contains("`authors_id`"), "Books should have authors_id column");
        
        // FK constraint should be in a separate ALTER TABLE statement
        String sql = String.join("\n", statements);
        assertTrue(sql.contains("ALTER TABLE `books` ADD FOREIGN KEY (`authors_id`) REFERENCES `authors`(`id`)"), 
            "Should have FK constraint referencing authors (via ALTER TABLE)");
    }
    
    @Test
    public void testLinkTableGeneration() {
        // Article has @Link(linktable="article_tag") List<Tag> tags
        // Should generate Article, Tag, article_tag link table, plus 2 ALTER TABLE for FK constraints
        List<String> statements = SchemaGenerator.generateCreateTableStatements(Article.class, Tag.class);
        
        System.out.println("Link table generation test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        // Should generate: Article CREATE, Tag CREATE, link table CREATE, 2 ALTER TABLE for FKs
        assertEquals(5, statements.size(), "Should generate 5 statements (Article + Tag + link table + 2 FK constraints)");
        
        String linkTable = statements.stream()
            .filter(s -> s.contains("CREATE TABLE") && s.contains("`article_tag`"))
            .findFirst()
            .orElse("");
        
        assertFalse(linkTable.isEmpty(), "Link table should be generated");
        assertTrue(linkTable.contains("`articles_id`"), "Link table should have articles_id column");
        assertTrue(linkTable.contains("`tags_id`"), "Link table should have tags_id column");
        assertTrue(linkTable.contains("PRIMARY KEY (`articles_id`, `tags_id`)"), 
            "Link table should have composite primary key");
        
        // FK constraints should be in separate ALTER TABLE statements
        String sql = String.join("\n", statements);
        assertTrue(sql.contains("ALTER TABLE `article_tag` ADD FOREIGN KEY (`articles_id`) REFERENCES `articles`(`id`)"), 
            "Should have FK constraint to articles (via ALTER TABLE)");
        assertTrue(sql.contains("ALTER TABLE `article_tag` ADD FOREIGN KEY (`tags_id`) REFERENCES `tags`(`id`)"), 
            "Should have FK constraint to tags (via ALTER TABLE)");
    }
    
    @Test
    public void testNoDuplicateFKConstraints() {
        // When EventWithFestival has @Link(linkfield="festivalID") Festival festival
        // and FestivalWithEvents has @Link(foreignlinkfield="festivalID") List<EventSubclass> events
        // There should be only ONE FK constraint for festivalID (via ALTER TABLE)
        List<String> statements = SchemaGenerator.generateCreateTableStatements(EventWithFestival.class, FestivalWithEvents.class);
        
        System.out.println("No duplicate FK constraints test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        // Count FK constraints for festivalID in all statements
        String sql = String.join("\n", statements);
        int fkCount = countOccurrences(sql, "FOREIGN KEY (`festivalID`)");
        assertEquals(1, fkCount, "Should have exactly 1 FK constraint for festivalID");
    }
    
    @Test
    public void testSelfReferencingManyToMany() {
        // Person has @Link(linktable="person_friends") List<Person> friends
        // This is a self-referencing many-to-many relationship
        // The link table should have two different column names to avoid name clash
        // Creates: Person table, person_friends link table, 2 ALTER TABLE for FKs
        List<String> statements = SchemaGenerator.generateCreateTableStatements(Person.class);
        
        System.out.println("Self-referencing many-to-many test:");
        for (String stmt : statements) {
            System.out.println(stmt);
            System.out.println();
        }
        
        // Should generate: Person CREATE, link table CREATE, 2 ALTER TABLE for FKs
        assertEquals(4, statements.size(), "Should generate 4 statements (Person + link table + 2 FK constraints)");
        
        String linkTable = statements.stream()
            .filter(s -> s.contains("CREATE TABLE") && s.contains("`person_friends`"))
            .findFirst()
            .orElse("");
        
        assertFalse(linkTable.isEmpty(), "Link table should be generated");
        
        // The link table should have two DIFFERENT columns (not both person_id)
        // It should use person_id for owner and friends_id for the field name
        assertTrue(linkTable.contains("`person_id`"), "Link table should have person_id column");
        assertTrue(linkTable.contains("`friends_id`"), "Link table should have friends_id column (from field name)");
        
        // Should have composite primary key with different columns
        assertTrue(linkTable.contains("PRIMARY KEY (`person_id`, `friends_id`)"), 
            "Link table should have composite primary key");
        
        // FK constraints should be in separate ALTER TABLE statements
        String sql = String.join("\n", statements);
        assertTrue(sql.contains("ALTER TABLE `person_friends` ADD FOREIGN KEY (`person_id`) REFERENCES `person`(`id`)"), 
            "Should have FK constraint to person for person_id (via ALTER TABLE)");
        assertTrue(sql.contains("ALTER TABLE `person_friends` ADD FOREIGN KEY (`friends_id`) REFERENCES `person`(`id`)"), 
            "Should have FK constraint for friends_id (via ALTER TABLE)");
    }
    
    // ========== Test entities for @Column(nullable, unique, length, precision, scale) ==========
    
    @Table("accounts")
    public static class Account {
        @Id
        Long id;
        
        @org.pojoquery.annotations.Column(nullable = false, unique = true)
        String username;
        
        @org.pojoquery.annotations.Column(length = 100, nullable = false, unique = true)
        String email;
        
        @org.pojoquery.annotations.Column(length = 50)
        String displayName;
        
        String bio;  // Nullable, default length
    }
    
    @Table("prices")
    public static class Price {
        @Id
        Long id;
        
        @org.pojoquery.annotations.Column(precision = 10, scale = 2)
        BigDecimal amount;
        
        BigDecimal defaultPrecision;  // Default precision 19,4
    }
    
    @Test
    public void testNotNullAnnotation() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Account.class);
        String sql = String.join("\n", sqlList);
        System.out.println("Column nullable=false test:\n" + sql);
        
        // username and email should have NOT NULL
        assertTrue(sql.contains("`username` VARCHAR(255) NOT NULL"), "username should be NOT NULL");
        assertTrue(sql.contains("`email` VARCHAR(100) NOT NULL"), "email should be NOT NULL");
        
        // displayName and bio should be nullable (no NOT NULL)
        assertTrue(sql.contains("`displayName` VARCHAR(50)") && 
            !sql.contains("`displayName` VARCHAR(50) NOT NULL"), "displayName should not have NOT NULL");
        assertTrue(sql.contains("`bio` VARCHAR(255)") &&
            !sql.contains("`bio` VARCHAR(255) NOT NULL"), "bio should be nullable (default VARCHAR)");
    }
    
    @Test
    public void testUniqueAnnotation() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Account.class);
        String sql = String.join("\n", sqlList);
        System.out.println("Column unique=true test:\n" + sql);
        
        // username and email should have UNIQUE
        assertTrue(sql.contains("`username` VARCHAR(255) NOT NULL UNIQUE"), "username should be UNIQUE");
        assertTrue(sql.contains("`email` VARCHAR(100) NOT NULL UNIQUE"), "email should be UNIQUE");
        
        // displayName should not have UNIQUE
        assertFalse(sql.contains("`displayName`") && sql.contains("displayName` VARCHAR(50) UNIQUE"), "displayName should not have UNIQUE");
    }
    
    @Test
    public void testColumnLengthAnnotation() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Account.class);
        String sql = String.join("\n", sqlList);
        System.out.println("Column length annotation test:\n" + sql);
        
        // email should be VARCHAR(100)
        assertTrue(sql.contains("`email` VARCHAR(100)"), "email should be VARCHAR(100)");
        
        // displayName should be VARCHAR(50)
        assertTrue(sql.contains("`displayName` VARCHAR(50)"), "displayName should be VARCHAR(50)");
        
        // bio should use default VARCHAR(255)
        assertTrue(sql.contains("`bio` VARCHAR(255)"), "bio should use default VARCHAR(255)");
    }
    
    @Test
    public void testColumnPrecisionScaleAnnotation() {
        List<String> sqlList = SchemaGenerator.generateCreateTableStatements(Price.class);
        String sql = String.join("\n", sqlList);
        System.out.println("Column precision/scale annotation test:\n" + sql);
        
        // amount should be DECIMAL(10,2)
        assertTrue(sql.contains("`amount` DECIMAL(10,2)"), "amount should be DECIMAL(10,2)");
        
        // defaultPrecision should use default DECIMAL(19,4)
        assertTrue(sql.contains("`defaultPrecision` DECIMAL(19,4)"), "defaultPrecision should use default DECIMAL(19,4)");
    }
}
