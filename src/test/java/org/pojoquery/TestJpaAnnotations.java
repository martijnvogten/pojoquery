package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.DbContext.QuoteStyle;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.integrationtest.UseDialect;
import org.pojoquery.pipeline.AQTSchemaGenerator;
import org.pojoquery.pipeline.querytree.QueryTreeBuilder;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.schema.SchemaGenerator;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.JakartaAnnotations;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Tests that JPA annotations (jakarta.persistence) work correctly with PojoQuery.
 * This verifies the AnnotationHelper correctly reads JPA annotations as alternatives
 * to PojoQuery's native annotations.
 */
@UseDialect(Dialect.HSQLDB)
public class TestJpaAnnotations {

    // ========== Test Entities with JPA Annotations ==========

    @Table(name = "jpa_user")
    public static class JpaUser {
        @Id
        @Column(name = "user_id")
        Long id;

        @Column(name = "user_name", length = 100, unique = true)
        String username;

        @Column(nullable = false)
        String email;

        @Lob
        String biography;

        @Transient
        String temporaryField;
    }

    @Table(name = "jpa_order", schema = "sales")
    public static class JpaOrder {
        @Id
        Long orderId;

        @Column(precision = 10, scale = 2)
        BigDecimal totalAmount;

        @JoinColumn(name = "customer_id")
        JpaUser customer;
    }

    // Simple order entity without schema - for database tests
    @Table(name = "jpa_simple_order")
    public static class JpaSimpleOrder {
        @Id
        @FieldName("order_id")
        Long id;

        @Column(precision = 10, scale = 2)
        BigDecimal totalAmount;

        @JoinColumn(name = "buyer_id")
        JpaUser buyer;
    }

    @Table(name = "jpa_address")
    public static class JpaAddress {
        String street;
        String city;
        String zipCode;
    }

    @Table(name = "jpa_company")
    public static class JpaCompany {
        @Id
        @Column(name = "company_id")
        Long id;

        String name;

        // JPA @Embedded with no prefix - true JPA semantics (no column prefix)
        @Embedded
        JpaAddress address;
    }

    // ========== Tests ==========

    @Test
    public void testJpaIdAnnotation() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        FieldModel idField = new ReflectionTypeModel(JpaUser.class).getDeclaredFields().stream()
            .filter(f -> f.getName().equals("id"))
            .findFirst().orElseThrow();

        FieldModel transformed = JakartaAnnotations.transformField(idField);
        transformed.getAnnotation(org.pojoquery.annotations.Id.class)
            .orElseThrow(() -> new AssertionError("Field 'id' should have PojoQuery @Id annotation mapped"));
        
        transformed.getAnnotation(FieldName.class).flatMap(map -> map.getStringValue())
            .filter(it -> it.equals("user_id"))
            .orElseThrow(() -> new AssertionError("Field 'id' should have PojoQuery @FieldName annotation with value 'user_id'"));
        
