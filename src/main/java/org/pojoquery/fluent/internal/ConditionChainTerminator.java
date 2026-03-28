package org.pojoquery.fluent.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.fluent.ConditionTerminator;
import org.pojoquery.fluent.FluentQuery;
import org.pojoquery.fluent.FluentQuery.Appender;
import org.pojoquery.fluent.QueryTerminator;
import org.pojoquery.fluent.Terminator;

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

	@Override
	public S and() {
		expressionCallback.append(" AND ");
		return starter;
	}

	@Override
	public Terminator<S> and(Terminator<?> other) {
		expressionCallback.append(" AND (" + other.toSql().getSql() + ")", other.toSql().getParameters());
		return this;
	}
	
	@Override
	public S or() {
		expressionCallback.append(" OR ");
		return starter;
	}

	@Override
	public Terminator<S> or(Terminator<?> other) {
		expressionCallback.append(" OR (" + other.toSql().getSql() + ")", other.toSql().getParameters());
		return this;
	}

	public List<R> list(Connection c) throws SQLException {
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

