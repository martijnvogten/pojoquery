package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.DB.Transaction;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

public class InsertsIT {

	@Table("user")
	public static class User {
		@Id
		Long id;
	}
	
	@Table("file")
	public static class File {
		@Id
		Long id;
		byte[] data;
	}
	
	@Test
	public void testInserts() {
		DataSource db = initDatabase();
		
		DB.runInTransaction(db, (Connection c) -> {
			User u = new User();
			PojoQuery.insert(c, u);
			Assertions.assertEquals((Long)1L, u.id);
		});
	}
	
	@Test
	public void testRollback() {
		DataSource db = initDatabase();
		
		try {
			DB.runInTransaction(db, new Transaction<Void>() {
				@Override
				public Void run(Connection connection) {
					User u = new User();
					PojoQuery.insert(connection, u);
					Assertions.assertEquals((Long)1L, u.id);
					throw new RuntimeException("error");
				}
			});
		} catch (RuntimeException e) {
			
		}
		List<User> users = PojoQuery.build(User.class).execute(db);
		Assertions.assertEquals(0, users.size());
	}

	private static DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class);
		return db;
	}
	
}
