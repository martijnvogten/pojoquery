package nl.pojoquery.pipeline;

import static nl.pojoquery.util.Strings.implode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.pojoquery.SqlExpression;
import nl.pojoquery.util.Iterables;

public class SqlQuery {
	private int offset = -1;
	private int rowCount = -1;
	private String table;
	private List<SqlField> fields = new ArrayList<SqlField>();
	private List<SqlJoin> joins = new ArrayList<SqlJoin>();
	private List<SqlExpression> wheres = new ArrayList<SqlExpression>();
	private List<String> groupBy = new ArrayList<String>();
	private List<String> orderBy = new ArrayList<String>();

	public static class SqlField {
		public final String alias;
		public final SqlExpression expression;
		
		public SqlField(SqlExpression expression) {
			this(expression, null);
		}
		
		public SqlField(SqlExpression expression, String alias) {
			this.expression = expression;
			this.alias = alias;
		}
	}

	public enum JoinType {
		LEFT,
		RIGHT,
		INNER
	}
	
	public static class SqlJoin {
		public final JoinType joinType;
		public final String table;
		public final String alias;
		public final SqlExpression joinCondition;
		
		public SqlJoin(JoinType type, String tableName, String alias, SqlExpression joinCondition) {
			this.joinType = type;
			this.table = tableName;
			this.alias = alias;
			this.joinCondition = joinCondition;
		}
	}

	public SqlQuery() {
	}
	
	public SqlQuery(String table) {
		this.table = table;
	}

	public List<SqlField> getFields() {
		return fields;
	}

	public void setFields(List<SqlField> fields) {
		this.fields = fields;
	}

	public List<SqlJoin> getJoins() {
		return joins;
	}

	public void setJoins(List<SqlJoin> joins) {
		this.joins = joins;
	}

	public List<SqlExpression> getWheres() {
		return wheres;
	}

	public void setWheres(List<SqlExpression> wheres) {
		this.wheres = wheres;
	}

	public List<String> getGroupBy() {
		return groupBy;
	}

	public void setGroupBy(List<String> groupBy) {
		this.groupBy = groupBy;
	}

	public List<String> getOrderBy() {
		return orderBy;
	}

	public SqlQuery setOrderBy(List<String> orderBy) {
		this.orderBy = orderBy;
		return this;
	}

	public SqlQuery addField(SqlExpression expression) {
		fields.add(new SqlField(expression));
		return this;
	}
	
	public SqlQuery addField(SqlExpression expression, String alias) {
		fields.add(new SqlField(expression, alias));
		return this;
	}

	public SqlQuery addGroupBy(String group) {
		groupBy.add(group);
		return this;
	}

	public SqlQuery addWhere(SqlExpression where) {
		wheres.add(where);
		return this;
	}

	public SqlQuery addWhere(String sql, Object... params) {
		wheres.add(new SqlExpression(sql, Arrays.asList(params)));
		return this;
	}

	public SqlQuery addOrderBy(String order) {
		orderBy.add(order);
		return this;
	}

	public SqlQuery setLimit(int rowCount) {
		return setLimit(-1, rowCount);
	}

	public SqlQuery setLimit(int offset, int rowCount) {
		this.offset = offset;
		this.rowCount = rowCount;
		return this;
	}

	public SqlExpression toStatement() {
		List<SqlExpression> fieldExpressions = new ArrayList<SqlExpression>();
		for(SqlField field : fields) {
			if (field.alias == null) {
				fieldExpressions.add(field.expression);
			} else {
				SqlExpression resolved = resolveAliases(field.expression, "");
				String sql = resolved.getSql() + " AS `" + field.alias + "`";
				fieldExpressions.add(new SqlExpression(sql, resolved.getParameters()));
			}
		}
		SqlExpression fieldsExp = SqlExpression.implode(",\n ", fieldExpressions);
		return toStatement(new SqlExpression("SELECT\n " + fieldsExp.getSql(), fieldsExp.getParameters()), table, joins, wheres, groupBy, orderBy, offset, rowCount);
	}

