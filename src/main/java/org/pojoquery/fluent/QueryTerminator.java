package org.pojoquery.fluent;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface QueryTerminator<R> {
	public List<R> list(Connection c) throws SQLException;

	public QueryTerminator<R> addOrderBy(String orderBy);

	public QueryTerminator<R> addGroupBy(String groupBy);

	public QueryTerminator<R> setLimit(int limit);
}

