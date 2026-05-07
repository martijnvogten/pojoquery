package org.pojoquery.fluent.internal;

import java.util.Collections;
import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.fluent.FieldExpression;
import org.pojoquery.fluent.FluentQuery.Appender;
import org.pojoquery.fluent.Operators;

public class ConditionChainOperators<T,V> implements Operators<T>, FieldExpression<V> {
	private final T terminator;
	private final SqlExpression baseExpression;
	private final Appender appendExpression;
	// private final Class<V> fieldType;

	public ConditionChainOperators(SqlExpression baseExpression, Class<V> fieldType, T terminator, Appender appendExpression) {
		// this.fieldType = fieldType;
		this.baseExpression = baseExpression;
		this.terminator = terminator;
		this.appendExpression = appendExpression;
	}

	@Override
	public SqlExpression toSql() {
		return baseExpression;
	}

	private void appendBinaryExpression(String operator, Object operand) {
		SqlExpression result = SqlExpression.implode(
			" ", 
			List.of(baseExpression, SqlExpression.sql(operator), toOperandExpression(operand)));
		appendExpression.append(result.getSql(), result.getParameters());
	}

	private SqlExpression toOperandExpression(Object operand) {
		return operand instanceof FieldExpression<?> fieldExpr ? fieldExpr.toSql() : SqlExpression.sql("?", operand);
	}

	public T eq(Object value) {
		appendBinaryExpression("=", value);
		return terminator;
	}
	
	public T gt(Object value) {
		appendBinaryExpression(">", value);
		return terminator;
	}

	public T lt(Object value) {
		appendBinaryExpression("<", value);
		return terminator;
	}

	public T gte(Object value) {
		appendBinaryExpression(">=", value);
		return terminator;
	}

	public T lte(Object value) {
		appendBinaryExpression("<=", value);
		return terminator;
	}

	public T isNull() {
		SqlExpression result = SqlExpression.implode(" ", List.of(baseExpression, SqlExpression.sql("IS NULL")));
		appendExpression.append(result.getSql(), result.getParameters());
		return terminator;
	}

	public T isNotNull() {
		SqlExpression result = SqlExpression.implode(" ", List.of(baseExpression, SqlExpression.sql("IS NOT NULL")));
		appendExpression.append(result.getSql(), result.getParameters());
		return terminator;
	}

	public T ne(Object value) {
		appendBinaryExpression("<>", value);
		return terminator;
	}

	public T like(Object value) {
		appendBinaryExpression("LIKE", value);
		return terminator;
	}
	public T notLike(Object value) {
		appendBinaryExpression("NOT LIKE", value);
		return terminator;
	}

	public T between(Object value, Object value2) {
		SqlExpression result = SqlExpression.implode(" ", List.of(baseExpression, SqlExpression.sql("BETWEEN"), toOperandExpression(value), SqlExpression.sql("AND"), toOperandExpression(value2)));
		appendExpression.append(result.getSql(), result.getParameters());
		return terminator;
	}

	public T in(Object... values) {
		String placeholders = String.join(", ", Collections.nCopies(values.length, "?"));
		SqlExpression result = SqlExpression.implode(" ", List.of(baseExpression, SqlExpression.sql("IN (" + placeholders + ")", values)));
		appendExpression.append(result.getSql(), result.getParameters());
		return terminator;
	}

	public T notIn(Object... values) {
		String placeholders = String.join(", ", Collections.nCopies(values.length, "?"));
		SqlExpression result = SqlExpression.implode(" ", List.of(baseExpression, SqlExpression.sql("NOT IN (" + placeholders + ")", values)));
		appendExpression.append(result.getSql(), result.getParameters());
		return terminator;
	}
}
