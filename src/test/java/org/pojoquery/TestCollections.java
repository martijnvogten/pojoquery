package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.UseDialect;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.util.RecordIndenter;

@UseDialect(Dialect.MYSQL)
public class TestCollections {

	@Table("article")
	static class Article {
		@Id
		Long id;
		String title;
	}

	@Table("task")
	static class Task {
		@Id
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
		System.out.println(RecordIndenter.indent(AQTTransformer.buildQueryTreeForType(User.class).toString()));

		assertEquals(
			TestUtils.norm("""
				SELECT
				`user`.`id` AS `user.id`,
				`roles`.`element` AS `roles.value`
				FROM `user` AS `user`
				LEFT JOIN `user_roles` AS `roles` ON `roles`.`user_id` = `user`.`id`
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
		
		List<User> users = PojoQuery.build(User.class).processRows(result);
		assertEquals(1, users.size());
		assertEquals(2, users.get(0).roles.size());
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
				LEFT JOIN `user_roles` AS `roles` ON `roles`.`user_id` = `user`.`id`
				LEFT JOIN `article` AS `articles` ON `articles`.`user_id` = `user`.`id`
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
				LEFT JOIN `user_roles` AS `roles` ON `roles`.`user_id` = `user`.`id`
				LEFT JOIN `task` AS `tasks` ON `tasks`.`user_id` = `user`.`id`
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