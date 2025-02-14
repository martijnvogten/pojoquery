package nl.pojoquery;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.QueryBuilder;

import org.junit.Assert;
import org.junit.Test;

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
		Assert.assertEquals(TestUtils.norm("SELECT COUNT(DISTINCT article.id) FROM `article` AS `article`"), TestUtils.norm(countStatement.getSql()));
	}
	
	@Test
	public void testCountWithJoins() {
		SqlExpression countStatement = QueryBuilder.from(ArticleDetail.class).buildCountStatement();
		Assert.assertEquals(TestUtils.norm("SELECT COUNT(DISTINCT article.id) FROM `article` AS `article` LEFT JOIN `comment` AS `comments` ON `article`.id = `comments`.article_id"), TestUtils.norm(countStatement.getSql()));
	}
}
