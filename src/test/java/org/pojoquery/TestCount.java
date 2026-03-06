package org.pojoquery;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.UseDialect;

@UseDialect(Dialect.MYSQL)
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
	@Disabled("refactoring in progress")
	public void testCount() {
		// SqlExpression countStatement = PojoQuery.build(Article.class).buildCountStatement();
		// Assertions.assertEquals(TestUtils.norm("SELECT COUNT(DISTINCT article.id) FROM `article` AS `article`"), TestUtils.norm(countStatement.getSql()));
	}
	
	@Test
	@Disabled("refactoring in progress")
	public void testCountWithJoins() {
		// SqlExpression countStatement = PojoQuery.build(ArticleDetail.class).buildCountStatement();
		
		// Assertions.assertEquals(norm("""
		// 		SELECT
		// 		 COUNT(DISTINCT article.id)
		// 		FROM `article` AS `article`
		// 		 LEFT JOIN `comment` AS `comments` ON `article`.`id` = `comments`.`article_id`
		// 		"""), TestUtils.norm(countStatement.getSql()));
	}
}
