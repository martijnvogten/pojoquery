package org.pojoquery.fluent;

import org.pojoquery.SqlExpression;

/**
 * S: conditionStarter (not an interface, contains the fields)
 */
public interface Terminator<S, T extends Terminator<S, T>> {
	public S and();
	
	public T and(Terminator<?, ?> other);

	public S or();

	public T or(Terminator<?, ?> other);

	public SqlExpression toSql();
}
