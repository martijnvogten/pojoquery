package nl.pojoquery;

import static nl.pojoquery.TestUtils.map;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Table;

import org.junit.Test;

public class TestEnums {

	@Table("user")
	static class User {
		@Id
		Long id;
		
		@Link(linktable="user_roles", foreignvaluefield="element")
		Role[] roles;
		
		State state;
	}
	
	enum Role {
		ADMIN,
		AGENT
	}
	
	enum State {
		EMPLOYED,
		UNEMPLOYED
	}
		
	@SuppressWarnings("unchecked")
	@Test
	public void testBasics() {
		assertEquals(
				"SELECT" +
				" `user`.id `user.id`," +
				" `user_roles`.element `roles.value`," +
				" `user`.state `user.state`" +
				" FROM user" +
				" LEFT JOIN user_roles `user_roles` ON `user_roles`.user_id=user.id", 
			TestUtils.norm(PojoQuery.build(User.class).toSql()));
		
		List<Map<String, Object>> result = Arrays.asList(
			map(
				"user.id", (Object)1L,
				"user.state", State.EMPLOYED.name(),
				"roles.value", Role.ADMIN.name()
			),
			map(
				"user.id", (Object)1L, 
				"user.state", State.EMPLOYED.name(), 
				"roles.value", Role.AGENT.name())
			);
		
		List<User> users = PojoQuery.processRows(result, User.class);
		assertEquals(1, users.size());
		assertEquals(Role.ADMIN, users.get(0).roles[0]);
		assertEquals(Role.AGENT, users.get(0).roles[1]);
		assertEquals(State.EMPLOYED, users.get(0).state);
	}
	
}