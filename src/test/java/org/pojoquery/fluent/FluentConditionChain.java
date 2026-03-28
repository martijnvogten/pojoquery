package org.pojoquery.fluent;

import java.sql.Connection;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;
import org.pojoquery.SqlExpression;
import org.pojoquery.fluent.FluentConditionChain.BookQuery.BookQueryStaticConditionTerminator;

public class FluentConditionChain {

	static class Book {
		public String title;
	}

	class ConditionChainOperators<S, T> {
		private String fieldName;
		private Supplier<T> terminatorSupplier;
	
		public ConditionChainOperators(String fieldName, Supplier<T> terminatorSupplier) {
			this.fieldName = fieldName;
			this.terminatorSupplier = terminatorSupplier;
		}

		public T eq(String value) {
			return terminatorSupplier.get();
		}
	}

	abstract class FluentQuery<R> {
		public List<R> list(Connection c) {
			return List.of();
		}
	}

	class BookQuery extends FluentQuery<Book> {
		public BookQueryConditionChainOperators<BookQueryStaticConditionTerminator> title = new BookQueryConditionChainOperators<BookQueryStaticConditionTerminator>("title", BookQueryStaticConditionTerminator::new);

		class BookQueryConditionChainOperators<T> extends ConditionChainOperators<BookQueryConditionChainStarter<T>, T> {
			public BookQueryConditionChainOperators(String fieldName, Supplier<T> terminatorSupplier) {
				super(fieldName, terminatorSupplier);
			}
		}

		class BookQueryStaticConditionTerminator extends StaticConditionChainTerminator<BookQueryConditionChainStarter<BookQueryStaticConditionTerminator>> {
			public BookQueryStaticConditionTerminator() {
				setStarterSupplier(() -> new BookQueryConditionChainStarter<BookQueryStaticConditionTerminator>(() -> this));
			}
		}

		class BookQueryConditionTerminator extends ConditionChainTerminator<Book, BookQueryConditionChainStarter<BookQueryConditionTerminator>, BookQueryConditionTerminator> {
			public BookQueryConditionTerminator() {
				super(BookQuery.this);
				setStarterSupplier(() -> new BookQueryConditionChainStarter<BookQueryConditionTerminator>(() -> this));
			}
		}

		class BookQueryConditionChainStarter<T> {
			public BookQueryConditionChainOperators<T> title;
			
			Supplier<T> terminatorSupplier;
			public BookQueryConditionChainStarter(Supplier<T> terminatorSupplier) {
				this.terminatorSupplier = terminatorSupplier;
				this.title = new BookQueryConditionChainOperators<T>("title", terminatorSupplier);
			}
		}

		public BookQueryConditionChainStarter<BookQueryConditionTerminator> where() {
			return new BookQueryConditionChainStarter<>(BookQueryConditionTerminator::new);
		}
	}

	interface QueryTerminator<R, T extends QueryTerminator<R, T>> {
		public List<R> list(Connection c);
		public T addOrderBy(SqlExpression orderBy);
		public T addGroupBy(SqlExpression groupBy);
		public T setLimit(int limit);
	}

	class StaticConditionChainTerminator<S> {
		private Supplier<S> starterSupplier;

		protected StaticConditionChainTerminator() {
		}

		protected void setStarterSupplier(Supplier<S> starterSupplier) {
			this.starterSupplier = starterSupplier;
		}

		public S and() {
			return starterSupplier.get();
		}
		
		public S or() {
			return starterSupplier.get();
		}

	}

	class ConditionChainTerminator<R,S, T extends ConditionChainTerminator<R,S,T>> implements QueryTerminator<R, T> {
		private Supplier<S> starterSupplier;
		private FluentQuery<R> query;

		protected ConditionChainTerminator(FluentQuery<R> query) {
			this.query = query;
		}

		protected void setStarterSupplier(Supplier<S> starterSupplier) {
			this.starterSupplier = starterSupplier;
		}

		public S and() {
			return starterSupplier.get();
		}
		
		public S or() {
			return starterSupplier.get();
		}

		public List<R> list(Connection c) {
			return query.list(c);
		}

		@Override
		public T addOrderBy(SqlExpression orderBy) {
			return (T) this;
		}

		@Override
		public T addGroupBy(SqlExpression groupBy) {
			return (T) this;
		}

		@Override
		public T setLimit(int limit) {
			return (T) this;
		}
	}

	@Test
	public void test() {
		BookQuery q = new BookQuery();
		 BookQueryStaticConditionTerminator cond = q.title.eq("henk").and().title.eq("piet");
		//  BookQueryStaticConditionTerminator cond = q.title().eq("henk").and().title().eq("piet").or().;

		new BookQuery().where().title.eq("The Hobbit").and().title.eq("The Lord of the Rings").list(null);
	}
}
