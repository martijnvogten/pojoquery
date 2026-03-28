package org.pojoquery.fluent;

import org.pojoquery.SqlExpression;

/**
 * S: conditionStarter (not an interface, contains the fields)
 */
public interface Terminator<S> {
	public S and();

	public S or();

	public SqlExpression toSql();
}
