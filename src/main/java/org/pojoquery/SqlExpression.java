package org.pojoquery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.pojoquery.util.Iterables;
import org.pojoquery.util.Strings;

/**
 * Represents a SQL expression with its associated parameters.
 * 
 * <p>SqlExpression encapsulates a SQL fragment along with its bound parameters,
 * enabling safe parameterized queries and preventing SQL injection. It is used
 * throughout PojoQuery for building WHERE clauses, custom fields, and other
 * SQL fragments.</p>
 * 
 * <h2>Creating Expressions</h2>
 * <pre>{@code
 * // Using the static factory method
 * SqlExpression expr = SqlExpression.sql("status = ? AND created > ?", "active", someDate);
 * 
 * // Using the constructor
 * SqlExpression expr = new SqlExpression("name LIKE ?", List.of("%John%"));
 * }</pre>
 * 
 * <h2>Using with PojoQuery</h2>
 * <pre>{@code
 * PojoQuery.build(User.class)
 *     .addWhere(SqlExpression.sql("{user}.status = ?", "active"))
 *     .addField(SqlExpression.sql("COUNT(*)"), "totalCount")
 *     .execute(dataSource);
 * }</pre>
 * 
 * <h2>Combining Expressions</h2>
 * <pre>{@code
 * List<SqlExpression> conditions = List.of(
 *     SqlExpression.sql("status = ?", "active"),
 *     SqlExpression.sql("role = ?", "admin")
 * );
 * SqlExpression combined = SqlExpression.implode(" AND ", conditions);
 * // Result: "status = ? AND role = ?" with parameters ["active", "admin"]
 * }</pre>
 * 
 * @see PojoQuery#addWhere(SqlExpression)
 * @see PojoQuery#addField(SqlExpression, String)
 */
public class SqlExpression {
	private final String sql;
	private final Iterable<Object> parameters;
	
	/**
	 * Creates a SQL expression with no parameters.
	 * 
	 * @param sql the SQL string
	 */
	public SqlExpression(String sql) {
		this(sql, Collections.emptyList());
	}
	
	/**
	 * Creates a SQL expression with the given parameters.
	 * 
	 * @param sql the SQL string with parameter placeholders (?)
	 * @param params the parameter values
	 */
	public SqlExpression(String sql, Iterable<Object> params) {
		this.sql = sql;
		this.parameters = params;
	}

	/**
	 * Returns the SQL string.
	 * 
	 * @return the SQL string
	 */
	public String getSql() {
		return sql;
	}

	/**
	 * Returns the parameter values for this expression.
	 * 
	 * @return the parameter values
	 */
	public Iterable<Object> getParameters() {
		return parameters;
	}
	
	/**
	 * Creates a SQL expression with the given SQL and parameters.
	 * 
	 * <p>This is the preferred way to create SqlExpression instances:</p>
	 * <pre>{@code
	 * SqlExpression.sql("name = ? AND status = ?", "John", "active")
	 * }</pre>
	 * 
	 * @param sql the SQL string with parameter placeholders (?)
	 * @param parameters the parameter values in order
	 * @return a new SqlExpression
	 */
	public static SqlExpression sql(String sql, Object... parameters) {
		return new SqlExpression(sql, Arrays.asList(parameters));
	}
	
	/**
	 * Combines multiple SQL expressions into one, joining them with the specified glue string.
	 * 
	 * <p>Example:</p>
	 * <pre>{@code
	 * List<SqlExpression> conditions = List.of(
	 *     SqlExpression.sql("a = ?", 1),
	 *     SqlExpression.sql("b = ?", 2)
	 * );
	 * SqlExpression combined = SqlExpression.implode(" AND ", conditions);
	 * // SQL: "a = ? AND b = ?"
	 * // Parameters: [1, 2]
	 * }</pre>
	 * 
	 * @param glue the string to insert between expressions (e.g., " AND ", " OR ", ", ")
	 * @param expressions the expressions to combine
	 * @return a new SqlExpression combining all inputs
	 */
	public static SqlExpression implode(String glue, Iterable<SqlExpression> expressions) {
		List<String> sqlParts = new ArrayList<>();
		List<Object> params = new ArrayList<>();
		for(SqlExpression part : expressions) {
			sqlParts.add(part.getSql());
			Iterables.addAll(params, part.getParameters());
		}
		return new SqlExpression(Strings.implode(glue, sqlParts), params);
	}

	@Override
	public String toString() {
		return "SqlExpression [sql=" + sql + ", parameters=" + parameters + "]";
	}
}
