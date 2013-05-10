package nl.pojoquery.integrationtest;

import javax.sql.DataSource;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;

import org.junit.Assert;
import org.junit.Test;

public class TestInserts {

	@Table("user")
	static class User {
		@Id
		Long id;
	}
	
	@Test
	public void testInserts() {
		DataSource db = MysqlDatabases.createDatabase("localhost", "pojoquery_integrationtest", "root", "");
		DB.executeDDL(db, "CREATE TABLE user (id BIGINT NOT NULL AUTO_INCREMENT, PRIMARY KEY (id));");
		
		User u = new User();
		PojoQuery.insert(db, u);
		Assert.assertEquals((Long)1L, u.id);
	}
	
}
