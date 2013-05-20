package nl.pojoquery;

import nl.pojoquery.PojoQuery.MappingException;
import nl.pojoquery.annotations.Table;

import org.junit.Assert;
import org.junit.Test;

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
			PojoQuery.build(clz);
		} catch (MappingException me) {
			caught = true;
		}
		Assert.assertTrue(caught);
	}
	
}
