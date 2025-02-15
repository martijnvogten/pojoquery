package nl.pojoquery;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.internal.MappingException;
import nl.pojoquery.pipeline.QueryBuilder;

public class TestDetectCycles {

	@Table("department")
	static class Department {
		@Id
		Long id;
		String name;
		List<Employee> employees;
	}
	
	@Table("employee")
	static class Employee {
		@Id
		Long id;
		String name;
		String email;
		Department department;
	}
	
	@Test
	public void testIt() {
		try {
			QueryBuilder.from(Department.class).toStatement().getSql();
			Assert.fail();
		} catch (MappingException e) {
		}
	}
}
