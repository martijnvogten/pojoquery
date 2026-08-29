package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.AbstractQueryTree.TableInfo;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.querytree.transforms.ExpressionResolver;
import org.pojoquery.util.Strings;

/**
 * Builds a SELECT statement that returns each root entity as a single JSON
 * document, using the database's native JSON functions (JSON_OBJECT /
 * JSON_ARRAYAGG or their dialect equivalents) instead of flat columns.
 *
 * <p>Collections are pushed into derived-table joins that group by the foreign
 * key, so the outer query stays at one row per root entity. Every (sub)query
 * emits its JSON document in a single column aliased {@link #JSON_COLUMN};
 * subqueries additionally expose their join key as a plain column.</p>
 *
 * <p>All dialect-specific JSON syntax is delegated to the {@link DbContext}
 * JSON methods. Field expressions, join conditions and clauses may contain
 * {@code {alias.column}} markers, which are resolved when the statement is
 * built; in WHERE, GROUP BY and ORDER BY clauses, {@code {this.column}} resolves
 * to this statement's own table alias.</p>
 *
 * @see AQTJsonDirectTransformer
 */
public class JsonSqlQuery {

	/** Column alias under which every (sub)query emits its JSON document. */
	public static final String JSON_COLUMN = "json";

	public static record SelectField(SqlExpression expression, String alias) {
	}

	public sealed interface JsonJoin permits TableJoin, SubQueryJoin {
		JoinType type();

		String alias();

		SqlExpression joinCondition();
	}

	public static record TableJoin(JoinType type, TableInfo table, String alias, SqlExpression joinCondition)
			implements JsonJoin {
	}

	/**
	 * @param lateral whether the subquery is correlated with the rows of the
	 *                statement it is joined to, and so must be introduced with
	 *                {@code LATERAL}
	 */
	public static record SubQueryJoin(JoinType type, SqlExpression subquery, String alias,
			SqlExpression joinCondition, boolean lateral) implements JsonJoin {
	}

	private final DbContext dbContext;
	private final DocumentShape documentShape;
	private final JsonSqlQuery parent;

	private TableInfo table;
	private SqlExpression tableSubQuery;
	private String tableAlias;
	private SqlExpression jsonValue;
	private boolean aggregated;
	private final List<SelectField> selectFields = new ArrayList<>();
	private final List<JsonJoin> joins = new ArrayList<>();
	private List<String> groupBy = new ArrayList<>();
	private final List<SqlExpression> havings = new ArrayList<>();
	private List<String> orderBy = new ArrayList<>();
	private final List<SqlExpression> wheres = new ArrayList<>();
	private final List<SqlQuery.WithClause> withClauses = new ArrayList<>();
	private final Map<String, String> columnMarkers = new HashMap<>();
	private int offset = -1;
	private int rowCount = -1;

	public JsonSqlQuery(DbContext dbContext) {
		this(dbContext, DocumentShape.OBJECT);
	}

	public JsonSqlQuery(DbContext dbContext, DocumentShape documentShape) {
		this(dbContext, documentShape, null);
	}

	private JsonSqlQuery(DbContext dbContext, DocumentShape documentShape, JsonSqlQuery parent) {
		this.dbContext = dbContext;
		this.documentShape = documentShape;
		this.parent = parent;
	}

	public JsonSqlQuery startSubQuery() {
		return new JsonSqlQuery(dbContext, documentShape, this);
	}

	/** The shape every document of this statement is assembled in. */
	public DocumentShape getDocumentShape() {
		return documentShape;
	}

	public DbContext getDbContext() {
		return dbContext;
	}

	public void setTable(TableInfo table, String alias) {
		this.table = table;
		this.tableAlias = alias;
	}

	/**
	 * Selects from a derived table rather than a named one.
	 *
	 * <p>Used where the rows to aggregate into a document are themselves the
	 * result of a query - an aggregate projection, whose columns exist only in
	 * its own select list.</p>
	 *
	 * @param subQuery the statement producing the rows
	 * @param alias    the alias to expose it under
	 */
	public void setTable(SqlExpression subQuery, String alias) {
		this.tableSubQuery = subQuery;
		this.tableAlias = alias;
	}

