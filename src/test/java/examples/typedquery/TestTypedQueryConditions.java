package examples.typedquery;

import static examples.typedquery.Employee_.*;
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
import org.pojoquery.typedquery.Condition;

/**
 * Tests for jOOQ-style condition API in TypedQuery.
 */
@ExtendWith(DbContextExtension.class)
public class TestTypedQueryConditions {

    private DataSource dataSource;

    @BeforeEach
    void setup() {
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));

        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:condition_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;

        SchemaGenerator.createTables(dataSource, Employee.class);
    }

    @Test
    void testSimpleCondition() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Johnson", 60000);

            // Simple condition: lastName = 'Smith'
            List<Employee> results = new EmployeeQuery()
                .where(lastName.eq("Smith"))
                .list(c);

            assertEquals(1, results.size());
            assertEquals("Alice", results.get(0).firstName);
        });
    }

    @Test
    void testOrCondition() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Johnson", 60000);
            insertEmployee(c, "Charlie", "Williams", 70000);

            // OR condition: lastName = 'Smith' OR lastName = 'Johnson'
            Condition<Employee> nameFilter = lastName.eq("Smith").or(lastName.eq("Johnson"));
            
            List<Employee> results = new EmployeeQuery()
                .where(nameFilter)
                .orderBy(firstName)
                .list(c);

            assertEquals(2, results.size());
            assertEquals("Alice", results.get(0).firstName);
            assertEquals("Bob", results.get(1).firstName);
        });
    }

    @Test
    void testAndCondition() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Smith", 80000);
            insertEmployee(c, "Charlie", "Johnson", 90000);

            // AND condition: lastName = 'Smith' AND salary > 60000
            Condition<Employee> filter = lastName.eq("Smith").and(salary.gt(60000));
            
            List<Employee> results = new EmployeeQuery()
                .where(filter)
                .list(c);

            assertEquals(1, results.size());
            assertEquals("Bob", results.get(0).firstName);
        });
    }

    @Test
    void testComplexCondition() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Johnson", 60000);
            insertEmployee(c, "Charlie", "Smith", 80000);
            insertEmployee(c, "Diana", "Williams", 90000);

            // Complex: (lastName = 'Smith' OR lastName = 'Johnson') AND salary > 55000
            Condition<Employee> nameFilter = lastName.eq("Smith").or(lastName.eq("Johnson"));
            Condition<Employee> salaryFilter = salary.gt(55000);
            
            List<Employee> results = new EmployeeQuery()
                .where(nameFilter.and(salaryFilter))
                .orderBy(firstName)
                .list(c);

            assertEquals(2, results.size());
            assertEquals("Bob", results.get(0).firstName);   // Johnson, 60000
            assertEquals("Charlie", results.get(1).firstName); // Smith, 80000
        });
    }

    @Test
    void testMultipleWhereConditions() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Smith", 80000);
            insertEmployee(c, "Charlie", "Johnson", 90000);

            // Multiple where() calls are ANDed
            List<Employee> results = new EmployeeQuery()
                .where(lastName.eq("Smith"))
                .where(salary.gt(60000))
                .list(c);

            assertEquals(1, results.size());
            assertEquals("Bob", results.get(0).firstName);
        });
    }

    @Test
    void testComparisonMethods() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "A", 40000);
            insertEmployee(c, "Bob", "B", 60000);
            insertEmployee(c, "Charlie", "C", 80000);
            insertEmployee(c, "Diana", "D", 100000);

            // Test gt (greater than)
            assertEquals(2, new EmployeeQuery().where(salary.gt(60000)).list(c).size());
            
            // Test ge (greater or equal)
            assertEquals(3, new EmployeeQuery().where(salary.ge(60000)).list(c).size());
            
            // Test lt (less than)
            assertEquals(1, new EmployeeQuery().where(salary.lt(60000)).list(c).size());
            
            // Test le (less or equal)
            assertEquals(2, new EmployeeQuery().where(salary.le(60000)).list(c).size());
            
            // Test between
            assertEquals(2, new EmployeeQuery().where(salary.between(50000, 90000)).list(c).size());
        });
    }

    @Test
    void testLikeCondition() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Smithson", 60000);
            insertEmployee(c, "Charlie", "Jones", 70000);

            List<Employee> results = new EmployeeQuery()
                .where(lastName.like("Smith%"))
                .orderBy(firstName)
                .list(c);

            assertEquals(2, results.size());
            assertEquals("Alice", results.get(0).firstName);
            assertEquals("Bob", results.get(1).firstName);
        });
    }

    @Test
    void testInCondition() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Johnson", 60000);
            insertEmployee(c, "Charlie", "Williams", 70000);

            List<Employee> results = new EmployeeQuery()
                .where(lastName.in("Smith", "Johnson"))
                .orderBy(firstName)
                .list(c);

            assertEquals(2, results.size());
            assertEquals("Alice", results.get(0).firstName);
            assertEquals("Bob", results.get(1).firstName);
        });
    }

    @Test
    void testNotCondition() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Johnson", 60000);

            List<Employee> results = new EmployeeQuery()
                .where(lastName.eq("Smith").not())
                .list(c);

            assertEquals(1, results.size());
            assertEquals("Bob", results.get(0).firstName);
        });
    }

    @Test
    void testReusableConditions() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Johnson", 60000);
            insertEmployee(c, "Charlie", "Smith", 80000);

            // Create reusable conditions
            Condition<Employee> isSmith = lastName.eq("Smith");
            Condition<Employee> highEarner = salary.gt(55000);

            // Use in different combinations
            List<Employee> smiths = new EmployeeQuery().where(isSmith).list(c);
            List<Employee> highEarners = new EmployeeQuery().where(highEarner).list(c);
            List<Employee> highEarningSmith = new EmployeeQuery().where(isSmith.and(highEarner)).list(c);

            assertEquals(2, smiths.size());
            assertEquals(2, highEarners.size());
            assertEquals(1, highEarningSmith.size());
            assertEquals("Charlie", highEarningSmith.get(0).firstName);
        });
    }

    @Test
    void testStaticOrMethod() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Johnson", 60000);
            insertEmployee(c, "Charlie", "Williams", 70000);

            // Use static Condition.or() for multiple conditions
            Condition<Employee> filter = Condition.or(
                lastName.eq("Smith"),
                lastName.eq("Johnson"),
                lastName.eq("Brown")  // doesn't exist
            );

            List<Employee> results = new EmployeeQuery()
                .where(filter)
                .orderBy(firstName)
                .list(c);

            assertEquals(2, results.size());
        });
    }

    private void insertEmployee(Connection c, String firstName, String lastName, int salary) {
        Employee emp = new Employee();
        emp.firstName = firstName;
        emp.lastName = lastName;
        emp.email = firstName.toLowerCase() + "@example.com";
        emp.salary = salary;
        PojoQuery.insert(c, emp);
    }
}
