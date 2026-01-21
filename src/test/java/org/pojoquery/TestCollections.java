package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.QueryBuilder;

public class TestCollections {

    @BeforeEach
	public void setup() {
		DbContext.setDefault(DbContext.forDialect(Dialect.MYSQL));
	}
	
	@Table("article")
	static class Article {
		Long id;
		String title;
	}

	@Table("task")
	static class Task {
		Long id;
		String title;
	}
	
	@Table("user")
	static class User {
		@Id
		Long id;
		
		@Link(linktable="user_roles", fetchColumn="element")
		Set<Role> roles;
	}
	
	static class UserDetail extends User {
		List<Article> articles;
	}
	
	static class UserWithTasks extends User {
		Set<Task> tasks;
	}
	
	enum Role {
		ADMIN,
		AGENT
	}
	
	@Test
	public void testBasics() {
		PojoQuery<User> pq = PojoQuery.build(User.class);
		assertEquals(
			TestUtils.norm("""
				SELECT
				 `user`.`id` AS `user.id`,
				 `roles`.`element` AS `roles.value`
				FROM `user` AS `user`
				 LEFT JOIN `user_roles` AS `roles` ON `user`.`id` = `roles`.`user_id`
				"""), 
			TestUtils.norm(pq.toSql()));
		
		List<Map<String, Object>> result = List.of(
			Map.of(
				"user.id", (Object)1L,
				"roles.value", Role.ADMIN.name()
			),
			Map.of(
				"user.id", (Object)1L, 
				"roles.value", Role.AGENT.name())
			);
		
		List<User> users = QueryBuilder.from(User.class).processRows(result);
		assertEquals(1, users.size());
		Assertions.assertTrue(users.get(0).roles.contains(Role.ADMIN));
	}
	
	@Test
	public void testCollections() {
		PojoQuery<UserDetail> pq = PojoQuery.build(UserDetail.class);
		assertEquals(
			TestUtils.norm("""
				SELECT
				 `user`.`id` AS `user.id`,
				 `roles`.`element` AS `roles.value`,
				 `articles`.`id` AS `articles.id`,
				 `articles`.`title` AS `articles.title`
				FROM `user` AS `user`
				 LEFT JOIN `user_roles` AS `roles` ON `user`.`id` = `roles`.`user_id`
				 LEFT JOIN `article` AS `articles` ON `user`.`id` = `articles`.`user_id`
				"""), 
			TestUtils.norm(pq.toSql()));
		
		List<Map<String, Object>> result = List.of(
				Map.of(
					"user.id", (Object)1L,
					"roles.value", Role.ADMIN.name(),
					"articles.id", 1L,
					"articles.title", "title"
				),
				Map.of(
					"user.id", (Object)1L, 
					"roles.value", Role.AGENT.name(),
					"articles.id", 1L,
					"articles.title", "title"
				));
			
		List<UserDetail> users = pq.processRows(result);
		Assertions.assertEquals(1, users.get(0).articles.size());
	}
	
	@Test
	public void testSets() {
		PojoQuery<UserWithTasks> pq = PojoQuery.build(UserWithTasks.class);
		assertEquals(
			TestUtils.norm("""
				SELECT
				 `user`.`id` AS `user.id`,
				 `roles`.`element` AS `roles.value`,
				 `tasks`.`id` AS `tasks.id`,
				 `tasks`.`title` AS `tasks.title`
				FROM `user` AS `user`
				 LEFT JOIN `user_roles` AS `roles` ON `user`.`id` = `roles`.`user_id`
				 LEFT JOIN `task` AS `tasks` ON `user`.`id` = `tasks`.`user_id`
				"""), 
			TestUtils.norm(pq.toSql()));
		
		List<Map<String, Object>> result = List.of(
				Map.of(
					"user.id", (Object)1L,
					"roles.value", Role.ADMIN.name(),
					"tasks.id", 1L,
					"tasks.title", "title"
				),
				Map.of(
					"user.id", (Object)1L, 
					"roles.value", Role.AGENT.name(),
					"tasks.id", 1L,
					"tasks.title", "title"
				));
			
		List<UserWithTasks> users = pq.processRows(result);
		Assertions.assertEquals(HashSet.class, users.get(0).tasks.getClass());
		Assertions.assertEquals(1, users.get(0).tasks.size());
	}
}