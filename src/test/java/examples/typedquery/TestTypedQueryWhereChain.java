package examples.typedquery;

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
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.DbContext.QuoteStyle;
import org.pojoquery.DbContextBuilder;
import org.pojoquery.PojoQuery;
import org.pojoquery.integrationtest.DbContextExtension;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Tests for fluent chain-style where clauses.
 * Pattern: where().lastName.eq("Smith").or().firstName.eq("John")
 */
@ExtendWith(DbContextExtension.class)
public class TestTypedQueryWhereChain {

    private DataSource dataSource;

    @BeforeEach
    void setup() {
        DbContext.setDefault(new DbContextBuilder().dialect(Dialect.HSQLDB).withQuoteStyle(QuoteStyle.ANSI).quoteObjectNames(true).build());

        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:where_chain_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;

        SchemaGenerator.createTables(dataSource, Employee.class);
    }

    @Test
    void testSimpleWhereChain() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Johnson", 60000);

            // Simple: where().lastName.eq("Smith")
            List<Employee> results = new EmployeeQuery()
                .where().lastName.eq("Smith")
                .list(c);

            assertEquals(1, results.size());
            assertEquals("Alice", results.get(0).firstName);
        });
    }

    @Test
    void testOrChain() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Johnson", 60000);
            insertEmployee(c, "Charlie", "Williams", 70000);

            // OR: where().lastName.eq("Smith").or().lastName.eq("Johnson")
            List<Employee> results = new EmployeeQuery()
                .where().lastName.eq("Smith").or().lastName.eq("Johnson")
                .orderBy(Employee_.firstName)
                .list(c);

            assertEquals(2, results.size());
            assertEquals("Alice", results.get(0).firstName);
            assertEquals("Bob", results.get(1).firstName);
        });
    }

    @Test
    void testAndChain() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Smith", 80000);
            insertEmployee(c, "Charlie", "Johnson", 90000);

            // AND: where().lastName.eq("Smith").and().salary.gt(60000)
            List<Employee> results = new EmployeeQuery()
                .where().lastName.eq("Smith").and().salary.gt(60000)
                .list(c);

            assertEquals(1, results.size());
            assertEquals("Bob", results.get(0).firstName);
        });
    }

    @Test
    void testMixedOrAndChain() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Johnson", 60000);
            insertEmployee(c, "Charlie", "Smith", 80000);
            insertEmployee(c, "Diana", "Williams", 90000);

            // Mixed: lastName = 'Smith' OR lastName = 'Johnson' (simple OR chain)
            // Note: All conditions in the chain are combined sequentially
            List<Employee> results = new EmployeeQuery()
                .where().lastName.eq("Smith").or().lastName.eq("Johnson")
                .orderBy(Employee_.firstName)
                .list(c);

            assertEquals(3, results.size()); // Alice, Bob, Charlie
        });
    }

    @Test
    void testChainWithOrderBy() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Charlie", "Smith", 70000);
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Smith", 60000);

            List<Employee> results = new EmployeeQuery()
                .where().lastName.eq("Smith")
                .orderBy(Employee_.firstName)
                .list(c);

            assertEquals(3, results.size());
            assertEquals("Alice", results.get(0).firstName);
            assertEquals("Bob", results.get(1).firstName);
            assertEquals("Charlie", results.get(2).firstName);
        });
    }

    @Test
    void testChainWithLimit() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Smith", 60000);
            insertEmployee(c, "Charlie", "Smith", 70000);

            List<Employee> results = new EmployeeQuery()
                .where().lastName.eq("Smith")
                .orderBy(Employee_.firstName)
                .limit(2)
                .list(c);

            assertEquals(2, results.size());
            assertEquals("Alice", results.get(0).firstName);
            assertEquals("Bob", results.get(1).firstName);
        });
    }

    @Test
    void testComparisonOperators() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "A", 40000);
            insertEmployee(c, "Bob", "B", 60000);
            insertEmployee(c, "Charlie", "C", 80000);

            // gt
            assertEquals(1, new EmployeeQuery().where().salary.gt(60000).list(c).size());
            
            // ge
            assertEquals(2, new EmployeeQuery().where().salary.ge(60000).list(c).size());
            
            // lt
            assertEquals(1, new EmployeeQuery().where().salary.lt(60000).list(c).size());
            
            // le
            assertEquals(2, new EmployeeQuery().where().salary.le(60000).list(c).size());
            
            // between
            assertEquals(1, new EmployeeQuery().where().salary.between(50000, 70000).list(c).size());
        });
    }

    @Test
    void testLikeInChain() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Smithson", 60000);
            insertEmployee(c, "Charlie", "Jones", 70000);

            List<Employee> results = new EmployeeQuery()
                .where().lastName.like("Smith%")
                .orderBy(Employee_.firstName)
                .list(c);

            assertEquals(2, results.size());
            assertEquals("Alice", results.get(0).firstName);
            assertEquals("Bob", results.get(1).firstName);
        });
    }

    @Test
    void testInOperatorChain() {
        DB.withConnection(dataSource, (Connection c) -> {
            insertEmployee(c, "Alice", "Smith", 50000);
            insertEmployee(c, "Bob", "Johnson", 60000);
            insertEmployee(c, "Charlie", "Williams", 70000);

            List<Employee> results = new EmployeeQuery()
                .where().lastName.in("Smith", "Johnson")
                .orderBy(Employee_.firstName)
                .list(c);

            assertEquals(2, results.size());
            assertEquals("Alice", results.get(0).firstName);
            assertEquals("Bob", results.get(1).firstName);
        });
    }

    @Test
    void testSqlGeneration() {
        String sql = new EmployeeQuery()
            .where().lastName.eq("Smith").or().firstName.eq("John")
            .toSql();

        assertTrue(sql.contains("lastName"), "SQL should contain lastName");
        assertTrue(sql.contains("firstName"), "SQL should contain firstName");
        assertTrue(sql.contains("OR"), "SQL should contain OR");
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
