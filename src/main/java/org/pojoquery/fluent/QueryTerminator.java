package org.pojoquery.fluent;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface QueryTerminator<R,O,G,PK> {
	public List<R> list(Connection c);
	
	public Optional<R> first(Connection c);
	
	public Optional<R> findById(Connection c, PK id);

	public QueryTerminator<R,O,G,PK> addOrderBy(String orderBy);
	public O orderBy();

	public QueryTerminator<R,O,G,PK> addGroupBy(String groupBy);
	public G groupBy();

	public QueryTerminator<R,O,G,PK> setLimit(int limit);
}

