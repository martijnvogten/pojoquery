package examples.typedquery;

import static examples.typedquery.Employee_.firstName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;

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
 * Tests for OR clause support in TypedQuery.
 */
@ExtendWith(DbContextExtension.class)
public class TestTypedQueryOrClauses {

    private DataSource dataSource;

    @BeforeEach
    void setup() {
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));

        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:typed_or_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;

        SchemaGenerator.createTables(dataSource, Employee.class);
    }

    @Test
    void testSimpleOrClause() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Insert test data
            insertEmployee(c, "Alice", "Smith", "alice@example.com", 50000);
            insertEmployee(c, "Bob", "Johnson", "bob@example.com", 60000);
            insertEmployee(c, "Charlie", "Williams", "charlie@example.com", 70000);
            insertEmployee(c, "Diana", "Smith", "diana@example.com", 55000);

            // Test OR clause: lastName = 'Smith' OR lastName = 'Johnson'
            List<Employee> results = new EmployeeQuery()
        		.where().lastName.is("Smith")
                .or().lastName.is("Johnson")
                .orderBy(firstName)
                .list(c);

            assertEquals(3, results.size());
            assertEquals("Alice", results.get(0).firstName);   // Smith
            assertEquals("Bob", results.get(1).firstName);     // Johnson
            assertEquals("Diana", results.get(2).firstName);   // Smith

            // Verify SQL looks correct
            String sql = new EmployeeQuery()
                .begin()
                    .where().lastName.is("Smith")
                    .or().lastName.is("Johnson")
                .end()
                .toSql();
            System.out.println("Generated SQL: " + sql);
            assertTrue(sql.contains("OR"), "SQL should contain OR");
        });
    }

    @Test
    void testOrWithAndClauses() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Insert test data
            insertEmployee(c, "Alice", "Smith", "alice@example.com", 50000);
            insertEmployee(c, "Bob", "Johnson", "bob@example.com", 60000);
            insertEmployee(c, "Charlie", "Smith", "charlie@example.com", 80000);
            insertEmployee(c, "Diana", "Williams", "diana@example.com", 55000);

            // Test: (lastName = 'Smith' OR lastName = 'Johnson') AND salary > 55000
            List<Employee> results = new EmployeeQuery()
        		.where()
	                .begin()
	                    .lastName.is("Smith")
	                    .or().lastName.is("Johnson")
	                .end()
                .and().salary.greaterThan(55000)
                .orderBy(firstName)
                .list(c);

            assertEquals(2, results.size());
            assertEquals("Bob", results.get(0).firstName);     // Johnson, 60000
            assertEquals("Charlie", results.get(1).firstName); // Smith, 80000

            String sql = new EmployeeQuery()
        		.where()
                .begin()
                    .lastName.is("Smith")
                    .or().lastName.is("Johnson")
                .end()
                .and().salary.greaterThan(55000)
                .toSql();
            System.out.println("Generated SQL with AND: " + sql);
        });
    }

    @Test
    void testOrWithThreeConditions() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", "alice@example.com", 50000);
            insertEmployee(c, "Bob", "Johnson", "bob@example.com", 60000);
            insertEmployee(c, "Charlie", "Williams", "charlie@example.com", 70000);
            insertEmployee(c, "Diana", "Brown", "diana@example.com", 55000);

            // Test three OR conditions
            List<Employee> results = new EmployeeQuery()
        		.where()
                .lastName.is("Smith")
                .or().lastName.is("Johnson")
                .or().lastName.is("Williams")
                .orderBy(firstName)
                .list(c);

            assertEquals(3, results.size());
            assertEquals("Alice", results.get(0).firstName);
            assertEquals("Bob", results.get(1).firstName);
            assertEquals("Charlie", results.get(2).firstName);
        });
    }

    @Test
    void testOrWithComparableFields() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", "alice@example.com", 40000);
            insertEmployee(c, "Bob", "Johnson", "bob@example.com", 60000);
            insertEmployee(c, "Charlie", "Williams", "charlie@example.com", 90000);

            // Test: salary < 50000 OR salary > 80000
            List<Employee> results = new EmployeeQuery()
                .begin()
                    .where().salary.lessThan(50000)
                    .or().salary.greaterThan(80000)
                .end()
                .orderBy(firstName)
                .list(c);

            assertEquals(2, results.size());
            assertEquals("Alice", results.get(0).firstName);   // 40000
            assertEquals("Charlie", results.get(1).firstName); // 90000
        });
    }

    @Test
    void testOrWithLike() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", "alice@gmail.com", 50000);
            insertEmployee(c, "Bob", "Johnson", "bob@yahoo.com", 60000);
            insertEmployee(c, "Charlie", "Williams", "charlie@gmail.com", 70000);
            insertEmployee(c, "Diana", "Brown", "diana@outlook.com", 55000);

            // Test: email LIKE '%@gmail.com' OR email LIKE '%@yahoo.com'
            List<Employee> results = new EmployeeQuery()
                .begin()
                    .where().email.like("%@gmail.com")
                    .or().email.like("%@yahoo.com")
                .end()
                .orderBy(firstName)
                .list(c);

            assertEquals(3, results.size());
            assertEquals("Alice", results.get(0).firstName);
            assertEquals("Bob", results.get(1).firstName);
            assertEquals("Charlie", results.get(2).firstName);
        });
    }

    @Test
    void testMultipleOrGroups() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", "alice@gmail.com", 50000);
            insertEmployee(c, "Bob", "Johnson", "bob@gmail.com", 60000);
            insertEmployee(c, "Charlie", "Smith", "charlie@yahoo.com", 70000);
            insertEmployee(c, "Diana", "Brown", "diana@gmail.com", 55000);

            // Test: (lastName = 'Smith' OR lastName = 'Johnson') AND (email LIKE '%@gmail.com')
            List<Employee> results = new EmployeeQuery()
                .begin()
                    .where().lastName.is("Smith")
                    .or().lastName.is("Johnson")
                .end()
                .where().email.like("%@gmail.com")
                .orderBy(firstName)
                .list(c);

            assertEquals(2, results.size());
            assertEquals("Alice", results.get(0).firstName);  // Smith + gmail
            assertEquals("Bob", results.get(1).firstName);    // Johnson + gmail
            // Charlie is Smith but yahoo, Diana is Brown
        });
    }

    @Test
    void testEmptyOrGroup() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", "alice@example.com", 50000);

            // Empty begin().end() should not affect results
            List<Employee> results = new EmployeeQuery()
                .begin()
                .end()
                .list(c);

            assertEquals(1, results.size());
        });
    }

    @Test
    void testSingleConditionInOrGroup() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", "alice@example.com", 50000);
            insertEmployee(c, "Bob", "Johnson", "bob@example.com", 60000);

            // Single condition in begin/end should work like regular where
            List<Employee> results = new EmployeeQuery()
                .begin()
                    .where().lastName.is("Smith")
                .end()
                .list(c);

            assertEquals(1, results.size());
            assertEquals("Alice", results.get(0).firstName);
        });
    }

    private void insertEmployee(Connection c, String firstName, String lastName, String email, int salary) {
        Employee emp = new Employee();
        emp.firstName = firstName;
        emp.lastName = lastName;
        emp.email = email;
        emp.salary = salary;
        PojoQuery.insert(c, emp);
    }
}
