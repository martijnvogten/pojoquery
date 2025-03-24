package nl.pojoquery.integrationtest;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.integrationtest.db.TestDatabase;

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
	
	static class UserDetail extends User {
		@Link(linktable="user_roles", fetchColumn="role")
		Set<Role> roles = new HashSet<>();
	}

	@Test
	public void testUpdates() {
		DataSource db = TestDatabase.dropAndRecreate();
		DB.executeDDL(db, "CREATE TABLE user (id BIGINT NOT NULL AUTO_INCREMENT, username VARCHAR(255), PRIMARY KEY (id));");

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
		DB.executeDDL(db, "CREATE TABLE user (id BIGINT NOT NULL AUTO_INCREMENT, username VARCHAR(255), PRIMARY KEY (id));");
		DB.executeDDL(db, "CREATE TABLE article (id BIGINT NOT NULL AUTO_INCREMENT, author_id BIGINT NOT NULL, title VARCHAR(255), PRIMARY KEY (id));");
		
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
		DB.executeDDL(db, "CREATE TABLE user (id BIGINT NOT NULL AUTO_INCREMENT, username VARCHAR(255), PRIMARY KEY (id));");
		DB.executeDDL(db, "CREATE TABLE user_roles (user_id BIGINT NOT NULL, role VARCHAR(255), PRIMARY KEY (user_id, role));");
		
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