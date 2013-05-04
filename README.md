PojoQuery
=========

PojoQuery is a light-weight alternative to traditional Object Relational Mapping (ORM) frameworks in Java.
Unlike JPA or Hibernate, PojoQuery does not try to abstract away the database, but instead makes working with a relational database in Java as simple and pleasant as possible.

The basic idea is this: Instead of writing a SQL query in plain text, we create a Plain Old Java Object (POJO).
Each field or property in the POJO corresponds to a field in the SELECT clause of the query, and as a consequence, to each column in the query result. 


	@Table("article")
	public class Article {
		Long id;
		String title;
		String content;
	}

	// SELECT id, title, content FROM article
	List<Article> articles = PojoQuery.create(Article.class).execute(getDataSource());

PojoQuery creates a query from the `Article` pojo, and transforms the JDBC ResultSet into a list of `Article` instances.
Note that instead of a table, the POJO maps to the `ResultSet` which can also contain aggregate fields as is shown in a
more complex example:

Suppose we want to show a list of articles from a blog, with a number of comments and the date of the last comment:

	SELECT 
	  article.id, 
	  article.title, 
	  COUNT(comment.id) AS commentCount, 
	  MAX(comment.submitdate) AS lastCommentDate 
	FROM article
	  LEFT JOIN comment ON comment.article_id = article.id
	GROUP BY 1, 2

PojoQuery uses annotations to provide the select, join and group by clauses, like so:

	@Table("article")
	@Join("LEFT JOIN comment ON comment.article_id = article.id")
	@GroupBy("1, 2")
	public class ArticleView {
		Long id;
		String title;
		
		@Select("COUNT(comment.id)")
		int commentCount;
		
		@Select("MAX(comment.submitdate)")
		Date lastCommentDate;
	}

Now to create a detailed view of an article, including all comments and the author we might do use a `User` pojo:

	@Table("user")
	public class User {
		@Id
		Long id;
		String firstName;
		String lastName;
		String email;
	}
	
	@Table("comment")
	public class CommentDetail {
		@Id
		Long id;
		String comment;
		User author;
		Date submitdate;
	}
	
	@Table("article")
	public class ArticleDetail extends Article {
		User author;
		Comment[] comments;
	}

	ArticleDetail article = PojoQuery.create(ArticleDetail.class)
		.addWhere("id=?", 1L)
		.addOrderBy("comments.submitdate DESC")
		.execute(getDataSource());

	if (article.comments.length > 0) {
		User lastCommentAuthor = article.comments[0].author;
	}

