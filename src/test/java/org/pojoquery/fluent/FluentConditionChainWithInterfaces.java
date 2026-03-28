package org.pojoquery.fluent;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.DefaultSqlQuery;

public class FluentConditionChainWithInterfaces {

	@Table("book")
	static class Book {
		@Id
		Long id;
		public String title;
	}

	/**
	 * S: conditionStarter (not an interface, contains the fields)
	 */
	interface Terminator<S> {
		public S and();

		public S or();

		public SqlExpression toSql();
	}

	/**
	 * R: result type (Book)
	 * T: terminator interface (this interface)
	 */
	interface QueryTerminator<R> {
		public List<R> list(Connection c);

		public QueryTerminator<R> addOrderBy(String orderBy);

		public QueryTerminator<R> addGroupBy(String groupBy);

		public QueryTerminator<R> setLimit(int limit);
	}

	interface ConditionTerminator<R, S, T extends ConditionChainTerminator<R, S, T>>
			extends QueryTerminator<R>, Terminator<S> {
	}

	interface Operators<T> {
		public T eq(Object value);
	}

	abstract static class ConditionChainOperators<T> implements Operators<T> {
		private final String tableAlias;
		private final String fieldName;
		private final T terminator;

		public ConditionChainOperators(String tableAlias, String fieldName, T terminator) {
			this.tableAlias = tableAlias;
			this.fieldName = fieldName;
			this.terminator = terminator;
		}

		public T eq(Object value) {
			appendExpression("{" + tableAlias + "." + fieldName + "} = ?", value);
			return terminator;
		}

		protected abstract void appendExpression(String sql, Object... parameters);
	}

	abstract static class FluentQuery<R> {
		DefaultSqlQuery query = new DefaultSqlQuery(DbContext.getDefault());
		List<SqlExpression> staticConditionSql = new ArrayList<>();
		List<SqlExpression> whereConditionSql = new ArrayList<>();

		FluentQuery(Class<R> type) {
			RootNode aqt = AQTTransformer.buildQueryTreeForType(type);
			AQTTransformer.toSql(aqt, query);
		}

		protected void appendExpression(String sql, Object... parameters) {
			whereConditionSql.add(SqlExpression.sql(sql, parameters));
		}
		
		protected void appendStaticExpression(String sql, Object... parameters) {
			staticConditionSql.add(SqlExpression.sql(sql, parameters));
		}

		public void addOrderBy(String orderBy) {
			query.addOrderBy(orderBy);
		}

		public void addGroupBy(String groupBy) {
			query.addGroupBy(groupBy);
		}

		public void setLimit(int limit) {
			query.setLimit(limit);
		}

		public List<R> list(Connection c) {
			return List.of();
		}

		public SqlExpression getStaticConditionSql() {
			SqlExpression result = SqlExpression.implode(" ", staticConditionSql);
			staticConditionSql.clear();
			return result;
		}

		public SqlExpression getSql() {
			query.addWhere(SqlExpression.implode(" ", whereConditionSql));
			whereConditionSql.clear();
			return query.toStatement();
		}
	}

	class BookQuery extends FluentQuery<Book> {

		private final ConditionTerminator<Book, BookQueryConditionStarter, BookQueryConditionTerminator> conditionTerminator;
		private final Terminator<BookQuery> staticTerminator;
		private final BookQueryConditionStarter starter;
		
		{
			conditionTerminator = new BookQueryConditionTerminator();
			starter = new BookQueryConditionStarter();
			staticTerminator = new StaticConditionChainTerminator<BookQuery>(BookQuery.this, BookQuery.this);
			((BookQueryConditionTerminator)conditionTerminator).setStarter(starter);
		}
		
		public StaticBookQueryConditionOperators id = new StaticBookQueryConditionOperators("book", "id");
		public StaticBookQueryConditionOperators title = new StaticBookQueryConditionOperators("book", "title");

		public class BookQueryConditionStarter {
			public BookQueryConditionOperators id = new BookQueryConditionOperators("book", "id");
			public BookQueryConditionOperators title = new BookQueryConditionOperators("book", "title");
		}

		BookQuery() {
			super(Book.class);
		}

		class StaticBookQueryConditionOperators extends ConditionChainOperators<Terminator<BookQuery>> {
			public StaticBookQueryConditionOperators(String tableAlias, String fieldName) {
				super(tableAlias, fieldName, staticTerminator);
			}

			@Override
			protected void appendExpression(String sql, Object... parameters) {
				BookQuery.this.appendStaticExpression(sql, parameters);
			}
		}

		class BookQueryConditionOperators extends
				ConditionChainOperators<ConditionTerminator<Book, BookQueryConditionStarter, BookQueryConditionTerminator>> {
			public BookQueryConditionOperators(String tableAlias, String fieldName) {
				super(tableAlias, fieldName, conditionTerminator);
			}

			@Override
			protected void appendExpression(String sql, Object... parameters) {
				BookQuery.this.appendExpression(sql, parameters);
			}
		}

		class BookQueryConditionTerminator
				extends ConditionChainTerminator<Book, BookQueryConditionStarter, BookQueryConditionTerminator> {
			public BookQueryConditionTerminator() {
				super(BookQuery.this);
			}
		}

		BookQueryConditionStarter where() {
			return starter;
		}
	}

	static class StaticConditionChainTerminator<S> implements Terminator<S> {
		private final S starter;
		private final FluentQuery<?> query;

		public StaticConditionChainTerminator(FluentQuery<?> query, S starter) {
			this.query = query;
			this.starter = starter;
		}

		public S and() {
			query.appendStaticExpression(" AND ");
			return starter;
		}

		public S or() {
			query.appendStaticExpression(" OR ");
			return starter;
		}

		@Override
		public SqlExpression toSql() {
			return query.getStaticConditionSql();
		}

	}

	class ConditionChainTerminator<R, S, T extends ConditionChainTerminator<R, S, T>>
			implements ConditionTerminator<R, S, T> {
		private S starter;
		private FluentQuery<R> query;

		protected ConditionChainTerminator(FluentQuery<R> query) {
			this.query = query;
		}

		public void setStarter(S starter) {
			this.starter = starter;
		}

		public S and() {
			query.appendExpression(" AND ");
			return starter;
		}
		
		public S or() {
			query.appendExpression(" OR ");
			return starter;
		}

		public List<R> list(Connection c) {
			return query.list(c);
		}

		@Override
		public QueryTerminator<R> addOrderBy(String orderBy) {
			query.addOrderBy(orderBy);
			return (QueryTerminator<R>) this;
		}

		@Override
		public QueryTerminator<R> addGroupBy(String groupBy) {
			query.addGroupBy(groupBy);
			return (QueryTerminator<R>) this;
		}

		@Override
		public QueryTerminator<R> setLimit(int limit) {
			query.setLimit(limit);
			return (QueryTerminator<R>) this;
		}

		@Override
		public SqlExpression toSql() {
			return query.getSql();
		}
	}

	@Test
	public void test() {
		BookQuery q = new BookQuery();
		SqlExpression a = q.title.eq("The Hobbit").and().title.eq("The Lord of the Rings").toSql();
		System.out.println("SQL: " + a.getSql());
		System.out.println("PARAMS: " + a.getParameters());
		// q.where().title.eq("The Hobbit").or().id.eq(1L).addGroupBy("lkejrl").addOrderBy("lkerj").setLimit(10).list(null);
		System.out.println("SQL: " + new BookQuery().where().title.eq("The Hobbit").or().id.eq(1L).and().title.eq("The Lord of the Rings").toSql().getSql());
	}
}
