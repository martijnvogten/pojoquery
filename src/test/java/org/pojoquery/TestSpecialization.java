package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.pojoquery.TestUtils.norm;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.UseDialect;

@UseDialect(Dialect.MYSQL)
public class TestSpecialization {

	@Table("employee")
	static class EmployeeRef {
		@Id
		private Integer id;
		private String name;

		public Integer getId() {
			return id;
		}

		public String getName() {
			return name;
		}
	}
	
	static class Employee extends EmployeeRef {
		private EmployeeRef manager;
		private BigDecimal salary;
		private Department department;

		public EmployeeRef getManager() {
			return manager;
		}
		public BigDecimal getSalary() {
			return salary;	
		}
		public Department getDepartment() {
			return department;
		}
	}

	static class EmployeeDetail extends Employee {
		private java.time.LocalDate hireDate;
		private Employee manager;

		public java.time.LocalDate getHireDate() {
			return hireDate;
		}

		public Employee getManager() {
			return manager;
		}

		public void setManager(Employee manager) {
			this.manager = manager;
		}
	}

	@Table("department")
	static class Department {
		@Id
		private Integer id;
		private String name;
		public Integer getId() {
			return id;
		}
		public String getName() {
			return name;
		}
	}

	static class DepartmentWithEmployees extends Department {
		private java.util.List<EmployeeRef> employees;

		public java.util.List<EmployeeRef> getEmployees() {
			return employees;
		}
	}

	@Test
	public void testManagerSpecialization() {
		String sql = PojoQuery.build(EmployeeDetail.class).toStatement().getSql();
		
		System.out.println(sql);
		
		// The manager property is specialized from EmployeeRef (in Employee) to Employee (in EmployeeDetail)
		// The generated SQL should include all Employee fields for manager, not just EmployeeRef fields.
		// The specialized manager property should completely replace the parent's version - no duplicates.
		assertEquals(norm("""
			SELECT
			`employee`.`id` AS `employee.id`,
			`employee`.`name` AS `employee.name`,
			`manager`.`id` AS `manager.id`,
			`manager`.`name` AS `manager.name`,
			`employee`.`salary` AS `employee.salary`,
			`department`.`id` AS `department.id`,
			`department`.`name` AS `department.name`,
			`employee`.`hireDate` AS `employee.hireDate`,
			`manager`.`id` AS `manager.id`,
			`manager`.`name` AS `manager.name`,
			`manager.manager`.`id` AS `manager.manager.id`,
			`manager.manager`.`name` AS `manager.manager.name`,
			`manager`.`salary` AS `manager.salary`,
			`manager.department`.`id` AS `manager.department.id`,
			`manager.department`.`name` AS `manager.department.name`
			FROM `employee` AS `employee`
			LEFT JOIN `employee` AS `manager` ON `employee`.`manager_id` = `manager`.`id`
			LEFT JOIN `department` AS `department` ON `employee`.`department_id` = `department`.`id`
			LEFT JOIN `employee` AS `manager` ON `employee`.`manager_id` = `manager`.`id`
			LEFT JOIN `employee` AS `manager.manager` ON `manager`.`manager_id` = `manager.manager`.`id`
			LEFT JOIN `department` AS `manager.department` ON `manager`.`department_id` = `manager.department`.`id`
			"""), norm(sql));
	}

}
