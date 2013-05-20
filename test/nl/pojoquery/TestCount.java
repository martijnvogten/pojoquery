package nl.pojoquery;

import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;

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
		SqlExpression countStatement = PojoQuery.build(Article.class).buildCountStatement();
		Assert.assertEquals(TestUtils.norm("SELECT COUNT(DISTINCT article.id) FROM article"), TestUtils.norm(countStatement.getSql()));
	}
	
	@Test
	public void testCountWithJoins() {
		SqlExpression countStatement = PojoQuery.build(ArticleDetail.class).buildCountStatement();
		Assert.assertEquals(TestUtils.norm("SELECT COUNT(DISTINCT article.id) FROM article LEFT JOIN comment `comments` ON `comments`.article_id=`article`.id"), TestUtils.norm(countStatement.getSql()));
	}
}
