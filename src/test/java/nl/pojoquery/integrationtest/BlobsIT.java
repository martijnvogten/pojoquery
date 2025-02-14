package nl.pojoquery.integrationtest;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.SqlExpression;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.integrationtest.db.TestDatabase;

public class BlobsIT {

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
		
		{
			File loaded = PojoQuery.build(File.class).findById(db, f.id);
			Assert.assertEquals("Hello world", new String(loaded.data));
		}
		
		DB.update(db, SqlExpression.sql("UPDATE file SET data = ? WHERE id = ?", new byte[] {1, 2, 3}, f.id));
		
		{
			File loaded = PojoQuery.build(File.class).findById(db, f.id);
			Assert.assertEquals(1, loaded.data[0]);
			Assert.assertEquals(2, loaded.data[1]);
			Assert.assertEquals(3, loaded.data[2]);
		}
	}
	
	private static DataSource initDatabase() {
		DataSource db = TestDatabase.dropAndRecreate();
		DB.executeDDL(db, "CREATE TABLE file (id BIGINT NOT NULL AUTO_INCREMENT, data BLOB, PRIMARY KEY (id))");
		return db;
	}
	
}
