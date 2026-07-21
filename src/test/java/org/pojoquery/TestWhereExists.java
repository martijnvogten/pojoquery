package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.pojoquery.internal.MappingException;

@UseDialect(Dialect.MYSQL)
public class TestWhereExists {

	@Table("user")
	public static class User {
		@Id
		public Long id;
		public String username;
		@Link(linktable = "user_role")
		public List<Role> roles;
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
	}

	@Test
	public void existsManyToMany() {
		SqlExpression stmt = PojoQuery.build(User.class)
				.whereExists("roles", "{roles.rolename} = ?", "admin")
				.toStatement();

		assertTrue(norm(stmt.getSql()).contains(norm(
				"WHERE EXISTS (SELECT 1 FROM `user_role` AS `roles.user_role`, `role` AS `roles` "
						+ "WHERE `roles.user_role`.`user_id` = `user`.`id` "
						+ "AND `roles.user_role`.`role_id` = `roles`.`id` "
						+ "AND `roles`.`rolename` = ?)")),
				stmt.getSql());
		assertEquals(List.of("admin"), params(stmt));
	}

	@Test
	public void notExistsManyToMany() {
		SqlExpression stmt = PojoQuery.build(User.class)
				.whereNotExists("roles", "{roles.rolename} = ?", "admin")
				.toStatement();

		assertTrue(norm(stmt.getSql()).contains(norm(
				"WHERE NOT EXISTS (SELECT 1 FROM `user_role` AS `roles.user_role`, `role` AS `roles` "
						+ "WHERE `roles.user_role`.`user_id` = `user`.`id` "
						+ "AND `roles.user_role`.`role_id` = `roles`.`id` "
						+ "AND `roles`.`rolename` = ?)")),
				stmt.getSql());
	}

	@Test
	public void existsOneToMany() {
		SqlExpression stmt = PojoQuery.build(AuthorWithBooks.class)
				.whereExists("books", "{books.title} = ?", "Book One")
				.toStatement();

		assertTrue(norm(stmt.getSql()).contains(norm(
				"WHERE EXISTS (SELECT 1 FROM `book` AS `books` "
						+ "WHERE `books`.`author_id` = `author`.`id` "
						+ "AND `books`.`title` = ?)")),
				stmt.getSql());
	}

	@Test
	public void existsNestedDepthTwo() {
		SqlExpression stmt = PojoQuery.build(User.class)
				.whereExists("roles.permissions", "{roles.permissions.permissionname} = ?", "write")
				.toStatement();

		assertTrue(norm(stmt.getSql()).contains(norm(
				"WHERE EXISTS (SELECT 1 FROM "
						+ "`role_permission` AS `roles.permissions.role_permission`, "
						+ "`permission` AS `roles.permissions`, "
						+ "`user_role` AS `roles.user_role`, "
						+ "`role` AS `roles` "
						+ "WHERE `roles.permissions.role_permission`.`role_id` = `roles`.`id` "
						+ "AND `roles.permissions.role_permission`.`permission_id` = `roles.permissions`.`id` "
						+ "AND `roles.user_role`.`user_id` = `user`.`id` "
						+ "AND `roles.user_role`.`role_id` = `roles`.`id` "
						+ "AND `roles.permissions`.`permissionname` = ?)")),
				stmt.getSql());
	}

	@Test
	public void existsZeroCondition() {
		SqlExpression stmt = PojoQuery.build(AuthorWithBooks.class)
				.whereExists("books")
				.toStatement();

		assertTrue(norm(stmt.getSql()).contains(norm(
				"WHERE EXISTS (SELECT 1 FROM `book` AS `books` "
						+ "WHERE `books`.`author_id` = `author`.`id`)")),
				stmt.getSql());
		assertEquals(List.of(), params(stmt));
	}

	@Test
	public void parameterOrderIsPreserved() {
		SqlExpression stmt = PojoQuery.build(User.class)
				.addWhere("{user.username} = ?", "joe")
				.whereExists("roles", "{roles.rolename} = ?", "admin")
				.toStatement();

		assertEquals(List.of("joe", "admin"), params(stmt));
	}

	@Test
	public void unknownAliasThrows() {
		MappingException ex = assertThrows(MappingException.class,
				() -> PojoQuery.build(User.class).whereExists("nonexistent", "1=1"));
		assertTrue(ex.getMessage().contains("nonexistent"), ex.getMessage());
	}

	@Test
	public void nonCollectionAliasThrows() {
		assertThrows(MappingException.class,
				() -> PojoQuery.build(User.class).whereExists("user", "1=1"));
	}

	private static List<Object> params(SqlExpression stmt) {
		List<Object> result = new ArrayList<>();
		for (Object p : stmt.getParameters()) {
			result.add(p);
		}
		return result;
	}
}
