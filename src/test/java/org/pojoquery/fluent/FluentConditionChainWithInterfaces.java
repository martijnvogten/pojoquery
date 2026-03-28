package org.pojoquery.fluent;

import org.junit.Test;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.fluent.internal.ConditionChainOperators;

public class FluentConditionChainWithInterfaces {

	@Table("book")
	public static class Book {
		@Id
		public Long id;
		public String title;
		public Person author;
	}

	@Table("person")
	public static class Person {
		@Id
		public Long id;
		public String name;
	}

	public static class BookQuery extends FluentQuery<Book, BookQuery, BookQuery.Where> {

		public final ConditionChainOperators<Terminator<BookQuery>> id;
		public final ConditionChainOperators<Terminator<BookQuery>> title;
		public final StaticAuthor author;

		public class StaticAuthor {
			public final ConditionChainOperators<Terminator<BookQuery>> id = staticOp("author", "id");
			public final ConditionChainOperators<Terminator<BookQuery>> name = staticOp("author", "name");
		}

		public class Where {
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?>> id = chainOp("book", "id");
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?>> title = chainOp("book", "title");
			public final WhereAuthor author = new WhereAuthor();

			public class WhereAuthor {
				public final ConditionChainOperators<ConditionTerminator<Book, Where, ?>> id = chainOp("author", "id");
				public final ConditionChainOperators<ConditionTerminator<Book, Where, ?>> name = chainOp("author", "name");
			}
		}

		public BookQuery() {
			super(Book.class, q -> ((BookQuery)q).new Where());
			// Initialize static operators after super() completes
			this.id = staticOp("book", "id");
			this.title = staticOp("book", "title");
			this.author = new StaticAuthor();
		}
	}

	@Test
	public void test() {
		BookQuery q = new BookQuery();
		SqlExpression a = q.title.eq("The Hobbit").and().title.eq("The Lord of the Rings").toSql();
		System.out.println("SQL: " + a.getSql());
		System.out.println("PARAMS: " + a.getParameters());
		System.out.println("SQL: " + new BookQuery().where().title.eq("The Hobbit").or().id.eq(1L).and().title.eq("The Lord of the Rings").toSql().getSql());
		
		// Test nested author access
		SqlExpression authorQuery = new BookQuery().where().author.name.eq("Tolkien").toSql();
		System.out.println("AUTHOR SQL: " + authorQuery.getSql());
	}
}
