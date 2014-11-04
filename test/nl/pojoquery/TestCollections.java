package nl.pojoquery;

import static nl.pojoquery.TestUtils.map;
import static nl.pojoquery.TestUtils.norm;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.QueryBuilder;

import org.junit.Assert;
import org.junit.Test;

public class TestCollections {

	@Table("user")
	static class User {
		@Id
		Long id;
		
		@Link(linktable="user_roles", fetchColumn="element")
		Set<Role> roles;
	}
	
	enum Role {
		ADMIN,
		AGENT
	}
	
	@Test
	public void testBasics() {
		
		assertEquals(
				norm("SELECT" +
				" `user`.id AS `user.id`," +
				" `roles`.element AS `roles.value`" +
				" FROM user" +
				" LEFT JOIN user_roles AS `roles` ON `user`.id = `roles`.user_id"), 
			norm(QueryBuilder.from(User.class).toStatement().getSql()));
		
		List<Map<String, Object>> result = Arrays.asList(
			map(
				"user.id", (Object)1L,
				"roles.value", Role.ADMIN.name()
			),
			map(
				"user.id", (Object)1L, 
				"roles.value", Role.AGENT.name())
			);
		
		List<User> users = QueryBuilder.from(User.class).processRows(result);
		assertEquals(1, users.size());
		Assert.assertTrue(users.get(0).roles.contains(Role.ADMIN));
	}
	
}