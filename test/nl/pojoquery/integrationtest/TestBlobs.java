package nl.pojoquery.integrationtest;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.integrationtest.db.TestDatabase;

public class TestBlobs {

	@Table("file")
	public static class File {
		@Id
		Long id;
		byte[] data;
	}
	
	@Test
	public void testInserts() {
		DataSource db = initDatabase();
		
		File f = new File();
		f.data = new String("Hello world").getBytes();
		PojoQuery.insert(db, f);
		Assert.assertEquals((Long)1L, f.id);
		
		File loaded = PojoQuery.build(File.class).findById(db, f.id);
		Assert.assertEquals("Hello world", new String(loaded.data));
		
	}
	
	private static DataSource initDatabase() {
		DataSource db = TestDatabase.dropAndRecreate();
		DB.executeDDL(db, "CREATE TABLE file (id BIGINT NOT NULL AUTO_INCREMENT, data BLOB, PRIMARY KEY (id))");
		return db;
	}
	
}
