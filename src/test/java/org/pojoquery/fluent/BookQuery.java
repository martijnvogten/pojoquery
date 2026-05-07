package org.pojoquery.fluent;

import org.pojoquery.fluent.FluentConditionChainWithInterfaces.Book;
import org.pojoquery.fluent.internal.ConditionChainOperators;
import org.pojoquery.fluent.internal.StaticConditionChainTerminator;

public class BookQuery extends FluentQuery<Book, BookQuery, BookQuery.Where, BookQuery.OrderBy, BookQuery.GroupBy, Long> {

	public final ConditionChainOperators<Terminator<BookQuery,StaticConditionChainTerminator<BookQuery>>, Long> id;
	public final ConditionChainOperators<Terminator<BookQuery,StaticConditionChainTerminator<BookQuery>>, String> title;
	public final StaticAuthor author;

	public class StaticAuthor {
		public final ConditionChainOperators<Terminator<BookQuery,StaticConditionChainTerminator<BookQuery>>, Long> id = staticOp("author", "id", Long.class);
		public final ConditionChainOperators<Terminator<BookQuery,StaticConditionChainTerminator<BookQuery>>, String> name = staticOp("author", "name", String.class);
	}

	public class Where {
		public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy, Long>, Long> id = chainOp("book", "id", Long.class);
		public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy, Long>, String> title = chainOp("book", "title", String.class);
		public final WhereAuthor author = new WhereAuthor();

		public class WhereAuthor {
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy, Long>, Long> id = chainOp("author", "id", Long.class);
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy, Long>, String> name = chainOp("author", "name", String.class);
		}
	}

	public class OrderBy {
		public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy, Long>> id = orderByOp("book", "id");
		public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy, Long>> title = orderByOp("book", "title");
		public final OrderByAuthor author = new OrderByAuthor();

		public class OrderByAuthor {
			public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy, Long>> id = orderByOp("author", "id");
			public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy, Long>> name = orderByOp("author", "name");
		}
	}

	public class GroupBy {
		public final QueryTerminator<Book, OrderBy, GroupBy, Long> id = groupByOp("book", "id");
		public final QueryTerminator<Book, OrderBy, GroupBy, Long> title = groupByOp("book", "title");
		public final GroupByAuthor author = new GroupByAuthor();

		public class GroupByAuthor {
			public final QueryTerminator<Book, OrderBy, GroupBy, Long> id = groupByOp("author", "id");
			public final QueryTerminator<Book, OrderBy, GroupBy, Long> name = groupByOp("author", "name");
		}
	}

	public BookQuery() {
		super(Book.class);
		// Initialize static operators after super() completes
		this.id = staticOp("book", "id", Long.class);
		this.title = staticOp("book", "title", String.class);
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