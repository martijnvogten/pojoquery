package org.pojoquery.pipeline;

import static org.pojoquery.util.Strings.implode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.util.CurlyMarkers;
import org.pojoquery.util.Iterables;

@SuppressWarnings("unchecked")
public abstract class SqlQuery<SQ extends SqlQuery<?>> {
	private int offset = -1;
	private int rowCount = -1;
	private String schema;
	private String table;
	private List<SqlField> fields = new ArrayList<SqlField>();
	private List<SqlJoin> joins = new ArrayList<SqlJoin>();
	private List<SqlExpression> wheres = new ArrayList<SqlExpression>();
	private List<String> groupBy = new ArrayList<String>();
	private List<String> orderBy = new ArrayList<String>();
	private final DbContext dbContext;

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

		@Override
		public String toString() {
			return "SqlField [alias=" + alias + ", expression=" + expression + "]";
		}
	}

	public enum JoinType {
		LEFT,
		RIGHT,
		INNER
	}
	
	public static class SqlJoin {
		public final JoinType joinType;
		public final String schema;
		public final String table;
		public final String alias;
		public final SqlExpression joinCondition;

		public SqlJoin(JoinType type, String schemaName, String tableName, String alias, SqlExpression joinCondition) {
			this.joinType = type;
			this.schema = "".equals(schemaName) ? null : schemaName;
			this.table = tableName;
			this.alias = alias;
			this.joinCondition = joinCondition;
		}
	}

	public SqlQuery(DbContext context) {
		this.dbContext  = context;
	}
	
	public SqlQuery(DbContext context, String table) {
		this(context);
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

	public SQ setOrderBy(List<String> orderBy) {
		this.orderBy = orderBy;
		return (SQ) this;
	}

	public SQ addField(SqlExpression expression) {
		fields.add(new SqlField(expression));
		return (SQ)this;
	}
	
	public SQ addField(SqlExpression expression, String alias) {
		fields.add(new SqlField(expression, alias));
		return (SQ)this;
	}

	public SQ addGroupBy(String group) {
		groupBy.add(group);
		return (SQ)this;
	}

	public SQ addWhere(SqlExpression where) {
		wheres.add(where);
		return (SQ)this;
	}

	public SQ addWhere(String sql, Object... params) {
		wheres.add(new SqlExpression(sql, Arrays.asList(params)));
		return (SQ)this;
	}

	public SQ addOrderBy(String order) {
		orderBy.add(order);
		return (SQ)this;
	}

	public SQ setLimit(int rowCount) {
		return setLimit(-1, rowCount);
	}

	public SQ setLimit(int offset, int rowCount) {
		this.offset = offset;
		this.rowCount = rowCount;
		return (SQ)this;
	}

	public SqlExpression toStatement() {
		List<SqlExpression> fieldExpressions = new ArrayList<SqlExpression>();
		for(SqlField field : fields) {
			if (field.alias == null) {
				fieldExpressions.add(field.expression);
			} else {
				SqlExpression resolved = resolveAliases(dbContext, field.expression, table);
				String sql = resolved.getSql() + " AS " + dbContext.quoteAlias(field.alias);
				fieldExpressions.add(new SqlExpression(sql, resolved.getParameters()));
			}
		}
		SqlExpression fieldsExp = SqlExpression.implode(",\n ", fieldExpressions);
		return toStatement(new SqlExpression("SELECT\n " + fieldsExp.getSql(), fieldsExp.getParameters()), schema, table, joins, wheres, groupBy, orderBy, offset, rowCount);
	}

	private SqlExpression resolveAliases(DbContext context, SqlExpression sql, String thisAlias) {
		return new SqlExpression(CurlyMarkers.processMarkers(sql.getSql(), marker -> {
			if ("this".equals(marker)) {
				return context.quoteAlias(thisAlias);
			}
			for (SqlField field : fields) {
				if (marker.equals(field.alias)) {
					// Check if field expression is a simple {alias.column} pattern (would cause infinite recursion)
					// In that case, skip field matching and use alias.column handling below
					String fieldSql = field.expression.getSql();
					if (fieldSql.matches("\\{[a-zA-Z0-9_\\.]+\\}")) {
						break; // Fall through to alias.column handling
					}
					return resolveAliases(dbContext, field.expression, thisAlias).getSql();
				}
			}
			// Check if marker is a known table/join alias (which may contain dots)
			if (marker.equals(table)) {
				return context.quoteAlias(marker);
			}
			for (SqlJoin j : joins) {
				if (marker.equals(j.alias)) {
					return context.quoteAlias(marker);
				}
			}
			// Handle alias.column patterns (e.g., "events.festivalID" -> "events"."festivalID")
			// Use last dot to split alias from column name since aliases can contain dots
			int lastDotIndex = marker.lastIndexOf('.');
			if (lastDotIndex > 0) {
				String tableAlias = marker.substring(0, lastDotIndex);
				String columnName = marker.substring(lastDotIndex + 1);
				return context.quoteAlias(tableAlias) + "." + context.quoteObjectNames(columnName);
			}
			return context.quoteAlias(marker);
		}), sql.getParameters());
	}

	public SqlExpression toStatement(SqlExpression selectClause, String schema, String from, List<SqlJoin> joins, List<SqlExpression> wheres, List<String> groupBy,
			List<String> orderBy, int offset, int rowCount) {

		List<Object> params = new ArrayList<Object>();
		Iterables.addAll(params, selectClause.getParameters());

		SqlExpression whereClause = buildWhereClause(wheres);
		Iterables.addAll(params, whereClause.getParameters());

		String groupByClause = resolveAliases(dbContext, SqlExpression.sql(buildClause("GROUP BY", groupBy)), getTable()).getSql();
		String orderByClause = resolveAliases(dbContext, SqlExpression.sql(buildClause("ORDER BY", orderBy)), getTable()).getSql();
		String limitClause = buildLimitClause(offset, rowCount);

		ArrayList<SqlExpression> joinExpressions = new ArrayList<SqlExpression>();
		for(SqlJoin j : joins) {
			String sql = j.joinType.name() + " JOIN " + DB.prefixAndQuoteTableName(dbContext, j.schema, j.table) + " AS " + dbContext.quoteAlias(j.alias);
			SqlExpression resolved = resolveAliases(dbContext, j.joinCondition, table);
			if (j.joinCondition != null) {
				sql += " ON " + resolved.getSql();
			}
			SqlExpression expr = new SqlExpression(sql, resolved.getParameters());
			joinExpressions.add(expr);
		}
		SqlExpression joinsClause = SqlExpression.implode("\n ", joinExpressions);
		
		Iterables.addAll(params, joinsClause.getParameters());

		String sql = implode(" ", Arrays.asList(
					selectClause.getSql(), 
					"\nFROM", DB.prefixAndQuoteTableName(dbContext, schema, from), "AS", dbContext.quoteAlias(from) + "\n", 
					joinsClause.getSql(), 
					whereClause == null ? "" : whereClause.getSql(), 
					groupByClause,
					orderByClause, 
					limitClause
				));

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

	private SqlExpression buildWhereClause(List<SqlExpression> parts) {
		List<Object> parameters = new ArrayList<Object>();
		String whereClause = "";
		if (parts.size() > 0) {
			List<String> clauses = new ArrayList<String>();
			for (SqlExpression exp : parts) {
				clauses.add(resolveAliases(dbContext, exp, getTable()).getSql());
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
		addJoin(type, null, tableName, alias, joinCondition);
	}

	public void addJoin(JoinType type, String schemaName, String tableName, String alias, SqlExpression joinCondition) {
		joins.add(new SqlJoin(type, schemaName, tableName, alias, joinCondition));
	}

	public void setTable(String schemaName, String tableName) {
		this.schema = schemaName;
		this.table = tableName;
	}

	public String getSchema() {
		return this.schema;
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

	public int getOffset() {
		return this.offset;
	}

	public int getRowCount() {
		return this.rowCount;
	}

	public DbContext getDbContext() {
		return this.dbContext;
	}

}
