package org.pojoquery.integrationtest;

import java.time.LocalDate;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabase;
import org.pojoquery.schema.SchemaGenerator;

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
		SchemaGenerator.createTables(db, User.class);
		return db;
	}
	
}
