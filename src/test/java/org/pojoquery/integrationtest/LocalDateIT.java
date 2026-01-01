package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.time.LocalDate;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
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

		DB.runInTransaction(db, (Connection c) -> {
			User u = new User();
			u.dateOfBirth = LocalDate.of(2015, 4, 15);
			PojoQuery.insert(c, u);
			Assert.assertEquals((Long)1L, u.id);
			
			User loaded = PojoQuery.build(User.class).findById(c, u.id);
			Assert.assertEquals(LocalDate.of(2015, 4, 15), loaded.dateOfBirth);
		});
	}
	
	private static DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class);
		return db;
	}
	
}
