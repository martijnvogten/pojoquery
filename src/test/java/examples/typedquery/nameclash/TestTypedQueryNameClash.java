package examples.typedquery.nameclash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Test that verifies nested relationship classes are properly named to avoid collisions.
 * 
 * <p>The relationship chain is:
 * Company -> mainDepartment (Department) -> head (Manager) -> department (Department)
 * 
 * <p>Without proper nesting, both Department instances would generate a class 
 * named "department" causing a compilation error.
 * 
 * <p>With proper nesting, we should get:
 * <ul>
 *   <li>Company_.mainDepartment - first Department</li>
 *   <li>Company_.mainDepartment.head - Manager</li>
 *   <li>Company_.mainDepartment.head.department - second Department (properly nested)</li>
 * </ul>
 */
@ExtendWith(DbContextExtension.class)
public class TestTypedQueryNameClash {

    private DataSource dataSource;

    @BeforeEach
    void setup() {
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));

        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:nameclash_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;

        SchemaGenerator.createTables(dataSource, Department.class, Manager.class, Company.class);
    }

    /**
     * Test that we can access nested fields without name collisions.
     * 
     * <p>This test verifies that:
     * <ol>
     *   <li>Company_.mainDepartment.name refers to mainDepartment.name</li>
     *   <li>Company_.mainDepartment.head.name refers to mainDepartment.head.name</li>
     *   <li>Company_.mainDepartment.head.department.name refers to mainDepartment.head.department.name</li>
     * </ol>
     * 
     * <p>All three should be distinct and unambiguous.
     */
    @Test
    void testNestedFieldsHaveUniqueClassNames() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Create marketing department reference (will be manager's department)
            DepartmentRef marketingRef = new DepartmentRef();
            marketingRef.name = "Marketing";
            PojoQuery.insert(c, marketingRef);
            
            // Create manager with marketing department
            Manager alice = new Manager("Alice", marketingRef);
            PojoQuery.insert(c, alice);
            
            // Create engineering department with Alice as head
            Department engineering = new Department("Engineering", "SF");
            engineering.head = alice;
            PojoQuery.insert(c, engineering);
            
            // Create company with engineering as main department
            Company acme = new Company("Acme Corp");
            acme.mainDepartment = engineering;
            PojoQuery.insert(c, acme);
            
            // Query using the generated typed query
            // This should compile only if there are no name clashes
            List<Company> results = new CompanyQuery()
                // Filter by main department name
                .where(Company_.mainDepartment.name).is("Engineering")
                // Also filter by the manager's name
                .where(Company_.mainDepartment.head.name).is("Alice")
                // Also filter by the manager's department name
                .where(Company_.mainDepartment.head.department.name).is("Marketing")
                .list(c);
            
            assertEquals(1, results.size());
            Company company = results.get(0);
            assertEquals("Acme Corp", company.name);
            assertNotNull(company.mainDepartment);
            assertEquals("Engineering", company.mainDepartment.name);
            assertNotNull(company.mainDepartment.head);
            assertEquals("Alice", company.mainDepartment.head.name);
            assertNotNull(company.mainDepartment.head.department);
            assertEquals("Marketing", company.mainDepartment.head.department.name);
        });
    }
    
    /**
     * Test that the different nested classes have distinct ALIAS values.
     */
    @Test
    void testNestedClassesHaveDistinctAliases() {
        // These should all be different paths
        String mainDeptAlias = Company_.mainDepartment.ALIAS;
        String headAlias = Company_.mainDepartment.head.ALIAS;
        String headDeptAlias = Company_.mainDepartment.head.department.ALIAS;
        
        // Verify they're all distinct
        assertNotEquals(mainDeptAlias, headAlias, "mainDepartment and head should have different aliases");
        assertNotEquals(mainDeptAlias, headDeptAlias, "mainDepartment and head.department should have different aliases");
        assertNotEquals(headAlias, headDeptAlias, "head and head.department should have different aliases");
        
        // Verify the paths reflect the nesting
        assertTrue(mainDeptAlias.contains("mainDepartment"), "mainDepartment alias should contain 'mainDepartment'");
        assertTrue(headAlias.contains("head"), "head alias should contain 'head'");
        assertTrue(headDeptAlias.contains("department"), "head.department alias should contain 'department'");
    }
}
