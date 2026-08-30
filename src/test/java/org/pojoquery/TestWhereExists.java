package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pojoquery.TestUtils.norm;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.UseDialect;

@UseDialect(Dialect.MYSQL)
public class TestWhereExists {

	@Table("user")
	public static class User {
		@Id
		public Long id;
		public String username;
		@Link(linktable = "user_role")
		public List<Role> roles;
		public List<Device> devices;
	}

	@Table("role")
	public static class Role {
		@Id
		public Long id;
		public String rolename;
		@Link(linktable = "role_permission")
		public List<Permission> permissions;
	}

	@Table("permission")
	public static class Permission {
		@Id
		public Long id;
		public String permissionname;
	}

	@Table("device")
	public static class Device {
		@Id
		public Long id;
		public String name;
	}

	@Table("author")
	public static class Author {
		@Id
		public Long id;
		public String name;
	}

	public static class AuthorWithBooks extends Author {
		public List<Book> books;
	}

	@Table("book")
	public static class Book {
		@Id
		public Long id;
		public String title;
		public Publisher publisher;
	}

	@Table("publisher")
	public static class Publisher {
		@Id
		public Long id;
		public String name;
	}

	@Test
	public void existsManyToMany() {
		SqlExpression stmt = PojoQuery.build(User.class)
				.whereExists("{roles.rolename} = ?", "admin")
				.toStatement();

		assertTrue(norm(stmt.getSql()).contains(norm(
				"WHERE `user`.`id` IN (SELECT `user`.`id` FROM `user` AS `user` "
						+ "LEFT JOIN `user_role` AS `roles.user_role` ON `roles.user_role`.`user_id` = `user`.`id` "
						+ "LEFT JOIN `role` AS `roles` ON `roles.user_role`.`role_id` = `roles`.`id` "
						+ "WHERE `roles`.`rolename` = ?)")),
				stmt.getSql());
		assertEquals(List.of("admin"), params(stmt));
	}

	@Test
	public void subqueryPrunesUnreferencedJoins() {
		SqlExpression stmt = PojoQuery.build(User.class)
				.whereExists("{roles.rolename} = ?", "admin")
				.toStatement();

		// The sub-query must not drag in the permissions or devices joins.
		String subquery = stmt.getSql().substring(stmt.getSql().indexOf("IN ("));
		assertFalse(subquery.contains("role_permission"), subquery);
		assertFalse(subquery.contains("`device`"), subquery);
	}

	@Test
	public void notExistsManyToMany() {
		SqlExpression stmt = PojoQuery.build(User.class)
				.whereNotExists("{roles.rolename} = ?", "admin")
				.toStatement();

		assertTrue(norm(stmt.getSql()).contains(norm(
				"WHERE `user`.`id` NOT IN (SELECT `user`.`id` FROM `user` AS `user` "
						+ "LEFT JOIN `user_role` AS `roles.user_role` ON `roles.user_role`.`user_id` = `user`.`id` "
						+ "LEFT JOIN `role` AS `roles` ON `roles.user_role`.`role_id` = `roles`.`id` "
						+ "WHERE `roles`.`rolename` = ?)")),
				stmt.getSql());
	}

	@Test
	public void existsOneToMany() {
		SqlExpression stmt = PojoQuery.build(AuthorWithBooks.class)
				.whereExists("{books.title} = ?", "Book One")
				.toStatement();

		assertTrue(norm(stmt.getSql()).contains(norm(
				"WHERE `author`.`id` IN (SELECT `author`.`id` FROM `author` AS `author` "
						+ "LEFT JOIN `book` AS `books` ON `books`.`author_id` = `author`.`id` "
						+ "WHERE `books`.`title` = ?)")),
				stmt.getSql());
	}

