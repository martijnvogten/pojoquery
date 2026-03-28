package org.pojoquery.fluent;

import java.sql.Connection;
import java.util.List;

import org.junit.Test;
import org.pojoquery.SqlExpression;

public class FluentConditionChainWithInterfaces {

	static class Book {
		public String title;
	}

	/**
	 * S: conditionStarter (not an interface, contains the fields)
	 */
	interface Terminator<S> {
		public S and();
		public S or();
	}

	/**
	 * R: result type (Book)
	 * T: terminator interface (this interface)
	 */
	interface QueryTerminator<R, S, T extends QueryTerminator<R, S, T>> {
		public List<R> list(Connection c);
		public QueryTerminator<R, S, T> addOrderBy(SqlExpression orderBy);
		public QueryTerminator<R, S, T> addGroupBy(SqlExpression groupBy);
		public QueryTerminator<R, S, T> setLimit(int limit);
	}

	interface ConditionTerminator<R,S, T extends ConditionChainTerminator<R,S,T>> extends QueryTerminator<R, S, T>, Terminator<S>  {
	}

	interface Operators<T> {
		public T eq(String value);
	}

	class ConditionChainOperators<T> implements Operators<T> {
		private String fieldName;
		private T terminator;
	
		public ConditionChainOperators(String fieldName, T terminator) {
			this.fieldName = fieldName;
			this.terminator = terminator;
		}

		public T eq(String value) {
			return terminator;
		}
	}

	abstract class FluentQuery<R> {
		public List<R> list(Connection c) {
			return List.of();
		}
	}

	class BookQuery extends FluentQuery<Book> {
		public StaticBookQueryConditionOperators title = new StaticBookQueryConditionOperators("title");
		
		private class BookQueryConditionStarter {
			BookQueryConditionOperators title = new BookQueryConditionOperators("title", conditionTerminator);
		}

		private BookQueryConditionStarter starter = new BookQueryConditionStarter();

		private final ConditionTerminator<Book, BookQueryConditionStarter, BookQueryConditionTerminator> conditionTerminator;
		private final Terminator<BookQuery> staticTerminator;

		BookQuery() {
			this.conditionTerminator = new BookQueryConditionTerminator();
			this.staticTerminator = new StaticConditionChainTerminator<BookQuery>(this);
		}

		class StaticBookQueryConditionOperators extends ConditionChainOperators<Terminator<BookQuery>> {
			public StaticBookQueryConditionOperators(String fieldName) {
				super(fieldName, staticTerminator);
			}
		}

		class BookQueryConditionOperators extends ConditionChainOperators<ConditionTerminator<Book, BookQueryConditionStarter, BookQueryConditionTerminator>> {
			public BookQueryConditionOperators(String fieldName, ConditionTerminator<Book, BookQueryConditionStarter, BookQueryConditionTerminator> terminator) {
				super(fieldName, terminator);
			}
		}

		class BookQueryConditionTerminator 
				extends ConditionChainTerminator<Book, BookQueryConditionStarter, BookQueryConditionTerminator> {
			public BookQueryConditionTerminator() {
				super(BookQuery.this, starter);
			}
		}

		BookQueryConditionStarter where() {
			return new BookQueryConditionStarter();
		}
	}

	static class StaticConditionChainTerminator<S> implements Terminator<S>{
		private final S starter;

		protected StaticConditionChainTerminator(S starter) {
			this.starter = starter;
		}

		public S and() {
			return starter;
		}
		
		public S or() {
			return starter;
		}

	}

	class ConditionChainTerminator<R,S, T extends ConditionChainTerminator<R,S,T>> implements ConditionTerminator<R, S, T> {
		private S starter;
		private FluentQuery<R> query;

		protected ConditionChainTerminator(FluentQuery<R> query, S starter) {
			this.query = query;
			this.starter = starter;
		}

		public S and() {
			return starter;
		}
		
		public S or() {
			return starter;
		}

		public List<R> list(Connection c) {
			return query.list(c);
		}

		@Override
		public QueryTerminator<R, S, T> addOrderBy(SqlExpression orderBy) {
			return (QueryTerminator<R, S, T>) this;
		}
		
		@Override
		public QueryTerminator<R, S, T> addGroupBy(SqlExpression groupBy) {
			return (QueryTerminator<R, S, T>) this;
		}
		
		@Override
		public QueryTerminator<R, S, T> setLimit(int limit) {
			return (QueryTerminator<R, S, T>) this;
		}
	}

	@Test
	public void test() {
		BookQuery q = new BookQuery();
		q.title.eq("henk").and().title.eq("piet");
		//  BookQueryStaticConditionTerminator cond = q.title.eq("henk").and().title.eq("piet");
		//  BookQueryStaticConditionTerminator cond = q.title().eq("henk").and().title().eq("piet").or().;
		Terminator<BookQuery> a = q.title.eq("The Hobbit").and().title.eq("The Lord of the Rings");
		q.where().title.eq("The Hobbit").addGroupBy(SqlExpression.sql("lkejrl"));
		new BookQuery().where().title.eq("The Hobbit").and().title.eq("The Lord of the Rings").list(null);
	}
}
