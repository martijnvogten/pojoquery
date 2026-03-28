package org.pojoquery.fluent.internal;

import org.pojoquery.SqlExpression;

public abstract class StaticConditionChainTerminator<S> implements org.pojoquery.fluent.Terminator<S> {
	private final S starter;

	public StaticConditionChainTerminator(S starter) {
		this.starter = starter;
	}

	public S and() {
		appendStaticExpression(" AND ");
		return starter;
	}

	public S or() {
		appendStaticExpression(" OR ");
		return starter;
	}

	@Override
	public abstract SqlExpression toSql();

	protected abstract void appendStaticExpression(String sql, Object... parameters);
}

