package nl.pojoquery;

import static nl.pojoquery.TestUtils.map;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Table;

import org.junit.Assert;
import org.junit.Test;

public class TestCollections {

	@Table("user")
	static class User {
		@Id
		Long id;
		
		@Link(linktable="user_roles", foreignvaluefield="element", resultClass=Role.class)
		Set<Role> roles;
	}
	
	enum Role {
		ADMIN,
		AGENT
	}
	
	@Test
	public void testBasics() {
		assertEquals(
				"SELECT" +
				" `user`.id `user.id`," +
				" `user_roles`.element `roles.value`" +
				" FROM user" +
				" LEFT JOIN user_roles `user_roles` ON `user_roles`.user_id=user.id", 
			TestUtils.norm(PojoQuery.build(User.class).toSql()));
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> result = Arrays.asList(
			map(
				"user.id", (Object)1L,
				"roles.value", Role.ADMIN.name()
			),
			map(
				"user.id", (Object)1L, 
				"roles.value", Role.AGENT.name())
			);
		
		List<User> users = PojoQuery.processRows(result, User.class);
		assertEquals(1, users.size());
		Assert.assertTrue(users.get(0).roles.contains(Role.ADMIN));
	}
	
}