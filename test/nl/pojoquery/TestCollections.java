package nl.pojoquery;

import static nl.pojoquery.TestUtils.map;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.QueryBuilder;

public class TestCollections {
	
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
			TestUtils.norm("SELECT\n" + 
				" `user`.id AS `user.id`,\n" + 
				" `roles`.element AS `roles.value`\n" + 
				"FROM `user`\n" + 
				" LEFT JOIN `user_roles` AS `roles` ON `user`.id = `roles`.user_id"), 
			TestUtils.norm(pq.toSql()));
		
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
	
	@Test
	public void testCollections() {
		PojoQuery<UserDetail> pq = PojoQuery.build(UserDetail.class);
		assertEquals(
			TestUtils.norm("SELECT\n" + 
					" `user`.id AS `user.id`,\n" + 
					" `roles`.element AS `roles.value`,\n" + 
					" `articles`.id AS `articles.id`,\n" + 
					" `articles`.title AS `articles.title`\n" + 
					"FROM `user`\n" + 
					" LEFT JOIN `user_roles` AS `roles` ON `user`.id = `roles`.user_id\n" + 
					" LEFT JOIN `article` AS `articles` ON `user`.id = `articles`.user_id"), 
			TestUtils.norm(pq.toSql()));
		
		List<Map<String, Object>> result = Arrays.asList(
				map(
					"user.id", (Object)1L,
					"roles.value", Role.ADMIN.name(),
					"articles.id", 1L,
					"articles.title", "title"
				),
				map(
					"user.id", (Object)1L, 
					"roles.value", Role.AGENT.name(),
					"articles.id", 1L,
					"articles.title", "title"
				));
			
		List<UserDetail> users = pq.processRows(result);
		Assert.assertEquals(1, users.get(0).articles.size());
	}
	
	@Test
	public void testSets() {
		PojoQuery<UserWithTasks> pq = PojoQuery.build(UserWithTasks.class);
		assertEquals(
			TestUtils.norm("SELECT\n" + 
					" `user`.id AS `user.id`,\n" + 
					" `roles`.element AS `roles.value`,\n" + 
					" `tasks`.id AS `tasks.id`,\n" + 
					" `tasks`.title AS `tasks.title`\n" + 
					"FROM `user`\n" + 
					" LEFT JOIN `user_roles` AS `roles` ON `user`.id = `roles`.user_id\n" + 
					" LEFT JOIN `task` AS `tasks` ON `user`.id = `tasks`.user_id"), 
			TestUtils.norm(pq.toSql()));
		
		List<Map<String, Object>> result = Arrays.asList(
				map(
					"user.id", (Object)1L,
					"roles.value", Role.ADMIN.name(),
					"tasks.id", 1L,
					"tasks.title", "title"
				),
				map(
					"user.id", (Object)1L, 
					"roles.value", Role.AGENT.name(),
					"tasks.id", 1L,
					"tasks.title", "title"
				));
			
		List<UserWithTasks> users = pq.processRows(result);
		Assert.assertEquals(HashSet.class, users.get(0).tasks.getClass());
		Assert.assertEquals(1, users.get(0).tasks.size());
	}
}