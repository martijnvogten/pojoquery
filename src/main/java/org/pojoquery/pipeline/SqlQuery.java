package org.pojoquery.pipeline;

import static org.pojoquery.util.Strings.implode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.querytree.transforms.ExpressionResolver;
import org.pojoquery.util.CurlyMarkers;
import org.pojoquery.util.Iterables;

@SuppressWarnings("unchecked")
public abstract class SqlQuery<SQ extends SqlQuery<?>> implements AQTTransformer.PlainQueryBuilder {
	private int offset = -1;
	private int rowCount = -1;
	private String schema;
	private String table;
	private List<SqlField> fields = new ArrayList<SqlField>();
	private List<SqlJoin> joins = new ArrayList<SqlJoin>();
	private List<SqlExpression> wheres = new ArrayList<SqlExpression>();
	private List<String> groupBy = new ArrayList<String>();
	private List<String> orderBy = new ArrayList<String>();
	private List<WithClause> withClauses = new ArrayList<WithClause>();
	private final DbContext dbContext;

	public static class WithClause {
		public final String alias;
		public final List<String> columnNames;
		public final SqlExpression body;
		public final boolean recursive;

		public WithClause(String alias, List<String> columnNames, SqlExpression body, boolean recursive) {
			this.alias = alias;
			this.columnNames = columnNames;
			this.body = body;
			this.recursive = recursive;
		}
	}

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
		public final SqlExpression subquery; // For derived table joins

		public SqlJoin(JoinType type, String schemaName, String tableName, String alias, SqlExpression joinCondition) {
			this(type, schemaName, tableName, alias, joinCondition, null);
		}

		/**
		 * Creates a subquery (derived table) join.
		 * @param type join type (LEFT, RIGHT, INNER)
		 * @param subquery the SELECT statement for the derived table
		 * @param alias the alias for the subquery
		 * @param joinCondition the ON clause
		 */
		public SqlJoin(JoinType type, SqlExpression subquery, String alias, SqlExpression joinCondition) {
			this(type, null, null, alias, joinCondition, subquery);
		}

		private SqlJoin(JoinType type, String schemaName, String tableName, String alias, SqlExpression joinCondition, SqlExpression subquery) {
			this.joinType = type;
			this.schema = "".equals(schemaName) ? null : schemaName;
			this.table = tableName;
			this.alias = alias;
			this.joinCondition = joinCondition;
			this.subquery = subquery;
		}

