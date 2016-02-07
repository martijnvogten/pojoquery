package nl.pojoquery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import nl.pojoquery.util.Iterables;

public class SqlExpression {
	private final String sql;
	private final Iterable<Object> parameters;
	
	public SqlExpression(String sql) {
		this(sql, Collections.emptyList());
	}
	
	public SqlExpression(String sql, Iterable<Object> params) {
		this.sql = sql;
		this.parameters = params;
	}

	public String getSql() {
		return sql;
	}

	public Iterable<Object> getParameters() {
		return parameters;
	}
	
	public static SqlExpression sql(String sql, Object... parameters) {
		return new SqlExpression(sql, Arrays.asList(parameters));
	}
	
	public static SqlExpression join(Iterable<SqlExpression> expressions, String glue) {
		StringBuilder sql = new StringBuilder();
		for(SqlExpression exp : expressions) {
			if (sql.length() > 0) {
				sql.append(glue);
			}
			sql.append(exp.sql);
		}
		
		List<Object> allParams = new ArrayList<>();
		for(SqlExpression exp : expressions) {
			Iterables.addAll(allParams, exp.getParameters());
		}
		return new SqlExpression(sql.toString(), allParams);
	}

}
