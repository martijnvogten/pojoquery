package org.pojoquery;

import static org.pojoquery.TestUtils.norm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.QueryBuilder;

public class TestCount {

	@Table("article")
	static class Article {
		@Id
		Long id;
		String title;
	}
	
	@Table("comment")
	static class Comment {
		@Id
		Long id;
		String comment;
	}
	
	static class ArticleDetail extends Article {
		Comment[] comments;
	}
	
	@Test
	public void testCount() {
		SqlExpression countStatement = QueryBuilder.from(Article.class).buildCountStatement();
		Assertions.assertEquals(TestUtils.norm("SELECT COUNT(DISTINCT article.id) FROM `article` AS `article`"), TestUtils.norm(countStatement.getSql()));
	}
	
	@Test
	public void testCountWithJoins() {
		SqlExpression countStatement = QueryBuilder.from(ArticleDetail.class).buildCountStatement();
		
		Assertions.assertEquals(norm("""
				SELECT
				 COUNT(DISTINCT article.id)
				FROM `article` AS `article`
				 LEFT JOIN `comment` AS `comments` ON `article`.`id` = `comments`.`article_id`
				"""), TestUtils.norm(countStatement.getSql()));
	}
}
