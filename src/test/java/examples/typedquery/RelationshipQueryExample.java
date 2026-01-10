package examples.typedquery;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Example demonstrating idiomatic PojoQuery modeling with entity relationships.
 *
 * <p>This example shows:
 * <ul>
 *   <li>Using Ref pattern for lightweight foreign key references</li>
 *   <li>Rich domain models with constructors</li>
 *   <li>One-to-one relationships (Employee -> Department)</li>
 *   <li>One-to-many relationships (Employee -> Projects)</li>
 *   <li>Static inner classes for cohesive organization</li>
 * </ul>
 */
public class RelationshipQueryExample {

    // ========== Domain Model ==========

    /**
     * Department entity - full representation with all fields.
     */
    @Table("department")
    public static class Department {
        @Id
        public Long id;
        public String name;
        public String location;

        Department() {}

        public Department(String name, String location) {
            this.name = name;
            this.location = location;
        }

        /** Creates a lightweight reference for use in foreign key relationships. */
        public DepartmentRef toRef() {
            DepartmentRef ref = new DepartmentRef();
            ref.id = this.id;
            ref.name = this.name;
            return ref;
        }

        @Override
        public String toString() {
            return "Department[" + name + " @ " + location + "]";
        }
    }

    /**
     * Lightweight reference to a Department - used for foreign key relationships.
     * Maps to same table but only includes fields needed for display/reference.
     */
    @Table("department")
    public static class DepartmentRef {
        @Id
        public Long id;
        public String name;

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Employee entity - full representation for insert/update operations.
     */
    @Table("employee")
    public static class Employee {
        @Id
        public Long id;
        public String firstName;
        public String lastName;
        public String email;
        public DepartmentRef department;

        Employee() {}

        public Employee(String firstName, String lastName, String email, DepartmentRef department) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.department = department;
        }

        /** Creates a lightweight reference for use in foreign key relationships. */
        public EmployeeRef toRef() {
            EmployeeRef ref = new EmployeeRef();
            ref.id = this.id;
            ref.firstName = this.firstName;
            ref.lastName = this.lastName;
            return ref;
        }

        public String getFullName() {
            return firstName + " " + lastName;
        }

        @Override
        public String toString() {
            return "Employee[" + getFullName() + ", " + email + "]";
        }
    }

    /**
     * Lightweight reference to an Employee - used for foreign key relationships.
     */
    @Table("employee")
    public static class EmployeeRef {
        @Id
        public Long id;
        public String firstName;
        public String lastName;

        public String getFullName() {
            return firstName + " " + lastName;
        }

        @Override
        public String toString() {
            return getFullName();
        }
    }

    /**
     * Employee with all relationships loaded - used for queries that need full data.
     */
    public static class EmployeeWithProjects extends Employee {
        @Link(foreignlinkfield = "assignee_id")
        public List<Project> projects;
    }

    /**
     * Project entity with full details.
     */
    @Table("project")
    public static class Project {
        @Id
        public Long id;
        public String name;
        public String status;
        public EmployeeRef assignee;  // Uses EmployeeRef instead of raw employee_id

        Project() {}

        public Project(String name, String status, EmployeeRef assignee) {
            this.name = name;
            this.status = status;
            this.assignee = assignee;
        }

        @Override
        public String toString() {
            return "Project[" + name + " - " + status + 
                   (assignee != null ? ", assigned to " + assignee : "") + "]";
        }
    }

    // ========== Main Example ==========

    public static void main(String[] args) {
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));

        DataSource dataSource = createDataSource();

        // Create tables for all entity types
        SchemaGenerator.createTables(dataSource, Department.class, Employee.class, Project.class);

        DB.withConnection(dataSource, (Connection c) -> {
            // Create test data using rich domain model
            insertTestData(c);

            // Example 1: Load all employees with their departments
            System.out.println("=== Example 1: All employees with departments ===");
            List<Employee> employees = PojoQuery.build(Employee.class)
                .addOrderBy("{employee}.lastName ASC")
                .execute(c);
            for (Employee emp : employees) {
                System.out.println("  " + emp.getFullName() + " - " + 
                    (emp.department != null ? emp.department.name : "no department"));
            }
            System.out.println();

            // Example 2: Load employees with their projects
            System.out.println("=== Example 2: Employees with projects ===");
            List<EmployeeWithProjects> employeesWithProjects = PojoQuery.build(EmployeeWithProjects.class)
                .addOrderBy("{employee}.lastName ASC")
                .execute(c);
            for (EmployeeWithProjects emp : employeesWithProjects) {
                System.out.println("  " + emp.getFullName());
                if (emp.projects != null && !emp.projects.isEmpty()) {
                    for (Project p : emp.projects) {
                        System.out.println("    - " + p.name + " (" + p.status + ")");
                    }
                } else {
                    System.out.println("    (no projects)");
                }
            }
            System.out.println();

            // Example 3: Filter by department
            System.out.println("=== Example 3: Engineers only ===");
            List<Employee> engineers = PojoQuery.build(Employee.class)
                .addWhere("{department}.name = ?", "Engineering")
                .execute(c);
            for (Employee emp : engineers) {
                System.out.println("  " + emp.getFullName());
            }
            System.out.println();

            // Example 4: Find projects by status
            System.out.println("=== Example 4: Active projects with assignees ===");
            List<Project> activeProjects = PojoQuery.build(Project.class)
                .addWhere("{project}.status = ?", "active")
                .execute(c);
            for (Project p : activeProjects) {
                System.out.println("  " + p.name + " - " + 
                    (p.assignee != null ? "assigned to " + p.assignee.getFullName() : "unassigned"));
            }
            System.out.println();

            // Example 5: Show generated SQL
            System.out.println("=== Example 5: Generated SQL ===");
            String sql = PojoQuery.build(EmployeeWithProjects.class)
                .addWhere("{department}.location LIKE ?", "San%")
                .addOrderBy("{employee}.lastName ASC")
                .toSql();
            System.out.println(sql);
        });

        System.out.println("\nRelationship query example completed successfully!");
    }

    private static void insertTestData(Connection c) {

        // Create departments
        Department engineering = new Department("Engineering", "San Francisco");
        PojoQuery.insert(c, engineering);

        Department sales = new Department("Sales", "New York");
        PojoQuery.insert(c, sales);
        Department hr = new Department("HR", "Chicago");
        PojoQuery.insert(c, hr);

        // Create employees
        Employee alice = new Employee("Alice", "Smith", "alice@example.com", engineering.toRef());
        PojoQuery.insert(c, alice);
        Employee bob = new Employee("Bob", "Jones", "bob@example.com", engineering.toRef());
        PojoQuery.insert(c, bob);
        Employee carol = new Employee("Carol", "Wilson", "carol@example.com", sales.toRef());
        PojoQuery.insert(c, carol);
        Employee david = new Employee("David", "Brown", "david@example.com", hr.toRef());
        PojoQuery.insert(c, david);

        // Create projects using constructor with employee reference
        PojoQuery.insert(c, new Project("Project Alpha", "active", alice.toRef()));
        PojoQuery.insert(c, new Project("Project Beta", "active", alice.toRef()));
        PojoQuery.insert(c, new Project("Project Gamma", "completed", bob.toRef()));
        PojoQuery.insert(c, new Project("Sales Dashboard", "active", carol.toRef()));

        System.out.println("Inserted test data: 3 departments, 4 employees, 4 projects\n");
    }

    private static DataSource createDataSource() {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:relationship_example");
        ds.setUser("SA");
        ds.setPassword("");
        return ds;
    }
}
