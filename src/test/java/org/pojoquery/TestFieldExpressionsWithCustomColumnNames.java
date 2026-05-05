package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.pojoquery.TestUtils.norm;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Select;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.UseDialect;
import org.pojoquery.pipeline.DefaultSqlQuery;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.schema.SchemaGenerator;

@UseDialect(Dialect.HSQLDB)
public class TestFieldExpressionsWithCustomColumnNames {
	
	private DataSource db;
	
	@BeforeEach
	void setUp() {
		JDBCDataSource ds = new JDBCDataSource();
		ds.setUrl("jdbc:hsqldb:mem:field_expr_test_" + System.nanoTime());
		ds.setUser("SA");
		ds.setPassword("");
		db = ds;
		DbContext.setDefault(DbContext.forDialect(Dialect.HSQLDB));
		
		// Create tables for all entity classes
		SchemaGenerator.createTables(db, Person.class, Article.class, Book.class);
	}

	@Table("article")
	static class Article {
		@Id
		@FieldName("article_id")
		public Long id;
		
		// Use Java field name inside {} - will resolve to quoted column "title"
		@Select("LOWER({this.title})")
		public String title;
		
		public Person author;
	}
	
	@Table("person")
	static class Person {
		@Id
		@FieldName("person_id")
		public Long id;
		@FieldName("first_name")
		public String firstName;
		@FieldName("last_name")
		public String lastName;
		
		// Use Java field names inside {} - will resolve to quoted columns "first_name" and "last_name"
		@Select("CONCAT({this.firstName}, ' ', {this.lastName})")
		public String fullName;
	}
	
	@Table("book")
	static class Book {
		@Id
		@FieldName("book_id")
		public Long id;
		public Article[] articles;
	}
	
	@Test
	public void testFindById() {
		SqlQuery<DefaultSqlQuery> query = PojoQuery.build(Article.class).getQuery();
		query.addWhere("{this.id} = ?", 1L);

		// {this.id} uses Java field name 'id' which resolves to @FieldName column 'article_id'
		assertEquals(
				norm("""
					SELECT
					 "article"."article_id" AS "article.id",
					 LOWER("article"."title") AS "article.title",
					 "author"."person_id" AS "author.id",
					 "author"."first_name" AS "author.firstName",
					 "author"."last_name" AS "author.lastName",
					 CONCAT("author"."first_name", ' ', "author"."last_name") AS "author.fullName"
					FROM "article" AS "article"
					 LEFT JOIN "person" AS "author" ON "article"."author_id" = "author"."person_id"
					WHERE "article"."article_id" = ?
					"""), 
				norm(query.toStatement().getSql()));
	}
	
	@Test
	public void testFindByTitle() {
		SqlQuery<DefaultSqlQuery> query = PojoQuery.build(Article.class).getQuery();
		query.addWhere("{this.title} = ?", "some title");

		assertEquals(
				norm("""
					SELECT
					 "article"."article_id" AS "article.id",
					 LOWER("article"."title") AS "article.title",
					 "author"."person_id" AS "author.id",
					 "author"."first_name" AS "author.firstName",
					 "author"."last_name" AS "author.lastName",
					 CONCAT("author"."first_name", ' ', "author"."last_name") AS "author.fullName"
					FROM "article" AS "article"
					 LEFT JOIN "person" AS "author" ON "article"."author_id" = "author"."person_id"
					WHERE LOWER("article"."title") = ?
					"""), 
				norm(query.toStatement().getSql()));
	}
	
	@Test
	public void testFindByIdExecute() {
		DB.withConnection(db, (Connection c) -> {
			PojoQuery<Article> query = PojoQuery.build(Article.class);
			query.addWhere("{this.id} = ?", 1L);
			
			// Verify query executes without parse errors (empty result expected)
			List<Article> result = assertDoesNotThrow(() -> query.execute(c));
			assertEquals(0, result.size());
		});
	}

	@Test
	public void testAliases() {
		
		SqlQuery<DefaultSqlQuery> query = PojoQuery.build(Article.class).getQuery();
		query.addWhere("{author.fullName} = ?", "Jane Doe");
		
		assertEquals(
				norm("""
					SELECT
					 "article"."article_id" AS "article.id",
					 LOWER("article"."title") AS "article.title",
					 "author"."person_id" AS "author.id",
					 "author"."first_name" AS "author.firstName",
					 "author"."last_name" AS "author.lastName",
					 CONCAT("author"."first_name", ' ', "author"."last_name") AS "author.fullName"
					FROM "article" AS "article"
					 LEFT JOIN "person" AS "author" ON "article"."author_id" = "author"."person_id"
					WHERE CONCAT("author"."first_name", ' ', "author"."last_name") = ?
					"""), 
				norm(query.toStatement().getSql()));
	}
	
	@Test
	public void testAliasesExecute() {
		DB.withConnection(db, (Connection c) -> {
			PojoQuery<Article> query = PojoQuery.build(Article.class);
			query.addWhere("{author.fullName} = ?", "Jane Doe");
			
			List<Article> result = assertDoesNotThrow(() -> query.execute(c));
			assertEquals(0, result.size());
		});
	}
	
