package org.pojoquery.integrationtest;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Lob;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

public class BlobsIT {

	@Table("file")
	public static class File {
		@Id
		Long id;
		byte[] data;
	}
	
	@Table("article")
	public static class Article {
		@Id
		Long id;
		String title;
		@Lob
		String content;
	}
	
	@Test
	public void testBlobInserts() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, c -> {
			File f = new File();
			f.data = new String("Hello world").getBytes();
			PojoQuery.insert(c, f);
			Assertions.assertEquals((Long)1L, f.id);
			
			{
				File loaded = PojoQuery.build(File.class).findById(c, f.id);
				Assertions.assertEquals("Hello world", new String(loaded.data));
			}
			
			DB.update(c, SqlExpression.sql("UPDATE file SET data = ? WHERE id = ?", new byte[] {1, 2, 3}, f.id));
			
			{
				File loaded = PojoQuery.build(File.class).findById(c, f.id);
				Assertions.assertEquals(1, loaded.data[0]);
				Assertions.assertEquals(2, loaded.data[1]);
				Assertions.assertEquals(3, loaded.data[2]);
			}
		});
	}
	
	private static DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, File.class);
		return db;
	}
	
	@Test
	public void testClobInserts() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Article.class);
		
		DB.withConnection(db, (Connection c) -> {
			// Create a large text content (larger than typical VARCHAR)
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 1000; i++) {
				sb.append("This is line ").append(i).append(" of the article content.\n");
			}
			String largeContent = sb.toString();
			
			Article article = new Article();
			article.title = "Test Article";
			article.content = largeContent;
			PojoQuery.insert(c, article);
			Assertions.assertEquals((Long)1L, article.id);
			
			// Load and verify
			Article loaded = PojoQuery.build(Article.class).findById(c, article.id);
			Assertions.assertEquals("Test Article", loaded.title);
			Assertions.assertEquals(largeContent, loaded.content);
			
			// Update the CLOB
			String updatedContent = "Updated content that is much shorter.";
			DB.update(c, SqlExpression.sql("UPDATE article SET content = ? WHERE id = ?", updatedContent, article.id));
			
			Article reloaded = PojoQuery.build(Article.class).findById(c, article.id);
			Assertions.assertEquals(updatedContent, reloaded.content);
		});
	}
	
}
