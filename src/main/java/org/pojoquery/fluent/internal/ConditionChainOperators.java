package org.pojoquery.fluent.internal;

import org.pojoquery.fluent.Operators;

public abstract class ConditionChainOperators<T> implements Operators<T> {
	private final String tableAlias;
	private final String fieldName;
	private final T terminator;

	public ConditionChainOperators(String tableAlias, String fieldName, T terminator) {
		this.tableAlias = tableAlias;
		this.fieldName = fieldName;
		this.terminator = terminator;
	}

	public T eq(Object value) {
		appendExpression("{" + tableAlias + "." + fieldName + "} = ?", value);
		return terminator;
	}

	protected abstract void appendExpression(String sql, Object... parameters);
}
