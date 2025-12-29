package org.pojoquery.integrationtest;

import org.junit.Assert;
import org.junit.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.MysqlDatabases;
import org.pojoquery.schema.SchemaGenerator;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

public class SchemaPrefixesIT {
	private static String[] schemas = new String[]{
		"pojoquery_integrationtest_schema1",
		"pojoquery_integrationtest_schema2",
		"pojoquery_integrationtest_schema3"
	};

	public static DataSource dropAndRecreate() {
		String host = System.getProperty("pojoquery.integrationtest.host", "localhost");
		String username = System.getProperty("pojoquery.integrationtest.username", "root");
		String password = System.getProperty("pojoquery.integrationtest.password", "");

		DataSource db = MysqlDatabases.getMysqlDataSource(host, null, username, password);

		for (String schema : schemas) {
			DB.executeDDL(db, "DROP DATABASE IF EXISTS " + schema);
			DB.executeDDL(db, "CREATE DATABASE " + schema + " DEFAULT CHARSET utf8");
		}

		return db;
	}


	@Table(value="article", schema="pojoquery_integrationtest_schema1")
	static class Article {
		@Id
		public Long id;
		public String title;
	}

	@Table(value="book", schema="pojoquery_integrationtest_schema2")
	static class Book {
		@Id
		public Long id;
		public String title;
		public Article[] articles;
	}

	@Test
	public void testCrud() {
		List<Map<String, Object>> results;

		DataSource db = dropAndRecreate();
		for (String ddl : SchemaGenerator.generateCreateTableStatements(Article.class, Book.class)) {
			DB.executeDDL(db, ddl);
		}
		DB.insert(
			db,
			"pojoquery_integrationtest_schema1",
			"article",
			Map.of(
				"title", "How to awesomize stuff"
			)
		);
		results = DB.queryRows(db, "SELECT title FROM pojoquery_integrationtest_schema1.article WHERE id=1");
		Assert.assertEquals(1, results.size());
		Assert.assertEquals("How to awesomize stuff", results.get(0).get("title"));
		DB.insertOrUpdate(
			db,
			"pojoquery_integrationtest_schema1",
			"article",
			Map.of(
				"id", 1,
				"title", "How to awesomize stuff even better"
			)
		);
		results = DB.queryRows(db, "SELECT title FROM pojoquery_integrationtest_schema1.article WHERE id=1");
		Assert.assertEquals(1, results.size());
		Assert.assertEquals("How to awesomize stuff even better", results.get(0).get("title"));
		DB.update(
			db,
			"pojoquery_integrationtest_schema1",
			"article",
			Map.of(
				"title", "How to awesomize stuff to the max"
			),
			Map.of(
				"id", 1
			)
		);

		results = DB.queryRows(db, "SELECT title FROM pojoquery_integrationtest_schema1.article WHERE id=1");
		Assert.assertEquals(1, results.size());
		Assert.assertEquals("How to awesomize stuff to the max", results.get(0).get("title"));

		DB.insert(db, "pojoquery_integrationtest_schema1", "article", Map.of("id", 2, "title", "Part II - how to make sure stuff works"));
		DB.insert(db, "pojoquery_integrationtest_schema2", "book", Map.of("id", 1, "title", "Great lessons from the beyond"));

		DB.update(db, new SqlExpression("UPDATE pojoquery_integrationtest_schema1.article SET book_id=1"));

		List<Book> books = PojoQuery.build(Book.class).execute(db);

		Assert.assertEquals(1, books.size());
		Assert.assertEquals(2, books.get(0).articles.length);
	}
}
