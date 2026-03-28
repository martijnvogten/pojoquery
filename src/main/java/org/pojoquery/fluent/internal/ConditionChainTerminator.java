package org.pojoquery.fluent.internal;

import java.sql.Connection;
import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.fluent.ConditionTerminator;
import org.pojoquery.fluent.FluentQuery;
import org.pojoquery.fluent.FluentQuery.Appender;
import org.pojoquery.fluent.QueryTerminator;

public class ConditionChainTerminator<R, S, T extends ConditionChainTerminator<R, S, T>>
		implements ConditionTerminator<R, S, T> {
	private S starter;
	private final FluentQuery<R, ?, ?> query;
	private final Appender expressionCallback;

	public ConditionChainTerminator(FluentQuery<R, ?, ?> query, Appender builder) {
		this.query = query;
		this.expressionCallback = builder;
	}

	public void setStarter(S starter) {
		this.starter = starter;
	}

	public S and() {
		expressionCallback.append(" AND ");
		return starter;
	}
	
	public S or() {
		expressionCallback.append(" OR ");
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
}

