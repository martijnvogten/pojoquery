package org.pojoquery.typedquery;

import static org.pojoquery.SqlExpression.sql;

import java.sql.Connection;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;
import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.typedquery.FluentExperiments.BookQuery.BookQueryStaticConditionChain;

public class FluentExperiments {

	static class Book {
		@Id
		public Long id;
		public String title;
	}

	static class OrderByBuilder<Q> {
	}

	interface OrderByTarget {
		void orderBy(String fieldExpression, boolean ascending);
	}

	static class OrderByField<Q> {
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
	}

	static class BookQuery {
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
			query.addField(SqlExpression.sql("{book.id}"), "book.id");
			query.addField(SqlExpression.sql("{book.title}"), "book.title");
		}

		public BookQuery() {
			initializeQuery();
		}

		public BookQueryWhereBuilder where() {
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
		 * this is neede to enable groupBy().firstName.list() syntax
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

		class BookQueryWhereBuilder implements ConditionChain<BookQueryWhereBuilder.BookQueryWhereConditionTerminator> {
			public final ComparableConditionBuilderField<Long, BookQueryWhereBuilder.BookQueryWhereConditionTerminator> id = new ComparableConditionBuilderField<Long, BookQueryWhereBuilder.BookQueryWhereConditionTerminator>(
					() -> this, "b", "id");
			public final ComparableConditionBuilderField<String, BookQueryWhereBuilder.BookQueryWhereConditionTerminator> title = new ComparableConditionBuilderField<String, BookQueryWhereBuilder.BookQueryWhereConditionTerminator>(
					() -> this, "b", "title");

			List<SqlExpression> collectedConditions = new java.util.ArrayList<>();
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

	static class StaticConditionTerminator<C, E> {
		private final C continuation;
		private ConditionBuilder builder;
		private final E termination;

		public StaticConditionTerminator(ConditionBuilder builder, C chainedContinuation, E termination) {
			this.continuation = chainedContinuation;
			this.builder = builder;
			this.termination = termination;
		}

		public C and() {
			builder = builder.add(sql(" AND "));
			return continuation;
		}

		public C or() {
			builder = builder.add(sql(" OR "));
			return continuation;
		}

		public E and(SqlExpression expr) {
			builder = builder.add(sql(" AND ")).startClause().add(expr).endClause();
			return termination;
		}

		public E or(SqlExpression expr) {
			builder = builder.add(sql(" OR ")).startClause().add(expr).endClause();
			return termination;
		}
	}

	interface ConditionBuilder {
		ConditionBuilder startClause();

		ConditionBuilder endClause();

		ConditionBuilder add(SqlExpression expr);
	}

	@Test
	public void testFluentApi() {
		// new BookQuery().where().title.isNotNull().groupBy().title.list(null);
		BookQuery q = new BookQuery();
		BookQueryStaticConditionChain condition = q.title.eq("John").and().title.isNotNull()
				.and(q.title.eq("piet").or().title.eq("henk").and(q.id.gt(123L)));
		System.out.println(condition.get().getSql());
		// System.out.println(q.where().id.eq(123L).and().title.eq("My Book
		// Title").and(q.title.eq("henk")).orderBy().title.asc().query.toStatement().getSql());
	}
}
