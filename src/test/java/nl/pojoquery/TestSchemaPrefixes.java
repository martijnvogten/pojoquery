package nl.pojoquery;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.JoinCondition;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.QueryBuilder;
import org.junit.Test;

import static nl.pojoquery.TestUtils.norm;
import static org.junit.Assert.assertEquals;

public class TestSchemaPrefixes {

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
				norm("SELECT " + 
						" `article`.id AS `article.id`, " + 
						" `article`.title AS `article.title`, " + 
						" `authors`.name AS `authors.name` " + 
						"FROM `schema1`.`article` AS `article`" +
						" LEFT JOIN `schema2`.`person` AS `authors` ON `article`.authorName=`authors`.name"),
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
						"FROM `schema3`.`book` AS `book`\n" +
						" LEFT JOIN `schema1`.`article` AS `articles` ON `book`.id = `articles`.book_id\n" +
						" LEFT JOIN `schema2`.`person` AS `articles.authors` ON `articles`.authorName=`articles.authors`.name"),
				norm(QueryBuilder.from(Book.class).getQuery().toStatement().getSql()));
	}
	
	@Test
	public void testAliasesWithAnExtraJoinWithoutObjectQuoting() {
		
		DbContext context = new DbContext.DefaultDbContext();
		context.setQuoteObjectNames(false);
		
		assertEquals(
				norm("SELECT\n" + 
						" `book`.id AS `book.id`,\n" + 
						" `articles`.id AS `articles.id`,\n" + 
						" `articles`.title AS `articles.title`,\n" + 
						" `articles.authors`.name AS `articles.authors.name`\n" + 
						"FROM schema3.book AS `book`\n" +
						" LEFT JOIN schema1.article AS `articles` ON `book`.id = `articles`.book_id\n" +
						" LEFT JOIN schema2.person AS `articles.authors` ON `articles`.authorName=`articles.authors`.name"),
				norm(QueryBuilder.from(context, Book.class).getQuery().toStatement().getSql()));
	}
}
