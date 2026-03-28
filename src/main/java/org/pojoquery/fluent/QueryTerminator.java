package org.pojoquery.fluent;

import java.sql.Connection;
import java.util.List;

public interface QueryTerminator<R> {
	public List<R> list(Connection c);

	public QueryTerminator<R> addOrderBy(String orderBy);

	public QueryTerminator<R> addGroupBy(String groupBy);

	public QueryTerminator<R> setLimit(int limit);
}

