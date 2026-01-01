package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.pojoquery.TestUtils.norm;

import org.junit.jupiter.api.Test;
import org.pojoquery.annotations.Table;

public class TestQueryBuilder {
	
	@Table("article")
	static class Article {
		Long id;
	}
	
	@Test
	public void testBasicSql() {
		assertEquals(norm("SELECT `article`.`id` AS `article.id` FROM `article` AS `article`"), norm(PojoQuery.build(Article.class).toSql()));
	}
}