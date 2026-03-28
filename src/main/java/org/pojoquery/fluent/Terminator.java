package org.pojoquery.fluent;

import org.pojoquery.SqlExpression;

/**
 * S: conditionStarter (not an interface, contains the fields)
 */
public interface Terminator<S> {
	public S and();
	
	public Terminator<S> and(Terminator<?> other);

	public S or();

	public Terminator<S> or(Terminator<?> other);

	public SqlExpression toSql();
}
