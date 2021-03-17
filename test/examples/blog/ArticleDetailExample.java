package examples.blog;

import java.util.Date;

import javax.sql.DataSource;

import nl.pojoquery.DB;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.pipeline.QueryBuilder;
import nl.pojoquery.pipeline.SqlQuery;

public class ArticleDetailExample {
	
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
		public CommentDetail[] comments;
	}

	public static void main(String[] args) {
		DataSource db = BlogDb.create("localhost", "pojoquery_blog", "root", "");
		
		QueryBuilder<ArticleDetail> qb = QueryBuilder.from(ArticleDetail.class);
		SqlQuery q = qb.getQuery()
				.addWhere("article.id=?", 1L)
				.addOrderBy("comments.submitdate DESC");
		System.out.println(q.toStatement().getSql());
		
		for(ArticleDetail article : qb.processRows(DB.queryRows(db, q.toStatement()))) {
			if (article.comments != null) {
				System.out.println("Article #" + article.id + " by " + article.author.getFullName() + " has " + article.comments.length + " comments");
				for(CommentDetail comment : article.comments) {
					System.out.println("One comment by " + comment.author.getFullName() + ": \"" + comment.comment + "\"");
				}
			}
		}
		
	}

}
