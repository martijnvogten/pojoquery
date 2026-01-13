package examples.typedquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static examples.typedquery.EmployeeWithRelations_.projects;

import java.sql.Connection;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.DbContext.QuoteStyle;
import org.pojoquery.DbContextBuilder;
import org.pojoquery.PojoQuery;
import org.pojoquery.integrationtest.DbContextExtension;
import org.pojoquery.schema.SchemaGenerator;

import examples.typedquery.EmployeeWithRelations_.projects;

/**
 * Tests for TypedQuery relationship handling.
 */
@ExtendWith(DbContextExtension.class)
public class TestTypedQueryRelationships {

    private DataSource dataSource;

    @BeforeEach
    void setup() {
        DbContext.setDefault(new DbContextBuilder().dialect(Dialect.HSQLDB).withQuoteStyle(QuoteStyle.ANSI).quoteObjectNames(true).build());

        // Use unique database per test to ensure clean state
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:typed_rel_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;

        // Create tables in correct order (referenced tables first)
        SchemaGenerator.createTables(dataSource, Department.class, Project.class, EmployeeWithRelations.class);
    }

    /**
     * Tests that multiple employees sharing the same department all get the
     * department properly linked.
     *
     * This was a bug where only the first employee to reference a department
     * would get it linked, because the deduplication map prevented creating
     * the link for subsequent employees.
     */
    @Test
    void testSharedDepartmentLinkedToAllEmployees() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Create a single department
            Department engineering = new Department("Engineering", "San Francisco");
            PojoQuery.insert(c, engineering);

            // Create two employees in the same department
            // Note: We set the department reference, not the department_id field.
            // PojoQuery extracts FK values from the referenced object's ID field.
            EmployeeWithRelations alice = new EmployeeWithRelations("Alice", "Smith", "alice@example.com", engineering);
            PojoQuery.insert(c, alice);

            EmployeeWithRelations bob = new EmployeeWithRelations("Bob", "Jones", "bob@example.com", engineering);
            PojoQuery.insert(c, bob);

            // Query both employees with their department relationships
            var employees = new EmployeeWithRelationsQuery()
                .orderBy(EmployeeWithRelations_.firstName)
                .list(c);

            assertEquals(2, employees.size());

            // Both employees should have their department linked
            EmployeeWithRelations aliceResult = employees.get(0);
            assertEquals("Alice", aliceResult.firstName);
            assertNotNull(aliceResult.department, "Alice should have department linked");
            assertEquals("Engineering", aliceResult.department.name);

            EmployeeWithRelations bobResult = employees.get(1);
            assertEquals("Bob", bobResult.firstName);
            assertNotNull(bobResult.department, "Bob should have department linked");
            assertEquals("Engineering", bobResult.department.name);

            // Both should reference the same department instance (deduplication)
            assertSame(aliceResult.department, bobResult.department,
                "Both employees should share the same Department instance");
        });
    }

    /**
     * Tests that one-to-many relationships work correctly when the same
     * related entity appears in multiple rows.
     */
    @Test
    void testOneToManyRelationshipDeduplication() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Create department
            Department engineering = new Department("Engineering", "San Francisco");
            PojoQuery.insert(c, engineering);

            // Create employee with multiple projects
            EmployeeWithRelations alice = new EmployeeWithRelations("Alice", "Smith", "alice@example.com", engineering);
            PojoQuery.insert(c, alice);

            Project projectA = new Project("Project Alpha", "active", alice);
            PojoQuery.insert(c, projectA);

            Project projectB = new Project("Project Beta", "active", alice);
            PojoQuery.insert(c, projectB);

            // fetch Alice, but order her projects by name Z-A
             EmployeeWithRelations result = new EmployeeWithRelationsQuery()
                .orderByDesc(projects.name)
                .findById(c, alice.id);

            assertNotNull(result);
            assertNotNull(result);
            assertNotNull(result.projects);
            assertEquals(2, result.projects.size(), "Employee should have 2 projects");

            // Verify project names
            var projectNames = result.projects.stream()
                .map(p -> p.name)
                .toList();
            assertEquals("Project Beta", projectNames.get(0));
            assertEquals("Project Alpha", projectNames.get(1));
        });
    }

    /**
     * Tests that employees without related entities have null/empty relationships.
     */
    @Test
    void testEmployeeWithoutRelationships() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Create employee without department or projects
            EmployeeWithRelations loner = new EmployeeWithRelations("Loner", "McAlone", "loner@example.com", null);
            PojoQuery.insert(c, loner);

            // Query the employee
            EmployeeWithRelations result = new EmployeeWithRelationsQuery()
                .where(EmployeeWithRelations_.id).is(loner.id)
                .first(c);

            assertNotNull(result);
            assertNull(result.department, "Employee without department_id should have null department");
            assertTrue(result.projects == null || result.projects.isEmpty(),
                "Employee without projects should have null or empty projects list");
        });
    }
}
