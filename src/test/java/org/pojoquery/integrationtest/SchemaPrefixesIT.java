package org.pojoquery.integrationtest;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.schema.SchemaGenerator;

// Uses HSQLDB-specific CREATE SCHEMA syntax and builds its own HSQLDB DataSource.
@EnabledIfSystemProperty(named = "test.database", matches = "hsqldb")
public class SchemaPrefixesIT {

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
			DB.executeDDL(dataSource, "CREATE SCHEMA \"" + schema + "\" AUTHORIZATION DBA");
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
	// @Disabled("DB.upsert uses MySQL-specific ON DUPLICATE KEY UPDATE syntax; needs HSQLDB MERGE support")
	public void testCrud() {
		DataSource db = dropAndRecreate();
		SchemaGenerator.createTables(db, Article.class, Book.class);
		
		DB.withConnection(db, c -> {
			List<Map<String, Object>> results;
			
			DB.insert(
				c,
				"schema1",
				"article",
				Map.of(
					"title", "How to awesomize stuff"
				)
			);
			results = DB.queryRows(c, "SELECT \"title\" FROM \"schema1\".\"article\" WHERE \"id\"=1");
			Assertions.assertEquals(1, results.size());
			// HSQLDB returns column names in uppercase
			Assertions.assertEquals("How to awesomize stuff", results.get(0).get("title"));
			// Use update instead of upsert since we know the record exists
			DB.update(
				c,
				"schema1",
				"article",
				Map.of(
					"title", "How to awesomize stuff even better"
				),
				Map.of(
					"id", 1
				)
			);
			results = DB.queryRows(c, "SELECT \"title\" FROM \"schema1\".\"article\" WHERE \"id\"=1");
			Assertions.assertEquals(1, results.size());
			Assertions.assertEquals("How to awesomize stuff even better", results.get(0).get("title"));
			DB.update(
				c,
				"schema1",
				"article",
				Map.of(
					"title", "How to awesomize stuff to the max"
				),
				Map.of(
					"id", 1
				)
			);

			results = DB.queryRows(c, "SELECT \"title\" FROM \"schema1\".\"article\" WHERE \"id\"=1");
			Assertions.assertEquals(1, results.size());
			Assertions.assertEquals("How to awesomize stuff to the max", results.get(0).get("title"));

			DB.insert(c, "schema1", "article", Map.of("id", 2, "title", "Part II - how to make sure stuff works"));
			DB.insert(c, "schema2", "book", Map.of("id", 1, "title", "Great lessons from the beyond"));

			DB.update(c, new SqlExpression("UPDATE \"schema1\".\"article\" SET \"book_id\"=1"));

			List<Book> books = PojoQuery.build(Book.class).execute(c);

			Assertions.assertEquals(1, books.size());
			Assertions.assertEquals(2, books.get(0).articles.length);
		});
	}
}
