package org.pojoquery;

import static org.junit.Assert.assertEquals;
import static org.pojoquery.TestUtils.norm;

import org.junit.Test;
import org.pojoquery.DbContext.DefaultDbContext;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.QueryBuilder;
import org.pojoquery.pipeline.SqlQuery;

public class TestDbContext {

	@Table(value="article", schema="schema1")
	static class Article {
		@Id
		public Long id;
		public String title;
	}
	
	@Test
	public void testQuoting() {
		{
			SqlQuery<?> query = QueryBuilder.from(Article.class).getQuery();
			
			assertEquals(
					norm("""
						SELECT
						 `article`.`id` AS `article.id`,
						 `article`.`title` AS `article.title`
						FROM `schema1`.`article` AS `article`
						"""),
					norm(query.toStatement().getSql()));
		}
		
		{
			DefaultDbContext dbContext = new DefaultDbContext(DbContext.QuoteStyle.MYSQL, false);
			SqlQuery<?> query = QueryBuilder.from(dbContext, Article.class).getQuery();
			
			assertEquals(
					norm("""
						SELECT
						 `article`.id AS `article.id`,
						 `article`.title AS `article.title`
						FROM schema1.article AS `article`
						"""),
					norm(query.toStatement().getSql()));
		}
	}
}
