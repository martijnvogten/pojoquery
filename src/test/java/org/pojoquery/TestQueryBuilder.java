package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.pojoquery.TestUtils.norm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Table;

public class TestQueryBuilder {
	@BeforeEach
	public void setup() {
		DbContext.setDefault(DbContext.forDialect(Dialect.MYSQL));
	}
	
	@Table("article")
	static class Article {
		Long id;
	}
	
	@Test
	public void testBasicSql() {
		assertEquals(norm("SELECT `article`.`id` AS `article.id` FROM `article` AS `article`"), norm(PojoQuery.build(Article.class).toSql()));
	}
}