	public static SqlExpression resolveAliases(SqlExpression sql, String prefixAlias) {
		StringBuffer result = new StringBuffer();
		List<Object> parameters = new ArrayList<>();
		Matcher m = Pattern.compile("\\{([a-zA-Z0-9_\\.]+)\\}\\.").matcher(sql.getSql());
		while(m.find()) {
			String alias = m.group(1);
			String combinedAlias;
			if ("this".equals(alias)) {
				combinedAlias = prefixAlias;
			} else if (!prefixAlias.isEmpty()) {
				combinedAlias = prefixAlias + "." + alias;
			} else {
				combinedAlias = alias;
			}
			m.appendReplacement(result, "`" + combinedAlias + "`.");
		}
		m.appendTail(result);
		return new SqlExpression(result.toString(), parameters);
	}

	public static SqlExpression toStatement(SqlExpression selectClause, String from, List<SqlJoin> joins, List<SqlExpression> wheres, List<String> groupBy,
			List<String> orderBy, int offset, int rowCount) {

		List<Object> params = new ArrayList<Object>();
		Iterables.addAll(params, selectClause.getParameters());

		SqlExpression whereClause = buildWhereClause(wheres);
		Iterables.addAll(params, whereClause.getParameters());

		String groupByClause = buildClause("GROUP BY", groupBy);
		String orderByClause = buildClause("ORDER BY", orderBy);
		String limitClause = buildLimitClause(offset, rowCount);

		ArrayList<SqlExpression> joinExpressions = new ArrayList<SqlExpression>();
		for(SqlJoin j : joins) {
			String sql = j.joinType.name() + " JOIN " + j.table + " AS `" + j.alias + "`";
			SqlExpression resolved = resolveAliases(j.joinCondition, "");
			if (j.joinCondition != null) {
				sql += " ON " + resolved.getSql();
			}
			SqlExpression expr = new SqlExpression(sql, resolved.getParameters());
			joinExpressions.add(expr);
		}
		SqlExpression joinsClause = SqlExpression.implode("\n ", joinExpressions);
		
		Iterables.addAll(params, joinsClause.getParameters());

		String sql = implode(" ", Arrays.asList(selectClause.getSql(), "\nFROM", from, "\n", joinsClause.getSql(), whereClause == null ? "" : whereClause.getSql(), groupByClause,
				orderByClause, limitClause));

		return new SqlExpression(sql, params);
	}

	private static String buildLimitClause(int offset, int rowCount) {
		String limitClause = "";
		if (offset > -1 || rowCount > -1) {
			if (rowCount < 0) {
				// No rowcount
				rowCount = Integer.MAX_VALUE;
			}
			if (offset > -1) {
				limitClause = "\nLIMIT " + offset + "," + rowCount;
			} else {
				limitClause = "\nLIMIT " + rowCount;
			}
		}
		return limitClause;
	}

	private static SqlExpression buildWhereClause(List<SqlExpression> parts) {
		List<Object> parameters = new ArrayList<Object>();
		String whereClause = "";
		if (parts.size() > 0) {
			List<String> clauses = new ArrayList<String>();
			for (SqlExpression exp : parts) {
				clauses.add(exp.getSql());
				for (Object o : exp.getParameters()) {
					parameters.add(o);
				}
			}
			whereClause = "\nWHERE " + implode("\n AND ", clauses);
		}
		return new SqlExpression(whereClause, parameters);
	}

	private static String buildClause(String preamble, List<String> parts) {
		String groupByClause = "";
		if (parts != null && parts.size() > 0) {
			groupByClause = "\n" + preamble + " " + implode(", ", parts);
		}
		return groupByClause;
	}

	public void addJoin(JoinType type, String tableName, String alias, SqlExpression joinCondition) {
		joins.add(new SqlJoin(type, tableName, alias, joinCondition));
	}

	public void setTable(String table) {
		this.table = table;
	}
	
	public String getTable() {
		return this.table;
	}

	public static class NamedParameter {
		private final String name;

		public NamedParameter(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

}
