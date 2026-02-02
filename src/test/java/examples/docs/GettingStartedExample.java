package examples.docs;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.schema.SchemaGenerator;

/**
 * A complete getting started example demonstrating PojoQuery basics:
 * - Defining entity classes with relationships
 * - Creating database schema
 * - Inserting records
 * - Querying with filters, ordering, and automatic joins
 */
public class GettingStartedExample {

    // tag::entity[]
    @Table("department")
    public static class Department {
        @Id
        public Long id;
        
        public String name;
        public String location;
    }
    
    // Subclass that includes the employee collection (avoids cyclic references)
    public static class DepartmentWithEmployees extends Department {
        public List<Employee> employees;
    }
    
    @Table("employee")
    public static class Employee {
        @Id
        public Long id;
        
        public String firstName;
        public String lastName;
        public String email;
        
        // Links to department via department_id column (auto-inferred from field name)
        public Department department;
        
        // Helper method (not mapped to database)
        public String getFullName() {
            return firstName + " " + lastName;
        }
    }
    // end::entity[]

    public static void main(String[] args) {
        // tag::setup[]
        // 1. Configure the database dialect (do this once at startup)
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));
        
        // 2. Create a DataSource (use your connection pool in production)
        DataSource dataSource = createDataSource();
        
        // 3. Create the database schema from entity classes
        SchemaGenerator.createTables(dataSource, Department.class, Employee.class);
        // end::setup[]
        
        // tag::insert[]
        // Run all operations within a connection
        DB.withConnection(dataSource, (Connection c) -> {
            // Insert a department
            Department engineering = new Department();
            engineering.name = "Engineering";
            engineering.location = "Building A";
            PojoQuery.insert(c, engineering);
            System.out.println("Inserted department with ID: " + engineering.id);
            
            // Insert employees linked to the department
            Employee alice = new Employee();
            alice.firstName = "Alice";
            alice.lastName = "Anderson";
            alice.email = "alice@example.com";
            alice.department = engineering;
            PojoQuery.insert(c, alice);
            
            Employee bob = new Employee();
            bob.firstName = "Bob";
            bob.lastName = "Brown";
            bob.email = "bob@example.com";
            bob.department = engineering;
            PojoQuery.insert(c, bob);
            
            Employee carol = new Employee();
            carol.firstName = "Carol";
            carol.lastName = "Clark";
            carol.email = "carol@example.com";
            carol.department = engineering;
            PojoQuery.insert(c, carol);
            // end::insert[]
            
            // tag::query-all[]
            // Query all employees with their department, sorted by last name
            List<Employee> allEmployees = PojoQuery.build(Employee.class)
                .addOrderBy("{employee}.lastName ASC")
                .execute(c);
            
            System.out.println("\nAll employees:");
            for (Employee emp : allEmployees) {
                System.out.println("  " + emp.getFullName() + " - " + emp.department.name);
            }
            // end::query-all[]
            
            // tag::query-filter[]
            // Query with a filter
            List<Employee> filtered = PojoQuery.build(Employee.class)
                .addWhere("{employee}.lastName LIKE ?", "A%")
                .execute(c);
            
            System.out.println("\nEmployees with last name starting with 'A':");
            for (Employee emp : filtered) {
                System.out.println("  " + emp.getFullName());
            }
            // end::query-filter[]
            
            // tag::query-findbyid[]
            // Find by ID
            Employee found = PojoQuery.build(Employee.class)
                .findById(c, alice.id).orElseThrow();
            
            System.out.println("\nFound by ID: " + found.getFullName());
            // end::query-findbyid[]
            
            // tag::update[]
            // Update an employee
            found.email = "alice.anderson@example.com";
            PojoQuery.update(c, found);
            System.out.println("Updated email for: " + found.getFullName());
            // end::update[]
            
            // tag::delete[]
            // Delete an employee
            PojoQuery.delete(c, bob);
            System.out.println("Deleted: Bob Brown");
            
            // Verify deletion
            List<Employee> remaining = PojoQuery.build(Employee.class).execute(c);
            System.out.println("\nRemaining employees: " + remaining.size());
            // end::delete[]
            
            // tag::query-with-collection[]
            // Query department with all its employees using the subclass
            DepartmentWithEmployees dept = PojoQuery.build(DepartmentWithEmployees.class)
                .findById(c, engineering.id).orElseThrow();
            
            System.out.println("\nDepartment: " + dept.name + " (" + dept.location + ")");
            System.out.println("Employees:");
            for (Employee emp : dept.employees) {
                System.out.println("  - " + emp.getFullName());
            }
            // end::query-with-collection[]
        });
    }

    // tag::datasource[]
    private static DataSource createDataSource() {
        // Using HSQLDB in-memory database for this example
        // In production, use a connection pool like HikariCP
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:getting_started");
        ds.setUser("SA");
        ds.setPassword("");
        return ds;
    }
    // end::datasource[]
}
