package org.pojoquery.processor.expected;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.FieldMapping;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.DefaultSqlQuery;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.util.FieldHelper;

import org.pojoquery.typedquery.ChainFactory;
import org.pojoquery.typedquery.ChainableExpression;
import org.pojoquery.typedquery.ComparableConditionBuilderField;
import org.pojoquery.typedquery.ConditionBuilder;
import org.pojoquery.typedquery.ConditionBuilderField;
import org.pojoquery.typedquery.ConditionBuilderImpl;
import org.pojoquery.typedquery.ConditionChain;
import org.pojoquery.typedquery.OrderByField;
import org.pojoquery.typedquery.OrderByTarget;
import org.pojoquery.typedquery.TypedQuery;

import static org.pojoquery.SqlExpression.sql;

/**
 * Generated fluent query builder for {@link Book}.
 * <p>Usage example:
 * <pre>
 * BookQuery q = new BookQuery();
 * q.title.eq("John").and().title.isNotNull();
 * </pre>
 */
@SuppressWarnings("all")
public class BookQuery extends TypedQuery<Book, Long, BookQuery> {

    // Static condition builder fields for main entity
    public final ComparableConditionBuilderField<Long, BookQueryStaticConditionChain> id =
            new ComparableConditionBuilderField<>(() -> new BookQueryStaticConditionChain(), "book", "id");
    public final ComparableConditionBuilderField<String, BookQueryStaticConditionChain> title =
            new ComparableConditionBuilderField<>(() -> new BookQueryStaticConditionChain(), "book", "title");

    /** Static condition builder fields for the {@code author} relationship */
    public final AuthorFields author = new AuthorFields();

    public class AuthorFields {
        public final ComparableConditionBuilderField<Long, BookQueryStaticConditionChain> id =
                new ComparableConditionBuilderField<>(() -> new BookQueryStaticConditionChain(), "author", "id");
        public final ComparableConditionBuilderField<String, BookQueryStaticConditionChain> name =
                new ComparableConditionBuilderField<>(() -> new BookQueryStaticConditionChain(), "author", "name");
    }

