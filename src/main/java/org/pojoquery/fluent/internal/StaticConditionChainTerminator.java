package org.pojoquery.fluent.internal;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.pojoquery.SqlExpression;

public class StaticConditionChainTerminator<S> implements org.pojoquery.fluent.Terminator<S> {
	private final S starter;
	private final BiConsumer<String, Object[]> appendExpression;
	private final Supplier<SqlExpression> toSql;

	public StaticConditionChainTerminator(S starter, BiConsumer<String, Object[]> appendExpression, Supplier<SqlExpression> toSql) {
		this.starter = starter;
		this.appendExpression = appendExpression;
		this.toSql = toSql;
	}

	public S and() {
		appendExpression.accept(" AND ", new Object[0]);
		return starter;
	}

	public S or() {
		appendExpression.accept(" OR ", new Object[0]);
		return starter;
	}

	@Override
	public SqlExpression toSql() {
		return toSql.get();
	}
}