	/**
	 * Sets the expression producing the JSON document for a single row.
	 * When {@link #setAggregated(boolean) aggregated}, rows are combined into a
	 * JSON array with the dialect's array aggregate.
	 */
	public void setJsonValue(SqlExpression jsonValue) {
		this.jsonValue = jsonValue;
	}

	public void setAggregated(boolean aggregated) {
		this.aggregated = aggregated;
	}

	/** Adds a plain (non-JSON) select field, e.g. the join key of a subquery. */
	public void addField(SqlExpression expression, String alias) {
		selectFields.add(new SelectField(expression, alias));
	}

	/**
	 * Records that {@code {alias.fieldName}} addresses the given column
	 * expression.
	 *
	 * <p>A flat query resolves such a marker from its own select list, whose
	 * labels are exactly {@code alias.fieldName}. This statement selects a single
	 * JSON document instead, so the columns it reads never appear as select
	 * labels and the mapping has to be recorded as the document is assembled.
	 * Without it, a marker naming a Java field is emitted verbatim as an
	 * identifier - wrong for every field whose column has a different name, see
	 * {@link org.pojoquery.annotations.FieldName}.</p>
	 *
	 * @param alias      the table alias the field belongs to
	 * @param fieldName  the Java field name
	 * @param expression the expression selecting the field's column
	 */
	public void addColumnMarker(String alias, String fieldName, SqlExpression expression) {
		columnMarkers.put(alias + "." + fieldName, expression.getSql());
	}

	public void addTableJoin(JoinType type, TableInfo table, String alias, SqlExpression joinCondition) {
		joins.add(new TableJoin(type, table, alias, joinCondition));
	}

	public void addSubQueryJoin(JoinType type, SqlExpression subquery, String alias, SqlExpression joinCondition) {
		joins.add(new SubQueryJoin(type, subquery, alias, joinCondition, false));
	}

	/**
	 * Joins a subquery that is correlated with this statement's rows.
	 *
	 * <p>It needs no join condition: an aggregate without {@code GROUP BY}
	 * produces exactly one row even over an empty correlated set, so the join
	 * matches every parent row and the correlation lives in the subquery's own
	 * WHERE. A parent with no elements gets a NULL aggregate, which the caller
	 * coalesces to an empty array exactly as it does for an unmatched grouped
	 * subquery.</p>
	 *
	 * @param type     the join type
	 * @param subquery the correlated subquery
	 * @param alias    the alias to expose it under
	 */
	public void addLateralSubQueryJoin(JoinType type, SqlExpression subquery, String alias) {
		joins.add(new SubQueryJoin(type, subquery, alias, SqlExpression.sql("TRUE"), true));
	}

	public void addWhere(SqlExpression where) {
		wheres.add(where);
	}

	/**
	 * Adds a common table expression to this statement.
	 *
	 * <p>Clauses added to a subquery are hoisted to the outermost statement, so a
	 * derived table can name a CTE while the {@code WITH} preamble stays where
	 * every dialect accepts it.</p>
	 */
	public void addWithClause(String alias, List<String> columnNames, SqlExpression body, boolean recursive) {
		if (parent != null) {
			parent.addWithClause(alias, columnNames, body, recursive);
			return;
		}
		withClauses.add(new SqlQuery.WithClause(alias, columnNames, body, recursive));
	}

	public void setGroupBy(List<String> groupBy) {
		this.groupBy = new ArrayList<>(groupBy);
	}

	/**
	 * Adds a HAVING condition.
	 *
	 * <p>Useful without a GROUP BY as well: an aggregate query returns one row
	 * over an empty set, and a HAVING is the portable way to suppress it.</p>
	 */
	public void addHaving(SqlExpression having) {
		havings.add(having);
	}

	public void setOrderBy(List<String> orderBy) {
		this.orderBy = new ArrayList<>(orderBy);
	}

	public void setLimit(int offset, int rowCount) {
		this.offset = offset;
		this.rowCount = rowCount;
	}

