
package org.pojoquery.processor;

import org.pojoquery.fluent.ConditionTerminator;
import org.pojoquery.fluent.FluentQuery;
import org.pojoquery.fluent.Terminator;
import org.pojoquery.fluent.internal.ConditionChainOperators;
import org.pojoquery.processor.TestFluentAQTCodeGenerator.Book;

public class BookQuery extends FluentQuery<Book, BookQuery, BookQuery.Where> {

	public final ConditionChainOperators<Terminator<BookQuery>> id;
	public final ConditionChainOperators<Terminator<BookQuery>> title;
	public final StaticAuthor author;
	public final StaticReviews reviews;
	public final StaticCategories categories;

	public class StaticAuthor {
		public final ConditionChainOperators<Terminator<BookQuery>> id = staticOp("author", "id");
		public final ConditionChainOperators<Terminator<BookQuery>> name = staticOp("author", "name");
	}

	public class StaticReviews {
		public final ConditionChainOperators<Terminator<BookQuery>> id = staticOp("reviews", "id");
		public final ConditionChainOperators<Terminator<BookQuery>> content = staticOp("reviews", "content");
		public final ConditionChainOperators<Terminator<BookQuery>> rating = staticOp("reviews", "rating");
	}

	public class StaticCategories {
		public final ConditionChainOperators<Terminator<BookQuery>> id = staticOp("categories", "id");
		public final ConditionChainOperators<Terminator<BookQuery>> name = staticOp("categories", "name");
	}

	public class Where {
		public final ConditionChainOperators<ConditionTerminator<Book, Where, ?>> id = chainOp("book", "id");
		public final ConditionChainOperators<ConditionTerminator<Book, Where, ?>> title = chainOp("book", "title");
		public final WhereAuthor author = new WhereAuthor();
		public final WhereReviews reviews = new WhereReviews();
		public final WhereCategories categories = new WhereCategories();

		public class WhereAuthor {
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?>> id = chainOp("author", "id");
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?>> name = chainOp("author", "name");
		}

		public class WhereReviews {
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?>> id = chainOp("reviews", "id");
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?>> content = chainOp("reviews", "content");
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?>> rating = chainOp("reviews", "rating");
		}

		public class WhereCategories {
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?>> id = chainOp("categories", "id");
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?>> name = chainOp("categories", "name");
		}

	}

	public BookQuery() {
		super(Book.class, q -> ((BookQuery) q).new Where());
		this.id = staticOp("book", "id");
		this.title = staticOp("book", "title");
		this.author = new StaticAuthor();
		this.reviews = new StaticReviews();
		this.categories = new StaticCategories();
	}
}
