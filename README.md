PojoQuery
=========

PojoQuery is a light-weight utility for working with relational databases in Java. 
Instead of writing a SQL query in plain text, PojoQuery leverages Plain Old Java classes (POJO's) 
to define the set of fields and tables (joins) to fetch.
Because each field or property in the POJO corresponds to a field in the SELECT clause 
of the query, the resultset maps perfectly to the defining classes to obtain a 
type-safe result.

```java
class ArticleExample {
	javax.sql.DataSource database = DB.getDataSource();
	
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
class Article {
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
```
	

PojoQuery creates a SQL query from the `ArticleDetail` pojo, and transforms the JDBC ResultSet 
into `ArticleDetail` instances.
The generated SQL is _predictable_ and easy to read, much like you would write yourself:

```sql
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
```

Note that PojoQuery uses the POJO _fieldnames_ `comments` and `author` to construct linkfield names `author_id` and 
aliases for fields and tables `comments.author.id` and `comments.author` (you can also specify your own 
using annotations).

### No lazy loading: no complexity...

The major difference with traditional Java ORM frameworks (JPA, Hibernate) is that instead of defining 
_all links_ in the database we only specify the _links to fetch_. This means that there is _no lazy loading_.

Although lazy loading has its obvious benefits (i.e. no need to specify which linked entities to load beforehand), 
the drawbacks are significant: 
- entities can only be used [in the context of a session](https://www.google.nl/search?q=lazyinitializationexception)
- we cannot serialize objects easily to JSON, XML, [GWT](https://developers.google.com/web-toolkit/articles/using_gwt_with_hibernate)
- proxy classes kill `instanceof` and `getClass`, complicate debugging and testing

### ... instead: views!

The alternative that PojoQuery offers is best to think of as a _view_: a set of fields and tables 
with their respective join conditions. Thought of it this way, `ArticleDetail` is a _view_ that contains 
all information needed to display an article in a blog: the title, content, comments and their authors.

As an alternative example, when displaying articles in a list, we are not interested in individual comments. For this 
purpose we create a different view, which only specifies a link to the author of the article.

```java
class ArticleListView extends Article {
	User author;
}
```
	

### Customization through annotations

Being able to predict the field and table aliases makes it easy to extend the query. You can add clauses 
using the methods `.addJoin()`, `addGroupBy()`, etc. or you can define them beforehand on the POJO itself
using annotations.
Let's say we want to improve on `ArticleListView` by adding two fields: the number of comments and 
the date of the last comment. 


```java
@Join("LEFT JOIN comment ON comment.article_id=article.id")
@GroupBy("article.id")
class ArticleListView extends Article {
	User author;
	
	@Select("COUNT(comment.id)")
	int commentCount;
	
	@Select("MAX(comment.submitDate)")
	Date lastCommentDate;
}
```

The Join, GroupBy and Select clauses are simply copied into the query.

```sql
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
```
