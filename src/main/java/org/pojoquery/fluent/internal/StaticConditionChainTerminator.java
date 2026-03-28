package org.pojoquery.fluent.internal;

import java.util.function.Supplier;

import org.pojoquery.SqlExpression;
import org.pojoquery.fluent.FluentQuery.Appender;
import org.pojoquery.fluent.Terminator;

public class StaticConditionChainTerminator<S> implements Terminator<S> {
	private final S starter;
	private final Appender target;
	private final Supplier<SqlExpression> toSql;

	public StaticConditionChainTerminator(S starter, Appender target, Supplier<SqlExpression> toSql) {
		this.starter = starter;
		this.target = target;
		this.toSql = toSql;
	}

	public S and() {
		target.append(" AND ", new Object[0]);
		return starter;
	}

	@Override
	public Terminator<S> and(Terminator<?> other) {
		target.append(" AND (" + other.toSql().getSql() + ")", other.toSql().getParameters());
		return this;
	}

	public S or() {
		target.append(" OR ", new Object[0]);
		return starter;
	}
	
	@Override
	public Terminator<S> or(Terminator<?> other) {
		target.append(" OR (" + other.toSql().getSql() + ")", other.toSql().getParameters());
		return this;
	}

	@Override
	public SqlExpression toSql() {
		return toSql.get();
	}

}

