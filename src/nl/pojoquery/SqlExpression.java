package nl.pojoquery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import nl.pojoquery.util.Iterables;
import nl.pojoquery.util.Strings;

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
	
	public static SqlExpression implode(String glue, Iterable<SqlExpression> expressions) {
		List<String> sqlParts = new ArrayList<>();
		List<Object> params = new ArrayList<>();
		for(SqlExpression part : expressions) {
			sqlParts.add(part.getSql());
			Iterables.addAll(params, part.getParameters());
		}
		return new SqlExpression(Strings.implode(glue, sqlParts), params);
	}
}
