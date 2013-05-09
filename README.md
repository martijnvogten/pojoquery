PojoQuery
=========

PojoQuery is a light-weight utility for working with relational databases in Java. 
Instead of writing a SQL query in plain text, PojoQuery leverages Plain Old Java classes (POJO's) 
to define the set of fields and tables (joins) we need to fetch.
Because each field or property in the POJO corresponds to a field in the SELECT clause 
of the query, the resultset maps perfectly to the defining classes to obtain a 
type-safe result.

	class ArticleExample {
		DataSource database;
		
		ArticleDetail fetchArticle(Long articleId) {
			return PojoQuery.build(ArticleDetail.class)
				.addWhere("article.id=?", articleId)
				.addOrderBy("comments.submitdate")
				.execute(database).get(0);
		}
	}
	
	class ArticleDetail extends Article {
		User author;
		CommentDetail[] comments;
	}
	
	@Table("article")
	class Article
		Long id;
		String title;
		String content;
		Date publishdate;
	}
	
	@Table("comment")
	class CommentDetail {
		Long id;
		String comment;
		Date submitdate;
		User author;
	}
	
	@Table("user")
	class User {
		Long id;
		String firstName;
		String lastName;
		String email;
	}
	
	

PojoQuery creates a SQL query from the `ArticleDetail` pojo, and transforms the JDBC ResultSet 
into `ArticleDetail` instances.
The exact SQL is easy to read and understand, much like you would write yourself:

	PojoQuery.build(ArticleDetail.class)
		.addWhere("article.id=?", articleId)
		.addOrderBy("comments.submitdate DESC")
		.toSql()	
output:

	SELECT
	 `article`.id `article.id`,
	 `article`.title `article.title`,
	 `article`.content `article.content`,
	 `author`.id `author.id`,
	 `author`.firstName `author.firstName`,
	 `author`.lastName `author.lastName`,
	 `author`.email `author.email`,
	 `comments`.id `comments.id`,
	 `comments`.comment `comments.comment`,
	 `comments`.submitdate `comments.submitdate`,
	 `comments.author`.id `comments.author.id`,
	 `comments.author`.firstName `comments.author.firstName`,
	 `comments.author`.lastName `comments.author.lastName`,
	 `comments.author`.email `comments.author.email` 
	FROM article 
	 LEFT JOIN user `author` ON `article`.author_id=`author`.id
	 LEFT JOIN comment `comments` ON `comments`.article_id=`article`.id
	 LEFT JOIN user `comments.author` ON `comments`.author_id=`comments.author`.id 
	WHERE article.id=?  
	ORDER BY comments.submitdate

Note that PojoQuery 'guesses' names of linkfields using the default strategy [tablename]_id
(you can use annotations to override field and table names at any time).

### No lazy loading: views!


The major difference with traditional Java ORM frameworks (JPA, Hibernate) is that instead of defining 
_all links_ in the database we only specify the _links to fetch_. This means that there is _no lazy loading_.

Although lazy loading has its obvious benefits (i.e. no need to specify which linked entities to load beforehand), 
the drawbacks are significant: 
- all business logic must be contained in a session
- we cannot serialize objects easily to JSON, XML
- proxy classes kill `instanceof`, `getClass` and complicate debugging
- overall increased complexity

The alternative that PojoQuery offers is best to think of as a _view_: a set of fields and tables 
with their respective join conditions. Thought of it this way, ArticleDetail is a 'view' that contains 
all information needed to display an article in a blog: the title, content, comments and their respective authors.

When displaying articles in a list, we would create a different view, because we are not interested in the comments, 
we only need to show an author. Easy enough:

	class ArticleListView extends Article {
		User author;
	}

### Customization through annotations

You still have full control over the SQL that is generated:
Let's say we want to improve on the list by adding the number of comments and date of last comment. 
We can add custom query clauses using annotations.

	@Join("LEFT JOIN comment ON comment.article_id=article.id")
	@GroupBy("article.id")
	class ArticleListView extends Article {
		User author;
		
		@Select("COUNT(comment.id)")
		int commentCount;
		
		@Select("MAX(comment.submitDate)")
		Date lastCommentDate;
	}

The Join, GroupBy and Select clauses are simply copied into the query.

	SELECT
	 `article`.id `article.id`,
	 `article`.title `article.title`,
	 `article`.content `article.content`,
	 `author`.id `author.id`,
	 `author`.firstName `author.firstName`,
	 `author`.lastName `author.lastName`,
	 `author`.email `author.email`,
	 COUNT(comment.id) `article.commentCount`,
	 MAX(comment.submitdate) `article.lastCommentDate` 
	FROM article 
	 LEFT JOIN comment ON comment.article_id = article.id
	 LEFT JOIN user `author` ON `article`.author_id=`author`.id 
	WHERE article_id=? 
	GROUP BY article.id  
