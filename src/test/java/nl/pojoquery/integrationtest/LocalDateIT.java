package nl.pojoquery.integrationtest;

import java.time.LocalDate;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.integrationtest.db.TestDatabase;

public class LocalDateIT {

	@Table("user")
	public static class User {
		@Id
		Long id;
		LocalDate dateOfBirth;
	}
	
	@Test
	public void testInserts() {
		DataSource db = initDatabase();

		User u = new User();
		u.dateOfBirth = LocalDate.of(2015, 4, 15);
		PojoQuery.insert(db, u);
		Assert.assertEquals((Long)1L, u.id);
		
		User loaded = PojoQuery.build(User.class).findById(db, u.id);
		Assert.assertEquals(LocalDate.of(2015, 4, 15), loaded.dateOfBirth);
	}
	
	private static DataSource initDatabase() {
		DataSource db = TestDatabase.dropAndRecreate();
		DB.executeDDL(db, "CREATE TABLE user (id BIGINT NOT NULL AUTO_INCREMENT, dateOfBirth DATE, PRIMARY KEY (id))");
		return db;
	}
	
}
