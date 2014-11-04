package nl.pojoquery;

import static nl.pojoquery.TestUtils.norm;
import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import nl.pojoquery.annotations.Embedded;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.QueryBuilder;

import org.junit.Test;

public class TestEmbedded {

	static class Address {
		String address;
		String city;
	}
	
	@Table("user")
	static class User {
		@Id
		Long id;
		
		@Embedded
		Address home;
	}
	
	@Table("article")
	static class ArticleDetail {
		Long id;
		String title;
		User author;
	}
	
	private List<Map<String, Object>> RESULT_ARTICLE_DETAIL = Collections.singletonList(TestUtils.<String,Object>map(
			"article.id", 1L, 
			"article.title", "The title", 
			"author.id", 1L, 
			"author.home.address", "501, Broadway", 
			"author.home.city", "New York D.C."));
	
	@Test
	public void testBasics() {
		assertEquals(
				norm("SELECT" +
				" `user`.id AS `user.id`," +
				" `user`.home_address AS `home.address`," +
				" `user`.home_city AS `home.city`" +
				" FROM user"), norm(QueryBuilder.from(User.class).getQuery().toStatement().getSql()));
		
		List<Map<String, Object>> result = Collections.singletonList(TestUtils.<String,Object>map(
				"user.id", 1L, 
				"home.address", "501, Broadway", 
				"home.city", "New York D.C."));
		
		List<User> users = QueryBuilder.from(User.class).processRows(result);
		assertEquals("501, Broadway", users.get(0).home.address);
	}
	
	@Test
	public void testUsingPipeline() {
		QueryBuilder<ArticleDetail> p = QueryBuilder.from(ArticleDetail.class);
		List<ArticleDetail> articles = p.processRows(RESULT_ARTICLE_DETAIL);
		
		assertEquals("501, Broadway", articles.get(0).author.home.address);
	}
}
