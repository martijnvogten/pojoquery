package examples.blog;

import java.util.Date;

import javax.sql.DataSource;

import org.pojoquery.DB;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.QueryBuilder;
import org.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;

public class ArticleDetailExample {
	
	// tag::user-entity[]
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
	// end::user-entity[]
	
	// tag::comment-entity[]
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
	// end::comment-entity[]
	
	// tag::article-entity[]
	@Table("article")
	public static class Article {
		@Id
		public Long id;
		public String title;
		public String content;
		public Long author_id;
	}
	
	@Table("views")
	public static class Views {
		@Id
		public Long id;
		public Long article_id;
		public Date viewedAt;
	}
	
	public static class ArticleDetail extends Article {
		public User author;
		public CommentDetail[] comments;
	}
	// end::article-entity[]

	public static void main(String[] args) {
		DataSource db = BlogDb.create("localhost", "pojoquery_blog", "root", "");
		
		// tag::query[]
		QueryBuilder<ArticleDetail> qb = QueryBuilder.from(ArticleDetail.class);
		DefaultSqlQuery q = qb.getQuery()
				.addWhere("{article}.id=?", 1L)
				.addOrderBy("{comments}.submitdate DESC");
		System.out.println(q.toStatement().getSql());
		
		for(ArticleDetail article : qb.processRows(DB.queryRows(db, q.toStatement()))) {
			if (article.comments != null) {
				System.out.println("Article #" + article.id + " by " + article.author.getFullName() + " has " + article.comments.length + " comments");
				for(CommentDetail comment : article.comments) {
					System.out.println("One comment by " + comment.author.getFullName() + ": \"" + comment.comment + "\"");
				}
			}
		}
		// end::query[]
		
	}

}
