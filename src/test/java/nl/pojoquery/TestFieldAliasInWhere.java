package nl.pojoquery;

import static nl.pojoquery.TestUtils.norm;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.JoinCondition;
import nl.pojoquery.annotations.Select;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.CustomQueryBuilder.DefaultSqlQuery;
import nl.pojoquery.pipeline.QueryBuilder;

public class TestFieldAliasInWhere {

	@Table("article")
	static class Article {
		@Id
		public Long id;
		
		@Select("LOWER(title)")
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
		DefaultSqlQuery query = QueryBuilder.from(Article.class).getQuery();
		query.addWhere("{article.title} = 'test'");
		System.out.println(query.getFields());
		assertEquals(
				norm("""
						SELECT
						 `article`.id AS `article.id`,
						 LOWER(title) AS `article.title`,
						 `authors`.name AS `authors.name`
						FROM `article` AS `article`
						 LEFT JOIN `person` AS `authors` ON `article`.authorName=`authors`.name
						WHERE (LOWER(title)) = 'test'
						"""), 
				norm(query.toStatement().getSql()));
	}
}
