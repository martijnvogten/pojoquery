package examples.typedquery;

import static examples.typedquery.EmployeeWithRelations_.Department;
import static examples.typedquery.EmployeeWithRelations_.Projects;
import static examples.typedquery.EmployeeWithRelations_.firstName;
import static examples.typedquery.EmployeeWithRelations_.lastName;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Example demonstrating typed queries with entity relationships.
 *
 * <p>This example shows:
 * <ul>
 *   <li>One-to-one relationships (Employee -> Department)</li>
 *   <li>One-to-many relationships (Employee -> Projects)</li>
 *   <li>Filtering on related entity fields</li>
 *   <li>Automatic entity deduplication</li>
 * </ul>
 */
public class RelationshipQueryExample {

    public static void main(String[] args) {
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));

        DataSource dataSource = createDataSource();

        // Create tables (use fully qualified names to avoid shadowing by static imports)
        SchemaGenerator.createTables(dataSource, examples.typedquery.Department.class, examples.typedquery.Project.class);
        SchemaGenerator.createTables(dataSource, EmployeeWithRelations.class);

        DB.runInTransaction(dataSource, (Connection c) -> {
            try {
                insertTestData(c);
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            }

            // Example 1: Load employees with their departments and projects
            System.out.println("=== Example 1: Load all employees with relationships ===");
            List<EmployeeWithRelations> employees = new EmployeeWithRelationsQuery()
                .orderBy(lastName)
                .list(c);
            for (EmployeeWithRelations emp : employees) {
                System.out.println(emp);
                if (emp.department != null) {
                    System.out.println("  Department: " + emp.department);
                }
                if (emp.projects != null && !emp.projects.isEmpty()) {
                    System.out.println("  Projects:");
                    for (Project p : emp.projects) {
                        System.out.println("    - " + p);
                    }
                }
            }

            System.out.println();

            // Example 2: Filter by department name
            System.out.println("=== Example 2: Find employees in Engineering department ===");
            List<EmployeeWithRelations> engineers = new EmployeeWithRelationsQuery()
                .where(Department.name).is("Engineering")
                .orderBy(firstName)
                .list(c);
            for (EmployeeWithRelations emp : engineers) {
                System.out.println("  " + emp.firstName + " " + emp.lastName);
            }
            System.out.println();

            // Example 3: Filter by project status
            System.out.println("=== Example 3: Find employees with active projects ===");
            List<EmployeeWithRelations> activeProjectEmployees = new EmployeeWithRelationsQuery()
                .where(Projects.status).is("active")
                .list(c);
            for (EmployeeWithRelations emp : activeProjectEmployees) {
                System.out.println("  " + emp.firstName + " " + emp.lastName +
                    " - " + emp.projects.size() + " project(s)");
            }
            System.out.println();

            // Example 4: Find by ID with relationships loaded
            System.out.println("=== Example 4: Find employee by ID ===");
            EmployeeWithRelations emp = new EmployeeWithRelationsQuery().findById(c, 1L);
            if (emp != null) {
                System.out.println("Found: " + emp.firstName + " " + emp.lastName);
                System.out.println("Department: " + (emp.department != null ? emp.department.name : "none"));
                System.out.println("Projects: " + (emp.projects != null ? emp.projects.size() : 0));
            }
            System.out.println();

            // Example 5: Show generated SQL
            System.out.println("=== Example 5: Generated SQL ===");
            String sql = new EmployeeWithRelationsQuery()
                .where(Department.location).like("San%")
                .orderBy(lastName)
                .toSql();
            System.out.println(sql);
        });

        System.out.println("\nRelationship query example completed successfully!");
    }

    private static void insertTestData(Connection c) throws java.sql.SQLException {
        // Insert departments (use fully qualified name to avoid shadowing by static import)
        examples.typedquery.Department eng = new examples.typedquery.Department();
        eng.name = "Engineering";
        eng.location = "San Francisco";
        PojoQuery.insert(c, eng);

        examples.typedquery.Department sales = new examples.typedquery.Department();
        sales.name = "Sales";
        sales.location = "New York";
        PojoQuery.insert(c, sales);

        examples.typedquery.Department hr = new examples.typedquery.Department();
        hr.name = "HR";
        hr.location = "Chicago";
        PojoQuery.insert(c, hr);

        // Insert employees using PreparedStatement
        try (var ps = c.prepareStatement("INSERT INTO employee (id, firstName, lastName, email, department_id) VALUES (?, ?, ?, ?, ?)")) {
            insertEmployee(ps, 1L, "Alice", "Smith", "alice@example.com", 1L);
            insertEmployee(ps, 2L, "Bob", "Jones", "bob@example.com", 1L);
            insertEmployee(ps, 3L, "Carol", "Wilson", "carol@example.com", 2L);
            insertEmployee(ps, 4L, "David", "Brown", "david@example.com", 3L);
        }

        // Insert projects
        try (var ps = c.prepareStatement("INSERT INTO project (id, name, status, employee_id) VALUES (?, ?, ?, ?)")) {
            insertProject(ps, 1L, "Project Alpha", "active", 1L);
            insertProject(ps, 2L, "Project Beta", "active", 1L);
            insertProject(ps, 3L, "Project Gamma", "completed", 2L);
            insertProject(ps, 4L, "Sales Dashboard", "active", 3L);
        }

        System.out.println("Inserted test data: 3 departments, 4 employees, 4 projects\n");
    }

    private static void insertEmployee(java.sql.PreparedStatement ps, Long id, String firstName,
                                       String lastName, String email, Long deptId) throws java.sql.SQLException {
        ps.setLong(1, id);
        ps.setString(2, firstName);
        ps.setString(3, lastName);
        ps.setString(4, email);
        ps.setLong(5, deptId);
        ps.executeUpdate();
    }

    private static void insertProject(java.sql.PreparedStatement ps, Long id, String name,
                                      String status, Long empId) throws java.sql.SQLException {
        ps.setLong(1, id);
        ps.setString(2, name);
        ps.setString(3, status);
        ps.setLong(4, empId);
        ps.executeUpdate();
    }

    private static DataSource createDataSource() {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:relationship_example");
        ds.setUser("SA");
        ds.setPassword("");
        return ds;
    }
}
