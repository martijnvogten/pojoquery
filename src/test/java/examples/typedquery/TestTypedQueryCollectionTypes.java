package examples.typedquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.integrationtest.DbContextExtension;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Tests that TypedQuery correctly handles different collection types (List, Set).
 * 
 * <p>The generated processRows() method must instantiate the correct collection type:
 * <ul>
 *   <li>List fields should get ArrayList instances</li>
 *   <li>Set fields should get HashSet instances</li>
 * </ul>
 */
@ExtendWith(DbContextExtension.class)
public class TestTypedQueryCollectionTypes {

    private DataSource dataSource;

    @BeforeEach
    void setup() {
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));

        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:typed_coll_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;
    }

    /**
     * Tests that Set fields are populated with HashSet instances.
     */
    @Test
    void testSetCollectionType() {
        // Create tables
        SchemaGenerator.createTables(dataSource, Project.class, EmployeeWithSetProjects.class);

        DB.withConnection(dataSource, (Connection c) -> {
            // Create employee
            EmployeeWithSetProjects alice = new EmployeeWithSetProjects();
            alice.firstName = "Alice";
            alice.lastName = "Smith";
            alice.email = "alice@example.com";
            PojoQuery.insert(c, alice);

            // Create projects for the employee
            Project projectA = new Project("Project Alpha", "active", alice);
            PojoQuery.insert(c, projectA);

            Project projectB = new Project("Project Beta", "active", alice);
            PojoQuery.insert(c, projectB);

            // Query using TypedQuery
            var employees = new EmployeeWithSetProjectsQuery()
                .where(EmployeeWithSetProjects_.id).is(alice.id)
                .list(c);

            assertEquals(1, employees.size(), "Should have 1 employee");
            EmployeeWithSetProjects result = employees.get(0);

            // Verify projects collection
            assertNotNull(result.projects, "Projects should not be null");
            assertEquals(2, result.projects.size(), "Employee should have 2 projects");

            // Key assertion: The collection should be a Set (HashSet), not a List
            assertTrue(result.projects instanceof Set, 
                "Projects field should be a Set, got: " + result.projects.getClass().getName());
            assertTrue(result.projects instanceof HashSet,
                "Projects field should be a HashSet, got: " + result.projects.getClass().getName());

            // Verify project names
            Set<String> projectNames = new HashSet<>();
            for (Project p : result.projects) {
                projectNames.add(p.name);
            }
            assertTrue(projectNames.contains("Project Alpha"));
            assertTrue(projectNames.contains("Project Beta"));
        });
    }

    /**
     * Tests that List fields are populated with ArrayList instances (regression test).
     */
    @Test
    void testListCollectionType() {
        // Create tables  
        SchemaGenerator.createTables(dataSource, Department.class, Project.class, EmployeeWithRelations.class);

        DB.withConnection(dataSource, (Connection c) -> {
            // Create department
            Department engineering = new Department("Engineering", "San Francisco");
            PojoQuery.insert(c, engineering);

            // Create employee
            EmployeeWithRelations alice = new EmployeeWithRelations("Alice", "Smith", "alice@example.com", engineering);
            PojoQuery.insert(c, alice);

            // Create projects
            Project projectA = new Project("Project Alpha", "active", alice);
            PojoQuery.insert(c, projectA);

            Project projectB = new Project("Project Beta", "active", alice);
            PojoQuery.insert(c, projectB);

            // Query using TypedQuery
            var employees = new EmployeeWithRelationsQuery()
                .where(EmployeeWithRelations_.id).is(alice.id)
                .list(c);

            assertEquals(1, employees.size());
            EmployeeWithRelations result = employees.get(0);

            // Verify projects collection is a List
            assertNotNull(result.projects);
            assertEquals(2, result.projects.size());
            assertTrue(result.projects instanceof java.util.List,
                "Projects field should be a List, got: " + result.projects.getClass().getName());
            assertTrue(result.projects instanceof java.util.ArrayList,
                "Projects field should be an ArrayList, got: " + result.projects.getClass().getName());
        });
    }

    /**
     * Tests that array fields are populated correctly.
     */
    @Test
    void testArrayCollectionType() {
        // Create tables
        SchemaGenerator.createTables(dataSource, Project.class, EmployeeWithArrayProjects.class);

        DB.withConnection(dataSource, (Connection c) -> {
            // Create employee
            EmployeeWithArrayProjects alice = new EmployeeWithArrayProjects();
            alice.firstName = "Alice";
            alice.lastName = "Smith";
            alice.email = "alice@example.com";
            PojoQuery.insert(c, alice);

            // Create projects for the employee
            Project projectA = new Project("Project Alpha", "active", alice);
            PojoQuery.insert(c, projectA);

            Project projectB = new Project("Project Beta", "active", alice);
            PojoQuery.insert(c, projectB);

            // Query using TypedQuery
            var employees = new EmployeeWithArrayProjectsQuery()
                .where(EmployeeWithArrayProjects_.id).is(alice.id)
                .list(c);

            assertEquals(1, employees.size(), "Should have 1 employee");
            EmployeeWithArrayProjects result = employees.get(0);

            // Verify projects array
            assertNotNull(result.projects, "Projects should not be null");
            assertEquals(2, result.projects.length, "Employee should have 2 projects");

            // Key assertion: The field should be an array of Project
            assertTrue(result.projects.getClass().isArray(),
                "Projects field should be an array, got: " + result.projects.getClass().getName());
            assertEquals(Project.class, result.projects.getClass().getComponentType(),
                "Projects should be Project[], got: " + result.projects.getClass().getName());

            // Verify project names
            Set<String> projectNames = new HashSet<>();
            for (Project p : result.projects) {
                projectNames.add(p.name);
            }
            assertTrue(projectNames.contains("Project Alpha"));
            assertTrue(projectNames.contains("Project Beta"));
        });
    }
}
