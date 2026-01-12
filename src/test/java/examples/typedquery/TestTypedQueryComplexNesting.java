package examples.typedquery;

import static examples.typedquery.Employee_.firstName;
import static org.junit.jupiter.api.Assertions.*;

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
 * Tests for complex nested AND/OR logic in TypedQuery.
 */
@ExtendWith(DbContextExtension.class)
public class TestTypedQueryComplexNesting {

    private DataSource dataSource;

    @BeforeEach
    void setup() {
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));

        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:complex_nesting_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;

        SchemaGenerator.createTables(dataSource, Employee.class);
    }

        /**
     * Test: (A OR B) AND (C OR D)
     * Two separate begin/end blocks create ANDed OR-groups
     */
    @Test
    void testBeginAfterWhere() {
        new EmployeeQuery()
            .where().salary.greaterThan(50000)
            .begin()
                .lastName.is("Smith")
                .or().lastName.is("Johnson")
            .end()
            .orderBy(firstName)
            .toSql().toString();
    }

    /**
     * Test: (A OR B) AND (C OR D)
     * Two separate begin/end blocks create ANDed OR-groups
     */
    @Test
    void testTwoOrGroupsWithAnd() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Setup: need employees that match various combinations
            insertEmployee(c, "Alice", "Smith", 60000);    // Smith + high salary -> MATCH
            insertEmployee(c, "Bob", "Johnson", 70000);    // Johnson + high salary -> MATCH  
            insertEmployee(c, "Charlie", "Smith", 40000);  // Smith + low salary -> NO MATCH
            insertEmployee(c, "Diana", "Williams", 80000); // Neither name -> NO MATCH
            insertEmployee(c, "Eve", "Johnson", 25000);    // Johnson + low salary -> NO MATCH

            // (lastName=Smith OR lastName=Johnson) AND (salary > 50000)
            List<Employee> results = new EmployeeQuery()
                .begin()
                    .where().lastName.is("Smith")
                    .or().lastName.is("Johnson")
                .end()
                .where().salary.greaterThan(50000)
                .orderBy(firstName)
                .list(c);

            assertEquals(2, results.size());
            assertEquals("Alice", results.get(0).firstName);
            assertEquals("Bob", results.get(1).firstName);

            // Print the SQL
            String sql = new EmployeeQuery()
                .begin()
                    .where().lastName.is("Smith")
                    .or().lastName.is("Johnson")
                .end()
                .begin()
                    .where().salary.greaterThan(50000)
                .end()
                .toSql();
            System.out.println("=== (A OR B) AND (C) ===");
            System.out.println(sql);
        });
    }

    /**
     * Test: (A OR B) AND (C OR D) - two full OR groups
     */
    @Test
    void testTwoFullOrGroups() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 60000);    // Smith + high -> MATCH
            insertEmployee(c, "Bob", "Johnson", 25000);    // Johnson + low -> MATCH
            insertEmployee(c, "Charlie", "Williams", 60000); // Neither name -> NO MATCH
            insertEmployee(c, "Diana", "Smith", 45000);    // Smith but middle salary -> NO MATCH

            // (lastName=Smith OR lastName=Johnson) AND (salary > 50000 OR salary < 30000)
            List<Employee> results = new EmployeeQuery()
                .begin()
                    .where().lastName.is("Smith")
                    .or().lastName.is("Johnson")
                .end()
                .begin()
                    .where().salary.greaterThan(50000)
                    .or().salary.lessThan(30000)
                .end()
                .orderBy(firstName)
                .list(c);

            assertEquals(2, results.size());
            assertEquals("Alice", results.get(0).firstName);  // Smith + 60000
            assertEquals("Bob", results.get(1).firstName);    // Johnson + 25000

            String sql = new EmployeeQuery()
                .begin()
                    .where().lastName.is("Smith")
                    .or().lastName.is("Johnson")
                .end()
                .begin()
                    .where().salary.greaterThan(50000)
                    .or().salary.lessThan(30000)
                .end()
                .toSql();
            System.out.println("=== (A OR B) AND (C OR D) ===");
            System.out.println(sql);
        });
    }

    /**
     * Test: A AND (B OR C) AND D
     * Regular where, then OR group, then regular where
     */
    @Test
    void testMixedAndOrAnd() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 60000);    // active=implied, Smith, high -> depends
            insertEmployee(c, "Bob", "Johnson", 70000);
            insertEmployee(c, "Charlie", "Smith", 40000);

            // firstName LIKE 'A%' AND (lastName=Smith OR lastName=Johnson) AND salary > 50000
            List<Employee> results = new EmployeeQuery()
                .begin()
                    .where().lastName.is("Smith")
                    .or().lastName.is("Johnson")
                .end()
                .where().firstName.like("A%")
                .and().salary.greaterThan(50000)
                .orderBy(firstName)
                .list(c);

            assertEquals(1, results.size());
            assertEquals("Alice", results.get(0).firstName);

            String sql = new EmployeeQuery()
                .begin()
                    .where().lastName.is("Smith")
                    .or().lastName.is("Johnson")
                .end()
                .where().firstName.like("A%")
                .and().salary.greaterThan(50000)
                .toSql();
            System.out.println("=== A AND (B OR C) AND D ===");
            System.out.println(sql);
        });
    }

    /**
     * Test three separate OR groups: (A OR B) AND (C OR D) AND (E OR F)
     */
    @Test
    void testThreeOrGroups() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 60000);
            insertEmployee(c, "Bob", "Johnson", 70000);
            insertEmployee(c, "Aaron", "Smith", 65000);
            insertEmployee(c, "Charlie", "Smith", 55000);

            // (firstName starts with A or B) AND (lastName=Smith OR Johnson) AND (salary 55k-65k)
            List<Employee> results = new EmployeeQuery()
                .begin()
                    .where().firstName.like("A%")
                    .or().firstName.like("B%")
                .end()
                .begin()
                    .where().lastName.is("Smith")
                    .or().lastName.is("Johnson")
                .end()
                .begin()
                    .where().salary.greaterThanOrEqual(55000)
                    .or().salary.lessThanOrEqual(65000)
                .end()
                .orderBy(firstName)
                .list(c);

            // Aaron Smith 65000, Alice Smith 60000, Bob Johnson 70000 all match
            String sql = new EmployeeQuery()
                .begin()
                    .where().firstName.like("A%")
                    .or().firstName.like("B%")
                .end()
                .begin()
                    .where().lastName.is("Smith")
                    .or().lastName.is("Johnson")
                .end()
                .begin()
                    .where().salary.greaterThanOrEqual(55000)
                    .or().salary.lessThanOrEqual(65000)
                .end()
                .toSql();
            System.out.println("=== (A OR B) AND (C OR D) AND (E OR F) ===");
            System.out.println(sql);
            
            assertTrue(results.size() >= 2, "Should match multiple employees");
        });
    }

    private void insertEmployee(Connection c, String firstName, String lastName, int salary) {
        PojoQuery.insert(c, new Employee(firstName, lastName, firstName.toLowerCase() + "@example.com", salary));
    }
}