    /**
     * Condition chain for building static conditions.
     * Implements Supplier&lt;SqlExpression&gt; to be used in and()/or() methods.
     */
    public class BookQueryStaticConditionChain
            implements ConditionChain<BookQueryStaticConditionChain>, Supplier<SqlExpression> {

        public class StaticConditionFields {
            public final ComparableConditionBuilderField<Long, BookQueryStaticConditionChain> id =
                    new ComparableConditionBuilderField<>(() -> BookQueryStaticConditionChain.this, "book", "id");
            public final ComparableConditionBuilderField<String, BookQueryStaticConditionChain> title =
                    new ComparableConditionBuilderField<>(() -> BookQueryStaticConditionChain.this, "book", "title");

            /** Condition fields for the {@code author} relationship */
            public final AuthorConditionFields author = new AuthorConditionFields();

            public class AuthorConditionFields {
                public final ComparableConditionBuilderField<Long, BookQueryStaticConditionChain> id =
                        new ComparableConditionBuilderField<>(() -> BookQueryStaticConditionChain.this, "author", "id");
                public final ComparableConditionBuilderField<String, BookQueryStaticConditionChain> name =
                        new ComparableConditionBuilderField<>(() -> BookQueryStaticConditionChain.this, "author", "name");
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

        public BookQueryStaticConditionChain and(Supplier<SqlExpression> expr) {
            builder.add(sql(" AND ")).startClause().add(expr.get()).endClause();
            return this;
        }

        public BookQueryStaticConditionChain or(Supplier<SqlExpression> expr) {
            builder.add(sql(" OR ")).startClause().add(expr.get()).endClause();
            return this;
        }

        @Override
        public BookQueryStaticConditionChain getContinuation() {
            return this;
        }

        @Override
        public SqlExpression get() {
            return SqlExpression.implode("", ((ConditionBuilderImpl) builder).getExpressions());
        }
    }

    @Override
    protected void initializeQuery() {
        query.setTable(null, "book");
        query.addJoin(org.pojoquery.pipeline.SqlQuery.JoinType.LEFT, null, "person", "author", SqlExpression.sql("{book.author_id} = {author.id}"));
        query.addField(sql("{book.id}"), "book.id");
        query.addField(sql("{book.title}"), "book.title");
        query.addField(sql("{author.id}"), "author.id");
        query.addField(sql("{author.name}"), "author.name");
    }

    public BookQuery() {
        initializeQuery();
    }

    /** Collected WHERE conditions - lives on query for direct access from builders. */
    protected final List<SqlExpression> collectedConditions = new java.util.ArrayList<>();

    /** Applies any pending where conditions to the query. */
    protected void applyPendingConditions() {
        if (!collectedConditions.isEmpty()) {
            SqlExpression whereExpr = SqlExpression.implode("", collectedConditions);
            collectedConditions.clear();
            query.addWhere(whereExpr);
        }
    }

    @Override
    public List<Book> list(Connection connection) {
        applyPendingConditions();
        return super.list(connection);
    }

    @Override
    public List<Long> listIds(Connection connection) {
        applyPendingConditions();
        return super.listIds(connection);
    }

    public BookQueryWhereBuilder where() {
        if (!collectedConditions.isEmpty()) {
            collectedConditions.add(sql(" AND "));
        }
        return new BookQueryWhereBuilder(this);
    }

    /**
     * Adds a where condition from a static condition chain and returns a terminator for continued chaining.
     * <p>Example: {@code q.where(q.concat(q.author.name, " ", q.author.email).eq("James Brown")).and().author.isNotNull()}
     */
    public BookQueryWhereBuilder.BookQueryWhereBuilderConditionTerminator where(Supplier<SqlExpression> condition) {
        if (!collectedConditions.isEmpty()) {
            collectedConditions.add(sql(" AND "));
        }
        BookQueryWhereBuilder whereBuilder = new BookQueryWhereBuilder(this);
        whereBuilder.builder.add(condition.get());
        return whereBuilder.getContinuation();
    }

    // === SQL function methods with chainable return types ===

    /**
     * Creates a CONCAT expression from the given parts.
     * Parts can be ConditionBuilderField instances or literal values.
     * <p>Example: {@code q.where(q.concat(q.firstName, " ", q.lastName).eq("John Doe").and().id.gt(1L))}
     */
    public ChainableExpression<String, BookQueryStaticConditionChain> concat(Object... parts) {
        return buildConcat(() -> new BookQueryStaticConditionChain(), parts);
    }

    /**
     * Creates a LOWER expression.
     * <p>Example: {@code q.where(q.lower(q.email).eq("john@example.com"))}
     */
    public ChainableExpression<String, BookQueryStaticConditionChain> lower(Object part) {
        return buildSingleArgFunction("LOWER", () -> new BookQueryStaticConditionChain(), part);
    }

    /**
     * Creates an UPPER expression.
     */
    public ChainableExpression<String, BookQueryStaticConditionChain> upper(Object part) {
        return buildSingleArgFunction("UPPER", () -> new BookQueryStaticConditionChain(), part);
    }

    /**
     * Creates a TRIM expression.
     */
    public ChainableExpression<String, BookQueryStaticConditionChain> trim(Object part) {
        return buildSingleArgFunction("TRIM", () -> new BookQueryStaticConditionChain(), part);
    }

    /**
     * Creates a LENGTH expression.
     */
    public ChainableExpression<Number, BookQueryStaticConditionChain> length(Object part) {
        return buildSingleArgFunction("LENGTH", () -> new BookQueryStaticConditionChain(), part);
    }

    /**
     * Creates a COALESCE expression.
     */
    public <V> ChainableExpression<V, BookQueryStaticConditionChain> coalesce(Object... parts) {
        return buildMultiArgFunction("COALESCE", () -> new BookQueryStaticConditionChain(), parts);
    }

    /**
     * Creates an ABS expression.
     */
    public <V extends Number> ChainableExpression<V, BookQueryStaticConditionChain> abs(Object part) {
        return buildSingleArgFunction("ABS", () -> new BookQueryStaticConditionChain(), part);
    }

    /**
     * Creates a SUBSTRING expression.
     */
    public ChainableExpression<String, BookQueryStaticConditionChain> substring(Object part, int start, int len) {
        return buildSubstring(() -> new BookQueryStaticConditionChain(), part, start, len);
    }

    public BookQueryOrderByBuilder orderBy() {
        return new BookQueryOrderByBuilder();
    }

    public BookQueryGroupByBuilder groupBy() {
        return new BookQueryGroupByBuilder();
    }

    public BookQuery groupBy(String fieldExpression) {
        query.addGroupBy(fieldExpression);
        return this;
    }

    public BookQuery orderBy(String fieldExpression, boolean ascending) {
        query.addOrderBy(fieldExpression + (ascending ? " ASC" : " DESC"));
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Book> processRows(List<Map<String, Object>> rows) throws NoSuchFieldException, IllegalAccessException {

        // Book field mappings
        FieldMapping fmBookId = dbContext.getFieldMapping(FieldHelper.getField(Book.class, "id"));
        FieldMapping fmBookTitle = dbContext.getFieldMapping(FieldHelper.getField(Book.class, "title"));

        // Person field mappings
        FieldMapping fmAuthorId = dbContext.getFieldMapping(FieldHelper.getField(Person.class, "id"));
        FieldMapping fmAuthorName = dbContext.getFieldMapping(FieldHelper.getField(Person.class, "name"));

        // Link field: book.author
        Field fBookAuthor = FieldHelper.getField(Book.class, "author");
        fBookAuthor.setAccessible(true);

        // Entity deduplication maps
        List<Book> result = new ArrayList<>();
        Map<Object, Book> bookById = new HashMap<>();
        Map<Object, Person> authorById = new HashMap<>();

        for (Map<String, Object> row : rows) {
            // Process root entity: Book
            Object bookId = row.get("book.id");
            if (bookId == null) continue;

            Book book = bookById.get(bookId);
            if (book == null) {
                book = new Book();
                fmBookId.apply(book, row.get("book.id"));
                fmBookTitle.apply(book, row.get("book.title"));
                bookById.put(bookId, book);
                result.add(book);
            }

            // Process relationship: author (Person)
            Object authorId = row.get("author.id");
            if (authorId != null) {
                Person author = authorById.get(authorId);
                if (author == null) {
                    author = new Person();
                    fmAuthorId.apply(author, row.get("author.id"));
                    fmAuthorName.apply(author, row.get("author.name"));
                    authorById.put(authorId, author);
                }

                // Link to parent
                FieldHelper.putValueIntoField(book, fBookAuthor, author);
            }
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Book processRowStreaming(Map<String, Object> row, Map<Object, Object> entityCache) throws NoSuchFieldException, IllegalAccessException {

        // Book field mappings
        FieldMapping fmBookId = dbContext.getFieldMapping(FieldHelper.getField(Book.class, "id"));
        FieldMapping fmBookTitle = dbContext.getFieldMapping(FieldHelper.getField(Book.class, "title"));

        // Person field mappings
        FieldMapping fmAuthorId = dbContext.getFieldMapping(FieldHelper.getField(Person.class, "id"));
        FieldMapping fmAuthorName = dbContext.getFieldMapping(FieldHelper.getField(Person.class, "name"));

        // Link field: book.author
        Field fBookAuthor = FieldHelper.getField(Book.class, "author");
        fBookAuthor.setAccessible(true);

        Book rootEntity = null;

        // Process root entity: Book
        Object bookId = row.get("book.id");
        if (bookId != null) {
            Book book = (Book) entityCache.get(bookId);
            if (book == null) {
                book = new Book();
                fmBookId.apply(book, row.get("book.id"));
                fmBookTitle.apply(book, row.get("book.title"));
                entityCache.put(bookId, book);
            }
            rootEntity = book;

            // Process relationship: author (Person)
            Object authorId = row.get("author.id");
            if (authorId != null) {
                Person author = (Person) entityCache.get(authorId);
                if (author == null) {
                    author = new Person();
                    fmAuthorId.apply(author, row.get("author.id"));
                    fmAuthorName.apply(author, row.get("author.name"));
                    entityCache.put(authorId, author);
                }

                // Link to parent
                FieldHelper.putValueIntoField(book, fBookAuthor, author);
            }
        }

        return rootEntity;
    }

    @Override
    protected Object getPrimaryKeyFromRow(Map<String, Object> row) {
        return row.get("book.id");
    }

    @Override
    protected String getIdFieldName() {
        return "book.id";
    }

    @Override
    protected SqlExpression buildIdCondition(Object id) {
        return SqlExpression.sql("{book.id} = ?", id);
    }

    @Override
    protected Class<Book> getEntityClass() {
        return Book.class;
    }

    public class BookQueryGroupByBuilder {

        public final BookQueryGroupByField id;
        public final BookQueryGroupByField title;

        public BookQueryGroupByBuilder() {
            this.id = new BookQueryGroupByField("book", "id");
            this.title = new BookQueryGroupByField("book", "title");
        }
    }

    public class BookQueryOrderByBuilder implements OrderByTarget {

        public final OrderByField<BookQuery> id;
        public final OrderByField<BookQuery> title;

        /** OrderBy fields for the {@code author} relationship */
        public final AuthorOrderByFields author = new AuthorOrderByFields();

        public class AuthorOrderByFields {
            public final OrderByField<BookQuery> id =
                    new OrderByField<>(BookQueryOrderByBuilder.this, BookQuery.this, "author", "id");
            public final OrderByField<BookQuery> name =
                    new OrderByField<>(BookQueryOrderByBuilder.this, BookQuery.this, "author", "name");
        }

        public BookQueryOrderByBuilder() {
            this.id = new OrderByField<>(this, BookQuery.this, "book", "id");
            this.title = new OrderByField<>(this, BookQuery.this, "book", "title");
        }

        @Override
        public void orderBy(String fieldExpression, boolean ascending) {
            query.addOrderBy(fieldExpression + (ascending ? " ASC" : " DESC"));
        }
    }

    /**
     * Delegate class for callback pattern - allows groupBy().field.list() syntax.
     */
    private class BookQueryDelegate {
        protected void callback() {}

        public List<Book> list(Connection connection) {
            callback();
            return BookQuery.this.list(connection);
        }

        public List<Long> listIds(Connection connection) {
            callback();
            return BookQuery.this.listIds(connection);
        }

        public Optional<Book> first(Connection connection) {
            callback();
            return BookQuery.this.first(connection);
        }

        public Optional<Book> findById(Connection connection, Long id) {
            callback();
            return BookQuery.this.findById(connection, id);
        }

        public void stream(Connection connection, java.util.function.Consumer<Book> consumer) {
            callback();
            BookQuery.this.stream(connection, consumer);
        }

        public BookQueryGroupByBuilder groupBy() {
            callback();
            return BookQuery.this.groupBy();
        }

        public BookQueryOrderByBuilder orderBy() {
            callback();
            return BookQuery.this.orderBy();
        }
    }

    public class BookQueryGroupByField extends BookQueryDelegate {
        private String tableAlias;
        private String columnName;

        public BookQueryGroupByField(String tableAlias, String columnName) {
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
     */
    public class BookQueryWhereBuilder implements ConditionChain<BookQueryWhereBuilder.BookQueryWhereBuilderConditionTerminator> {

        public final ComparableConditionBuilderField<Long, BookQueryWhereBuilderConditionTerminator> id =
                new ComparableConditionBuilderField<>(this::getContinuation, "book", "id");
        public final ComparableConditionBuilderField<String, BookQueryWhereBuilderConditionTerminator> title =
                new ComparableConditionBuilderField<>(this::getContinuation, "book", "title");

        /** Where fields for the {@code author} relationship */
        public final AuthorWhereFields author = new AuthorWhereFields();

        public class AuthorWhereFields {
            public final ComparableConditionBuilderField<Long, BookQueryWhereBuilderConditionTerminator> id =
                    new ComparableConditionBuilderField<>(BookQueryWhereBuilder.this::getContinuation, "author", "id");
            public final ComparableConditionBuilderField<String, BookQueryWhereBuilderConditionTerminator> name =
                    new ComparableConditionBuilderField<>(BookQueryWhereBuilder.this::getContinuation, "author", "name");
        }

        ConditionBuilder builder = new WhereBuilderImpl();

        protected BookQueryWhereBuilder(BookQuery query) {
        }

        public class WhereBuilderImpl implements ConditionBuilder {
            public ConditionBuilder startClause() {
                BookQuery.this.collectedConditions.add(sql(" ("));
                return this;
            }

            public ConditionBuilder endClause() {
                BookQuery.this.collectedConditions.add(sql(") "));
                return this;
            }

            @Override
            public ConditionBuilder add(SqlExpression expr) {
                BookQuery.this.collectedConditions.add(expr);
                return this;
            }
        }

        public class BookQueryWhereBuilderConditionTerminator extends BookQueryDelegate
                implements ConditionChain<BookQueryWhereBuilderConditionTerminator> {

            public final ComparableConditionBuilderField<Long, BookQueryWhereBuilderConditionTerminator> id =
                    new ComparableConditionBuilderField<>(() -> this, "book", "id");
            public final ComparableConditionBuilderField<String, BookQueryWhereBuilderConditionTerminator> title =
                    new ComparableConditionBuilderField<>(() -> this, "book", "title");

            /** Terminator fields for the {@code author} relationship */
            public final AuthorTerminatorFields author = new AuthorTerminatorFields();

            public class AuthorTerminatorFields {
                public final ComparableConditionBuilderField<Long, BookQueryWhereBuilderConditionTerminator> id =
                        new ComparableConditionBuilderField<>(() -> BookQueryWhereBuilderConditionTerminator.this, "author", "id");
                public final ComparableConditionBuilderField<String, BookQueryWhereBuilderConditionTerminator> name =
                        new ComparableConditionBuilderField<>(() -> BookQueryWhereBuilderConditionTerminator.this, "author", "name");
            }

            public BookQueryWhereBuilderConditionTerminator and() {
                builder.add(sql(" AND "));
                return this;
            }

            public BookQueryWhereBuilderConditionTerminator or() {
                builder.add(sql(" OR "));
                return this;
            }

            public BookQueryWhereBuilderConditionTerminator and(Supplier<SqlExpression> expr) {
                builder.add(sql(" AND ")).startClause().add(expr.get()).endClause();
                return this;
            }

            public BookQueryWhereBuilderConditionTerminator or(Supplier<SqlExpression> expr) {
                builder.add(sql(" OR ")).startClause().add(expr.get()).endClause();
                return this;
            }

            @Override
            public ConditionBuilder getBuilder() {
                return builder;
            }

            @Override
            public BookQueryWhereBuilderConditionTerminator getContinuation() {
                return this;
            }
        }

        @Override
        public ConditionBuilder getBuilder() {
            return builder;
        }

        @Override
        public BookQueryWhereBuilderConditionTerminator getContinuation() {
            return new BookQueryWhereBuilderConditionTerminator();
        }
    }

}

