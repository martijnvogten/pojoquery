package org.pojoquery.fluent;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface QueryTerminator<R,O,G> {
	public List<R> list(Connection c) throws SQLException;

	public QueryTerminator<R,O,G> addOrderBy(String orderBy);
	public O orderBy();

	public QueryTerminator<R,O,G> addGroupBy(String groupBy);
	public G groupBy();

	public QueryTerminator<R,O,G> setLimit(int limit);
}

