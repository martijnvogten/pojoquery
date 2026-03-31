package org.pojoquery.processor;

import org.pojoquery.fluent.ConditionTerminator;
import org.pojoquery.fluent.FluentQuery;
import org.pojoquery.fluent.OrderByChain;
import org.pojoquery.fluent.QueryTerminator;
import org.pojoquery.fluent.Terminator;
import org.pojoquery.fluent.internal.ConditionChainOperators;
import org.pojoquery.fluent.internal.StaticConditionChainTerminator;
import org.pojoquery.processor.TestBookQueryCopy.Book;

public class BookQuery extends FluentQuery<Book, BookQuery, BookQuery.Where, BookQuery.OrderBy, BookQuery.GroupBy> {

	public final ConditionChainOperators<Terminator<BookQuery,StaticConditionChainTerminator<BookQuery>>, java.lang.Long> id;
	public final ConditionChainOperators<Terminator<BookQuery,StaticConditionChainTerminator<BookQuery>>, java.lang.String> title;
	public final StaticAuthor author;
	public final StaticReviews reviews;
	public final StaticCategories categories;

	public class StaticAuthor {
		public final ConditionChainOperators<Terminator<BookQuery, StaticConditionChainTerminator<BookQuery>>, java.lang.Long> id = staticOp("author", "id", java.lang.Long.class);
		public final ConditionChainOperators<Terminator<BookQuery, StaticConditionChainTerminator<BookQuery>>, java.lang.String> name = staticOp("author", "name", java.lang.String.class);
	}

	public class StaticReviews {
		public final ConditionChainOperators<Terminator<BookQuery, StaticConditionChainTerminator<BookQuery>>, java.lang.Long> id = staticOp("reviews", "id", java.lang.Long.class);
		public final ConditionChainOperators<Terminator<BookQuery, StaticConditionChainTerminator<BookQuery>>, java.lang.String> content = staticOp("reviews", "content", java.lang.String.class);
		public final ConditionChainOperators<Terminator<BookQuery, StaticConditionChainTerminator<BookQuery>>, java.lang.Integer> rating = staticOp("reviews", "rating", java.lang.Integer.class);
	}

	public class StaticCategories {
		public final ConditionChainOperators<Terminator<BookQuery, StaticConditionChainTerminator<BookQuery>>, java.lang.Long> id = staticOp("categories", "id", java.lang.Long.class);
		public final ConditionChainOperators<Terminator<BookQuery, StaticConditionChainTerminator<BookQuery>>, java.lang.String> name = staticOp("categories", "name", java.lang.String.class);
	}

	public class Where {
		public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>, java.lang.Long> id = chainOp("book", "id", java.lang.Long.class);
		public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>, java.lang.String> title = chainOp("book", "title", java.lang.String.class);
		public final WhereAuthor author = new WhereAuthor();
		public final WhereReviews reviews = new WhereReviews();
		public final WhereCategories categories = new WhereCategories();

		public class WhereAuthor {
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>, java.lang.Long> id = chainOp("author", "id", java.lang.Long.class);
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>, java.lang.String> name = chainOp("author", "name", java.lang.String.class);
		}

		public class WhereReviews {
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>, java.lang.Long> id = chainOp("reviews", "id", java.lang.Long.class);
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>, java.lang.String> content = chainOp("reviews", "content", java.lang.String.class);
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>, java.lang.Integer> rating = chainOp("reviews", "rating", java.lang.Integer.class);
		}

		public class WhereCategories {
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>, java.lang.Long> id = chainOp("categories", "id", java.lang.Long.class);
			public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>, java.lang.String> name = chainOp("categories", "name", java.lang.String.class);
		}

	}

	public class OrderBy {
		public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> id = orderByOp("book", "id");
		public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> title = orderByOp("book", "title");
		public final OrderByAuthor author = new OrderByAuthor();
		public final OrderByReviews reviews = new OrderByReviews();
		public final OrderByCategories categories = new OrderByCategories();

		public class OrderByAuthor {
			public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> id = orderByOp("author", "id");
			public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> name = orderByOp("author", "name");
		}

		public class OrderByReviews {
			public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> id = orderByOp("reviews", "id");
			public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> content = orderByOp("reviews", "content");
			public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> rating = orderByOp("reviews", "rating");
		}

		public class OrderByCategories {
			public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> id = orderByOp("categories", "id");
			public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> name = orderByOp("categories", "name");
		}

	}

	public class GroupBy {
		public final QueryTerminator<Book, OrderBy, GroupBy> id = groupByOp("book", "id");
		public final QueryTerminator<Book, OrderBy, GroupBy> title = groupByOp("book", "title");
		public final GroupByAuthor author = new GroupByAuthor();
		public final GroupByReviews reviews = new GroupByReviews();
		public final GroupByCategories categories = new GroupByCategories();

		public class GroupByAuthor {
			public final QueryTerminator<Book, OrderBy, GroupBy> id = groupByOp("author", "id");
			public final QueryTerminator<Book, OrderBy, GroupBy> name = groupByOp("author", "name");
		}

		public class GroupByReviews {
			public final QueryTerminator<Book, OrderBy, GroupBy> id = groupByOp("reviews", "id");
			public final QueryTerminator<Book, OrderBy, GroupBy> content = groupByOp("reviews", "content");
			public final QueryTerminator<Book, OrderBy, GroupBy> rating = groupByOp("reviews", "rating");
		}

		public class GroupByCategories {
			public final QueryTerminator<Book, OrderBy, GroupBy> id = groupByOp("categories", "id");
			public final QueryTerminator<Book, OrderBy, GroupBy> name = groupByOp("categories", "name");
		}

	}

	public BookQuery() {
		super(Book.class);
		this.id = staticOp("book", "id", java.lang.Long.class);
		this.title = staticOp("book", "title", java.lang.String.class);
		this.author = new StaticAuthor();
		this.reviews = new StaticReviews();
		this.categories = new StaticCategories();
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