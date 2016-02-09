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
		
		@Link(linktable="user_roles", foreignvaluefield="element", resultClass=Role.class)
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
		assertEquals(
				"SELECT" +
				" `user`.id `user.id`," +
				" `user_roles`.element `roles.value`" +
				" FROM user" +
				" LEFT JOIN user_roles `user_roles` ON `user_roles`.user_id=user.id", 
			TestUtils.norm(PojoQuery.build(User.class).toSql()));
		
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
	
	@Test
	public void testCollections() {
		assertEquals(
				"SELECT "
				+ "`user`.id `user.id`, "
				+ "`user_roles`.element `roles.value`, "
				+ "`articles`.id `articles.id`, "
				+ "`articles`.title `articles.title` "
				+ "FROM user "
				+ "LEFT JOIN user_roles `user_roles` ON `user_roles`.user_id=user.id "
				+ "LEFT JOIN article `articles` ON `articles`.user_id=`user`.id", 
			TestUtils.norm(PojoQuery.build(UserDetail.class).toSql()));
		
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
			
		List<UserDetail> users = PojoQuery.processRows(result, UserDetail.class);
		Assert.assertEquals(1, users.get(0).articles.size());
	}
	
	@Test
	public void testSets() {
		assertEquals(
				"SELECT "
				+ "`user`.id `user.id`, "
				+ "`user_roles`.element `roles.value`, "
				+ "`tasks`.id `tasks.id`, "
				+ "`tasks`.title `tasks.title` "
				+ "FROM user "
				+ "LEFT JOIN user_roles `user_roles` ON `user_roles`.user_id=user.id "
				+ "LEFT JOIN task `tasks` ON `tasks`.user_id=`user`.id", 
			TestUtils.norm(PojoQuery.build(UserWithTasks.class).toSql()));
		
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
			
		List<UserWithTasks> users = PojoQuery.processRows(result, UserWithTasks.class);
		Assert.assertEquals(HashSet.class, users.get(0).tasks.getClass());
		Assert.assertEquals(1, users.get(0).tasks.size());
	}
}