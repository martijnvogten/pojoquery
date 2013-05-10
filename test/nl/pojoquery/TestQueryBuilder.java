package nl.pojoquery;

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
		assertEquals("SELECT `article`.id `article.id` FROM article", TestUtils.norm(PojoQuery.build(Article.class).toSql()));
	}
}