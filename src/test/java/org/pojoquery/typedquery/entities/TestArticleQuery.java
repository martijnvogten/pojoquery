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
 * Integration test for ArticleQuery.
 * This test allows quick iteration on the query class before applying changes to the code generator.
 */
@ExtendWith(DbContextExtension.class)
public class TestArticleQuery {

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

            // Query using ArticleQuery
            List<Article> results = new ArticleQuery().list(c);

            assertEquals(1, results.size());
            assertEquals("Hello World", results.get(0).title);
            assertEquals("First article content", results.get(0).content);
            assertNotNull(results.get(0).author);
            assertEquals("Alice", results.get(0).author.name);
        });
    }

    @Test
    void testBasicLikeQuery() {
        DB.withConnection(dataSource, (Connection c) -> {
            // Insert test data
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Hello World", "First article content", alice);

            // Query using ArticleQuery
            List<Article> results = new ArticleQuery().where().title.like("Hello%").list(c);

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
            List<Article> results = new ArticleQuery()
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

            ArticleQuery q = new ArticleQuery();
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
            List<Article> results = new ArticleQuery()
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
            List<Article> results = new ArticleQuery()
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
            List<Article> results = new ArticleQuery()
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
            List<Article> results = new ArticleQuery()
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
            List<Article> results = new ArticleQuery()
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
            List<Article> results = new ArticleQuery()
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
            ArticleQuery q = new ArticleQuery();
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
            List<Article> results = new ArticleQuery()
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
            List<Article> results = new ArticleQuery()
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
            List<Article> results = new ArticleQuery()
                    .orderBy().id.asc()
                    .list(c);

            assertEquals(3, results.size());
            // All articles should reference the same author instance (deduplication)
            assertSame(results.get(0).author, results.get(1).author);
            assertSame(results.get(1).author, results.get(2).author);
        });
    }

    // === Comparison operators ===

    @Test
    void testComparisonOperators() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "A", "Content", alice);
            insertArticle(c, "B", "Content", alice);
            insertArticle(c, "C", "Content", alice);

            // gt (greater than)
            assertEquals(2, new ArticleQuery().where().title.gt("A").list(c).size());
            // lt (less than)
            assertEquals(2, new ArticleQuery().where().title.lt("C").list(c).size());
            // ge (greater or equal)
            assertEquals(3, new ArticleQuery().where().title.ge("A").list(c).size());
            // le (less or equal)
            assertEquals(3, new ArticleQuery().where().title.le("C").list(c).size());
            // ne (not equal)
            assertEquals(2, new ArticleQuery().where().title.ne("B").list(c).size());
        });
    }

    @Test
    void testInNotLikeBetween() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Alpha", "Content", alice);
            insertArticle(c, "Beta", "Content", alice);
            insertArticle(c, "Gamma", "Content", alice);

            // in
            assertEquals(2, new ArticleQuery().where().title.in("Alpha", "Gamma").list(c).size());
            // notLike
            assertEquals(2, new ArticleQuery().where().title.notLike("A%").list(c).size());
            // between
            assertEquals(2, new ArticleQuery().where().title.between("Alpha", "Beta").list(c).size());
        });
    }

    // === SQL Functions ===

    @Test
    void testSqlFunctions() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Hello", "World", alice);

            ArticleQuery q = new ArticleQuery();

            // lower
            assertEquals(1, q.where(q.lower(q.title).eq("hello")).list(c).size());
            // upper
            assertEquals(1, new ArticleQuery().where(new ArticleQuery().upper(new ArticleQuery().title).eq("HELLO")).list(c).size());
            // length
            assertEquals(1, new ArticleQuery().where(new ArticleQuery().length(new ArticleQuery().title).eq(5)).list(c).size());
            // concat
            ArticleQuery q2 = new ArticleQuery();
            assertEquals(1, q2.where(q2.concat(q2.title, q2.content).eq("HelloWorld")).list(c).size());
        });
    }

    @Test
    void testTrimSubstringCoalesceAbs() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Test", "Content", alice);

            // trim (test that it works, value has no spaces)
            ArticleQuery q1 = new ArticleQuery();
            assertEquals(1, q1.where(q1.trim(q1.title).eq("Test")).list(c).size());

            // substring
            ArticleQuery q2 = new ArticleQuery();
            assertEquals(1, q2.where(q2.substring(q2.title, 1, 2).eq("Te")).list(c).size());

            // coalesce
            ArticleQuery q3 = new ArticleQuery();
            assertEquals(1, q3.where(q3.coalesce(q3.title, "default").eq("Test")).list(c).size());
        });
    }

    // === Field-to-field comparisons ===

    @Test
    void testFieldToFieldComparison() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person same = insertPerson(c, "Same", "same@example.com");
            Person diff = insertPerson(c, "Different", "diff@example.com");
            insertArticle(c, "Same", "Content", same);      // title == author.name
            insertArticle(c, "Other", "Content", diff);     // title != author.name

            // eq with field
            assertEquals(1, new ArticleQuery().where().title.eq(new ArticleQuery().author.name).list(c).size());
            // ne with field
            assertEquals(1, new ArticleQuery().where().title.ne(new ArticleQuery().author.name).list(c).size());
        });
    }

    // === where(Supplier) and chained conditions ===

    @Test
    void testWhereWithSupplier() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Article 1", "Content 1", alice);
            insertArticle(c, "Article 2", "Content 2", alice);

            ArticleQuery q = new ArticleQuery();
            List<Article> results = q.where(q.title.eq("Article 1")).list(c);

            assertEquals(1, results.size());
            assertEquals("Article 1", results.get(0).title);
        });
    }

    @Test
    void testChainedAndOrWithSupplier() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "A1", "X", alice);
            insertArticle(c, "A2", "Y", alice);
            insertArticle(c, "B1", "X", alice);

            ArticleQuery q = new ArticleQuery();
            // (title LIKE 'A%') AND (content = 'X')
            List<Article> results = q.where(q.title.like("A%")).and(q.content.eq("X")).list(c);
            assertEquals(1, results.size());
            assertEquals("A1", results.get(0).title);

            // (title = 'A1') OR (title = 'B1')
            ArticleQuery q2 = new ArticleQuery();
            List<Article> results2 = q2.where(q2.title.eq("A1")).or(q2.title.eq("B1")).list(c);
            assertEquals(2, results2.size());
        });
    }

    // === ChainableExpression comparisons (SQL functions returning chainable expressions) ===

    @Test
    void testChainableExpressionEqNe() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "hello", "Hello", alice);  // lower case
            insertArticle(c, "WORLD", "World", alice);

            // LOWER(title) = 'hello'
            ArticleQuery q1 = new ArticleQuery();
            assertEquals(1, q1.where(q1.lower(q1.title).eq("hello")).list(c).size());

            // LOWER(title) <> 'hello'
            ArticleQuery q2 = new ArticleQuery();
            assertEquals(1, q2.where(q2.lower(q2.title).ne("hello")).list(c).size());
        });
    }

    @Test
    void testExpressionToExpressionComparison() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Hello", "Hello", alice);  // title == content
            insertArticle(c, "Hello", "World", alice);  // title != content

            // Compare two expressions: LOWER(title) = LOWER(content)
            ArticleQuery q = new ArticleQuery();
            assertEquals(1, q.where(q.lower(q.title).eq(q.lower(q.content))).list(c).size());
        });
    }

    @Test
    void testExpressionIsNullIsNotNull() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Test", "Content", alice);
            insertArticle(c, "Test", "Content", null);

            // author.name isNull/isNotNull using expression wrapper
            ArticleQuery q1 = new ArticleQuery();
            assertEquals(1, q1.where(q1.coalesce(q1.author.name, "").eq("")).list(c).size());

            ArticleQuery q2 = new ArticleQuery();
            assertEquals(1, q2.where(q2.coalesce(q2.author.name, "").ne("")).list(c).size());
        });
    }

    @Test 
    void testExpressionLikeNotLike() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Hello World", "Content", alice);
            insertArticle(c, "Goodbye", "Content", alice);

            // LOWER(title) LIKE 'hello%'
            ArticleQuery q1 = new ArticleQuery();
            assertEquals(1, q1.where(q1.lower(q1.title).like("hello%")).list(c).size());

            // LOWER(title) NOT LIKE 'hello%'
            ArticleQuery q2 = new ArticleQuery();
            assertEquals(1, q2.where(q2.lower(q2.title).notLike("hello%")).list(c).size());
        });
    }

    @Test
    void testExpressionInNotIn() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = insertPerson(c, "Alice", "alice@example.com");
            insertArticle(c, "Alpha", "Content", alice);
            insertArticle(c, "Beta", "Content", alice);
            insertArticle(c, "Gamma", "Content", alice);

            // LOWER(title) IN ('alpha', 'gamma')
            ArticleQuery q1 = new ArticleQuery();
            assertEquals(2, q1.where(q1.lower(q1.title).in("alpha", "gamma")).list(c).size());

            // LOWER(title) NOT IN ('alpha', 'gamma')
            ArticleQuery q2 = new ArticleQuery();
            assertEquals(1, q2.where(q2.lower(q2.title).notIn("alpha", "gamma")).list(c).size());
        });
    }

    @Test
    void testExpressionEqField() {
        DB.withConnection(dataSource, (Connection c) -> {
            Person same = insertPerson(c, "same", "same@example.com");
            Person diff = insertPerson(c, "different", "diff@example.com");
            insertArticle(c, "SAME", "Content", same);  // LOWER(title) == author.name
            insertArticle(c, "Other", "Content", diff);

            // LOWER(title) = author.name (comparing expression to field)
            ArticleQuery q = new ArticleQuery();
            assertEquals(1, q.where(q.lower(q.title).eq(q.author.name)).list(c).size());
        });
    }

}
