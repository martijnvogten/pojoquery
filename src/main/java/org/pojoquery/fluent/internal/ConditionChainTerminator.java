package org.pojoquery.fluent.internal;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import org.pojoquery.SqlExpression;
import org.pojoquery.fluent.ConditionTerminator;
import org.pojoquery.fluent.FluentQuery;
import org.pojoquery.fluent.FluentQuery.Appender;
import org.pojoquery.fluent.QueryTerminator;
import org.pojoquery.fluent.Terminator;

public class ConditionChainTerminator<R, S, T extends ConditionChainTerminator<R, S, T, O, G>, O, G>
		implements ConditionTerminator<R, S, T, O, G> {
	private S starter;
	private final FluentQuery<R, ?, ?, O, G> query;
	private final Appender expressionCallback;

	public ConditionChainTerminator(FluentQuery<R, ?, ?, O, G> query, Appender builder) {
		this.query = query;
		this.expressionCallback = builder;
	}

	public void setStarter(S starter) {
		this.starter = starter;
	}

	@Override
	public S and() {
		expressionCallback.append("AND", List.of());
		return starter;
	}

	@Override
	@SuppressWarnings("unchecked")
	public T and(Terminator<?, ?> other) {
		SqlExpression otherSql = other.toSql();
		expressionCallback.append("AND (" + otherSql.getSql() + ")", otherSql.getParameters());
		return (T) this;
	}
	
	@Override
	public S or() {
		expressionCallback.append("OR", List.of());
		return starter;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public T or(Terminator<?, ?> other) {
		SqlExpression otherSql = other.toSql();
		expressionCallback.append("OR (" + otherSql.getSql() + ")", otherSql.getParameters());
		return (T) this;
	}

	public List<R> list(Connection c) {
		return query.list(c);
	}

	@Override
	public Optional<R> first(Connection c) {
		return query.first(c);
	}

	@Override
	public QueryTerminator<R,O,G> addOrderBy(String orderBy) {
		query.addOrderBy(orderBy);
		return (QueryTerminator<R,O,G>) this;
	}

	@Override
	public O orderBy() {
		return query.orderBy();
	}

	@Override
	public QueryTerminator<R,O,G> addGroupBy(String groupBy) {
		query.addGroupBy(groupBy);
		return (QueryTerminator<R,O,G>) this;
	}

	@Override
	public QueryTerminator<R,O,G> setLimit(int limit) {
		query.setLimit(limit);
		return (QueryTerminator<R,O,G>) this;
	}

	@Override
	public SqlExpression toSql() {
		return query.getSql();
	}

	@Override
	public G groupBy() {
		return query.groupBy();
	}

}

