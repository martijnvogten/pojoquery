package org.pojoquery.typedquery.entities;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.DbContext.QuoteStyle;
import org.pojoquery.DbContextBuilder;
import org.pojoquery.integrationtest.DbContextExtension;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Integration test for ArticleQueryCopy.
 * This test allows quick iteration on the query class before applying changes to the code generator.
 */
@ExtendWith(DbContextExtension.class)
public class TestArticleQueryCopy {

    private DataSource dataSource;

    @BeforeEach
    void setup() {
        DbContext.setDefault(new DbContextBuilder()
                .dialect(Dialect.HSQLDB)
                .withQuoteStyle(QuoteStyle.ANSI)
                .quoteObjectNames(true)
                .build());

        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:article_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;

        // Create tables for Article and Person
        SchemaGenerator.createTables(dataSource, Article.class, Person.class);
    }

	private Person insertPerson(Connection c, String name, String email) {
		Person p = new Person();
		p.name = name;
		p.email = email;
		org.pojoquery.PojoQuery.insert(c, p);
		return p;
	}

	private Article insertArticle(Connection c, String title, String content, Person author) {
		Article a = new Article();
		a.title = title;
		a.content = content;
		a.author = author;
		org.pojoquery.PojoQuery.insert(c, a);
		return a;
	}

    @Test
    void testBasicQuery() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Insert test data
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Hello World", "First article content", alice);

            // Query using ArticleQueryCopy
            List<Article> results = new ArticleQueryCopy().list(c);

