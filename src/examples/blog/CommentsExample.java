package examples.blog;

import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import nl.pojoquery.PojoQuery;
import nl.pojoquery.annotations.Table;

public class CommentsExample {
	@Table("article")
	public static class Article {
		Long id;
		String title;
	}
	
	@Table("user")
	public static class User {
		Long id;
		String email;
		String firstName;
		String lastName;
		
		public String getFullName() {
			return firstName + " " + lastName;
		}
	}
	
	@Table("comment") 
	public static class Comment {
		Long id;
		String comment;
		Date submitdate;
	}
	
	public static class CommentDetail extends Comment {
		User author;
		Article article;
	}
	
	public static List<CommentDetail> listComments(DataSource db, Long articleId, int startIndex, int maxResults) {
		return PojoQuery.build(CommentDetail.class)
				.addWhere("article.id=?", articleId)
				.addOrderBy("comment.submitdate DESC")
				.setLimit(startIndex, maxResults)
				.execute(db);
	}
	
	public static void run(DataSource db) {
//		SELECT
//		 `comment`.id `comment.id`,
//		 `comment`.comment `comment.comment`,
//		 `comment`.author_id `comment.author_id`,
//		 `author`.id `author.id`,
//		 `author`.email `author.email`,
//		 `author`.firstName `author.firstName`,
//		 `author`.lastName `author.lastName`,
//		 `article`.id `article.id`,
//		 `article`.title `article.title` 
//		FROM comment 
//		 LEFT JOIN user `author` ON `comment`.author_id=`author`.id
//		 LEFT JOIN article `article` ON `comment`.article_id=`article`.id 
//		WHERE article.id=?   
//		LIMIT 0,10
		
		List<CommentDetail> comments = listComments(db, 1L, 0, 10);
		
		for(CommentDetail comment : comments) {
			System.out.println("Comment by " + comment.author.getFullName() + ": " + comment.comment);
		}
	}

}
