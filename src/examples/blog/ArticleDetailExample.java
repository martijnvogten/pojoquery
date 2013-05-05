package examples.blog;

import java.util.Date;

import javax.sql.DataSource;

import nl.pojoquery.DB;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Table;

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

	public static void run(DataSource db) {
		createTables(db);
		
		insertData(db);
		
		for(ArticleDetail article : PojoQuery.create(ArticleDetail.class)
				.addWhere("article.id=?", 1L)
				.addOrderBy("comments.submitdate DESC")
				.execute(db)) {
			if (article.comments != null) {
				System.out.println("Article #" + article.id + " has " + article.comments.length + " comments");
				for(CommentDetail comment : article.comments) {
					System.out.println("The first comment was written by " + comment.author.getFullName() + ": \"" + comment.comment + "\"");
					break;
				}
			}
		}
		
	}

	private static void createTables(DataSource db) {
		DB.executeDDL(db, "CREATE TABLE article (id BIGINT NOT NULL AUTO_INCREMENT, title TEXT, content LONGTEXT, author_id BIGINT, PRIMARY KEY(id))");
		DB.executeDDL(db, "CREATE TABLE user    (id BIGINT NOT NULL AUTO_INCREMENT, email VARCHAR(255), firstName VARCHAR(255), lastName VARCHAR(255), PRIMARY KEY(id))");
		DB.executeDDL(db, "CREATE TABLE comment (id BIGINT NOT NULL AUTO_INCREMENT, article_id BIGINT NOT NULL, comment LONGTEXT, submitdate DATETIME, author_id BIGINT, PRIMARY KEY(id))");
	}
	
	private static void insertData(DataSource db) {
		User john = new User();
		john.email = "john@ewbank.nl";
		john.firstName = "John";
		john.lastName = "Ewbank";
		john.id = PojoQuery.insertOrUpdate(db, john);
		
		Article article = new Article();
		article.title = "King's song";
		article.content = "I wrote it";
		article.author_id = john.id;
		article.id = PojoQuery.insertOrUpdate(db, article);
		
		Comment comment = new Comment();
		comment.author_id = john.id;
		comment.article_id = article.id;
		comment.comment = "I like it too!";
		comment.submitdate = new Date();
		comment.id = PojoQuery.insertOrUpdate(db, comment);
	}
}
