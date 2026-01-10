package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.pojoquery.TestUtils.norm;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.DbContextExtension;
import org.pojoquery.pipeline.QueryBuilder;

@ExtendWith(DbContextExtension.class)
public class TestEnums {

	@BeforeEach
	public void setUp() {
		DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.MYSQL));
	}

	@Table("user")
	static class User {
		@Id
		Long id;
		
		@Link(linktable="user_roles", fetchColumn="element")
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
		
	@Test
	public void testBasics() {
		assertEquals(
			norm("""
				SELECT
				 `user`.`id` AS `user.id`,
				 `roles`.`element` AS `roles.value`,
				 `user`.`state` AS `user.state`
				 FROM `user` AS `user`
				 LEFT JOIN `user_roles` AS `roles` ON `user`.`id` = `roles`.`user_id`
				"""), 
			norm(QueryBuilder.from(User.class).toStatement().getSql()));
		
		List<Map<String, Object>> result = List.of(
			Map.of(
				"user.id", (Object)1L,
				"user.state", State.EMPLOYED.name(),
				"roles.value", Role.ADMIN.name()
			),
			Map.of(
				"user.id", (Object)1L, 
				"user.state", State.EMPLOYED.name(), 
				"roles.value", Role.AGENT.name())
			);
		
		List<User> users = QueryBuilder.from(User.class).processRows(result);
		
		assertEquals(1, users.size());
		assertEquals(Role.ADMIN, users.get(0).roles[0]);
		assertEquals(Role.AGENT, users.get(0).roles[1]);
		assertEquals(State.EMPLOYED, users.get(0).state);
	}
	
}