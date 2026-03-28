package org.pojoquery.fluent;

import org.pojoquery.fluent.FluentConditionChainWithInterfaces.Book;
import org.pojoquery.fluent.internal.ConditionChainOperators;

public class BookQuery extends FluentQuery<Book, BookQuery, BookQuery.Where> {

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
		super(Book.class, q -> ((BookQuery) q).new Where());
		// Initialize static operators after super() completes
		this.id = staticOp("book", "id");
		this.title = staticOp("book", "title");
		this.author = new StaticAuthor();
	}
}