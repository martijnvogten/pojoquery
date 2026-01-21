package examples.docs;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.schema.SchemaGenerator;

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

        public Person() {}

        public Person(String name) {
            this.name = name;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
    }

    // Employee subclass - has its own 'employee' table
    @Table("employee")
    public static class Employee extends Person {
        String department;
        BigDecimal salary;

        public Employee() {}

        public Employee(String name, String department, BigDecimal salary) {
            super(name);
            this.department = department;
            this.salary = salary;
        }

        public String getDepartment() { return department; }
        public BigDecimal getSalary() { return salary; }
    }

    // Customer subclass - has its own 'customer' table
    @Table("customer")
    public static class Customer extends Person {
        Integer loyaltyPoints;

        public Customer() {}

        public Customer(String name, Integer loyaltyPoints) {
            super(name);
            this.loyaltyPoints = loyaltyPoints;
        }

        public Integer getLoyaltyPoints() { return loyaltyPoints; }
    }
    // end::entities[]

    public static void main(String[] args) {
        DataSource db = createDatabase();
        SchemaGenerator.createTables(db, Employee.class, Customer.class);

        DB.withConnection(db, (Connection c) -> {
            insertTestData(c);

            // tag::query-base[]
            // Querying for the base class fetches instances of the correct subclass
            List<Person> persons = PojoQuery.build(Person.class).execute(c);

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
                .addWhere("{employee}.department = ?", "Sales")
                .execute(c);
            
            for (Employee emp : employees) {
                System.out.println("Sales employee: " + emp.getName());
            }
            // end::query-subclass[]
        });
    }

    private static void insertTestData(Connection c) {
        // Insert employees
        PojoQuery.insert(c, new Employee("Alice", "Sales", new BigDecimal("75000")));
        PojoQuery.insert(c, new Employee("Bob", "Engineering", new BigDecimal("85000")));
        
        // Insert customers
        PojoQuery.insert(c, new Customer("Carol", 500));
        PojoQuery.insert(c, new Customer("Dave", 1200));
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
