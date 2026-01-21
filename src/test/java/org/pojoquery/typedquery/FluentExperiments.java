package org.pojoquery.typedquery;

import static org.pojoquery.SqlExpression.sql;

import java.sql.Connection;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;
import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;
import org.pojoquery.pipeline.SqlQuery;

public class FluentExperiments {

	// Below is the original class that will be annotated with @GenerateQuery
	static class Book {
		@Id
		public Long id;
		public String title;
	}

	// These are the utility classes that will be available to the generated code
	// Move them to a separate package if needed

	interface ConditionChain<C> {
		C getContinuation();

		ConditionBuilder getBuilder();
	}

	interface ChainFactory<C> {
		ConditionChain<C> createChain();
	}

	public static class ConditionBuilderImpl implements ConditionBuilder {

		List<SqlExpression> expressions = new java.util.ArrayList<>();

		@Override
		public ConditionBuilder startClause() {
			expressions.add(sql("("));
			return this;
		}

		@Override
		public ConditionBuilder endClause() {
			expressions.add(sql(")"));
			return this;
		}

		@Override
		public ConditionBuilder add(SqlExpression expr) {
			expressions.add(expr);
			return this;
		}

		public SqlExpression toSql() {
			return SqlExpression.implode("", expressions);
		}
	}

	interface OrderByTarget {
		void orderBy(String fieldExpression, boolean ascending);
	}

	public static class OrderByField<Q> {
		private final String tableAlias;
		private final String columnName;
		private final OrderByTarget target;
		private final Q query;

		public OrderByField(OrderByTarget target, Q query, String tableAlias, String columnName) {
			this.target = target;
			this.query = query;
			this.tableAlias = tableAlias;
			this.columnName = columnName;
		}

		public Q asc() {
			target.orderBy("{" + tableAlias + "." + columnName + "}", true);
			return query;
		}

		public Q desc() {
			target.orderBy("{" + tableAlias + "." + columnName + "}", false);
			return query;
		}
	}


	static class ConditionBuilderField<T, C> {
		protected final ChainFactory<C> chainFactory;

		protected final String tableAlias;
		protected final String columnName;

		public ConditionBuilderField(ChainFactory<C> chainFactory, String tableAlias, String columnName) {
			this.chainFactory = chainFactory;
			this.tableAlias = tableAlias;
			this.columnName = columnName;
		}

		public C eq(T other) {
			var op = chainFactory.createChain();
			op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} = ?", other));
			return op.getContinuation();
		}

