package org.pojoquery;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.annotations.Table;
import org.pojoquery.internal.MappingException;
import org.pojoquery.pipeline.QueryBuilder;

public class TestExceptions {

	@Table("user")
	class NonStaticClass {
		Long id;
	}
	
	@Table("user")
	static class User {
		Group other;
	}
	
	@Table("usergroup")
	static class Group {
		User[] users;
	}
	
	@Test
	public void testNonStaticInnerClass() {
		assertMappingException(NonStaticClass.class);
	}

	@Test
	public void testCircularReference() {
		assertMappingException(User.class);
	}
	
	private void assertMappingException(Class<?> clz) {
		boolean caught = true;
		try {
			QueryBuilder.from(clz);
		} catch (MappingException me) {
			caught = true;
		}
		Assertions.assertTrue(caught);
	}
	
}
