package org.pojoquery.fluent;

import org.pojoquery.SqlExpression;

public interface FieldExpression<T> {
	public SqlExpression toSql();
}
