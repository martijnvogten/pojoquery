package org.pojoquery.fluent.internal;

import java.util.List;
import java.util.function.Supplier;

import org.pojoquery.SqlExpression;
import org.pojoquery.fluent.FluentQuery.Appender;
import org.pojoquery.fluent.Terminator;

public class StaticConditionChainTerminator<S> implements Terminator<S, StaticConditionChainTerminator<S>> {
	private final S starter;
	private final Appender target;
	private final Supplier<SqlExpression> toSql;

	public StaticConditionChainTerminator(S starter, Appender target, Supplier<SqlExpression> toSql) {
		this.starter = starter;
		this.target = target;
		this.toSql = toSql;
	}

	public S and() {
		target.append("AND", List.of());
		return starter;
	}

	@Override
	public StaticConditionChainTerminator<S> and(Terminator<?, ?> other) {
		SqlExpression otherSql = other.toSql();
		target.append("AND (" + otherSql.getSql() + ")", otherSql.getParameters());
		return this;
	}

	public S or() {
		target.append("OR", List.of());
		return starter;
	}
	
	@Override
	public StaticConditionChainTerminator<S> or(Terminator<?, ?> other) {
		SqlExpression otherSql = other.toSql();
		target.append("OR (" + otherSql.getSql() + ")", otherSql.getParameters());
		return this;
	}

	@Override
	public SqlExpression toSql() {
		return toSql.get();
	}

}

