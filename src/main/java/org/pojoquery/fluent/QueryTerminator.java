package org.pojoquery.fluent;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface QueryTerminator<R,O,G> {
	public List<R> list(Connection c);
	public Optional<R> first(Connection c);

	public QueryTerminator<R,O,G> addOrderBy(String orderBy);
	public O orderBy();

	public QueryTerminator<R,O,G> addGroupBy(String groupBy);
	public G groupBy();

	public QueryTerminator<R,O,G> setLimit(int limit);
}

