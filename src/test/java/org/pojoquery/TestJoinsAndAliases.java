package org.pojoquery;

import static org.pojoquery.TestUtils.norm;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.DbContextExtension;
import org.pojoquery.pipeline.QueryBuilder;

@ExtendWith(DbContextExtension.class)
public class TestJoinsAndAliases {

	@BeforeEach
	public void setUp() {
		DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.MYSQL));
	}

	@Table("article")
	static class Article {
		@Id
		public Long id;
		public String title;
		
		@JoinCondition("{this}.authorName = {authors}.name")
		public Person[] authors;
	}
	
	@Table("person")
	static class Person {
		public String name;
	}
	
	@Table("book")
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
				FROM `article` AS `article`
				 LEFT JOIN `person` AS `authors` ON `article`.authorName = `authors`.name
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
				FROM `book` AS `book`
				 LEFT JOIN `article` AS `articles` ON `book`.`id` = `articles`.`book_id`
				 LEFT JOIN `person` AS `articles.authors` ON `articles`.authorName = `articles.authors`.name
				"""), 
			norm(QueryBuilder.from(Book.class).getQuery().toStatement().getSql()));
	}
}