	@Test
	public void testAliasesDeeper() {
		
		SqlQuery<DefaultSqlQuery> query = PojoQuery.build(Book.class).getQuery();
		query.addWhere("{articles.author.fullName} = ?", "Jane Doe");
		
		assertEquals(
				norm("""
					SELECT
					 "book"."book_id" AS "book.id",
					 "articles"."article_id" AS "articles.id",
					 LOWER("articles"."title") AS "articles.title",
					 "articles.author"."person_id" AS "articles.author.id",
					 "articles.author"."first_name" AS "articles.author.firstName",
					 "articles.author"."last_name" AS "articles.author.lastName",
					 CONCAT("articles.author"."first_name", ' ', "articles.author"."last_name") AS "articles.author.fullName"
					FROM "book" AS "book"
					 LEFT JOIN "article" AS "articles" ON "articles"."book_id" = "book"."book_id"
					 LEFT JOIN "person" AS "articles.author" ON "articles"."author_id" = "articles.author"."person_id"
					WHERE CONCAT("articles.author"."first_name", ' ', "articles.author"."last_name") = ?
					"""), 
				norm(query.toStatement().getSql()));
	}
	
	@Test
	public void testAliasesDeeperExecute() {
		DB.withConnection(db, (Connection c) -> {
			PojoQuery<Book> query = PojoQuery.build(Book.class);
			query.addWhere("{articles.author.fullName} = ?", "Jane Doe");
			
			List<Book> result = assertDoesNotThrow(() -> query.execute(c));
			assertEquals(0, result.size());
		});
	}

	@Test
	public void testAliasesInOrderBy() {
		
		SqlQuery<DefaultSqlQuery> query = PojoQuery.build(Article.class).getQuery();
		query.addOrderBy("{author.fullName} ASC");
		
		assertEquals(
				norm("""
					SELECT
					 "article"."article_id" AS "article.id",
					 LOWER("article"."title") AS "article.title",
					 "author"."person_id" AS "author.id",
					 "author"."first_name" AS "author.firstName",
					 "author"."last_name" AS "author.lastName",
					 CONCAT("author"."first_name", ' ', "author"."last_name") AS "author.fullName"
					FROM "article" AS "article"
					 LEFT JOIN "person" AS "author" ON "article"."author_id" = "author"."person_id"
					ORDER BY CONCAT("author"."first_name", ' ', "author"."last_name") ASC
					"""), 
				norm(query.toStatement().getSql()));
	}
	
	@Test
	public void testAliasesInOrderByExecute() {
		DB.withConnection(db, (Connection c) -> {
			PojoQuery<Article> query = PojoQuery.build(Article.class);
			query.addOrderBy("{author.fullName} ASC");
			
			List<Article> result = assertDoesNotThrow(() -> query.execute(c));
			assertEquals(0, result.size());
		});
	}
	
	@Test
	public void testAliasesInOrderByDeeper() {
		
		SqlQuery<DefaultSqlQuery> query = PojoQuery.build(Book.class).getQuery();
		query.addOrderBy("{articles.author.fullName} DESC");
		
		assertEquals(
				norm("""
					SELECT
					 "book"."book_id" AS "book.id",
					 "articles"."article_id" AS "articles.id",
					 LOWER("articles"."title") AS "articles.title",
					 "articles.author"."person_id" AS "articles.author.id",
					 "articles.author"."first_name" AS "articles.author.firstName",
					 "articles.author"."last_name" AS "articles.author.lastName",
					 CONCAT("articles.author"."first_name", ' ', "articles.author"."last_name") AS "articles.author.fullName"
					FROM "book" AS "book"
					 LEFT JOIN "article" AS "articles" ON "articles"."book_id" = "book"."book_id"
					 LEFT JOIN "person" AS "articles.author" ON "articles"."author_id" = "articles.author"."person_id"
					ORDER BY CONCAT("articles.author"."first_name", ' ', "articles.author"."last_name") DESC
					"""), 
				norm(query.toStatement().getSql()));
	}
	
	@Test
	public void testAliasesInOrderByDeeperExecute() {
		DB.withConnection(db, (Connection c) -> {
			PojoQuery<Book> query = PojoQuery.build(Book.class);
			query.addOrderBy("{articles.author.fullName} DESC");
			
			List<Book> result = assertDoesNotThrow(() -> query.execute(c));
			assertEquals(0, result.size());
		});
	}
	
	@Test
	public void testAliasesInJoinCondition() {
		
		SqlQuery<DefaultSqlQuery> query = PojoQuery.build(Article.class).getQuery();
		query.addJoin(
			org.pojoquery.pipeline.SqlQuery.JoinType.LEFT,
			"audit_log",
			"audit",
			SqlExpression.sql("{audit.entity_name} = {author.fullName}")
		);
		query.addWhere("{audit.date} < ?", LocalDate.of(2024, 1, 1));
		
		assertEquals(
				norm("""
					SELECT
					 "article"."article_id" AS "article.id",
					 LOWER("article"."title") AS "article.title",
					 "author"."person_id" AS "author.id",
					 "author"."first_name" AS "author.firstName",
					 "author"."last_name" AS "author.lastName",
					 CONCAT("author"."first_name", ' ', "author"."last_name") AS "author.fullName"
					FROM "article" AS "article"
					 LEFT JOIN "person" AS "author" ON "article"."author_id" = "author"."person_id"
					 LEFT JOIN "audit_log" AS "audit" ON "audit"."entity_name" = CONCAT("author"."first_name", ' ', "author"."last_name")
					WHERE "audit"."date" < ?
					"""), 
				norm(query.toStatement().getSql()));
	}
}
