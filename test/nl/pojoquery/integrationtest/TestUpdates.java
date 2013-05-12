package nl.pojoquery.integrationtest;

import javax.sql.DataSource;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;

import org.junit.Assert;
import org.junit.Test;

public class TestUpdates {
	
	@Table("user")
	static class User {
		@Id
		Long id;
		String username;
	}

	@Test
	public void testUpdates() {
		DataSource connection = MysqlDatabases.createDatabase("localhost", "pojoquery_integrationtest", "root", "");
		DB.executeDDL(connection, "CREATE TABLE user (id BIGINT NOT NULL AUTO_INCREMENT, username VARCHAR(255), PRIMARY KEY (id));");

		User u = new User();
		PojoQuery.insert(connection, u);
		Assert.assertEquals((Long)1L, u.id);
		
		u.username = "john";
		PojoQuery.update(connection, u);
		
		User loaded = PojoQuery.build(User.class).findById(connection, u.id);
		Assert.assertEquals("john", loaded.username);
	}
	
}