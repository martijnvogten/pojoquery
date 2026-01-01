package examples.docs;

import java.math.BigDecimal;
import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;

/**
 * Example demonstrating table-per-subclass inheritance.
 * Each subclass has its own table that extends the base table.
 */
public class TablePerSubclassInheritanceExample {

    // tag::entities[]
    // Base class mapped to the 'person' table
    @Table("person")
    @SubClasses({Employee.class, Customer.class})
    public static class Person {
        @Id Long id;
        String name;

        public Long getId() { return id; }
        public String getName() { return name; }
    }

    // Employee subclass - has its own 'employee' table
    @Table("employee")
    public static class Employee extends Person {
        String department;
        BigDecimal salary;

        public String getDepartment() { return department; }
        public BigDecimal getSalary() { return salary; }
    }

    // Customer subclass - has its own 'customer' table
    @Table("customer")
    public static class Customer extends Person {
        Integer loyaltyPoints;

        public Integer getLoyaltyPoints() { return loyaltyPoints; }
    }
    // end::entities[]

    public static void main(String[] args) {
        DataSource db = createDatabase();
        createTables(db);
        insertTestData(db);

        // tag::query-base[]
        // Querying for the base class fetches instances of the correct subclass
        List<Person> persons = PojoQuery.build(Person.class).execute(db);

        for (Person p : persons) {
            if (p instanceof Employee emp) {
                System.out.println("Employee: " + emp.getName() + ", Dept: " + emp.getDepartment());
            } else if (p instanceof Customer cust) {
                System.out.println("Customer: " + cust.getName() + ", Points: " + cust.getLoyaltyPoints());
            }
        }
        // end::query-base[]

        // tag::query-subclass[]
        // You can also query directly for a subclass
        List<Employee> employees = PojoQuery.build(Employee.class)
            .addWhere("employee.department = ?", "Sales")
            .execute(db);
        
        for (Employee emp : employees) {
            System.out.println("Sales employee: " + emp.getName());
        }
        // end::query-subclass[]
    }

    // tag::schema[]
    private static void createTables(DataSource db) {
        DB.executeDDL(db, """
            CREATE TABLE person (
                id BIGINT IDENTITY PRIMARY KEY,
                name VARCHAR(255)
            )
            """);
        DB.executeDDL(db, """
            CREATE TABLE employee (
                id BIGINT PRIMARY KEY REFERENCES person(id),
                department VARCHAR(100),
                salary DECIMAL(10,2)
            )
            """);
        DB.executeDDL(db, """
            CREATE TABLE customer (
                id BIGINT PRIMARY KEY REFERENCES person(id),
                loyalty_points INT
            )
            """);
    }
    // end::schema[]

    private static void insertTestData(DataSource db) {
        // Insert employees (person + employee tables)
        DB.executeDDL(db, "INSERT INTO person (id, name) VALUES (1, 'Alice')");
        DB.executeDDL(db, "INSERT INTO employee (id, department, salary) VALUES (1, 'Sales', 75000)");
        
        DB.executeDDL(db, "INSERT INTO person (id, name) VALUES (2, 'Bob')");
        DB.executeDDL(db, "INSERT INTO employee (id, department, salary) VALUES (2, 'Engineering', 85000)");
        
        // Insert customers (person + customer tables)
        DB.executeDDL(db, "INSERT INTO person (id, name) VALUES (3, 'Carol')");
        DB.executeDDL(db, "INSERT INTO customer (id, loyalty_points) VALUES (3, 500)");
        
        DB.executeDDL(db, "INSERT INTO person (id, name) VALUES (4, 'Dave')");
        DB.executeDDL(db, "INSERT INTO customer (id, loyalty_points) VALUES (4, 1200)");
    }

    private static DataSource createDatabase() {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:inheritance_example");
        ds.setUser("SA");
        ds.setPassword("");
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));
        return ds;
    }
}