            assertEquals(1, results.size());
            assertEquals("Hello World", results.get(0).title);
            assertEquals("First article content", results.get(0).content);
            assertNotNull(results.get(0).author);
            assertEquals("Alice", results.get(0).author.name);
        });
    }

    @Test
    void testWhereClauseSimple() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            Person bob = insertPerson(c, "Bob", "bob@example.com");
            insertArticle(c, "Article One", "Content 1", alice);
            insertArticle(c, "Article Two", "Content 2", bob);

            // Filter by title
            List<Article> results = new ArticleQueryCopy()
                    .where().title.eq("Article One").list(c);

            assertEquals(1, results.size());
            assertEquals("Article One", results.get(0).title);
        });
    }

    @Test
    public void testQueryWithWhereClause() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            Person bob = insertPerson(c, "Bob", "bob@example.com");
            insertArticle(c, "First Article", "Content 1", alice);
            insertArticle(c, "Second Article", "Content 2", bob);
            insertArticle(c, "Third Article", "Content 3", alice);

            ArticleQueryCopy q = new ArticleQueryCopy();
            q.where().title.eq("First Article");
            
            List<Article> articles = q.list(c);
            
            assertEquals(1, articles.size());
            assertEquals("First Article", articles.get(0).title);
            assertEquals("Alice", articles.get(0).author.name);
        });
    }

    @Test
    void testWhereClauseWithRelationship() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            Person bob = insertPerson(c, "Bob", "bob@example.com");
            insertArticle(c, "Article by Alice", "Content 1", alice);
            insertArticle(c, "Article by Bob", "Content 2", bob);

            // Filter by author name
            List<Article> results = new ArticleQueryCopy()
                    .where().author.name.eq("Alice").list(c);

            assertEquals(1, results.size());
            assertEquals("Article by Alice", results.get(0).title);
            assertEquals("Alice", results.get(0).author.name);
        });
    }

    @Test
    void testWhereClauseAndChain() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Hello World", "Content 1", alice);
            insertArticle(c, "Hello Java", "Content 2", alice);
            insertArticle(c, "Goodbye World", "Content 3", alice);

            // Filter with AND chain
            List<Article> results = new ArticleQueryCopy()
                    .where().title.like("Hello%")
                    .and().content.eq("Content 2")
                    .list(c);

            assertEquals(1, results.size());
            assertEquals("Hello Java", results.get(0).title);
        });
    }

    @Test
    void testWhereClauseOrChain() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Article One", "Content 1", alice);
            insertArticle(c, "Article Two", "Content 2", alice);
            insertArticle(c, "Article Three", "Content 3", alice);

            // Filter with OR chain
            List<Article> results = new ArticleQueryCopy()
                    .where().title.eq("Article One")
                    .or().title.eq("Article Three")
                    .list(c);

            assertEquals(2, results.size());
        });
    }

    @Test
    void testOrderBy() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Zebra", "Content 1", alice);
            insertArticle(c, "Apple", "Content 2", alice);
            insertArticle(c, "Mango", "Content 3", alice);

            // Order by title ascending
            List<Article> results = new ArticleQueryCopy()
                    .orderBy().title.asc()
                    .list(c);

            assertEquals(3, results.size());
            assertEquals("Apple", results.get(0).title);
            assertEquals("Mango", results.get(1).title);
            assertEquals("Zebra", results.get(2).title);
        });
    }

    @Test
    void testOrderByDescending() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Zebra", "Content 1", alice);
            insertArticle(c, "Apple", "Content 2", alice);
            insertArticle(c, "Mango", "Content 3", alice);

            // Order by title descending
            List<Article> results = new ArticleQueryCopy()
                    .orderBy().title.desc()
                    .list(c);

            assertEquals(3, results.size());
            assertEquals("Zebra", results.get(0).title);
            assertEquals("Mango", results.get(1).title);
            assertEquals("Apple", results.get(2).title);
        });
    }

    @Test
    void testOrderByRelationship() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person zoe = insertPerson(c, "Zoe", "zoe@example.com");
            Person adam = insertPerson(c, "Adam", "adam@example.com");
            insertArticle(c, "Article 1", "Content 1", zoe);
            insertArticle(c, "Article 2", "Content 2", adam);

            // Order by author name ascending
            List<Article> results = new ArticleQueryCopy()
                    .orderBy().author.name.asc()
                    .list(c);

            assertEquals(2, results.size());
            assertEquals("Adam", results.get(0).author.name);
            assertEquals("Zoe", results.get(1).author.name);
        });
    }

    @Test
    void testStaticCondition() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Article 1", "Content 1", alice);
            insertArticle(c, "Article 2", "Content 2", alice);

            // Use static condition builder
            ArticleQueryCopy q = new ArticleQueryCopy();
            var condition = q.title.eq("Article 1");

            // The static condition should produce a SqlExpression
            assertNotNull(condition);
        });
    }

    @Test
    void testAuthorIsNull() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Article with author", "Content 1", alice);
            insertArticle(c, "Article without author", "Content 2", null);

            // Filter where author is null
            List<Article> results = new ArticleQueryCopy()
                    .where().author.isNull()
                    .list(c);

            assertEquals(1, results.size());
            assertEquals("Article without author", results.get(0).title);
            assertNull(results.get(0).author);
        });
    }

    @Test
    void testAuthorIsNotNull() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Article with author", "Content 1", alice);
            insertArticle(c, "Article without author", "Content 2", null);

            // Filter where author is not null
            List<Article> results = new ArticleQueryCopy()
                    .where().author.isNotNull()
                    .list(c);

            assertEquals(1, results.size());
            assertEquals("Article with author", results.get(0).title);
            assertNotNull(results.get(0).author);
        });
    }

    @Test
    void testMultipleArticlesSameAuthor() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Article 1", "Content 1", alice);
            insertArticle(c, "Article 2", "Content 2", alice);
            insertArticle(c, "Article 3", "Content 3", alice);

            // All articles by the same author
            List<Article> results = new ArticleQueryCopy()
                    .orderBy().id.asc()
                    .list(c);

            assertEquals(3, results.size());
            // All articles should reference the same author instance (deduplication)
            assertSame(results.get(0).author, results.get(1).author);
            assertSame(results.get(1).author, results.get(2).author);
        });
    }

}
