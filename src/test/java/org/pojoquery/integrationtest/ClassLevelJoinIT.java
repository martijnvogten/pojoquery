package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.GroupBy;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Join;
import org.pojoquery.annotations.Select;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Integration tests for class-level @Join annotations.
 * 
 * <p>Tests the ability to declare arbitrary table joins at the class level,
 * such as joining to tables for aggregation purposes (e.g., counting comments,
 * counting views) without creating explicit field relationships.</p>
 * 
 * <p>Note: Some tests are marked as @Disabled as they require class-level @Join
 * annotation processing which is documented but not yet fully implemented. See
 * {@link examples.blog.ArticleListViewExample} for the expected usage pattern.</p>
 */
@UseDialect(Dialect.HSQLDB)
public class ClassLevelJoinIT {

	// Base entities
	
	@Table("author")
	static class Author {
		@Id
		Long id;
		String firstName;
		String lastName;
	}

	@Table("article")
	static class Article {
		@Id
		Long id;
		String title;
		String content;
		Long author_id;
	}

	static class ArticleWithAuthor extends Article {
		Author author;
	}

	@Table("comment")
	static class Comment {
		@Id
		Long id;
		Long article_id;
		String text;
		Date submitdate;
	}

	@Table("views")
	static class ArticleView {
		@Id
		Long id;
		Long article_id;
		Date viewedAt;
	}

	// Class with class-level @Join annotations for aggregation
	// Note: Class-level @Join annotations are not yet processed by the query builder

	@Join(type = JoinType.LEFT, tableName = "comment", alias = "comment", joinCondition = "{comment.article_id} = {this.id}")
	@Join(type = JoinType.LEFT, tableName = "views", alias = "views", joinCondition = "{views.article_id} = {this.id}")
	@GroupBy({"{article.id}", "{article.title}", "{article.content}", "{article.author_id}", "{author.id}", "{author.firstName}", "{author.lastName}"})
	static class ArticleListView extends ArticleWithAuthor {
		@Select("COUNT(DISTINCT {comment.id})")
		Long commentCount;

		@Select("COUNT(DISTINCT {views.id})")
		Long viewCount;

		@Select("MAX({comment.submitdate})")
		Date lastCommentDate;
	}

	// Simple class-level join without aggregation
	// Note: Class-level @Join annotations are not yet processed by the query builder

	@Join(type = JoinType.INNER, tableName = "author", alias = "joined_author", joinCondition = "{joined_author.id} = {this.author_id}")
	static class ArticleWithJoinedAuthor extends Article {
		@Select("{joined_author.firstName}")
		String authorFirstName;

		@Select("{joined_author.lastName}")
		String authorLastName;
	}

	/**
	 * Test that class-level @Join annotations are processed in SQL generation.
	 */
	@Test
	public void testClassLevelJoinSqlGeneration() {
		PojoQuery<ArticleWithJoinedAuthor> q = PojoQuery.build(ArticleWithJoinedAuthor.class);
		String sql = q.toSql();

		System.out.println(sql);

		// Verify the SQL contains INNER JOIN from the @Join annotation
		Assertions.assertTrue(sql.contains("INNER JOIN"), "SQL should contain INNER JOIN");
		Assertions.assertTrue(sql.contains("\"author\"") || sql.contains("`author`"), 
			"SQL should join the author table");
		Assertions.assertTrue(sql.contains("joined_author"), 
			"SQL should use the alias from @Join");
	}

	/**
	 * Test that multiple @Join annotations (via @Repeatable) are processed.
	 */
	@Test
	public void testMultipleClassLevelJoinsSqlGeneration() {
		PojoQuery<ArticleListView> q = PojoQuery.build(ArticleListView.class);
		String sql = q.toSql();

		// Verify the SQL contains both LEFT JOINs from the @Join annotations
		Assertions.assertTrue(sql.contains("LEFT JOIN"), "SQL should contain LEFT JOIN");
		Assertions.assertTrue(sql.contains("\"comment\"") || sql.contains("`comment`"), 
			"SQL should join the comment table");
		Assertions.assertTrue(sql.contains("\"views\"") || sql.contains("`views`"), 
			"SQL should join the views table");
		Assertions.assertTrue(sql.contains("GROUP BY"), "SQL should contain GROUP BY");
	}

