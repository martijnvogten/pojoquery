package org.pojoquery.typedquery.entities;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.FieldMapping;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.util.FieldHelper;

import org.pojoquery.typedquery.chain.ChainFactory;
import org.pojoquery.typedquery.chain.ComparableConditionBuilderField;
import org.pojoquery.typedquery.chain.ConditionBuilder;
import org.pojoquery.typedquery.chain.ConditionBuilderField;
import org.pojoquery.typedquery.chain.ConditionBuilderImpl;
import org.pojoquery.typedquery.chain.ConditionChain;
import org.pojoquery.typedquery.chain.OrderByField;
import org.pojoquery.typedquery.chain.OrderByTarget;

import static org.pojoquery.SqlExpression.sql;

/**
 * Copy of ArticleQuery for iteration and testing.
 * Once this is bug-free, changes can be applied to the code generator.
 */
@SuppressWarnings("all")
public class ArticleQueryCopy {

    // Static condition builder fields for main entity
    public final ComparableConditionBuilderField<Long, ArticleQueryCopyStaticConditionChain> id =
            new ComparableConditionBuilderField<>(() -> new ArticleQueryCopyStaticConditionChain(), "article", "id");
    public final ComparableConditionBuilderField<String, ArticleQueryCopyStaticConditionChain> title =
            new ComparableConditionBuilderField<>(() -> new ArticleQueryCopyStaticConditionChain(), "article", "title");
    public final ComparableConditionBuilderField<String, ArticleQueryCopyStaticConditionChain> content =
            new ComparableConditionBuilderField<>(() -> new ArticleQueryCopyStaticConditionChain(), "article", "content");

    /** Static condition builder fields for the {@code author} relationship */
    public final AuthorFields author = new AuthorFields();

    public class AuthorFields {
        public final ComparableConditionBuilderField<Long, ArticleQueryCopyStaticConditionChain> id =
                new ComparableConditionBuilderField<>(() -> new ArticleQueryCopyStaticConditionChain(), "author", "id");
        public final ComparableConditionBuilderField<String, ArticleQueryCopyStaticConditionChain> name =
                new ComparableConditionBuilderField<>(() -> new ArticleQueryCopyStaticConditionChain(), "author", "name");
        public final ComparableConditionBuilderField<String, ArticleQueryCopyStaticConditionChain> email =
                new ComparableConditionBuilderField<>(() -> new ArticleQueryCopyStaticConditionChain(), "author", "email");
    }

    /**
     * Condition chain for building static conditions.
     * Implements Supplier&lt;SqlExpression&gt; to be used in and()/or() methods.
     */
    public class ArticleQueryCopyStaticConditionChain
            implements ConditionChain<ArticleQueryCopyStaticConditionChain>, Supplier<SqlExpression> {

        public class StaticConditionFields {
            public final ComparableConditionBuilderField<Long, ArticleQueryCopyStaticConditionChain> id =
                    new ComparableConditionBuilderField<>(() -> ArticleQueryCopyStaticConditionChain.this, "article", "id");
            public final ComparableConditionBuilderField<String, ArticleQueryCopyStaticConditionChain> title =
                    new ComparableConditionBuilderField<>(() -> ArticleQueryCopyStaticConditionChain.this, "article", "title");
            public final ComparableConditionBuilderField<String, ArticleQueryCopyStaticConditionChain> content =
                    new ComparableConditionBuilderField<>(() -> ArticleQueryCopyStaticConditionChain.this, "article", "content");

            /** Condition fields for the {@code author} relationship */
            public final AuthorConditionFields author = new AuthorConditionFields();

            public class AuthorConditionFields {
                public final ComparableConditionBuilderField<Long, ArticleQueryCopyStaticConditionChain> id =
                        new ComparableConditionBuilderField<>(() -> ArticleQueryCopyStaticConditionChain.this, "author", "id");
                public final ComparableConditionBuilderField<String, ArticleQueryCopyStaticConditionChain> name =
                        new ComparableConditionBuilderField<>(() -> ArticleQueryCopyStaticConditionChain.this, "author", "name");
                public final ComparableConditionBuilderField<String, ArticleQueryCopyStaticConditionChain> email =
                        new ComparableConditionBuilderField<>(() -> ArticleQueryCopyStaticConditionChain.this, "author", "email");
            }
        }

        ConditionBuilder builder = new ConditionBuilderImpl();

        @Override
        public ConditionBuilder getBuilder() {
            return builder;
        }

        public StaticConditionFields and() {
            builder.add(sql(" AND "));
            return new StaticConditionFields();
        }

        public StaticConditionFields or() {
            builder.add(sql(" OR "));
            return new StaticConditionFields();
        }

        public ArticleQueryCopyStaticConditionChain and(Supplier<SqlExpression> expr) {
            builder.add(sql(" AND ")).startClause().add(expr.get()).endClause();
            return this;
        }

        public ArticleQueryCopyStaticConditionChain or(Supplier<SqlExpression> expr) {
            builder.add(sql(" OR ")).startClause().add(expr.get()).endClause();
            return this;
        }

        @Override
        public ArticleQueryCopyStaticConditionChain getContinuation() {
            return this;
        }

        @Override
        public SqlExpression get() {
            return SqlExpression.implode("", ((ConditionBuilderImpl) builder).getExpressions());
        }
    }

