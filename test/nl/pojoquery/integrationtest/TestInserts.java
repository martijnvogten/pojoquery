package nl.pojoquery.integrationtest;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;

import nl.pojoquery.DB;
import nl.pojoquery.DB.Transaction;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.integrationtest.db.TestDatabase;

public class TestInserts {

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
		
		User u = new User();
		PojoQuery.insert(db, u);
		Assert.assertEquals((Long)1L, u.id);
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
					Assert.assertEquals((Long)1L, u.id);
					throw new RuntimeException("error");
				}
			});
		} catch (RuntimeException e) {
			
		}
		List<User> users = PojoQuery.build(User.class).execute(db);
		Assert.assertEquals(0, users.size());
	}

	private static DataSource initDatabase() {
		DataSource db = TestDatabase.dropAndRecreate();
		DB.executeDDL(db, "CREATE TABLE user (id BIGINT NOT NULL AUTO_INCREMENT, PRIMARY KEY (id))");
		return db;
	}
	
}
