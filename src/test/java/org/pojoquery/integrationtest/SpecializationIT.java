package org.pojoquery.integrationtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Integration tests for field specialization in subclasses.
 * 
 * <p>Tests that when a subclass declares a field with the same name as a superclass field
 * but with a more specific type, the specialized version takes precedence.
 */
public class SpecializationIT {

	// ========== Entity Classes ==========
	
	@Table("department")
	static class Department {
		@Id
		Long id;
		String name;
	}
	
	@Table("employee")
	static class EmployeeRef {
		@Id
		Long id;
		String name;
	}
	
	static class Employee extends EmployeeRef {
		EmployeeRef manager;
		BigDecimal salary;
		Department department;
	}
	
	/**
	 * EmployeeDetail specializes the 'manager' field from EmployeeRef to Employee,
	 * allowing access to the manager's salary and department.
	 */
	static class EmployeeDetail extends Employee {
		LocalDate hireDate;
		Employee manager; // Specialization: more specific type than parent's EmployeeRef
	}

	// ========== Tests ==========
	
	@Test
	public void testManagerSpecialization() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			// Insert department
			Department engDept = new Department();
			engDept.name = "Engineering";
			PojoQuery.insert(c, engDept);
			
			Department hrDept = new Department();
			hrDept.name = "Human Resources";
			PojoQuery.insert(c, hrDept);
			
			// Insert CEO (no manager)
			EmployeeDetail ceo = new EmployeeDetail();
			ceo.name = "Alice CEO";
			ceo.salary = new BigDecimal("500000");
			ceo.department = engDept;
			ceo.hireDate = LocalDate.of(2010, 1, 15);
			PojoQuery.insert(c, ceo);
			
			// Insert manager who reports to CEO
			EmployeeDetail manager = new EmployeeDetail();
			manager.name = "Bob Manager";
			manager.salary = new BigDecimal("150000");
			manager.department = engDept;
			manager.manager = ceo;
			manager.hireDate = LocalDate.of(2015, 6, 1);
			PojoQuery.insert(c, manager);
			
			// Insert developer who reports to manager
			EmployeeDetail developer = new EmployeeDetail();
			developer.name = "Carol Developer";
			developer.salary = new BigDecimal("100000");
			developer.department = engDept;
			developer.manager = manager;
			developer.hireDate = LocalDate.of(2020, 3, 10);
			PojoQuery.insert(c, developer);
			
			// Query the developer using EmployeeDetail which specializes the manager field
			List<EmployeeDetail> employees = PojoQuery.build(EmployeeDetail.class)
				.addWhere("{employee}.id = ?", developer.id)
				.execute(c);
			
			assertEquals(1, employees.size());
			EmployeeDetail carol = employees.get(0);
			
			// Verify basic employee data
			assertEquals("Carol Developer", carol.name);
			assertEquals(0, new BigDecimal("100000").compareTo(carol.salary));
			assertEquals(LocalDate.of(2020, 3, 10), carol.hireDate);
			assertNotNull(carol.department);
			assertEquals("Engineering", carol.department.name);
			
			// Verify manager data - this is the key test for specialization!
			// Because manager is specialized to Employee type, we should get salary and department
			assertNotNull(carol.manager);
			assertEquals("Bob Manager", carol.manager.name);
			assertEquals(0, new BigDecimal("150000").compareTo(carol.manager.salary));
			assertNotNull(carol.manager.department);
			assertEquals("Engineering", carol.manager.department.name);
			
			// And we should also have the manager's manager (CEO) as EmployeeRef
			assertNotNull(carol.manager.manager);
			assertEquals("Alice CEO", carol.manager.manager.name);
		});
	}
	
	@Test
	public void testManagerSpecializationWithNullManager() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			// Insert department
			Department dept = new Department();
			dept.name = "Executive";
			PojoQuery.insert(c, dept);
			
			// Insert CEO (no manager)
			EmployeeDetail ceo = new EmployeeDetail();
			ceo.name = "Alice CEO";
			ceo.salary = new BigDecimal("500000");
			ceo.department = dept;
			ceo.hireDate = LocalDate.of(2010, 1, 15);
			PojoQuery.insert(c, ceo);
			
			// Query the CEO - should have null manager
			List<EmployeeDetail> employees = PojoQuery.build(EmployeeDetail.class)
				.addWhere("{employee}.id = ?", ceo.id)
				.execute(c);
			
			assertEquals(1, employees.size());
			EmployeeDetail alice = employees.get(0);
			
			assertEquals("Alice CEO", alice.name);
			assertNull(alice.manager);
		});
	}
	
	@Test
	public void testQueryWithNonSpecializedType() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			// Insert department
			Department dept = new Department();
			dept.name = "Engineering";
			PojoQuery.insert(c, dept);
			
			// Insert CEO
			EmployeeDetail ceo = new EmployeeDetail();
			ceo.name = "Alice CEO";
			ceo.salary = new BigDecimal("500000");
			ceo.department = dept;
			ceo.hireDate = LocalDate.of(2010, 1, 15);
			PojoQuery.insert(c, ceo);
			
			// Insert developer
			EmployeeDetail developer = new EmployeeDetail();
			developer.name = "Bob Developer";
			developer.salary = new BigDecimal("100000");
			developer.department = dept;
			developer.manager = ceo;
			developer.hireDate = LocalDate.of(2020, 3, 10);
			PojoQuery.insert(c, developer);
			
			// Query using Employee (not EmployeeDetail) - manager should be EmployeeRef type
			List<Employee> employees = PojoQuery.build(Employee.class)
				.addWhere("{employee}.id = ?", developer.id)
				.execute(c);
			
			assertEquals(1, employees.size());
			Employee bob = employees.get(0);
			
			assertEquals("Bob Developer", bob.name);
			assertNotNull(bob.manager);
			assertEquals("Alice CEO", bob.manager.name);
			
			// Manager is EmployeeRef type so salary should not be loaded
			// (manager.salary would be null because EmployeeRef doesn't have that field)
			assertEquals(EmployeeRef.class, bob.manager.getClass());
		});
	}

	private static DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Department.class, EmployeeDetail.class);
		return db;
	}
}