        transformed.getAnnotation(Id.class)
            .orElseThrow(() -> new AssertionError("Field 'id' should still have JPA @Id annotation"));

        
        ((TableNode)QueryTreeBuilder.from(JpaUser.class).root()).resolvedFields().stream()
            .filter(f -> f.columnName().equals("user_id"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("column name of id field should be 'user_id'"));

        List<String> statements = AQTSchemaGenerator.generateSchemaDDLFromClasses(dbContext, JpaUser.class);
        String sql = String.join("\n", statements);
        System.out.println(sql);

        // Should use @Id field as primary key
        assertTrue(sql.contains("PRIMARY KEY (user_id)"),
            "Should use JPA @Id field as primary key. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaTableAnnotation() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        TypeModel transformed = JakartaAnnotations.transformType(new ReflectionTypeModel(JpaUser.class));
        transformed.getAnnotation(org.pojoquery.annotations.Table.class)
            .orElseThrow(() -> new AssertionError("Type should have PojoQuery @Table annotation mapped"));


        List<String> statements = AQTSchemaGenerator.generateSchemaDDLFromClasses(dbContext, JpaUser.class);
        String sql = String.join("\n", statements);

        // Should use the JPA @Table name
        assertTrue(sql.contains("CREATE TABLE jpa_user"),
            "Should use table name from JPA @Table. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaColumnAnnotation() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        FieldModel usernameField = new ReflectionTypeModel(JpaUser.class).getDeclaredFields().stream()
            .filter(f -> f.getName().equals("username"))
            .map(JakartaAnnotations::transformField)
            .findFirst().orElseThrow();
        usernameField.getAnnotation(org.pojoquery.annotations.Column.class)
            .orElseThrow(() -> new AssertionError("Field 'username' should have PojoQuery @Column annotation mapped"));

        FieldModel emailField = new ReflectionTypeModel(JpaUser.class).getDeclaredFields().stream()
            .filter(f -> f.getName().equals("email"))
            .map(JakartaAnnotations::transformField)
            .findFirst().orElseThrow();
        Optional<Boolean> nullable =emailField.getAnnotation(org.pojoquery.annotations.Column.class)
            .orElseThrow(() -> new AssertionError("Field 'email' should have PojoQuery @Column annotation mapped"))
            .getBooleanAttribute("nullable");
        assertTrue(nullable.isPresent() && !nullable.get());

        List<String> statements = AQTSchemaGenerator.generateSchemaDDLFromClasses(dbContext, JpaUser.class);
        String sql = String.join("\n", statements);

        // Should use the JPA @Column(name=...) for column name
        assertTrue(sql.contains("user_name VARCHAR(100)"),
            "Should use column name and length from JPA @Column. Generated SQL:\n" + sql);

        // Should apply unique constraint from JPA @Column(unique=true)
        assertTrue(sql.contains("UNIQUE"),
            "Should apply unique constraint from JPA @Column. Generated SQL:\n" + sql);

        // Should apply NOT NULL from JPA @Column(nullable=false)
        assertTrue(sql.contains("email") && sql.contains("NOT NULL"),
            "Should apply NOT NULL from JPA @Column(nullable=false). Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaLobAnnotation() {
        DbContext dbContext = DbContext.builder()
            .dialect(Dialect.HSQLDB)
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        List<String> statements = AQTSchemaGenerator.generateSchemaDDLFromClasses(dbContext, JpaUser.class);
        String sql = String.join("\n", statements);

        // Should use CLOB type for @Lob String field

        assertTrue(sql.contains("biography CLOB"),
            "Should use CLOB type for JPA @Lob String field. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaTransientAnnotation() {
        DbContext dbContext = DbContext.builder()
            .dialect(Dialect.HSQLDB)
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        List<String> statements = AQTSchemaGenerator.generateSchemaDDLFromClasses(dbContext, JpaUser.class);
        String sql = String.join("\n", statements);

        // @Transient fields should be excluded
        assertTrue(!sql.contains("temporaryField"),
            "JPA @Transient field should be excluded. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaSchemaAnnotation() {
        DbContext dbContext = DbContext.builder()
            .dialect(Dialect.HSQLDB)
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        List<String> statements = AQTSchemaGenerator.generateSchemaDDLFromClasses(dbContext, JpaOrder.class);
        String sql = String.join("\n", statements);

        // Should include schema from JPA @Table(schema=...)
        assertTrue(sql.contains("sales.jpa_order"),
            "Should include schema from JPA @Table. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaColumnPrecisionScale() {
        DbContext dbContext = DbContext.builder()
            .dialect(Dialect.HSQLDB)
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        List<String> statements = AQTSchemaGenerator.generateSchemaDDLFromClasses(dbContext, JpaOrder.class);
        String sql = String.join("\n", statements);

        // Should use precision and scale from JPA @Column
        assertTrue(sql.contains("totalAmount DECIMAL(10,2)"),
            "Should use precision and scale from JPA @Column. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaJoinColumnAnnotation() {
        DbContext dbContext = DbContext.builder()
            .dialect(Dialect.HSQLDB)
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        // Test query generation with JPA @JoinColumn
        String sql = PojoQuery.build(dbContext, JpaOrder.class).toSql();

        // Should use the column name from JPA @JoinColumn
        assertTrue(sql.contains("customer_id"),
            "Should use column name from JPA @JoinColumn. Generated SQL:\n" + sql);
    }

    @Test
    public void testSchemaGeneratorJpaJoinColumnAnnotation() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        // Test SchemaGenerator with JPA @JoinColumn
        List<String> statements = AQTSchemaGenerator.generateSchemaDDLFromClasses(dbContext, JpaOrder.class);
        String sql = String.join("\n", statements);

        // Should use the column name from JPA @JoinColumn(name="customer_id")
        assertTrue(sql.contains("customer_id"),
            "SchemaGenerator should use column name from JPA @JoinColumn. Generated SQL:\n" + sql);
        // Should NOT use default fieldName_id
        assertTrue(!sql.contains("customer_id_id") && !sql.contains("customer BIGINT"),
            "SchemaGenerator should not use default naming when @JoinColumn is present. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaEmbeddedAnnotation() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        List<String> statements = AQTSchemaGenerator.generateSchemaDDLFromClasses(dbContext, JpaCompany.class);
        String sql = String.join("\n", statements);

        // JPA @Embedded uses NO prefix - fields use their original names (true JPA semantics)
        assertTrue(sql.contains("street VARCHAR"),
            "JPA @Embedded should use original field names (no prefix). Generated SQL:\n" + sql);
        assertTrue(sql.contains("city VARCHAR"),
            "JPA @Embedded should use original field names (no prefix). Generated SQL:\n" + sql);
        assertTrue(sql.contains("zipCode VARCHAR"),
            "JPA @Embedded should use original field names (no prefix). Generated SQL:\n" + sql);
    }

    @Test
    public void testQueryGenerationWithJpaAnnotations() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        String sql = PojoQuery.build(dbContext, JpaUser.class).toSql();

        // Query should use the JPA @Table name (with or without quoting)
        assertTrue(sql.contains("jpa_user"),
            "Query should use table name from JPA @Table. Generated SQL:\n" + sql);

        // Query should use the JPA @Column(name=...) for column name
        assertTrue(sql.contains("user_name"),
            "Query should use column name from JPA @Column. Generated SQL:\n" + sql);

        // Transient field should be excluded
        assertTrue(!sql.contains("temporaryField"),
            "JPA @Transient field should be excluded from query. Generated SQL:\n" + sql);
    }

    @Test
    public void testQueryJpaLobAnnotation() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        String sql = PojoQuery.build(dbContext, JpaUser.class).toSql();

        // @Lob field should be included in query selection
        assertTrue(sql.contains("biography"),
            "JPA @Lob field should be included in query. Generated SQL:\n" + sql);
    }

    @Test
    public void testQueryJpaEmbeddedAnnotation() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        // For embedded, the fields are inlined - tested via SchemaGenerator
        // QueryBuilder treats @Embedded fields as part of the parent table
        List<String> statements = AQTSchemaGenerator.generateSchemaDDLFromClasses(dbContext, JpaCompany.class);
        String sql = String.join("\n", statements);

        // JPA @Embedded fields should be in parent table with NO prefix
        assertTrue(sql.contains("jpa_company") && sql.contains("street VARCHAR"),
            "JPA @Embedded fields should be in parent table without prefix. Generated SQL:\n" + sql);
    }

    @Test
    public void testQueryJpaJoinColumnWithRelation() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        String sql = PojoQuery.build(dbContext, JpaOrder.class).toSql();

        // Query should join using @JoinColumn name
        assertTrue(sql.contains("customer_id"),
            "Query should use JPA @JoinColumn name for join. Generated SQL:\n" + sql);

        // Related entity fields should be included
        assertTrue(sql.contains("customer") && sql.contains("user_name"),
            "Query should include related entity fields. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaEmbeddedNoPrefix() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        List<String> statements = AQTSchemaGenerator.generateSchemaDDLFromClasses(dbContext, JpaCompany.class);
        String sql = String.join("\n", statements);

        // JPA @Embedded should NOT add any prefix - true JPA semantics
        assertTrue(sql.contains("street VARCHAR") && !sql.contains("address_street") && !sql.contains("addressstreet"),
            "JPA @Embedded should use original field names without any prefix. Generated SQL:\n" + sql);
        assertTrue(sql.contains("city VARCHAR"),
            "JPA @Embedded should use original field names without any prefix. Generated SQL:\n" + sql);
        assertTrue(sql.contains("zipCode VARCHAR"),
            "JPA @Embedded should use original field names without any prefix. Generated SQL:\n" + sql);
    }

    // ========== Insert/Update Tests with Database ==========

    private static DataSource createTestDatabase() {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:jpa_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));
        return ds;
    }

    @Test
    public void testInsertWithJpaAnnotations() {
        DataSource db = createTestDatabase();
        // Create table using SchemaGenerator
        SchemaGenerator.createTables(db, JpaUser.class);



        DB.withConnection(db, (Connection c) -> {
            // Insert entity with JPA annotations
            JpaUser user = new JpaUser();
            user.username = "john_doe";
            user.email = "john@example.com";
            user.biography = "A long biography text";
            user.temporaryField = "should be ignored";

            Long id = PojoQuery.insert(c, user);

            assertNotNull(id, "Insert should return generated ID");
            assertEquals(id, user.id, "Generated ID should be set on entity");

            // Verify data was inserted with correct column names
            List<Map<String, Object>> rows = DB.queryRows(c, 
                new SqlExpression("SELECT * FROM \"jpa_user\" WHERE \"user_id\" = ?", List.of(id)));
            assertEquals(1, rows.size());
            assertEquals("john_doe", rows.get(0).get("user_name")); // JPA @Column(name=...)
            assertEquals("john@example.com", rows.get(0).get("email"));
        });
    }

    @Test
    public void testUpdateWithJpaAnnotations() {
        DataSource db = createTestDatabase();
        // Create table
        SchemaGenerator.createTables(db, JpaUser.class);

        DB.withConnection(db, (Connection c) -> {
            JpaUser user = new JpaUser();
            user.username = "jane_doe";
            user.email = "jane@example.com";
            PojoQuery.insert(c, user);

            // Update the entity
            user.username = "jane_smith";
            user.email = "jane.smith@example.com";
            int affected = PojoQuery.update(c, user);

            assertEquals(1, affected, "Update should affect 1 row");

            // Verify update used correct column names
            List<Map<String, Object>> rows = DB.queryRows(c,
                new SqlExpression("SELECT * FROM \"jpa_user\" WHERE \"user_id\" = ?", List.of(user.id)));
            assertEquals("jane_smith", rows.get(0).get("user_name"));
            assertEquals("jane.smith@example.com", rows.get(0).get("email"));
        });
    }

    @Test
    public void testInsertWithJpaJoinColumn() {
        DataSource db = createTestDatabase();
        // Create tables
        SchemaGenerator.createTables(db, JpaUser.class, JpaSimpleOrder.class);

        DB.withConnection(db, (Connection c) -> {
            // Insert user first
            JpaUser customer = new JpaUser();
            customer.username = "customer1";
            customer.email = "customer@example.com";
            PojoQuery.insert(c, customer);

            // Insert order with customer reference
            JpaSimpleOrder order = new JpaSimpleOrder();
            order.totalAmount = new BigDecimal("99.95");
            order.buyer = customer;

            Long orderId = PojoQuery.insert(c, order);

            // Verify foreign key column used @JoinColumn name
            List<Map<String, Object>> rows = DB.queryRows(c,
                new SqlExpression("SELECT * FROM \"jpa_simple_order\" WHERE \"order_id\" = ?", List.of(orderId)));
            assertEquals(1, rows.size());
            assertEquals(customer.id, rows.get(0).get("buyer_id")); // JPA @JoinColumn(name=...)
        });
    }

    @Test
    public void testInsertWithJpaEmbedded() {
        DataSource db = createTestDatabase();
        // Create table
        SchemaGenerator.createTables(db, JpaCompany.class);

        DB.withConnection(db, (Connection c) -> {
            // Insert company with embedded address
            JpaCompany company = new JpaCompany();
            company.name = "Acme Corp";
            company.address = new JpaAddress();
            company.address.street = "123 Main St";
            company.address.city = "Springfield";
            company.address.zipCode = "12345";

            Long id = PojoQuery.insert(c, company);

            // Verify embedded fields were inserted without prefix (true JPA behavior)
            List<Map<String, Object>> rows = DB.queryRows(c,
                new SqlExpression("SELECT * FROM \"jpa_company\" WHERE \"company_id\" = ?", List.of(id)));
            assertEquals(1, rows.size());
            assertEquals("123 Main St", rows.get(0).get("street")); // No prefix
            assertEquals("Springfield", rows.get(0).get("city"));
            assertEquals("12345", rows.get(0).get("zipCode"));
        });
    }

    @Test
    public void testQueryWithJpaAnnotationsEndToEnd() {
        DataSource db = createTestDatabase();
        // Create tables
        SchemaGenerator.createTables(db, JpaUser.class, JpaSimpleOrder.class);

        DB.withConnection(db, (Connection c) -> {
            JpaUser customer = new JpaUser();
            customer.username = "test_user";
            customer.email = "test@example.com";
            PojoQuery.insert(c, customer);

            JpaSimpleOrder order = new JpaSimpleOrder();
            order.totalAmount = new BigDecimal("150.00");
            order.buyer = customer;
            PojoQuery.insert(c, order);

            // Query back using PojoQuery
            List<JpaSimpleOrder> orders = PojoQuery.build(JpaSimpleOrder.class)
                .addWhere("{order_id} = ?", order.id)
                .execute(c);

            assertEquals(1, orders.size());
            assertEquals(new BigDecimal("150.00"), orders.get(0).totalAmount);
            assertNotNull(orders.get(0).buyer);
            assertEquals("test_user", orders.get(0).buyer.username);
        });
    }
}
