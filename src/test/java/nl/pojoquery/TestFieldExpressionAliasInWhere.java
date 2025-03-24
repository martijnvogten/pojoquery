package nl.pojoquery;

import static nl.pojoquery.TestUtils.norm;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Select;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;
import nl.pojoquery.pipeline.QueryBuilder;

public class TestFieldExpressionAliasInWhere {

	@Table("article")
	static class Article {
		@Id
		public Long id;
		
		@Select("LOWER({this}.title)")
		public String title;
		
		public Person author;
	}
	
	@Table("person")
	static class Person {
		@Id
		public Long id;
		public String firstName;
		public String lastName;
		
		@Select("CONCAT({this}.firstName, ' ', {this}.lastName)")
		public String fullName;
	}
	
	@Table("book")
	static class Book {
		@Id
		public Long id;
		public Article[] articles;
	}
	
	@Test
	public void testAliases() {
		
		DefaultSqlQuery query = QueryBuilder.from(Article.class).getQuery();
		query.addWhere("{author.fullName} = ?", "Jane Doe");
		
		assertEquals(
				norm("""
					SELECT
					 `article`.id AS `article.id`,
					 LOWER(`article`.title) AS `article.title`,
					 `author`.id AS `author.id`,
					 `author`.firstName AS `author.firstName`,
					 `author`.lastName AS `author.lastName`,
					 CONCAT(`author`.firstName, ' ', `author`.lastName) AS `author.fullName`
					FROM `article` AS `article`
					 LEFT JOIN `person` AS `author` ON `article`.author_id = `author`.id
					WHERE CONCAT(`author`.firstName, ' ', `author`.lastName) = ?
					"""), 
				norm(query.toStatement().getSql()));
	}
	
	@Test
	public void testAliasesDeeper() {
		
		DefaultSqlQuery query = QueryBuilder.from(Book.class).getQuery();
		query.addWhere("{articles.author.fullName} = ?", "Jane Doe");
		
		assertEquals(
				norm("""
					SELECT
					 `book`.id AS `book.id`,
					 `articles`.id AS `articles.id`,
					 LOWER(`articles`.title) AS `articles.title`,
					 `articles.author`.id AS `articles.author.id`,
					 `articles.author`.firstName AS `articles.author.firstName`,
					 `articles.author`.lastName AS `articles.author.lastName`,
					 CONCAT(`articles.author`.firstName, ' ', `articles.author`.lastName) AS `articles.author.fullName`
					FROM `book` AS `book`
					 LEFT JOIN `article` AS `articles` ON `book`.id = `articles`.book_id
					 LEFT JOIN `person` AS `articles.author` ON `articles`.author_id = `articles.author`.id
					WHERE CONCAT(`articles.author`.firstName, ' ', `articles.author`.lastName) = ?
					"""), 
				norm(query.toStatement().getSql()));
	}
}
