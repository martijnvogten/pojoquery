package org.pojoquery.fluent.internal;

import java.util.List;

import org.pojoquery.fluent.FluentQuery.Appender;
import org.pojoquery.fluent.Operators;

public class ConditionChainOperators<T> implements Operators<T> {
	private final String tableAlias;
	private final String fieldName;
	private final T terminator;
	private final Appender appendExpression;

	public ConditionChainOperators(String tableAlias, String fieldName, T terminator, Appender appendExpression) {
		this.tableAlias = tableAlias;
		this.fieldName = fieldName;
		this.terminator = terminator;
		this.appendExpression = appendExpression;
	}

	public T eq(Object value) {
		appendExpression.append("{" + tableAlias + "." + fieldName + "} = ?", List.of(value));
		return terminator;
	}
}