		public C ne(T other) {
			var op = chainFactory.createChain();
			op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} <> ?", other));
			return op.getContinuation();
		}

		public C isNull() {
			var op = chainFactory.createChain();
			op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} IS NULL"));
			return op.getContinuation();
		}

		public C isNotNull() {
			var op = chainFactory.createChain();
			op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} IS NOT NULL"));
			return op.getContinuation();
		}
	}

	static class ComparableConditionBuilderField<T, C> extends ConditionBuilderField<T, C> {
		public ComparableConditionBuilderField(ChainFactory<C> chainFactory, String tableAlias, String columnName) {
			super(chainFactory, tableAlias, columnName);
		}

		public C gt(T other) {
			var op = chainFactory.createChain();
			op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} > ?", other));
			return op.getContinuation();
		}

		public C lt(T other) {
			var op = chainFactory.createChain();
			op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} < ?", other));
			return op.getContinuation();
		}

		public C ge(T other) {
			var op = chainFactory.createChain();
			op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} >= ?", other));
			return op.getContinuation();
		}

		public C le(T other) {
			var op = chainFactory.createChain();
			op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} <= ?", other));
			return op.getContinuation();
		}
	}

	interface Condition {
		SqlExpression toSqlExpression();
	}
	
	// F = fields type, T = self type (for CRTP)
	static class StaticConditionTerminator<F extends TerminatorFactoryProvider<F, T>, T extends StaticConditionTerminator<F, T>> implements Condition {
		private final ChainedExpression<F, T> chain;

		public StaticConditionTerminator(ChainedExpression<F, T> chain) {
			this.chain = chain;
		}

		public F and() {
			chain.add(sql(" AND "));
			// Set the chain in threadlocal so the next field operation continues this chain
			chainThreadLocal.set(chain);
			// Also store on the fields instance to survive threadlocal clearing by other chains
			F fields = chain.getFieldsType();
			fields.setActiveChain(chain);
			return fields;
		}

		public F or() {
			chain.add(sql(" OR "));
			// Set the chain in threadlocal so the next field operation continues this chain
			chainThreadLocal.set(chain);
			// Also store on the fields instance to survive threadlocal clearing by other chains
			F fields = chain.getFieldsType();
			fields.setActiveChain(chain);
			return fields;
		}

		public T and(Condition condition) {
			return and(condition.toSqlExpression());
		}

		public T or(Condition condition) {
			return or(condition.toSqlExpression());
		}

		@SuppressWarnings("unchecked")
		public T and(SqlExpression expr) {
			chain.add(sql(" AND ")).startClause().add(expr).endClause();
			return (T) this;
		}

		@SuppressWarnings("unchecked")
		public T or(SqlExpression expr) {
			chain.add(sql(" OR ")).startClause().add(expr).endClause();
			return (T) this;
		}

		public SqlExpression toSqlExpression() {
			return chain.toSql();
		}
	}

	interface ConditionBuilder {
		ConditionBuilder startClause();

		ConditionBuilder endClause();

		ConditionBuilder add(SqlExpression expr);
	}



	// THE GENERATED CODE IS SIMULATED BELOW: FOLLOW THIS AS CLOSE AS POSSIBLE

	// File: BookQuery.java

	public static class BookQuery {
		private List<SqlExpression> collectedConditions = new java.util.ArrayList<>();

		// For static condition chaining
		public final ComparableConditionBuilderField<Long, BookQueryStaticConditionChain> id = new ComparableConditionBuilderField<Long, BookQueryStaticConditionChain>(
				() -> new BookQueryStaticConditionChain(), "b", "id");
		public final ComparableConditionBuilderField<String, BookQueryStaticConditionChain> title = new ComparableConditionBuilderField<String, BookQueryStaticConditionChain>(
				() -> new BookQueryStaticConditionChain(), "b", "title");

		class BookQueryStaticConditionChain
				implements ConditionChain<BookQueryStaticConditionChain>, Supplier<SqlExpression> {
			class BookQueryStaticConditionFields {
				public final ComparableConditionBuilderField<Long, BookQueryStaticConditionChain> id = new ComparableConditionBuilderField<Long, BookQueryStaticConditionChain>(
						() -> BookQueryStaticConditionChain.this, "b", "id");
				public final ComparableConditionBuilderField<String, BookQueryStaticConditionChain> title = new ComparableConditionBuilderField<String, BookQueryStaticConditionChain>(
						() -> BookQueryStaticConditionChain.this, "b", "title");
			}

			ConditionBuilder builder = new ConditionBuilderImpl();

			@Override
			public ConditionBuilder getBuilder() {
				return builder;
			}

			public BookQueryStaticConditionFields and() {
				builder.add(sql(" AND "));
				return new BookQueryStaticConditionFields();
			}

			public BookQueryStaticConditionFields or() {
				builder.add(sql(" OR "));
				return new BookQueryStaticConditionFields();
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
				return SqlExpression.implode("", ((ConditionBuilderImpl) builder).expressions);
			}
		}

		protected SqlQuery<?> query = new DefaultSqlQuery(DbContext.getDefault());

		protected void initializeQuery() {
			query.setTable(null, "book");
			query.addField(SqlExpression.sql("{book.id}"), "book.id");
			query.addField(SqlExpression.sql("{book.title}"), "book.title");
		}

		public BookQuery() {
			initializeQuery();
		}

		public String toSql() {
			// add expressions from collectionConditions to a local copy of query.toStatement()
			// and return the SQL string
			SqlExpression statement = query.toStatement();
			if (collectedConditions.size() > 0) {
				SqlExpression whereExpr = SqlExpression.implode("", collectedConditions);
				statement = SqlExpression.implode(" ", List.of(statement, sql("WHERE"), whereExpr));
			}
			return query.toStatement().getSql();
		}

		public BookQueryWhereBuilder where() {
			if (collectedConditions.size() == 0) {
				collectedConditions.add(sql(" WHERE "));
			}
			return new BookQueryWhereBuilder(this);
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

		public List<Book> list(Connection connection) {
			if (collectedConditions.size() > 0) {
				SqlExpression whereExpr = SqlExpression.implode("", collectedConditions);
				collectedConditions.clear();
				query.addWhere(whereExpr);
			}

			return null;
		}

		public class BookQueryGroupByBuilder {

			public final BookQueryGroupByField id;
			public final BookQueryGroupByField title;

			public BookQueryGroupByBuilder() {
				this.id = new BookQueryGroupByField("b", "id");
				this.title = new BookQueryGroupByField("b", "title");
			}
		}

		public class BookQueryOrderByBuilder implements OrderByTarget {
			public final OrderByField<BookQuery> id;
			public final OrderByField<BookQuery> title;

			public BookQueryOrderByBuilder() {
				this.id = new OrderByField<BookQuery>(this, BookQuery.this, "b", "id");
				this.title = new OrderByField<BookQuery>(this, BookQuery.this, "b", "title");
			}

			@Override
			public void orderBy(String fieldExpression, boolean ascending) {
				query.addOrderBy(fieldExpression + (ascending ? " ASC" : " DESC"));
			}
		}

		/**
		 * if a field should mirror query methods like list() and groupBy()
		 * this is needed to enable groupBy().firstName.list() syntax
		 */
		private class BookQueryDelegate {
			protected void callback() {
			}

			public List<Book> list(Connection connection) {
				callback();
				return BookQuery.this.list(connection);
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
			public void callback() {
				query.addGroupBy("{" + tableAlias + "." + columnName + "}");
			}
		}

		// This is for where clause building. Make sure it is generated as an inner class
		public class BookQueryWhereBuilder implements ConditionChain<BookQueryWhereBuilder.BookQueryWhereConditionTerminator> {
			public final ComparableConditionBuilderField<Long, BookQueryWhereBuilder.BookQueryWhereConditionTerminator> id = new ComparableConditionBuilderField<Long, BookQueryWhereBuilder.BookQueryWhereConditionTerminator>(
					() -> this, "b", "id");
			public final ComparableConditionBuilderField<String, BookQueryWhereBuilder.BookQueryWhereConditionTerminator> title = new ComparableConditionBuilderField<String, BookQueryWhereBuilder.BookQueryWhereConditionTerminator>(
					() -> this, "b", "title");

			ConditionBuilder builder = new BuilderImpl();

			protected BookQueryWhereBuilder(BookQuery query) {
			}

			public void accept(SqlExpression expr) {
				collectedConditions.add(expr);
			}

			public class BuilderImpl implements ConditionBuilder {
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

			public class BookQueryWhereConditionTerminator extends BookQueryDelegate {

				public BookQueryWhereBuilder and() {
					builder.add(sql(" AND "));
					return BookQueryWhereBuilder.this;
				}

				public BookQueryWhereBuilder or() {
					builder.add(sql(" OR "));
					return BookQueryWhereBuilder.this;
				}

				public BookQueryWhereConditionTerminator and(Supplier<SqlExpression> expr) {
					builder.add(sql(" AND ")).startClause().add(expr.get()).endClause();
					return this;
				}

				public BookQueryWhereConditionTerminator or(Supplier<SqlExpression> expr) {
					builder.add(sql(" OR ")).startClause().add(expr.get()).endClause();
					return this;
				}

			}

			@Override
			public BookQueryWhereBuilder.BookQueryWhereConditionTerminator getContinuation() {
				return new BookQueryWhereConditionTerminator();
			}

			@Override
			public ConditionBuilder getBuilder() {
				return builder;
			}

		}

	}

	// END OF SIMULATED GENERATED CODE

	/** 
	 * The C type is the continuation type.
	 * The chainfield itself contains all applicable operations for that field,
	 * like eq(), ne(), isNull(), isNotNull(), gt(), lt(), etc.
	 * The chainfield methods return the C type, which contains methods like and(), or(), etc.
	 * Or chain terminators which are usually methods delegated to the main query object, like
	 * groupBy(), orderBy(), execute(), etc.
	 */
	static ThreadLocal<ChainedExpression<?, ?>> chainThreadLocal = new ThreadLocal<>();

	static class StaticChainedExpression<F, T> implements ChainedExpression<F, T> {
		ConditionBuilder builder = new ConditionBuilderImpl();
		private final Object chainId = new Object(); // unique identity for this chain

		final F fieldsImpl;
		final TerminatorFactory<F, T> terminatorFactory;

		public StaticChainedExpression(F fieldsImpl, TerminatorFactory<F, T> terminatorFactory) {
			this.fieldsImpl = fieldsImpl;
			this.terminatorFactory = terminatorFactory;
		}

		@Override
		public T getTerminator() {
			return terminatorFactory.createTerminator(this);
		}

		@Override
		public F getFieldsType() {
			return fieldsImpl;
		}

		@Override
		public ConditionBuilder add(SqlExpression expr) {
			builder.add(expr);
			return builder;
		}

		@Override
		public SqlExpression toSql() {
			return ((ConditionBuilderImpl)builder).toSql();
		}
		
		@Override
		public Object getChainId() {
			return chainId;
		}
	}

	@SuppressWarnings("unchecked")
	static class StaticChainField<V, F extends TerminatorFactoryProvider<F, T>, T> {
		private final String fieldName;
		private final F fields;

		public StaticChainField(F fields, String fieldName) {
			this.fieldName = fieldName;
			this.fields = fields;
		}
		
		private ChainedExpression<F, T> getOrCreateChain() {
			ChainedExpression<F, T> chain = (ChainedExpression<F, T>)chainThreadLocal.get();
			
			// Check if existing threadlocal chain belongs to a different fields instance
			if (chain != null && chain.getFieldsType() != fields) {
				chain = null; // Don't use another instance's chain
			}
			
			// If no valid threadlocal chain, check if fields has an active chain
			if (chain == null) {
				chain = fields.getActiveChain();
			}
			
			// if still no chain, create a new one
			if (chain == null) {
				chain = new StaticChainedExpression<>(fields, fields.getTerminatorFactory());
			}
			return chain;
		}
		
		// Here, the continuation type returned is a terminator type
		// that has and(), or(), etc. methods
		public T eq(V value) {
			ChainedExpression<F, T> chain = getOrCreateChain();
			chain.add(sql(fieldName + " = ?", value));
			// Clear both threadlocal and fields' active chain
			chainThreadLocal.set(null);
			fields.setActiveChain(null);
			return chain.getTerminator();
		}
		
		public T ne(V value) {
			ChainedExpression<F, T> chain = getOrCreateChain();
			chain.add(sql(fieldName + " <> ?", value));
			// Clear both threadlocal and fields' active chain
			chainThreadLocal.set(null);
			fields.setActiveChain(null);
			return chain.getTerminator();
		}
	}
	
	// Interface for types that can provide a terminator factory
	interface TerminatorFactoryProvider<F, T> {
		TerminatorFactory<F, T> getTerminatorFactory();
		
		// Store the active chain on this fields instance to survive threadlocal clearing
		ChainedExpression<F, T> getActiveChain();
		void setActiveChain(ChainedExpression<F, T> chain);
	}
	// // Book__ is no longer needed - BookFields creates its own properly typed fields
	// // Keeping a simplified version for static access if needed
	// static class Book__ {
	// 	// For static access, we use StandaloneBookFields
	// 	static final StandaloneBookFields INSTANCE = new StandaloneBookFields();
		
	// 	// Access fields through the instance
	// 	static StaticChainField<Long, BookFields<BookConditionTerminator>, BookConditionTerminator> id() {
	// 		return INSTANCE.id;
	// 	}
	// 	static StaticChainField<String, BookFields<BookConditionTerminator>, BookConditionTerminator> title() {
	// 		return INSTANCE.title;
	// 	}
	// }

	// CRTP: T must extend StaticConditionTerminator<BookFields<T>, T>
	// This creates a "closed loop" where the types reference each other correctly
	abstract static class BookFields<T extends StaticConditionTerminator<BookFields<T>, T>> 
			implements TerminatorFactoryProvider<BookFields<T>, T> {
		// Each instance creates its own fields that reference 'this' (which is properly typed)
		final StaticChainField<Long, BookFields<T>, T> id;
		final StaticChainField<String, BookFields<T>, T> title;
		final AuthorFields author;
		
		// Store active chain to survive threadlocal clearing by other chains
		private ChainedExpression<BookFields<T>, T> activeChain;
		
		BookFields() {
			this.id = new StaticChainField<>(this, "id");
			this.title = new StaticChainField<>(this, "title");
			this.author = new AuthorFields();
		}
		
		@Override
		public ChainedExpression<BookFields<T>, T> getActiveChain() {
			return activeChain;
		}
		
		@Override
		public void setActiveChain(ChainedExpression<BookFields<T>, T> chain) {
			this.activeChain = chain;
		}
		
		class AuthorFields {
			final StaticChainField<Long, BookFields<T>, T> id = new StaticChainField<>(BookFields.this, "author.id");
			final StaticChainField<String, BookFields<T>, T> firstName = new StaticChainField<>(BookFields.this, "author.firstName");
			final StaticChainField<String, BookFields<T>, T> lastName = new StaticChainField<>(BookFields.this, "author.lastName");
		}
	}
	
	// Concrete terminator that "closes the loop" - this is the fixed point
	static class BookConditionTerminator extends StaticConditionTerminator<BookFields<BookConditionTerminator>, BookConditionTerminator> {
		public BookConditionTerminator(ChainedExpression<BookFields<BookConditionTerminator>, BookConditionTerminator> chain) {
			super(chain);
		}
	}
	
	// Concrete BookFields for standalone condition building
	static class StandaloneBookFields extends BookFields<BookConditionTerminator> {
		@Override
		public TerminatorFactory<BookFields<BookConditionTerminator>, BookConditionTerminator> getTerminatorFactory() {
			return BookConditionTerminator::new;
		}
	}

	static class ThreadedBookQuery {
		public BookWhereBuilder where() {
			return new BookWhereBuilder();
		}
	}

	// Factory interface for creating terminators
	interface TerminatorFactory<F, T> {
		T createTerminator(ChainedExpression<F, T> chain);
	}

	interface ChainedExpression<F, T> {
		ConditionBuilder add(SqlExpression expr);
		F getFieldsType();  // returns the fields type, which has eq(), ne(), etc.
		T getTerminator();   // returns the terminator type, which has and(), or(), etc.
		SqlExpression toSql();
		Object getChainId(); // unique identifier for this chain to prevent cross-contamination
	}

	// BookWhereBuilder now properly extends BookFields with its own terminator
	static class BookWhereBuilder extends BookFields<BookWhereTerminator> {
		BookWhereBuilder() {
			super();
		}
		
		@Override
		public TerminatorFactory<BookFields<BookWhereTerminator>, BookWhereTerminator> getTerminatorFactory() {
			return BookWhereTerminator::new;
		}
	}
	
	// The terminator for BookWhereBuilder - extends with both F and T parameters
	static class BookWhereTerminator extends StaticConditionTerminator<BookFields<BookWhereTerminator>, BookWhereTerminator> {
		public BookWhereTerminator(ChainedExpression<BookFields<BookWhereTerminator>, BookWhereTerminator> chain) {
			super(chain);
		}

		public List<Book> list() {
			return List.of();
		}
	}

	@Test
	public void testCastingOfStaticConditionChains() {
		BookWhereBuilder q = new BookWhereBuilder();
		// Now the chain is fully typed!
		// BookWhereTerminator c = q.id.eq(42L).and().title.eq("My Title").or().id.ne(123L).and().author.firstName.eq("John");

		Assert.assertEquals("title = ?", q.title.eq("My Title").toSqlExpression().getSql());
		Assert.assertEquals("id = ? OR title = ?", q.id.eq(12L).or().title.eq("My Title").toSqlExpression().getSql());
		
		{
			// After .and(Condition), need to call .and() to get back to fields
			Condition c = q.title.eq("henk").and(q.id.eq(12L).or().title.eq("My Title")).and().id.ne(123L).and().title.eq("piet").and().author.firstName.eq("John");
			
			System.out.println("SQL: " + c.toSqlExpression().getSql());
		}
		{
			Condition c = q.title.eq("henk").and(q.title.eq("piet").and(q.id.eq(42L).or().id.eq(123L)).and().title.eq("My Title"));
			Assert.assertEquals("title = ? AND (title = ? AND (id = ? OR id = ?) AND title = ?)", c.toSqlExpression().getSql());
		}
		// Static access returns raw terminator - use BOOKFIELDS_INSTANCE for typed chaining
	}
	
	@Test
	public void testCrossContaminationPrevention() {
		// This test verifies that using two different BookWhereBuilder instances
		// doesn't cause cross-contamination via the ThreadLocal
		BookWhereBuilder q1 = new BookWhereBuilder();
		BookWhereBuilder q2 = new BookWhereBuilder();
		
		// Start a chain on q1
		var fields1 = q1.id.eq(1L).and();
		
		// Now use q2 - this should NOT contaminate q1's chain
		Condition c2 = q2.title.eq("q2 title");
		Assert.assertEquals("title = ?", c2.toSqlExpression().getSql());
		
		// Continue q1's chain - should only have q1's conditions
		Condition c1 = fields1.title.eq("q1 title");
		Assert.assertEquals("id = ? AND title = ?", c1.toSqlExpression().getSql());
	}

	@Test
	public void testFluentApi() {
		// {
		// 	// new BookQuery().where().title.isNotNull().groupBy().title.list(null);
		// 	BookQuery q = new BookQuery();
		// 	BookQueryStaticConditionChain condition = q.title.eq("John").and().title.isNotNull()
		// 	.and(q.title.eq("piet").or().title.eq("henk").and(q.id.gt(123L)));
		// 	q.where().id.eq(1L).and().title.eq("piet").and(condition).list(null);
		// 	System.out.println(q.toSql());
		// }
		{
			BookQuery q = new BookQuery();
			q.where().title.eq("My Book Title");
			
			System.out.println(q.toSql());
			q.list(null);

			// Static access now uses method syntax
			// Book__.id().eq(123L);
			
			System.out.println(q.toSql());
		}
		// System.out.println(q.where().id.eq(123L).and().title.eq("My Book
		// Title").and(q.title.eq("henk")).orderBy().title.asc().query.toStatement().getSql());
	}
}