    protected SqlQuery<?> query = new DefaultSqlQuery(DbContext.getDefault());
    protected DbContext dbContext = DbContext.getDefault();
    protected ArticleQueryCopyWhereBuilder pendingWhereBuilder;

    /** Applies any pending where conditions to the query. */
    protected void applyPendingConditions() {
        if (pendingWhereBuilder != null) {
            pendingWhereBuilder.applyConditions();
        }
    }

    protected void initializeQuery() {
        query.setTable(null, "article");
        query.addJoin(org.pojoquery.pipeline.SqlQuery.JoinType.LEFT, null, "person", "author", SqlExpression.sql("{article.author_id} = {author.id}"));
        query.addField(sql("{article.id}"), "article.id");
        query.addField(sql("{article.title}"), "article.title");
        query.addField(sql("{article.content}"), "article.content");
        query.addField(sql("{author.id}"), "author.id");
        query.addField(sql("{author.name}"), "author.name");
        query.addField(sql("{author.email}"), "author.email");
    }

    public ArticleQueryCopy() {
        initializeQuery();
    }

    public ArticleQueryCopyWhereBuilder where() {
        pendingWhereBuilder = new ArticleQueryCopyWhereBuilder(this);
        return pendingWhereBuilder;
    }

    public ArticleQueryCopyOrderByBuilder orderBy() {
        return new ArticleQueryCopyOrderByBuilder();
    }

    public ArticleQueryCopyGroupByBuilder groupBy() {
        return new ArticleQueryCopyGroupByBuilder();
    }

    public ArticleQueryCopy groupBy(String fieldExpression) {
        query.addGroupBy(fieldExpression);
        return this;
    }

    public ArticleQueryCopy orderBy(String fieldExpression, boolean ascending) {
        query.addOrderBy(fieldExpression + (ascending ? " ASC" : " DESC"));
        return this;
    }