		public boolean isSubquery() {
			return subquery != null;
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

	@Override
	public void setOrderBy(List<String> orderBy) {
		this.orderBy = orderBy;
	}

	public SQ addField(SqlExpression expression) {
		fields.add(new SqlField(expression));
		return (SQ)this;
	}
	
	@Override
	public void addField(SqlExpression expression, String alias) {
		fields.add(new SqlField(expression, alias));
	}

	public SQ addGroupBy(String group) {
		groupBy.add(group);
		return (SQ)this;
	}

	@Override
	public void addWhere(SqlExpression where) {
		wheres.add(where);
	}

	public SQ addWhere(String sql, Object... params) {
		wheres.add(new SqlExpression(sql, Arrays.asList(params)));
		return (SQ)this;
	}

	/**
	 * Adds a semi-join (or anti-join) condition of the form
	 * {@code {table.id} [NOT] IN (SELECT table.id FROM <table> <joins> WHERE <condition>)}.
	 *
	 * <p>
	 * The sub-query replicates this query's FROM table and joins, so the condition
	 * uses exactly the same {@code {alias.column}} markers as
	 * {@link #addWhere(String, Object...)}. Unlike a plain {@code addWhere} against
	 * a LEFT-joined collection alias (which both filters the root rows <em>and</em>
	 * truncates the hydrated collection), this condition only decides whether a
	 * root row is returned; the collections are still loaded in full by the outer
	 * LEFT JOINs.
	 *
	 * <p>
	 * Because all generated joins are LEFT JOINs, joins the condition does not
	 * reference can never change the set of returned ids; they are pruned from the
	 * sub-query (see {@link #pruneUnusedJoins()}). A single condition may reference
	 * collections on different paths, or correlate two collections with each
	 * other; the sub-query then ranges over the join product.
	 *
	 * @param idColumns the primary key column(s) of the FROM table
	 * @param condition the condition applied inside the sub-query
	 * @param negate    {@code true} to emit {@code NOT IN}, {@code false} for
	 *                  {@code IN}
	 */
	public SQ addWhereExists(List<String> idColumns, SqlExpression condition, boolean negate) {
		DefaultSqlQuery sub = new DefaultSqlQuery(dbContext);
		sub.setTable(schema, table);
		// Copy: pruning inside toStatement() must not affect this query.
		sub.setJoins(new ArrayList<>(joins));
		List<String> markers = new ArrayList<>();
		for (String idColumn : idColumns) {
			String marker = "{" + table + "." + idColumn + "}";
			markers.add(marker);
			// Pre-resolve the id markers: markers in this query's own WHERE clause are
			// post-processed, but field expressions of the embedded sub-select are
			// emitted verbatim.
			sub.addField(new SqlExpression(quoteObjectNames(marker)));
		}
		sub.addWhere(condition);
		SqlExpression subStatement = sub.toStatement();

		String left = markers.size() == 1 ? markers.get(0) : "(" + String.join(", ", markers) + ")";
		String sql = left + (negate ? " NOT IN (" : " IN (") + subStatement.getSql() + ")";
		wheres.add(new SqlExpression(sql, subStatement.getParameters()));
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

	public SqlExpression toListIdsStatement(SqlExpression idFieldExpression) {
		SqlExpression resolved = new SqlExpression(quoteObjectNames(resolveExpression(idFieldExpression.getSql(), table)), idFieldExpression.getParameters());
		return toStatement(new SqlExpression("SELECT\n DISTINCT " + resolved.getSql()), schema, table, joins, wheres, groupBy, orderBy, offset, rowCount);
	}

	public SqlExpression toCountStatement(SqlExpression idFieldExpression) {
		SqlExpression resolved = new SqlExpression(quoteObjectNames(resolveExpression(idFieldExpression.getSql(), table)), idFieldExpression.getParameters());
		return toStatement(new SqlExpression("SELECT COUNT(DISTINCT " + resolved.getSql() + ") "), schema, table, joins, wheres, null, null, -1, -1);
	}

	private Map<String, String> buildFieldsExpressionMapping(SqlField excludeField) {
		Map<String, String> mapping = new HashMap<>();
		for (SqlField f : fields) {
			if (f.alias == null || f == excludeField) {
				continue;
			}
			mapping.put(f.alias, f.expression.getSql());
		}
		return mapping;
	}

	public SqlExpression toStatement() {
		pruneUnusedJoins();
		List<SqlExpression> fieldExpressions = new ArrayList<SqlExpression>();
		for(SqlField field : fields) {
			if (field.alias == null) {
				fieldExpressions.add(field.expression);
			} else {
				Map<String, String> mapping = buildFieldsExpressionMapping(field);
				String resolved = resolveFieldAliases(field.expression.getSql(), mapping);
				String sql = quoteObjectNames(resolved) + " AS " + dbContext.quoteAlias(field.alias);
				fieldExpressions.add(new SqlExpression(sql, field.expression.getParameters()));
			}
		}
		SqlExpression fieldsExp = SqlExpression.implode(",\n ", fieldExpressions);
		return toStatement(new SqlExpression("SELECT\n " + fieldsExp.getSql(), fieldsExp.getParameters()), schema, table, joins, wheres, groupBy, orderBy, offset, rowCount);
	}

	private String resolveExpression(String exp, String thisAlias) {
		return resolveFieldAliases(ExpressionResolver.resolve(exp, thisAlias), buildFieldsExpressionMapping(null));
	}

	private String resolveFieldAliases(String expr, Map<String, String> mapping) {
		return CurlyMarkers.processMarkers(expr, marker -> {
				if (mapping.containsKey(marker)) {
					String replacement = mapping.get(marker);
					// To prevent looping forever, remove the current marker from the mapping
					Map<String, String> newMap = new HashMap<>(mapping);
					newMap.remove(marker);
					return resolveFieldAliases(replacement, newMap);
				} else {
					return "{" + marker + "}";
				}
			});
	}

	private String quoteObjectNames(String sql) {
		return quoteMarkers(dbContext, sql);
	}

	/**
	 * Resolves {@code {alias.column}} and {@code {name}} markers in a SQL string
	 * to properly quoted identifiers for the given context.
	 */
	public static String quoteMarkers(DbContext dbContext, String sql) {
		return CurlyMarkers.processMarkers(sql, marker -> {
			if (marker.contains(".")) {
				List<String> parts = Arrays.asList(marker.split("\\."));
				if (parts.size() > 1) {
					// Last part is column name, everything before is table alias (which may contain dots)
					String columnName = parts.get(parts.size() - 1);
					List<String> tableAliasParts = parts.subList(0, parts.size() - 1);
					String tableAlias = String.join(".", tableAliasParts);
					return dbContext.quoteAlias(tableAlias) + "." + dbContext.quoteObjectNames(columnName);
				}
			}
			return dbContext.quoteObjectNames(marker);
		});
	}

	public SqlExpression toStatement(SqlExpression selectClause, String schema, String from, List<SqlJoin> joins, List<SqlExpression> wheres, List<String> groupBy,
			List<String> orderBy, int offset, int rowCount) {

		List<Object> params = new ArrayList<Object>();

		SqlExpression withPreamble = buildWithPreamble();
		Iterables.addAll(params, withPreamble.getParameters());
		Iterables.addAll(params, selectClause.getParameters());

		SqlExpression whereClause = buildWhereClause(wheres);
		Iterables.addAll(params, whereClause.getParameters());

		String groupByClause = quoteObjectNames(resolveExpression(buildClause("GROUP BY", groupBy), getTable()));
		String orderByClause = quoteObjectNames(resolveExpression(buildClause("ORDER BY", orderBy), getTable()));
		String limitClause = buildLimitClause(offset, rowCount);

		ArrayList<SqlExpression> joinExpressions = new ArrayList<SqlExpression>();
		for(SqlJoin j : joins) {
			String sql;
			List<Object> joinParams = new ArrayList<>();
			if (j.isSubquery()) {
				// Derived table join: LEFT JOIN (SELECT ...) AS alias ON ...
				sql = j.joinType.name() + " JOIN (" + j.subquery.getSql() + ") AS " + dbContext.quoteAlias(j.alias);
				Iterables.addAll(joinParams, j.subquery.getParameters());
			} else {
				sql = j.joinType.name() + " JOIN " + DB.prefixAndQuoteTableName(dbContext, j.schema, j.table) + " AS " + dbContext.quoteAlias(j.alias);
			}
			String resolved = resolveExpression(j.joinCondition.getSql(), table);
			if (j.joinCondition != null) {
				sql += " ON " + resolved;
			}
			Iterables.addAll(joinParams, j.joinCondition.getParameters());
			SqlExpression expr = new SqlExpression(quoteObjectNames(sql), joinParams);
			joinExpressions.add(expr);
		}
		SqlExpression joinsClause = SqlExpression.implode("\n ", joinExpressions);
		
		Iterables.addAll(params, joinsClause.getParameters());

		String sql = implode(" ", Arrays.asList(
					withPreamble.getSql(),
					selectClause.getSql(), 
					"\nFROM", DB.prefixAndQuoteTableName(dbContext, schema, from), "AS", dbContext.quoteAlias(from), "\n",
					joinsClause.getSql(), 
					whereClause == null ? "" : whereClause.getSql(), 
					groupByClause,
					orderByClause, 
					limitClause
				));

		return new SqlExpression(sql, params);
	}

	private SqlExpression buildWithPreamble() {
		return buildWithPreamble(dbContext, withClauses);
	}

	/**
	 * Renders the {@code WITH [RECURSIVE] ...} preamble for the given common
	 * table expressions, or the empty expression when there are none.
	 *
	 * <p>A single {@code RECURSIVE} keyword covers the whole list, as the SQL
	 * standard requires, when any of the clauses is recursive.</p>
	 */
	public static SqlExpression buildWithPreamble(DbContext dbContext, List<WithClause> withClauses) {
		if (withClauses.isEmpty()) {
			return new SqlExpression("", new ArrayList<>());
		}
		boolean anyRecursive = withClauses.stream().anyMatch(w -> w.recursive);
		List<Object> params = new ArrayList<>();
		List<String> parts = new ArrayList<>();
		for (WithClause w : withClauses) {
			String header = dbContext.quoteAlias(w.alias);
			if (w.columnNames != null && !w.columnNames.isEmpty()) {
				List<String> quoted = new ArrayList<>();
				for (String col : w.columnNames) {
					quoted.add(dbContext.quoteObjectNames(col));
				}
				header += " (" + implode(", ", quoted) + ")";
			}
			parts.add(header + " AS (" + w.body.getSql() + ")");
			Iterables.addAll(params, w.body.getParameters());
		}
		String sql = "WITH " + (anyRecursive ? "RECURSIVE " : "") + implode(",\n ", parts) + "\n";
		return new SqlExpression(sql, params);
	}

	static String buildLimitClause(int offset, int rowCount) {
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
				clauses.add(quoteObjectNames(resolveExpression(exp.getSql(), getTable())));
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

	/**
	 * Removes joins that are not referenced by any field expression, WHERE/GROUP BY/ORDER BY condition, or
	 * transitively required join condition. The algorithm:
	 * <ol>
	 *   <li>Seed the used-alias set from field expressions and WHERE/GROUP BY/ORDER BY clauses.</li>
	 *   <li>For every join whose alias is in the used set, add the aliases referenced in
	 *       its join condition to the used set.</li>
	 *   <li>Repeat step 2 until no new aliases are discovered (fixed-point).</li>
	 *   <li>Retain only joins whose alias is in the final used set.</li>
	 * </ol>
	 */
	private void pruneUnusedJoins() {
		Set<String> used = new HashSet<>();

		// Seed from field expressions
		for (SqlField field : fields) {
			used.addAll(CurlyMarkers.extractAliases(field.expression.getSql()));
		}

		// Seed from WHERE conditions
		for (SqlExpression where : wheres) {
			used.addAll(CurlyMarkers.extractAliases(where.getSql()));
		}

		// Seed from GROUP BY clauses
		for (String group : groupBy) {
			used.addAll(CurlyMarkers.extractAliases(group));
		}

		// Seed from ORDER BY clauses
		for (String order : orderBy) {
			used.addAll(CurlyMarkers.extractAliases(order));
		}

		// Transitively expand: joins needed by already-used joins
		boolean changed = true;
		while (changed) {
			changed = false;
			for (SqlJoin j : joins) {
				if (used.contains(j.alias) && j.joinCondition != null) {
					Set<String> conditionAliases = CurlyMarkers.extractAliases(j.joinCondition.getSql());
					if (used.addAll(conditionAliases)) {
						changed = true;
					}
				}
			}
		}

		joins = joins.stream().filter(j -> used.contains(j.alias)).collect(Collectors.toList());
	}

	public void addJoin(JoinType type, String tableName, String alias, SqlExpression joinCondition) {
		addJoin(type, null, tableName, alias, joinCondition);
	}

	public void addJoin(JoinType type, String schemaName, String tableName, String alias, SqlExpression joinCondition) {
		joins.add(new SqlJoin(type, schemaName, tableName, alias, joinCondition));
	}

	/**
	 * Adds a subquery (derived table) join.
	 * @param type join type (LEFT, RIGHT, INNER)
	 * @param subquery the SELECT statement for the derived table
	 * @param alias the alias for the subquery
	 * @param joinCondition the ON clause
	 */
	public void addSubqueryJoin(JoinType type, SqlExpression subquery, String alias, SqlExpression joinCondition) {
		joins.add(new SqlJoin(type, subquery, alias, joinCondition));
	}

	public void addWithClause(String alias, List<String> columnNames, SqlExpression body, boolean recursive) {
		withClauses.add(new WithClause(alias, columnNames, body, recursive));
	}

	@Override
	public AQTTransformer.PlainQueryBuilder startSubQuery() {
		return new DefaultSqlQuery(dbContext);
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
