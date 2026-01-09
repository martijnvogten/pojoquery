package examples.typedquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Tests for TypedQuery relationship handling.
 */
public class TestTypedQueryRelationships {

    private DataSource dataSource;

    @BeforeEach
    void setup() {
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));

        // Use unique database per test to ensure clean state
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:typed_rel_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;

        // Create tables in correct order (referenced tables first)
        SchemaGenerator.createTables(dataSource, Department.class, Project.class);
        SchemaGenerator.createTables(dataSource, EmployeeWithRelations.class);
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
            Department engineering = new Department();
            engineering.name = "Engineering";
            engineering.location = "San Francisco";
            PojoQuery.insert(c, engineering);

            // Create two employees in the same department
            // Note: We set the department reference, not the department_id field.
            // PojoQuery extracts FK values from the referenced object's ID field.
            EmployeeWithRelations alice = new EmployeeWithRelations();
            alice.firstName = "Alice";
            alice.lastName = "Smith";
            alice.email = "alice@example.com";
            alice.department = engineering;
            PojoQuery.insert(c, alice);

            EmployeeWithRelations bob = new EmployeeWithRelations();
            bob.firstName = "Bob";
            bob.lastName = "Jones";
            bob.email = "bob@example.com";
            bob.department = engineering;
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
            Department engineering = new Department();
            engineering.name = "Engineering";
            engineering.location = "San Francisco";
            PojoQuery.insert(c, engineering);

            // Create employee with multiple projects
            EmployeeWithRelations alice = new EmployeeWithRelations();
            alice.firstName = "Alice";
            alice.lastName = "Smith";
            alice.email = "alice@example.com";
            alice.department = engineering;  // Use reference, not FK field
            PojoQuery.insert(c, alice);

            Project projectA = new Project();
            projectA.name = "Project Alpha";
            projectA.status = "active";
            projectA.employee_id = alice.id;
            PojoQuery.insert(c, projectA);

            Project projectB = new Project();
            projectB.name = "Project Beta";
            projectB.status = "active";
            projectB.employee_id = alice.id;
            PojoQuery.insert(c, projectB);

            // Query the employee - use list() to get all rows (first() would LIMIT 1)
            var employees = new EmployeeWithRelationsQuery()
                .where(EmployeeWithRelations_.id).is(alice.id)
                .list(c);

            assertEquals(1, employees.size(), "Should have 1 employee");
            EmployeeWithRelations result = employees.get(0);
            assertNotNull(result);
            assertNotNull(result.projects);
            assertEquals(2, result.projects.size(), "Employee should have 2 projects");

            // Verify project names
            var projectNames = result.projects.stream()
                .map(p -> p.name)
                .sorted()
                .toList();
            assertEquals("Project Alpha", projectNames.get(0));
            assertEquals("Project Beta", projectNames.get(1));
        });
    }

    /**
     * Tests that employees without related entities have null/empty relationships.
     */
    @Test
    void testEmployeeWithoutRelationships() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Create employee without department or projects
            EmployeeWithRelations loner = new EmployeeWithRelations();
            loner.firstName = "Loner";
            loner.lastName = "McAlone";
            loner.email = "loner@example.com";
            // Don't set department or projects
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
