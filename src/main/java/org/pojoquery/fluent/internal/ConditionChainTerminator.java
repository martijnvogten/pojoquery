package org.pojoquery.fluent.internal;

import java.sql.Connection;
import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.fluent.ConditionTerminator;
import org.pojoquery.fluent.FluentQuery;
import org.pojoquery.fluent.QueryTerminator;

public abstract class ConditionChainTerminator<R, S, T extends ConditionChainTerminator<R, S, T>>
		implements ConditionTerminator<R, S, T> {
	private S starter;
	private FluentQuery<R> query;

	protected ConditionChainTerminator(FluentQuery<R> query) {
		this.query = query;
	}

	public void setStarter(S starter) {
		this.starter = starter;
	}

	public S and() {
		appendExpression(" AND ");
		return starter;
	}
	
	public S or() {
		appendExpression(" OR ");
		return starter;
	}

	public List<R> list(Connection c) {
		return query.list(c);
	}

	@Override
	public QueryTerminator<R> addOrderBy(String orderBy) {
		query.addOrderBy(orderBy);
		return (QueryTerminator<R>) this;
	}

	@Override
	public QueryTerminator<R> addGroupBy(String groupBy) {
		query.addGroupBy(groupBy);
		return (QueryTerminator<R>) this;
	}

	@Override
	public QueryTerminator<R> setLimit(int limit) {
		query.setLimit(limit);
		return (QueryTerminator<R>) this;
	}

	@Override
	public SqlExpression toSql() {
		return query.getSql();
	}

	protected abstract void appendExpression(String sql, Object... parameters);
}

