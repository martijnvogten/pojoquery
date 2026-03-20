package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.pojoquery.TestUtils.norm;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.UseDialect;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.DefaultSqlQuery;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;

@UseDialect(Dialect.MYSQL)
public class TestBasics {

	@Table("user")
	public static class User {
		@Id
		public Long id;
		public String firstName;
		public String lastName;
		public String email;
		
		public String getFullName() {
			return (firstName + " " + lastName).trim();
		}
	}
	
	@Table("comment")
	public static class Comment {
		@Id
		public Long id;
		public Long article_id;
		public String comment;
		public Date submitdate;
		public Long author_id;
	}
	
	public static class CommentDetail extends Comment {
		public User author;
	}
	
	@Table("article")
	public static class Article {
		@Id
		public Long id;
		public String title;
		public String content;
		public Long author_id;
	}
	
	public static class ArticleDetail extends Article {
		public User author;
		public List<CommentDetail> comments;
	}

	@Test
	public void testIt() {
		String sql = PojoQuery.build(ArticleDetail.class).toStatement().getSql();
		RootNode aqt = AQTTransformer.buildQueryTreeForType(ArticleDetail.class);
		
		
		System.out.println(sql);
		
		String expected = """
			SELECT
			 `article`.`id` AS `article.id`,
			 `article`.`title` AS `article.title`,
			 `article`.`content` AS `article.content`,
			 `article`.`author_id` AS `article.author_id`,
			 `author`.`id` AS `author.id`,
			 `author`.`firstName` AS `author.firstName`,
			 `author`.`lastName` AS `author.lastName`,
			 `author`.`email` AS `author.email`,
			 `comments`.`id` AS `comments.id`,
			 `comments`.`article_id` AS `comments.article_id`,
			 `comments`.`comment` AS `comments.comment`,
			 `comments`.`submitdate` AS `comments.submitdate`,
			 `comments`.`author_id` AS `comments.author_id`,
			 `comments.author`.`id` AS `comments.author.id`,
			 `comments.author`.`firstName` AS `comments.author.firstName`,
			 `comments.author`.`lastName` AS `comments.author.lastName`,
			 `comments.author`.`email` AS `comments.author.email`
			FROM `article` AS `article`
			 LEFT JOIN `user` AS `author` ON `article`.`author_id` = `author`.`id`
			 LEFT JOIN `comment` AS `comments` ON `article`.`id` = `comments`.`article_id`
			 LEFT JOIN `user` AS `comments.author` ON `comments`.`author_id` = `comments.author`.`id`
			""";
		assertEquals(norm(expected), norm(sql));
		DefaultSqlQuery sqlQuery = new DefaultSqlQuery(DbContext.getDefault());
		AQTTransformer.toSql(aqt, sqlQuery);
		assertEquals(norm(expected), norm(sqlQuery.toStatement().getSql()));
		
	}
}
