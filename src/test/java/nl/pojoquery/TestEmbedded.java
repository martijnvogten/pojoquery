package nl.pojoquery;

import static nl.pojoquery.TestUtils.norm;
import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import nl.pojoquery.annotations.Embedded;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.QueryBuilder;

public class TestEmbedded {
	
	@Table("country")
	static class Country {
		@Id
		Long id;
		String name;
	}
	
	static class Address {
		String address;
		String city;
	}
	
	static class AddressWithCountry extends Address {
		Country country;
	}
	
	
	@Table("user")
	static class UserWithCountry {
		@Id
		Long id;
		
		@Embedded
		AddressWithCountry home;
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
		@Id
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
				" FROM `user` AS `user`"), norm(QueryBuilder.from(User.class).getQuery().toStatement().getSql()));
		
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
		System.out.println(p.getQuery().toStatement().getSql());
		List<ArticleDetail> articles = p.processRows(RESULT_ARTICLE_DETAIL);
		
		assertEquals("501, Broadway", articles.get(0).author.home.address);
	}
	
	@Test
	public void testEmbeddedLinkField() {
		assertEquals(
				norm("""
				SELECT
				 `user`.id AS `user.id`,
				 `user`.home_address AS `home.address`,
				 `user`.home_city AS `home.city`,
				 `home.country`.id AS `home.country.id`,
				 `home.country`.name AS `home.country.name`
				FROM `user` AS `user`
				 LEFT JOIN `country` AS `home.country` ON `user`.home_country_id = `home.country`.id
				"""), 
				norm(QueryBuilder.from(UserWithCountry.class).getQuery().toStatement().getSql()));
	}
	
	private List<Map<String, Object>> RESULT_USER_WITH_COUNTRY = Collections.singletonList(TestUtils.<String,Object>map(
			"user.id", 1L, 
			"home.address", "501, Broadway", 
			"home.city", "New York D.C.",
			"home.country.id", 1L,
			"home.country.name", "United States of America" 
			));
	
	@Test
	public void testEmbeddedLinkFieldUsingPipeline() {
		QueryBuilder<UserWithCountry> p = QueryBuilder.from(UserWithCountry.class);
		List<UserWithCountry> users = p.processRows(RESULT_USER_WITH_COUNTRY);
		
		assertEquals("United States of America", users.get(0).home.country.name);
	}

}
