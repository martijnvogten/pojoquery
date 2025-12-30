package org.pojoquery.integrationtest;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabase;
import org.pojoquery.schema.SchemaGenerator;

public class SchemaPrefixesIT {

	@BeforeClass
	public static void setupDbContext() {
		TestDatabase.initDbContext();
	}

	private static String[] schemas = new String[]{
		"schema1",
		"schema2",
		"schema3"
	};

	public static DataSource dropAndRecreate() {
		// Create a unique in-memory HSQLDB database for schema tests
		JDBCDataSource dataSource = new JDBCDataSource();
		dataSource.setUrl("jdbc:hsqldb:mem:schematest_" + System.nanoTime());
		dataSource.setUser("SA");
		dataSource.setPassword("");

		// HSQLDB uses CREATE SCHEMA
		for (String schema : schemas) {
			DB.executeDDL(dataSource, "CREATE SCHEMA " + schema + " AUTHORIZATION DBA");
		}

		return dataSource;
	}


	@Table(value="article", schema="schema1")
	static class Article {
		@Id
		public Long id;
		public String title;
	}

	@Table(value="book", schema="schema2")
	static class Book {
		@Id
		public Long id;
		public String title;
		public Article[] articles;
	}

	@Test
	// @Ignore("DB.insertOrUpdate uses MySQL-specific ON DUPLICATE KEY UPDATE syntax; needs HSQLDB MERGE support")
	public void testCrud() {
		List<Map<String, Object>> results;

		DataSource db = dropAndRecreate();
		SchemaGenerator.createTables(db, Article.class, Book.class);
		DB.insert(
			db,
			"schema1",
			"article",
			Map.of(
				"title", "How to awesomize stuff"
			)
		);
		results = DB.queryRows(db, "SELECT title FROM schema1.article WHERE id=1");
		Assert.assertEquals(1, results.size());
		// HSQLDB returns column names in uppercase
		Assert.assertEquals("How to awesomize stuff", results.get(0).get("TITLE"));
		// Use update instead of insertOrUpdate since we know the record exists
		DB.update(
			db,
			"schema1",
			"article",
			Map.of(
				"title", "How to awesomize stuff even better"
			),
			Map.of(
				"id", 1
			)
		);
		results = DB.queryRows(db, "SELECT title FROM schema1.article WHERE id=1");
		Assert.assertEquals(1, results.size());
		Assert.assertEquals("How to awesomize stuff even better", results.get(0).get("TITLE"));
		DB.update(
			db,
			"schema1",
			"article",
			Map.of(
				"title", "How to awesomize stuff to the max"
			),
			Map.of(
				"id", 1
			)
		);

		results = DB.queryRows(db, "SELECT title FROM schema1.article WHERE id=1");
		Assert.assertEquals(1, results.size());
		Assert.assertEquals("How to awesomize stuff to the max", results.get(0).get("TITLE"));

		DB.insert(db, "schema1", "article", Map.of("id", 2, "title", "Part II - how to make sure stuff works"));
		DB.insert(db, "schema2", "book", Map.of("id", 1, "title", "Great lessons from the beyond"));

		DB.update(db, new SqlExpression("UPDATE schema1.article SET book_id=1"));

		List<Book> books = PojoQuery.build(Book.class).execute(db);

		Assert.assertEquals(1, books.size());
		Assert.assertEquals(2, books.get(0).articles.length);
	}
}
