package nl.pojoquery;

import static nl.pojoquery.TestUtils.norm;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import nl.pojoquery.DbContext.DefaultDbContext;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.QueryBuilder;
import nl.pojoquery.pipeline.SqlQuery;

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
