package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.pojoquery.TestUtils.norm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.DbContextExtension;
import org.pojoquery.pipeline.QueryBuilder;

@ExtendWith(DbContextExtension.class)
public class TestSchemaPrefixes {

	@BeforeEach
	public void setUp() {
		DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.MYSQL));
	}
	
	@Table(value="article", schema="schema1")
	static class Article {
		@Id
		public Long id;
		public String title;
		
		@JoinCondition("{this}.authorName={authors}.name")
		public Person[] authors;
	}
	
	@Table(value="person", schema="schema2")
	static class Person {
		@Id
		public String name;
	}
	
	@Table(value="book", schema="schema3")
	static class Book {
		@Id
		public Long id;
		public Article[] articles;
	}
	
	@Test
	public void testAliases() {
		assertEquals(
				norm("""
					SELECT
					 `article`.`id` AS `article.id`,
					 `article`.`title` AS `article.title`,
					 `authors`.`name` AS `authors.name`
					FROM `schema1`.`article` AS `article`
					 LEFT JOIN `schema2`.`person` AS `authors` ON `article`.authorName=`authors`.name
					"""),
				norm(QueryBuilder.from(Article.class).getQuery().toStatement().getSql()));
	}
	
	@Test
	public void testAliasesWithAnExtraJoin() {
		assertEquals(
				norm("""
					SELECT
					 `book`.`id` AS `book.id`,
					 `articles`.`id` AS `articles.id`,
					 `articles`.`title` AS `articles.title`,
					 `articles.authors`.`name` AS `articles.authors.name`
					FROM `schema3`.`book` AS `book`
					 LEFT JOIN `schema1`.`article` AS `articles` ON `book`.`id` = `articles`.`book_id`
					 LEFT JOIN `schema2`.`person` AS `articles.authors` ON `articles`.authorName=`articles.authors`.name
					"""),
				norm(QueryBuilder.from(Book.class).getQuery().toStatement().getSql()));
	}
	
	@Test
	public void testAliasesWithAnExtraJoinWithoutObjectQuoting() {
		DbContext context = DbContext.builder()
			.dialect(DbContext.Dialect.MYSQL)
			.quoteObjectNames(false)
			.build();
		
		assertEquals(
				norm("""
					SELECT
					 `book`.id AS `book.id`,
					 `articles`.id AS `articles.id`,
					 `articles`.title AS `articles.title`,
					 `articles.authors`.name AS `articles.authors.name`
					FROM schema3.book AS `book`
					 LEFT JOIN schema1.article AS `articles` ON `book`.id = `articles`.book_id
					 LEFT JOIN schema2.person AS `articles.authors` ON `articles`.authorName=`articles.authors`.name
					"""),
				norm(QueryBuilder.from(context, Book.class).getQuery().toStatement().getSql()));
	}
}
