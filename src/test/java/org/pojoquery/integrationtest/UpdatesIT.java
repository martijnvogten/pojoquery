package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Cascade;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

public class UpdatesIT {
	
	@Table("user")
	static class User {
		@Id
		Long id;
		String username;
	}
	
	enum Role {
		EDITOR,
		ADMIN
	}
	
	@Table("article")
	static class Article {
		@Id
		Long id;
		User author;
		String title;
	}

	@Table("article")
	static class ArticleCascadeAuthor {
		@Id
		Long id;
		@Cascade
		User author;
		String title;
	}
	
	@Table("user_roles")
	static class UserRoles {
		@Id
		Long user_id;
		@Id
		String role;
	}
	
	static class UserDetail extends User {
		@Link(linktable="user_roles", fetchColumn="role")
		Set<Role> roles = new HashSet<>();
	}

	@Test
	public void testUpdates() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class);

		DB.withConnection(db, (Connection c) -> {
			User u = new User();
			PojoQuery.insert(c, u);
			Assertions.assertEquals((Long)1L, u.id);
			
			u.username = "john";
			PojoQuery.update(c, u);
			
			User loaded = PojoQuery.build(User.class).findById(c, u.id).orElseThrow();
			Assertions.assertEquals("john", loaded.username);
		});
	}
	
	@Test
	public void testInsertWithAuthor() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class, Article.class);
		
		DB.withConnection(db, (Connection c) -> {
			User u = new User();
			u.username = "bob";
			PojoQuery.insert(c, u);
			Assertions.assertEquals((Long)1L, u.id);
			
			Article a = new Article();
			a.author = u;
			a.title = "My life";
			PojoQuery.insert(c, a);
			
			Article read = PojoQuery.build(Article.class).findById(c, a.id).orElseThrow();
			Assertions.assertEquals(read.author.username, "bob");
		});
	}
	
	@Test
	public void testInsertWithRoles() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class, UserRoles.class);
		
		DB.withConnection(db, (Connection c) -> {
			UserDetail u = new UserDetail();
			u.roles.add(Role.EDITOR);
			PojoQuery.insert(c, u);
			Assertions.assertEquals((Long)1L, u.id);

			// Now query
			UserDetail read = PojoQuery.build(UserDetail.class).findById(c, 1L).orElseThrow();
			Assertions.assertEquals(1, read.roles.size());
			
			u.username = "john";
			PojoQuery.update(c, u);
			
			User loaded = PojoQuery.build(User.class).findById(c, u.id).orElseThrow();
			Assertions.assertEquals("john", loaded.username);
		});
	}

	@Test
	public void testUpdateDoesNotCascadeToReferencedEntityByDefault() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class, Article.class);

		DB.withConnection(db, (Connection c) -> {
			User author = new User();
			author.username = "alice";
			PojoQuery.insert(c, author);

			Article a = new Article();
			a.author = author;
			a.title = "Original title";
			PojoQuery.insert(c, a);

			// Mutate both the article and the referenced author
			a.title = "New title";
			a.author.username = "alice-MODIFIED";
			PojoQuery.update(c, a);

			// Article itself should be updated
			Article reloaded = PojoQuery.build(Article.class).findById(c, a.id).orElseThrow();
			Assertions.assertEquals("New title", reloaded.title);

			// But the referenced author should NOT have been cascade-updated
			User loadedAuthor = PojoQuery.build(User.class).findById(c, author.id).orElseThrow();
			Assertions.assertEquals("alice", loadedAuthor.username,
				"Referenced entity must not be updated unless @Cascade is present on the reference field");
		});
	}

	@Test
	public void testUpdateCascadesToReferencedEntityWhenAnnotated() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class, ArticleCascadeAuthor.class);

		DB.withConnection(db, (Connection c) -> {
			User author = new User();
			author.username = "bob";
			PojoQuery.insert(c, author);

			ArticleCascadeAuthor a = new ArticleCascadeAuthor();
			a.author = author;
			a.title = "Title";
			PojoQuery.insert(c, a);

			a.author.username = "bob-updated";
			PojoQuery.update(c, a);

			User loadedAuthor = PojoQuery.build(User.class).findById(c, author.id).orElseThrow();
			Assertions.assertEquals("bob-updated", loadedAuthor.username,
				"Referenced entity should be updated when @Cascade is present");
		});
	}
}