    public List<Article> list(Connection connection) {
        applyPendingConditions();
        SqlExpression stmt = query.toStatement();
        List<Map<String, Object>> rows = DB.queryRows(connection, stmt);
        try {
            return processRows(rows);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Article> processRows(List<Map<String, Object>> rows) throws NoSuchFieldException, IllegalAccessException {

        // Article field mappings
        FieldMapping fmArticleId = dbContext.getFieldMapping(FieldHelper.getField(Article.class, "id"));
        FieldMapping fmArticleTitle = dbContext.getFieldMapping(FieldHelper.getField(Article.class, "title"));
        FieldMapping fmArticleContent = dbContext.getFieldMapping(FieldHelper.getField(Article.class, "content"));

        // Person field mappings
        FieldMapping fmAuthorId = dbContext.getFieldMapping(FieldHelper.getField(Person.class, "id"));
        FieldMapping fmAuthorName = dbContext.getFieldMapping(FieldHelper.getField(Person.class, "name"));
        FieldMapping fmAuthorEmail = dbContext.getFieldMapping(FieldHelper.getField(Person.class, "email"));

        // Link field: article.author
        Field fArticleAuthor = FieldHelper.getField(Article.class, "author");
        fArticleAuthor.setAccessible(true);

        // Entity deduplication maps
        List<Article> result = new ArrayList<>();
        Map<Object, Article> articleById = new HashMap<>();
        Map<Object, Person> authorById = new HashMap<>();

        for (Map<String, Object> row : rows) {
            // Process root entity: Article
            Object articleId = row.get("article.id");
            if (articleId == null) continue;

            Article article = articleById.get(articleId);
            if (article == null) {
                article = new Article();
                fmArticleId.apply(article, row.get("article.id"));
                fmArticleTitle.apply(article, row.get("article.title"));
                fmArticleContent.apply(article, row.get("article.content"));
                articleById.put(articleId, article);
                result.add(article);
            }

            // Process relationship: author (Person)
            Object authorId = row.get("author.id");
            if (authorId != null) {
                Person author = authorById.get(authorId);
                if (author == null) {
                    author = new Person();
                    fmAuthorId.apply(author, row.get("author.id"));
                    fmAuthorName.apply(author, row.get("author.name"));
                    fmAuthorEmail.apply(author, row.get("author.email"));
                    authorById.put(authorId, author);
                }

                // Link to parent
                FieldHelper.putValueIntoField(article, fArticleAuthor, author);
            }
        }

        return result;
    }

    public class ArticleQueryCopyGroupByBuilder {

        public final ArticleQueryCopyGroupByField id;
        public final ArticleQueryCopyGroupByField title;
        public final ArticleQueryCopyGroupByField content;

        public ArticleQueryCopyGroupByBuilder() {
            this.id = new ArticleQueryCopyGroupByField("article", "id");
            this.title = new ArticleQueryCopyGroupByField("article", "title");
            this.content = new ArticleQueryCopyGroupByField("article", "content");
        }
    }

    public class ArticleQueryCopyOrderByBuilder implements OrderByTarget {

        public final OrderByField<ArticleQueryCopy> id;
        public final OrderByField<ArticleQueryCopy> title;
        public final OrderByField<ArticleQueryCopy> content;

        /** OrderBy fields for the {@code author} relationship */
        public final AuthorOrderByFields author = new AuthorOrderByFields();

        public class AuthorOrderByFields {
            public final OrderByField<ArticleQueryCopy> id =
                    new OrderByField<>(ArticleQueryCopyOrderByBuilder.this, ArticleQueryCopy.this, "author", "id");
            public final OrderByField<ArticleQueryCopy> name =
                    new OrderByField<>(ArticleQueryCopyOrderByBuilder.this, ArticleQueryCopy.this, "author", "name");
            public final OrderByField<ArticleQueryCopy> email =
                    new OrderByField<>(ArticleQueryCopyOrderByBuilder.this, ArticleQueryCopy.this, "author", "email");
        }

        public ArticleQueryCopyOrderByBuilder() {
            this.id = new OrderByField<>(this, ArticleQueryCopy.this, "article", "id");
            this.title = new OrderByField<>(this, ArticleQueryCopy.this, "article", "title");
            this.content = new OrderByField<>(this, ArticleQueryCopy.this, "article", "content");
        }

        @Override
        public void orderBy(String fieldExpression, boolean ascending) {
            query.addOrderBy(fieldExpression + (ascending ? " ASC" : " DESC"));
        }
    }

    /**
     * Delegate class for callback pattern - allows groupBy().field.list() syntax.
     */
    private class ArticleQueryCopyDelegate {
        protected void callback() {}

        public List<Article> list(Connection connection) {
            callback();
            return ArticleQueryCopy.this.list(connection);
        }

        public ArticleQueryCopyGroupByBuilder groupBy() {
            callback();
            return ArticleQueryCopy.this.groupBy();
        }

        public ArticleQueryCopyOrderByBuilder orderBy() {
            callback();
            return ArticleQueryCopy.this.orderBy();
        }
    }

    public class ArticleQueryCopyGroupByField extends ArticleQueryCopyDelegate {
        private String tableAlias;
        private String columnName;

        public ArticleQueryCopyGroupByField(String tableAlias, String columnName) {
            this.tableAlias = tableAlias;
            this.columnName = columnName;
        }

        @Override
        protected void callback() {
            query.addGroupBy("{" + tableAlias + "." + columnName + "}");
        }
    }

    /**
     * Where clause builder for fluent condition chaining.
     * Conditions are applied directly to the query as they are built.
     */
    public class ArticleQueryCopyWhereBuilder implements ConditionChain<ArticleQueryCopyWhereBuilder.ArticleQueryCopyWhereBuilderConditionTerminator> {

        public final ComparableConditionBuilderField<Long, ArticleQueryCopyWhereBuilderConditionTerminator> id =
                new ComparableConditionBuilderField<>(() -> this, "article", "id");
        public final ComparableConditionBuilderField<String, ArticleQueryCopyWhereBuilderConditionTerminator> title =
                new ComparableConditionBuilderField<>(() -> this, "article", "title");
        public final ComparableConditionBuilderField<String, ArticleQueryCopyWhereBuilderConditionTerminator> content =
                new ComparableConditionBuilderField<>(() -> this, "article", "content");

        /** Where fields for the {@code author} relationship */
        public final AuthorWhereFields author = new AuthorWhereFields();

        public class AuthorWhereFields {
            public final ComparableConditionBuilderField<Long, ArticleQueryCopyWhereBuilderConditionTerminator> id =
                    new ComparableConditionBuilderField<>(() -> ArticleQueryCopyWhereBuilder.this, "author", "id");
            public final ComparableConditionBuilderField<String, ArticleQueryCopyWhereBuilderConditionTerminator> name =
                    new ComparableConditionBuilderField<>(() -> ArticleQueryCopyWhereBuilder.this, "author", "name");
            public final ComparableConditionBuilderField<String, ArticleQueryCopyWhereBuilderConditionTerminator> email =
                    new ComparableConditionBuilderField<>(() -> ArticleQueryCopyWhereBuilder.this, "author", "email");

            /** Check if this relationship is null (by checking the ID field) */
            public ArticleQueryCopyWhereBuilderConditionTerminator isNull() {
                return id.isNull();
            }

            /** Check if this relationship is not null (by checking the ID field) */
            public ArticleQueryCopyWhereBuilderConditionTerminator isNotNull() {
                return id.isNotNull();
            }
        }

        private final ArticleQueryCopy parentQuery;
        List<SqlExpression> collectedConditions = new java.util.ArrayList<>();
        ConditionBuilder builder = new WhereBuilderImpl();
        private final ArticleQueryCopyWhereBuilderConditionTerminator terminator = new ArticleQueryCopyWhereBuilderConditionTerminator();

        protected ArticleQueryCopyWhereBuilder(ArticleQueryCopy query) {
            this.parentQuery = query;
        }

        public void accept(SqlExpression expr) {
            collectedConditions.add(expr);
        }

        /** Applies the collected conditions to the parent query. */
        void applyConditions() {
            if (!collectedConditions.isEmpty()) {
                parentQuery.query.addWhere(SqlExpression.implode("", collectedConditions));
                collectedConditions.clear();
                // Clear pending since we've applied
                parentQuery.pendingWhereBuilder = null;
            }
        }

        public class WhereBuilderImpl implements ConditionBuilder {
            public ConditionBuilder startClause() {
                collectedConditions.add(sql(" ("));
                return this;
            }

            public ConditionBuilder endClause() {
                collectedConditions.add(sql(") "));
                return this;
            }

            @Override
            public ConditionBuilder add(SqlExpression expr) {
                collectedConditions.add(expr);
                return this;
            }
        }

        public class ArticleQueryCopyWhereBuilderConditionTerminator extends ArticleQueryCopyDelegate {

            @Override
            protected void callback() {
                // No longer needed - conditions applied immediately
            }

            public ArticleQueryCopyWhereBuilder and() {
                builder.add(sql(" AND "));
                return ArticleQueryCopyWhereBuilder.this;
            }

            public ArticleQueryCopyWhereBuilder or() {
                builder.add(sql(" OR "));
                return ArticleQueryCopyWhereBuilder.this;
            }

            public ArticleQueryCopyWhereBuilderConditionTerminator and(Supplier<SqlExpression> expr) {
                builder.add(sql(" AND ")).startClause().add(expr.get()).endClause();
                return this;
            }

            public ArticleQueryCopyWhereBuilderConditionTerminator or(Supplier<SqlExpression> expr) {
                builder.add(sql(" OR ")).startClause().add(expr.get()).endClause();
                return this;
            }
        }

        @Override
        public ArticleQueryCopyWhereBuilderConditionTerminator getContinuation() {
            // Conditions will be applied when list() or similar is called
            return terminator;
        }

        @Override
        public ConditionBuilder getBuilder() {
            return builder;
        }
    }

}
