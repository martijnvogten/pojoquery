package nl.pojoquery;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import nl.pojoquery.annotations.Embedded;
import nl.pojoquery.annotations.Table;

import org.junit.Test;

public class TestEmbedded {

	static class Address {
		String address;
		String city;
	}
	
	@Table("user")
	static class User {
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
	
	@Test
	@SuppressWarnings("unchecked")
	public void testBasics() {
		assertEquals(
				"SELECT" +
				" `user`.id `user.id`," +
				" `user`.home_address `home.address`," +
				" `user`.home_city `home.city`" +
				" FROM user", TestUtils.norm(PojoQuery.build(User.class).toSql()));
		
		List<Map<String, Object>> result = Arrays.asList(TestUtils.map(
				"user.id", (Object)1L, 
				"home.address", "501, Broadway", 
				"home.city", "New York D.C."));
		
		List<User> users = PojoQuery.processRows(result, User.class);
		assertEquals("501, Broadway", users.get(0).home.address);
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void testInJoinedEntity() {
		List<Map<String, Object>> result = Arrays.asList(TestUtils.map(
				"article.id", (Object)1L, 
				"article.title", "The title", 
				"author.id", (Object)1L, 
				"author.home.address", "501, Broadway", 
				"author.home.city", "New York D.C."));
		
		List<ArticleDetail> articles = PojoQuery.processRows(result, ArticleDetail.class);
		assertEquals("501, Broadway", articles.get(0).author.home.address);
		
	}

}