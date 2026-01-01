package org.pojoquery;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.internal.MappingException;
import org.pojoquery.pipeline.QueryBuilder;

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
			Assertions.fail();
		} catch (MappingException e) {
		}
	}
}
