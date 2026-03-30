package org.pojoquery.fluent;

import org.pojoquery.fluent.FluentConditionChainWithInterfaces.Book;
import org.pojoquery.fluent.internal.ConditionChainOperators;

public class BookQuery extends FluentQuery<Book, BookQuery, BookQuery.Where, BookQuery.OrderBy, BookQuery.GroupBy> {

	public final ConditionChainOperators<Terminator<BookQuery>> id;
	public final ConditionChainOperators<Terminator<BookQuery>> title;
	public final StaticAuthor author;

	public class StaticAuthor {
		public final ConditionChainOperators<Terminator<BookQuery>> id = staticOp("author", "id");
		public final ConditionChainOperators<Terminator<BookQuery>> name = staticOp("author", "name");
	}

	public class Where {
		public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>> id = chainOp("book", "id");
		public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>> title = chainOp("book", "title");
		public final WhereAuthor author = new WhereAuthor();

		public class WhereAuthor {
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>> id = chainOp("author", "id");
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>> name = chainOp("author", "name");
		}
	}

	public class OrderBy {
		public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> id = orderByOp("book", "id");
		public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> title = orderByOp("book", "title");
		public final OrderByAuthor author = new OrderByAuthor();

		public class OrderByAuthor {
			public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> id = orderByOp("author", "id");
			public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> name = orderByOp("author", "name");
		}
	}

	public class GroupBy {
		public final QueryTerminator<Book, OrderBy, GroupBy> id = groupByOp("book", "id");
		public final QueryTerminator<Book, OrderBy, GroupBy> title = groupByOp("book", "title");
		public final GroupByAuthor author = new GroupByAuthor();

		public class GroupByAuthor {
			public final QueryTerminator<Book, OrderBy, GroupBy> id = groupByOp("author", "id");
			public final QueryTerminator<Book, OrderBy, GroupBy> name = groupByOp("author", "name");
		}
	}

	public BookQuery() {
		super(Book.class);
		// Initialize static operators after super() completes
		this.id = staticOp("book", "id");
		this.title = staticOp("book", "title");
		this.author = new StaticAuthor();
	}

	@Override
	protected BookQuery.Where createWhereConditionStarter() {
		return new Where();
	}

	@Override
	protected BookQuery.OrderBy createOrderByStarter() {
		return new OrderBy();
	}

	@Override
	protected BookQuery.GroupBy createGroupByStarter() {
		return new GroupBy();
	}
}