package org.pojoquery.fluent.internal;

import java.util.function.BiConsumer;

import org.pojoquery.fluent.Operators;

public class ConditionChainOperators<T> implements Operators<T> {
	private final String tableAlias;
	private final String fieldName;
	private final T terminator;
	private final BiConsumer<String, Object[]> appendExpression;

	public ConditionChainOperators(String tableAlias, String fieldName, T terminator, BiConsumer<String, Object[]> appendExpression) {
		this.tableAlias = tableAlias;
		this.fieldName = fieldName;
		this.terminator = terminator;
		this.appendExpression = appendExpression;
	}

	public T eq(Object value) {
		appendExpression.accept("{" + tableAlias + "." + fieldName + "} = ?", new Object[] { value });
		return terminator;
	}
}
