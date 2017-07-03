package nl.pojoquery;

import static nl.pojoquery.TestUtils.norm;
import static org.junit.Assert.assertEquals;
import nl.pojoquery.annotations.Table;

import org.junit.Test;

public class TestQueryBuilder {
	
	@Table("article")
	static class Article {
		Long id;
	}
	
	@Test
	public void testBasicSql() {
		assertEquals(norm("SELECT `article`.id AS `article.id` FROM `article`"), norm(PojoQuery.build(Article.class).toSql()));
	}
}