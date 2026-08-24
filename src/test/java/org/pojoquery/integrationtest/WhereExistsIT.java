package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.internal.MappingException;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Exercises {@link PojoQuery#whereExists} and {@link PojoQuery#whereNotExists}.
 * The distinguishing behaviour under test is that a semi-join filters root rows
 * by collection contents <em>without</em> truncating the eagerly loaded
 * collection, in contrast to a plain {@code addWhere} against a LEFT-joined
 * collection alias which both filters and truncates.
 */
public class WhereExistsIT {

	@Table("app_user")
	static class User {
		@Id Long id;
		String username;
		@Link(linktable = "user_role")
		List<Role> roles = new ArrayList<>();
	}

	@Table("role")
	static class Role {
		@Id Long id;
		String rolename;
		@Link(linktable = "role_permission")
		List<Permission> permissions = new ArrayList<>();
	}

	@Table("permission")
	static class Permission {
		@Id Long id;
		String permissionname;
	}

	@Table("author")
	static class Author {
		@Id Long id;
		String name;
	}

	static class AuthorWithBooks extends Author {
		List<Book> books = new ArrayList<>();
	}

	@Table("book")
	static class Book {
		@Id Long id;
		String title;
	}

	@Test
	public void whereExistsManyToManyFiltersRootWithoutTruncatingCollection() {
		DataSource db = initDatabase();

		List<User> result = PojoQuery.build(User.class)
				.whereExists("{roles.rolename} = ?", "admin")
				.addOrderBy("{app_user.username}")
				.execute(db);

		Assertions.assertEquals(List.of("joe"), usernames(result),
				"only the user holding the admin role must match");
		Assertions.assertEquals(List.of("admin", "editor"), rolenames(result.get(0)),
				"the eagerly loaded roles collection must NOT be truncated by the semi-join filter");
	}

	@Test
	public void whereExistsOneToManyFiltersRootWithoutTruncatingCollection() {
		DataSource db = initDatabase();

		List<AuthorWithBooks> result = PojoQuery.build(AuthorWithBooks.class)
				.whereExists("{books.title} = ?", "Book One")
				.execute(db);

		Assertions.assertEquals(List.of("alice"), authorNames(result));
		Assertions.assertEquals(List.of("Book One", "Book Two"), bookTitles(result.get(0)),
				"matching one book must keep the whole books collection intact");
	}

	@Test
	public void whereNotExistsManyToManyReturnsComplement() {
		DataSource db = initDatabase();

		List<User> result = PojoQuery.build(User.class)
				.whereNotExists("{roles.rolename} = ?", "admin")
				.addOrderBy("{app_user.username}")
				.execute(db);

		Assertions.assertEquals(List.of("bob", "jane"), usernames(result),
				"users without the admin role, including the user with no roles at all");
	}

	@Test
	public void whereExistsLoneMarkerTestsMereCollectionMembership() {
		DataSource db = initDatabase();

		List<User> withAnyRole = PojoQuery.build(User.class)
				.whereExists("{roles.id} IS NOT NULL")
				.addOrderBy("{app_user.username}")
				.execute(db);
		Assertions.assertEquals(List.of("jane", "joe"), usernames(withAnyRole));

		List<AuthorWithBooks> withoutAnyBook = PojoQuery.build(AuthorWithBooks.class)
				.whereNotExists("{books.id}")
				.execute(db);
		Assertions.assertEquals(List.of("bob-author"), authorNames(withoutAnyBook));
	}

	@Test
	public void whereExistsNestedDepthTwoCorrelatesThroughIntermediateCollection() {
		DataSource db = initDatabase();

		List<User> result = PojoQuery.build(User.class)
				.whereExists("{roles.permissions.permissionname} = ?", "write")
				.execute(db);

		Assertions.assertEquals(List.of("joe"), usernames(result),
				"only joe has a role (admin) that grants the write permission");
		Assertions.assertEquals(List.of("admin", "editor"), rolenames(result.get(0)),
				"nested EXISTS must not truncate the top-level roles collection");
	}

	@Test
	public void parameterOrderingIsPreservedBetweenAddWhereAndWhereExists() {
		DataSource db = initDatabase();

		List<User> result = PojoQuery.build(User.class)
				.addWhere("{app_user.username} = ?", "joe")
				.whereExists("{roles.rolename} = ?", "admin")
				.execute(db);

		Assertions.assertEquals(List.of("joe"), usernames(result));
	}

	@Test
	public void unknownAliasThrowsMappingException() {
		Assertions.assertThrows(MappingException.class,
				() -> PojoQuery.build(User.class).whereExists("{nonexistent.x} = 1"));
		Assertions.assertThrows(MappingException.class,
				() -> PojoQuery.build(User.class).whereExists("{nonexistent.id}"));
	}

	@Test
	public void addWhereTruncatesWhereWhereExistsDoesNot() {
		DataSource db = initDatabase();

		// addWhere against the LEFT-joined collection alias filters the root AND
		// truncates the collection to only the matching element.
		List<User> viaAddWhere = PojoQuery.build(User.class)
				.addWhere("{roles.rolename} = ?", "admin")
				.execute(db);
		Assertions.assertEquals(List.of("joe"), usernames(viaAddWhere));
		Assertions.assertEquals(List.of("admin"), rolenames(viaAddWhere.get(0)),
				"addWhere on the joined alias truncates the collection to the matched role");

		// whereExists filters the same root rows but leaves the collection whole.
		List<User> viaExists = PojoQuery.build(User.class)
				.whereExists("{roles.rolename} = ?", "admin")
				.execute(db);
		Assertions.assertEquals(List.of("joe"), usernames(viaExists));
		Assertions.assertEquals(List.of("admin", "editor"), rolenames(viaExists.get(0)),
				"whereExists filters without truncating");
	}

	private DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Permission.class, Role.class, User.class, AuthorWithBooks.class, Book.class);

		DB.withConnection(db, (Connection c) -> {
			Permission read = insertPermission(c, "read");
			Permission write = insertPermission(c, "write");

			Role admin = insertRole(c, "admin", List.of(read, write));
			Role editor = insertRole(c, "editor", List.of(read));

			insertUser(c, "joe", List.of(admin, editor));
			insertUser(c, "jane", List.of(editor));
			insertUser(c, "bob", List.of());

			Book bookOne = new Book();
			bookOne.title = "Book One";
			Book bookTwo = new Book();
			bookTwo.title = "Book Two";
			insertAuthor(c, "alice", List.of(bookOne, bookTwo));
			insertAuthor(c, "bob-author", List.of());
			return null;
		});
		return db;
	}

	private static Permission insertPermission(Connection c, String name) {
		Permission p = new Permission();
		p.permissionname = name;
		PojoQuery.insert(c, p);
		return p;
	}

	private static Role insertRole(Connection c, String name, List<Permission> permissions) {
		Role r = new Role();
		r.rolename = name;
		r.permissions.addAll(permissions);
		PojoQuery.insert(c, r);
		return r;
	}

	private static User insertUser(Connection c, String username, List<Role> roles) {
		User u = new User();
		u.username = username;
		u.roles.addAll(roles);
		PojoQuery.insert(c, u);
		return u;
	}

	private static AuthorWithBooks insertAuthor(Connection c, String name, List<Book> books) {
		AuthorWithBooks a = new AuthorWithBooks();
		a.name = name;
		a.books.addAll(books);
		PojoQuery.insert(c, a);
		return a;
	}

	private static List<String> usernames(List<User> users) {
		return users.stream().map(u -> u.username).sorted().toList();
	}

	private static List<String> rolenames(User user) {
		return user.roles.stream().map(r -> r.rolename).sorted().toList();
	}

	private static List<String> authorNames(List<? extends Author> authors) {
		return authors.stream().map(a -> a.name).sorted().toList();
	}

	private static List<String> bookTitles(AuthorWithBooks author) {
		return author.books.stream().map(b -> b.title).sorted().toList();
	}
}
