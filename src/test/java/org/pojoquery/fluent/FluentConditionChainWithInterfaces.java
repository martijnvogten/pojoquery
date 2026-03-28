package org.pojoquery.fluent;

import org.junit.Test;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.fluent.internal.ConditionChainOperators;
import org.pojoquery.fluent.internal.ConditionChainTerminator;
import org.pojoquery.fluent.internal.StaticConditionChainTerminator;

public class FluentConditionChainWithInterfaces {

	@Table("book")
	static class Book {
		@Id
		Long id;
		public String title;
	}

	public static class BookQuery extends FluentQuery<Book> {

		private final ConditionTerminator<Book, BookQueryConditionStarter, BookQueryConditionTerminator> conditionTerminator;
		private final Terminator<BookQuery> staticTerminator;
		private final BookQueryConditionStarter starter;
		
		{
			conditionTerminator = new BookQueryConditionTerminator();
			starter = new BookQueryConditionStarter();
			staticTerminator = new StaticBookQueryConditionTerminator();
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

		private class StaticBookQueryConditionOperators extends ConditionChainOperators<Terminator<BookQuery>> {
			public StaticBookQueryConditionOperators(String tableAlias, String fieldName) {
				super(tableAlias, fieldName, staticTerminator);
			}

			@Override
			protected void appendExpression(String sql, Object... parameters) {
				BookQuery.this.appendStaticExpression(sql, parameters);
			}
		}

		private class BookQueryConditionOperators extends
				ConditionChainOperators<ConditionTerminator<Book, BookQueryConditionStarter, BookQueryConditionTerminator>> {
			public BookQueryConditionOperators(String tableAlias, String fieldName) {
				super(tableAlias, fieldName, conditionTerminator);
			}

			@Override
			protected void appendExpression(String sql, Object... parameters) {
				BookQuery.this.appendExpression(sql, parameters);
			}
		}

		private class StaticBookQueryConditionTerminator extends StaticConditionChainTerminator<BookQuery> {
			public StaticBookQueryConditionTerminator() {
				super(BookQuery.this);
			}

			@Override
			public SqlExpression toSql() {
				return BookQuery.this.getStaticConditionSql();
			}

			@Override
			protected void appendStaticExpression(String sql, Object... parameters) {
				BookQuery.this.appendStaticExpression(sql, parameters);
			}
		}

		private class BookQueryConditionTerminator
				extends ConditionChainTerminator<Book, BookQueryConditionStarter, BookQueryConditionTerminator> {
			public BookQueryConditionTerminator() {
				super(BookQuery.this);
			}

			@Override
			protected void appendExpression(String sql, Object... parameters) {
				BookQuery.this.appendExpression(sql, parameters);
			}
		}

		BookQueryConditionStarter where() {
			return starter;
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