	public SqlExpression toStatement() {
		if (table == null && tableSubQuery == null) {
			throw new IllegalStateException("No table set");
		}
		if (jsonValue == null) {
			throw new IllegalStateException("No JSON value expression set");
		}
		List<SqlExpression> parts = new ArrayList<>();

		List<SqlExpression> selectParts = new ArrayList<>();
		for (SelectField field : selectFields) {
			selectParts.add(SqlExpression.implode("", List.of(
					field.expression(),
					SqlExpression.sql(" AS " + dbContext.quoteAlias(field.alias())))));
		}
		SqlExpression jsonExpression = aggregated ? dbContext.jsonArrayAgg(jsonValue) : jsonValue;
		selectParts.add(SqlExpression.implode("", List.of(
				jsonExpression,
				SqlExpression.sql(" AS " + dbContext.quoteAlias(JSON_COLUMN)))));

		if (!withClauses.isEmpty()) {
			parts.add(SqlQuery.buildWithPreamble(dbContext, withClauses));
		}
		parts.add(SqlExpression.sql("SELECT\n "));
		parts.add(SqlExpression.implode(",\n ", selectParts));
		if (tableSubQuery != null) {
			parts.add(SqlExpression.sql("\nFROM (\n"));
			parts.add(tableSubQuery);
			parts.add(SqlExpression.sql("\n) AS " + dbContext.quoteAlias(tableAlias)));
		} else {
			parts.add(SqlExpression.sql("\nFROM " + quoteTableName(table) + " AS " + dbContext.quoteAlias(tableAlias)));
		}

		for (JsonJoin join : joins) {
			List<SqlExpression> joinParts = new ArrayList<>();
			joinParts.add(SqlExpression.sql("\n" + join.type().name() + " JOIN "));
			if (join instanceof SubQueryJoin subQueryJoin) {
				joinParts.add(SqlExpression.sql(subQueryJoin.lateral() ? "LATERAL (\n" : "(\n"));
				joinParts.add(subQueryJoin.subquery());
				joinParts.add(SqlExpression.sql("\n)"));
			} else if (join instanceof TableJoin tableJoin) {
				joinParts.add(SqlExpression.sql(quoteTableName(tableJoin.table())));
			}
			joinParts.add(SqlExpression.sql(" AS " + dbContext.quoteAlias(join.alias()) + " ON "));
			joinParts.add(join.joinCondition());
			parts.add(SqlExpression.implode("", joinParts));
		}

		if (!wheres.isEmpty()) {
			parts.add(SqlExpression.sql("\nWHERE "));
			parts.add(SqlExpression.implode("\n AND ", wheres.stream().map(this::resolveThisAlias).toList()));
		}
		if (!groupBy.isEmpty()) {
			parts.add(SqlExpression.sql("\nGROUP BY " + Strings.implode(", ", resolveThisAlias(groupBy))));
		}
		if (!havings.isEmpty()) {
			parts.add(SqlExpression.sql("\nHAVING "));
			parts.add(SqlExpression.implode("\n AND ", havings.stream().map(this::resolveThisAlias).toList()));
		}
		if (!orderBy.isEmpty()) {
			parts.add(SqlExpression.sql("\nORDER BY " + Strings.implode(", ", resolveThisAlias(orderBy))));
		}
		String limitClause = SqlQuery.buildLimitClause(offset, rowCount);
		if (!limitClause.isEmpty()) {
			parts.add(SqlExpression.sql(limitClause));
		}

		SqlExpression statement = SqlExpression.implode("", parts);
		// Field-name markers first, then quoting: joins and clauses may address a
		// column by its Java field name here, just as they may in a flat query.
		String resolved = SqlQuery.resolveFieldAliases(statement.getSql(), columnMarkers);
		return new SqlExpression(SqlQuery.quoteMarkers(dbContext, resolved), statement.getParameters());
	}

	/**
	 * Resolves {@code {this}} and {@code {this.column}} markers in a clause to
	 * this statement's own table alias, as the flat query builder does.
	 */
	private SqlExpression resolveThisAlias(SqlExpression clause) {
		return new SqlExpression(ExpressionResolver.resolve(clause.getSql(), tableAlias), clause.getParameters());
	}

	private List<String> resolveThisAlias(List<String> clauses) {
		return clauses.stream().map(clause -> ExpressionResolver.resolve(clause, tableAlias)).toList();
	}

	private String quoteTableName(TableInfo table) {
		return table.schemaName() != null && !table.schemaName().isEmpty()
				? dbContext.quoteObjectNames(table.schemaName(), table.tableName())
				: dbContext.quoteObjectNames(table.tableName());
	}
}
