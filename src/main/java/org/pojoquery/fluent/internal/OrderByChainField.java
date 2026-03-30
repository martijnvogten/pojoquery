package org.pojoquery.fluent.internal;

import java.util.function.Consumer;

import org.pojoquery.fluent.OrderByChain;

public class OrderByChainField<T> implements OrderByChain<T> {
	private final String tableAlias;
	private final String fieldName;
	private final T terminator;
	private final Consumer<String> target;

	public OrderByChainField(String tableAlias, String fieldName, T terminator, Consumer<String> target) {
		this.tableAlias = tableAlias;
		this.fieldName = fieldName;
		this.terminator = terminator;
		this.target = target;
	}

	public T asc() {
		target.accept("{" + tableAlias + "." + fieldName + "} ASC");
		return terminator;
	}

	public T desc() {
		target.accept("{" + tableAlias + "." + fieldName + "} DESC");
		return terminator;
	}

}