	/**
	 * Test execution with class-level @Join and aggregation.
	 */
	@Test
	public void testClassLevelJoinExecution() {
		DataSource db = initDatabase();

		DB.withConnection(db, (Connection c) -> {
			insertTestData(c);
		});

		List<ArticleListView> articles = PojoQuery.build(ArticleListView.class)
				.addOrderBy("{article.id}")
				.execute(db);

		Assertions.assertEquals(2, articles.size());

		// First article: "Hello World" - 2 comments, 3 views
		ArticleListView hello = articles.get(0);
		Assertions.assertEquals("Hello World", hello.title);
		Assertions.assertEquals(2L, hello.commentCount);
		Assertions.assertEquals(3L, hello.viewCount);
		Assertions.assertNotNull(hello.author);
		Assertions.assertEquals("John", hello.author.firstName);

		// Second article: "Goodbye World" - 1 comment, 0 views
		ArticleListView goodbye = articles.get(1);
		Assertions.assertEquals("Goodbye World", goodbye.title);
		Assertions.assertEquals(1L, goodbye.commentCount);
		Assertions.assertEquals(0L, goodbye.viewCount);
		Assertions.assertEquals("Jane", goodbye.author.firstName);
	}

	/**
	 * Test execution with INNER JOIN via @Join annotation.
	 */
	@Test
	public void testInnerJoinExecution() {
		DataSource db = initDatabase();

		DB.withConnection(db, (Connection c) -> {
			insertTestData(c);
		});

		List<ArticleWithJoinedAuthor> articles = PojoQuery.build(ArticleWithJoinedAuthor.class)
				.addOrderBy("{article.id}")
				.execute(db);

		Assertions.assertEquals(2, articles.size());
		Assertions.assertEquals("John", articles.get(0).authorFirstName);
		Assertions.assertEquals("Doe", articles.get(0).authorLastName);
		Assertions.assertEquals("Jane", articles.get(1).authorFirstName);
		Assertions.assertEquals("Smith", articles.get(1).authorLastName);
	}

	private DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Author.class, Article.class, Comment.class, ArticleView.class);
		return db;
	}

	private void insertTestData(Connection c) {
		// Create authors
		Author john = new Author();
		john.firstName = "John";
		john.lastName = "Doe";
		PojoQuery.insert(c, john);

		Author jane = new Author();
		jane.firstName = "Jane";
		jane.lastName = "Smith";
		PojoQuery.insert(c, jane);

		// Create articles
		ArticleWithAuthor article1 = new ArticleWithAuthor();
		article1.title = "Hello World";
		article1.content = "This is my first post";
		article1.author = john;
		PojoQuery.insert(c, article1);

		ArticleWithAuthor article2 = new ArticleWithAuthor();
		article2.title = "Goodbye World";
		article2.content = "This is my last post";
		article2.author = jane;
		PojoQuery.insert(c, article2);

		// Create comments for article1
		Comment c1 = new Comment();
		c1.article_id = article1.id;
		c1.text = "Great post!";
		c1.submitdate = new Date(System.currentTimeMillis() - 1000);
		PojoQuery.insert(c, c1);

		Comment c2 = new Comment();
		c2.article_id = article1.id;
		c2.text = "Thanks for sharing";
		c2.submitdate = new Date();
		PojoQuery.insert(c, c2);

		// Create comment for article2
		Comment c3 = new Comment();
		c3.article_id = article2.id;
		c3.text = "Sad to see you go";
		c3.submitdate = new Date();
		PojoQuery.insert(c, c3);

		// Create views for article1 (3 views)
		for (int i = 0; i < 3; i++) {
			ArticleView v = new ArticleView();
			v.article_id = article1.id;
			v.viewedAt = new Date();
			PojoQuery.insert(c, v);
		}
		// article2 has no views
	}
}
