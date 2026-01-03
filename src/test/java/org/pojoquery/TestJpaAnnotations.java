package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.QuoteStyle;
import org.pojoquery.schema.SchemaGenerator;

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
public class TestJpaAnnotations {

    // ========== Test Entities with JPA Annotations ==========

    @Table(name = "jpa_user")
    public static class JpaUser {
        @Id
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

    @Table(name = "jpa_address")
    public static class JpaAddress {
        String street;
        String city;
        String zipCode;
    }

    @Table(name = "jpa_company")
    public static class JpaCompany {
        @Id
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

        List<String> statements = SchemaGenerator.generateCreateTableStatements(dbContext, JpaUser.class);
        String sql = String.join("\n", statements);

        // Should use @Id field as primary key
        assertTrue(sql.contains("PRIMARY KEY (id)"),
            "Should use JPA @Id field as primary key. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaTableAnnotation() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        List<String> statements = SchemaGenerator.generateCreateTableStatements(dbContext, JpaUser.class);
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

        List<String> statements = SchemaGenerator.generateCreateTableStatements(dbContext, JpaUser.class);
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
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        List<String> statements = SchemaGenerator.generateCreateTableStatements(dbContext, JpaUser.class);
        String sql = String.join("\n", statements);

        // Should use CLOB type for @Lob String field
        assertTrue(sql.contains("biography CLOB"),
            "Should use CLOB type for JPA @Lob String field. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaTransientAnnotation() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        List<String> statements = SchemaGenerator.generateCreateTableStatements(dbContext, JpaUser.class);
        String sql = String.join("\n", statements);

        // @Transient fields should be excluded
        assertTrue(!sql.contains("temporaryField"),
            "JPA @Transient field should be excluded. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaSchemaAnnotation() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        List<String> statements = SchemaGenerator.generateCreateTableStatements(dbContext, JpaOrder.class);
        String sql = String.join("\n", statements);

        // Should include schema from JPA @Table(schema=...)
        assertTrue(sql.contains("sales.jpa_order"),
            "Should include schema from JPA @Table. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaColumnPrecisionScale() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        List<String> statements = SchemaGenerator.generateCreateTableStatements(dbContext, JpaOrder.class);
        String sql = String.join("\n", statements);

        // Should use precision and scale from JPA @Column
        assertTrue(sql.contains("totalAmount DECIMAL(10,2)"),
            "Should use precision and scale from JPA @Column. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaJoinColumnAnnotation() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        // Test query generation with JPA @JoinColumn
        String sql = PojoQuery.build(dbContext, JpaOrder.class).toSql();

        // Should use the column name from JPA @JoinColumn
        assertTrue(sql.contains("customer_id"),
            "Should use column name from JPA @JoinColumn. Generated SQL:\n" + sql);
    }

    @Test
    public void testJpaEmbeddedAnnotation() {
        DbContext dbContext = DbContext.builder()
            .withQuoteStyle(QuoteStyle.NONE)
            .build();

        List<String> statements = SchemaGenerator.generateCreateTableStatements(dbContext, JpaCompany.class);
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
        List<String> statements = SchemaGenerator.generateCreateTableStatements(dbContext, JpaCompany.class);
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

        List<String> statements = SchemaGenerator.generateCreateTableStatements(dbContext, JpaCompany.class);
        String sql = String.join("\n", statements);

        // JPA @Embedded should NOT add any prefix - true JPA semantics
        assertTrue(sql.contains("street VARCHAR") && !sql.contains("address_street") && !sql.contains("addressstreet"),
            "JPA @Embedded should use original field names without any prefix. Generated SQL:\n" + sql);
        assertTrue(sql.contains("city VARCHAR"),
            "JPA @Embedded should use original field names without any prefix. Generated SQL:\n" + sql);
        assertTrue(sql.contains("zipCode VARCHAR"),
            "JPA @Embedded should use original field names without any prefix. Generated SQL:\n" + sql);
    }
}
