package examples.typedquery;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Tests for TypedQuery streaming and first() with relationships.
 * 
 * <p>The naive implementation of first() uses LIMIT 1, which doesn't work
 * correctly when the entity has joined collections - you'd only get one row
 * but miss the other related entities.
 * 
 * <p>Similarly, stream() needs to accumulate rows for the same entity before
 * emitting, because each row only contains one related entity from a collection.
 */
public class TestTypedQueryStreaming {

    private DataSource dataSource;

    @BeforeEach
    void setup() {
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));

        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:typed_stream_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;

        // Create tables
        SchemaGenerator.createTables(dataSource, Department.class, Project.class);
        SchemaGenerator.createTables(dataSource, EmployeeWithRelations.class);
    }

    /**
     * Test that first() correctly returns an entity with ALL its related entities,
     * not just the first row from the join.
     */
    @Test
    void testFirstWithCollections() {
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
            alice.department = engineering;
            PojoQuery.insert(c, alice);

            // Create 3 projects for Alice
            for (int i = 1; i <= 3; i++) {
                Project project = new Project();
                project.name = "Project " + i;
                project.status = "active";
                project.employee_id = alice.id;
                PojoQuery.insert(c, project);
            }

            // Use first() - this should return Alice with ALL 3 projects
            EmployeeWithRelations result = new EmployeeWithRelationsQuery()
                .where(EmployeeWithRelations_.id).is(alice.id)
                .first(c);

            assertNotNull(result, "Should find Alice");
            assertEquals("Alice", result.firstName);
            assertNotNull(result.projects, "Projects should not be null");
            
            // This is the key assertion - first() should return ALL projects, not just 1
            assertEquals(3, result.projects.size(), 
                "first() should return entity with ALL related projects, not just first row");
        });
    }

    /**
     * Test that stream() correctly accumulates all rows for an entity before
     * emitting it, so the callback receives complete entities with all their
     * related collections populated.
     */
    @Test
    void testStreamWithCollections() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Create department
            Department engineering = new Department();
            engineering.name = "Engineering";
            engineering.location = "San Francisco";
            PojoQuery.insert(c, engineering);

            // Create 2 employees, each with multiple projects
            EmployeeWithRelations alice = new EmployeeWithRelations();
            alice.firstName = "Alice";
            alice.lastName = "Smith";
            alice.email = "alice@example.com";
            alice.department = engineering;
            PojoQuery.insert(c, alice);

            for (int i = 1; i <= 3; i++) {
                Project project = new Project();
                project.name = "Alice Project " + i;
                project.status = "active";
                project.employee_id = alice.id;
                PojoQuery.insert(c, project);
            }

            EmployeeWithRelations bob = new EmployeeWithRelations();
            bob.firstName = "Bob";
            bob.lastName = "Jones";
            bob.email = "bob@example.com";
            bob.department = engineering;
            PojoQuery.insert(c, bob);

            for (int i = 1; i <= 2; i++) {
                Project project = new Project();
                project.name = "Bob Project " + i;
                project.status = "active";
                project.employee_id = bob.id;
                PojoQuery.insert(c, project);
            }

            // Use stream() - each emitted entity should be complete with all projects
            List<EmployeeWithRelations> results = new ArrayList<>();
            new EmployeeWithRelationsQuery()
                .orderBy(EmployeeWithRelations_.firstName)
                .stream(c, results::add);

            assertEquals(2, results.size(), "Should have 2 employees");

            // Alice should have all 3 projects
            EmployeeWithRelations aliceResult = results.get(0);
            assertEquals("Alice", aliceResult.firstName);
            assertNotNull(aliceResult.projects);
            assertEquals(3, aliceResult.projects.size(), 
                "Alice should have all 3 projects when streamed");

            // Bob should have all 2 projects
            EmployeeWithRelations bobResult = results.get(1);
            assertEquals("Bob", bobResult.firstName);
            assertNotNull(bobResult.projects);
            assertEquals(2, bobResult.projects.size(), 
                "Bob should have all 2 projects when streamed");
        });
    }

    /**
     * Test that stream() emits entities in the correct order based on ORDER BY.
     */
    @Test
    void testStreamOrderBy() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Create employees
            for (String name : new String[]{"Charlie", "Alice", "Bob"}) {
                EmployeeWithRelations emp = new EmployeeWithRelations();
                emp.firstName = name;
                emp.lastName = "Test";
                emp.email = name.toLowerCase() + "@example.com";
                PojoQuery.insert(c, emp);
            }

            // Stream with ORDER BY firstName DESC
            List<String> names = new ArrayList<>();
            new EmployeeWithRelationsQuery()
                .orderByDesc(EmployeeWithRelations_.firstName)
                .stream(c, emp -> names.add(emp.firstName));

            assertEquals(List.of("Charlie", "Bob", "Alice"), names,
                "Stream should emit entities in ORDER BY order");
        });
    }
}
