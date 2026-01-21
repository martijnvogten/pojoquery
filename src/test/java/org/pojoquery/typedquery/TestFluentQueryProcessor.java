package org.pojoquery.typedquery;

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
import org.pojoquery.DbContextBuilder;
import org.pojoquery.DbContext.QuoteStyle;
import org.pojoquery.PojoQuery;
import org.pojoquery.integrationtest.DbContextExtension;
import org.pojoquery.schema.SchemaGenerator;
import org.pojoquery.typedquery.entities.Article;
import org.pojoquery.typedquery.entities.ArticleQuery;
import org.pojoquery.typedquery.entities.ArticleQuery.ArticleQueryStaticConditionChain;
import org.pojoquery.typedquery.entities.Person;

/**
 * Test for the FluentQueryProcessor - tests the generated code pattern.
 */
@ExtendWith(DbContextExtension.class)
public class TestFluentQueryProcessor {

    private DataSource dataSource;

    @BeforeEach
    void setup() {
        // Set up HSQLDB with ANSI quoting
        DbContext.setDefault(new DbContextBuilder()
                .dialect(Dialect.HSQLDB)
                .withQuoteStyle(QuoteStyle.ANSI)
                .quoteObjectNames(true)
                .build());

        // Create unique in-memory database
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:fluent_query_test_" + System.nanoTime());
        ds.setUser("SA");
        ds.setPassword("");
        dataSource = ds;

        // Create tables - Person must be created before Article (FK dependency)
        SchemaGenerator.createTables(dataSource, Person.class, Article.class);

        // Insert test data using DB.withConnection and PojoQuery.insert
        DB.withConnection(dataSource, (Connection c) -> {
            Person alice = new Person();
            alice.id = 1L;
            alice.name = "Alice";
            alice.email = "alice@example.com";
            PojoQuery.insert(c, alice);

            Person bob = new Person();
            bob.id = 2L;
            bob.name = "Bob";
            bob.email = "bob@example.com";
            PojoQuery.insert(c, bob);

            Article article1 = new Article();
            article1.id = 1L;
            article1.title = "First Article";
            article1.content = "Content 1";
            article1.author = alice;
            PojoQuery.insert(c, article1);

            Article article2 = new Article();
            article2.id = 2L;
            article2.title = "Second Article";
            article2.content = "Content 2";
            article2.author = bob;
            PojoQuery.insert(c, article2);

            Article article3 = new Article();
            article3.id = 3L;
            article3.title = "Third Article";
            article3.content = "Content 3";
            article3.author = alice;
            PojoQuery.insert(c, article3);
        });
    }

    @Test
    public void testSimpleQuery() {
        DB.withConnection(dataSource, (Connection c) -> {
            ArticleQuery q = new ArticleQuery();
            
            List<Article> articles = q.list(c);
            
            assertEquals(3, articles.size());
            System.out.println("Found " + articles.size() + " articles");
            for (Article article : articles) {
                System.out.println("  - " + article.title + " by " + (article.author != null ? article.author.name : "null"));
            }
        });
    }

    @Test
    public void testQueryWithWhereClause() {
        DB.withConnection(dataSource, (Connection c) -> {
            ArticleQuery q = new ArticleQuery();
            List<Article> articles = q.where().title.eq("First Article").list(c);

            ArticleQueryStaticConditionChain cond = q.title.eq("henk");
            assertEquals("{article.title} = ?", cond.get().getSql());
            assertEquals("henk", cond.get().getParameters().iterator().next());
            
            assertEquals(1, articles.size());
            assertEquals("First Article", articles.get(0).title);
            assertEquals("Alice", articles.get(0).author.name);
        });
    }

    @Test
    public void testQueryWithComplexConditions() {
        DB.withConnection(dataSource, (Connection c) -> {
            ArticleQuery q = new ArticleQuery();
            List<Article> articles = q.where()
                    .title.eq("First Article")
                    .and(q.id.eq(1L).or().id.eq(3L))
                    .or().author.isNotNull()
                    .orderBy()
                    .title.desc().list(c);
            
            // All articles have non-null authors, so all 3 should be returned
            assertFalse(articles.isEmpty());
            System.out.println("Query with complex conditions returned " + articles.size() + " articles");
            for (Article article : articles) {
                System.out.println("  - " + article.title);
            }
        });
    }

    @Test
    public void testQueryByAuthorName() {
        DB.withConnection(dataSource, (Connection c) -> {
            ArticleQuery q = new ArticleQuery();
            List<Article> articles = q.where().author.name.eq("Alice").list(c);
            
            assertEquals(2, articles.size());
            for (Article article : articles) {
                assertEquals("Alice", article.author.name);
            }
        });
    }
}
