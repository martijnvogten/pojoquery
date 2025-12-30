package org.pojoquery.integrationtest;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabase;
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
		DataSource db = TestDatabase.dropAndRecreate();
		SchemaGenerator.createTables(db, User.class);

		User u = new User();
		PojoQuery.insert(db, u);
		Assert.assertEquals((Long)1L, u.id);
		
		u.username = "john";
		PojoQuery.update(db, u);
		
		User loaded = PojoQuery.build(User.class).findById(db, u.id);
		Assert.assertEquals("john", loaded.username);
	}
	
	@Test
	public void testInsertWithAuthor() {
		DataSource db = TestDatabase.dropAndRecreate();
		SchemaGenerator.createTables(db, User.class, Article.class);
		
		User u = new User();
		u.username = "bob";
		PojoQuery.insert(db, u);
		Assert.assertEquals((Long)1L, u.id);
		
		Article a = new Article();
		a.author = u;
		a.title = "My life";
		PojoQuery.insert(db, a);
		
		Article read = PojoQuery.build(Article.class).findById(db, a.id);
		Assert.assertEquals(read.author.username, "bob");
	}
	
	@Test
	public void testInsertWithRoles() {
		DataSource db = TestDatabase.dropAndRecreate();
		SchemaGenerator.createTables(db, User.class, UserRoles.class);
		
		UserDetail u = new UserDetail();
		u.roles.add(Role.EDITOR);
		PojoQuery.insert(db, u);
		Assert.assertEquals((Long)1L, u.id);

		// Now query
		UserDetail read = PojoQuery.build(UserDetail.class).findById(db, 1L);
		Assert.assertEquals(0, read.roles.size()); // Correct, pojoquery does not update collections
		
		// Now insert the role
		DB.insert(db, "user_roles", Map.of("user_id", u.id, "role", Role.EDITOR));
		
		UserDetail read1 = PojoQuery.build(UserDetail.class).findById(db, 1L);
		Assert.assertEquals(1, read1.roles.size());
		
		u.username = "john";
		PojoQuery.update(db, u);
		
		User loaded = PojoQuery.build(User.class).findById(db, u.id);
		Assert.assertEquals("john", loaded.username);
	}
	
}