	@Test
	public void existsNestedDepthTwo() {
		SqlExpression stmt = PojoQuery.build(User.class)
				.whereExists("{roles.permissions.permissionname} = ?", "write")
				.toStatement();

		assertTrue(norm(stmt.getSql()).contains(norm(
				"WHERE `user`.`id` IN (SELECT `user`.`id` FROM `user` AS `user` "
						+ "LEFT JOIN `user_role` AS `roles.user_role` ON `roles.user_role`.`user_id` = `user`.`id` "
						+ "LEFT JOIN `role` AS `roles` ON `roles.user_role`.`role_id` = `roles`.`id` "
						+ "LEFT JOIN `role_permission` AS `roles.permissions.role_permission` ON `roles.permissions.role_permission`.`role_id` = `roles`.`id` "
						+ "LEFT JOIN `permission` AS `roles.permissions` ON `roles.permissions.role_permission`.`permission_id` = `roles.permissions`.`id` "
						+ "WHERE `roles.permissions`.`permissionname` = ?)")),
				stmt.getSql());
	}

	@Test
	public void existsMultiplePathsInOneCondition() {
		// The condition may reference collections on different paths; both join
		// chains end up in the same sub-query.
		SqlExpression stmt = PojoQuery.build(User.class)
				.whereExists("{roles.rolename} = ? AND {devices.name} = ?", "admin", "laptop")
				.toStatement();

		String subquery = norm(stmt.getSql().substring(stmt.getSql().indexOf("IN (")));
		assertTrue(subquery.contains(norm(
				"LEFT JOIN `role` AS `roles` ON `roles.user_role`.`role_id` = `roles`.`id`")), subquery);
		assertTrue(subquery.contains(norm(
				"LEFT JOIN `device` AS `devices` ON `devices`.`user_id` = `user`.`id`")), subquery);
		assertEquals(List.of("admin", "laptop"), params(stmt));
	}

	@Test
	public void existsReferenceInsideCollection() {
		// A reference joined below the collection can be used in the condition.
		SqlExpression stmt = PojoQuery.build(AuthorWithBooks.class)
				.whereExists("{books.publisher.name} = ?", "Acme")
				.toStatement();

		String subquery = norm(stmt.getSql().substring(stmt.getSql().indexOf("IN (")));
		assertTrue(subquery.contains(norm(
				"LEFT JOIN `book` AS `books` ON `books`.`author_id` = `author`.`id`")), subquery);
		assertTrue(subquery.contains(norm(
				"LEFT JOIN `publisher` AS `books.publisher` ON `books`.`publisher_id` = `books.publisher`.`id`")), subquery);
		assertTrue(subquery.contains(norm("WHERE `books.publisher`.`name` = ?")), subquery);
	}

	@Test
	public void existsLoneMarkerExpandsToIsNotNull() {
		SqlExpression stmt = PojoQuery.build(AuthorWithBooks.class)
				.whereExists("{books.id} IS NOT NULL")
				.toStatement();

		assertTrue(norm(stmt.getSql()).contains(norm(
				"WHERE `author`.`id` IN (SELECT `author`.`id` FROM `author` AS `author` "
						+ "LEFT JOIN `book` AS `books` ON `books`.`author_id` = `author`.`id` "
						+ "WHERE `books`.`id` IS NOT NULL)")),
				stmt.getSql());
		assertEquals(List.of(), params(stmt));
	}

	@Test
	public void notExistsLoneMarkerTestsEmptiness() {
		SqlExpression stmt = PojoQuery.build(AuthorWithBooks.class)
				.whereNotExists("{books.id} IS NOT NULL")
				.toStatement();

		assertTrue(norm(stmt.getSql()).contains(norm(
				"WHERE `author`.`id` NOT IN (SELECT `author`.`id` FROM `author` AS `author` "
						+ "LEFT JOIN `book` AS `books` ON `books`.`author_id` = `author`.`id` "
						+ "WHERE `books`.`id` IS NOT NULL)")),
				stmt.getSql());
	}

	@Test
	public void parameterOrderIsPreserved() {
		SqlExpression stmt = PojoQuery.build(User.class)
				.addWhere("{user.username} = ?", "joe")
				.whereExists("{roles.rolename} = ?", "admin")
				.toStatement();

		assertEquals(List.of("joe", "admin"), params(stmt));
	}

	private static List<Object> params(SqlExpression stmt) {
		List<Object> result = new ArrayList<>();
		for (Object p : stmt.getParameters()) {
			result.add(p);
		}
		return result;
	}
}
