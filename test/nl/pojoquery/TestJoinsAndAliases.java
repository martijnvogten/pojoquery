package nl.pojoquery;

import static nl.pojoquery.TestUtils.norm;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.JoinCondition;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.QueryBuilder;

public class TestJoinsAndAliases {

	@Table("article")
	static class Article {
		@Id
		public Long id;
		public String title;
		
		@JoinCondition("{this}.authorName={authors}.name")
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
				norm("SELECT " + 
						" `article`.id AS `article.id`, " + 
						" `article`.title AS `article.title`, " + 
						" `authors`.name AS `authors.name` " + 
						"FROM `article`" + 
						" LEFT JOIN `person` AS `authors` ON `article`.authorName=`authors`.name"), 
				norm(QueryBuilder.from(Article.class).getQuery().toStatement().getSql()));
	}
	
	@Test
	public void testAliasesWithAnExtraJoin() {
		assertEquals(
				norm("SELECT\n" + 
						" `book`.id AS `book.id`,\n" + 
						" `articles`.id AS `articles.id`,\n" + 
						" `articles`.title AS `articles.title`,\n" + 
						" `articles.authors`.name AS `articles.authors.name`\n" + 
						"FROM `book`\n" + 
						" LEFT JOIN `article` AS `articles` ON `book`.id = `articles`.book_id\n" + 
						" LEFT JOIN `person` AS `articles.authors` ON `articles`.authorName=`articles.authors`.name"), 
				norm(QueryBuilder.from(Book.class).getQuery().toStatement().getSql()));
	}
}
