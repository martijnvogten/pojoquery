package nl.pojoquery;

import java.util.Arrays;
import java.util.Collections;

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
}
