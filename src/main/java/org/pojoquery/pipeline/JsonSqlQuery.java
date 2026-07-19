package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.AbstractQueryTree.TableInfo;
import org.pojoquery.pipeline.SqlQuery.JoinType;
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
 * built.</p>
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

	public static record SubQueryJoin(JoinType type, SqlExpression subquery, String alias, SqlExpression joinCondition)
			implements JsonJoin {
	}

	private final DbContext dbContext;

	private TableInfo table;
	private String tableAlias;
	private SqlExpression jsonValue;
	private boolean aggregated;
	private final List<SelectField> selectFields = new ArrayList<>();
	private final List<JsonJoin> joins = new ArrayList<>();
	private List<String> groupBy = new ArrayList<>();
	private List<String> orderBy = new ArrayList<>();
	private final List<SqlExpression> wheres = new ArrayList<>();
	private int offset = -1;
	private int rowCount = -1;

	public JsonSqlQuery(DbContext dbContext) {
		this.dbContext = dbContext;
	}

	public JsonSqlQuery startSubQuery() {
		return new JsonSqlQuery(dbContext);
	}

	public DbContext getDbContext() {
		return dbContext;
	}

	public void setTable(TableInfo table, String alias) {
		this.table = table;
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

	public void addTableJoin(JoinType type, TableInfo table, String alias, SqlExpression joinCondition) {
		joins.add(new TableJoin(type, table, alias, joinCondition));
	}

	public void addSubQueryJoin(JoinType type, SqlExpression subquery, String alias, SqlExpression joinCondition) {
		joins.add(new SubQueryJoin(type, subquery, alias, joinCondition));
	}

	public void addWhere(SqlExpression where) {
		wheres.add(where);
	}

	public void setGroupBy(List<String> groupBy) {
		this.groupBy = new ArrayList<>(groupBy);
	}

	public void setOrderBy(List<String> orderBy) {
		this.orderBy = new ArrayList<>(orderBy);
	}

	public void setLimit(int offset, int rowCount) {
		this.offset = offset;
		this.rowCount = rowCount;
	}

	public SqlExpression toStatement() {
		if (table == null) {
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

		parts.add(SqlExpression.sql("SELECT\n "));
		parts.add(SqlExpression.implode(",\n ", selectParts));
		parts.add(SqlExpression.sql("\nFROM " + quoteTableName(table) + " AS " + dbContext.quoteAlias(tableAlias)));

		for (JsonJoin join : joins) {
			List<SqlExpression> joinParts = new ArrayList<>();
			joinParts.add(SqlExpression.sql("\n" + join.type().name() + " JOIN "));
			if (join instanceof SubQueryJoin subQueryJoin) {
				joinParts.add(SqlExpression.sql("(\n"));
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
			parts.add(SqlExpression.implode("\n AND ", wheres));
		}
		if (!groupBy.isEmpty()) {
			parts.add(SqlExpression.sql("\nGROUP BY " + Strings.implode(", ", groupBy)));
		}
		if (!orderBy.isEmpty()) {
			parts.add(SqlExpression.sql("\nORDER BY " + Strings.implode(", ", orderBy)));
		}
		String limitClause = SqlQuery.buildLimitClause(offset, rowCount);
		if (!limitClause.isEmpty()) {
			parts.add(SqlExpression.sql(limitClause));
		}

		SqlExpression statement = SqlExpression.implode("", parts);
		return new SqlExpression(SqlQuery.quoteMarkers(dbContext, statement.getSql()), statement.getParameters());
	}

	private String quoteTableName(TableInfo table) {
		return table.schemaName() != null && !table.schemaName().isEmpty()
				? dbContext.quoteObjectNames(table.schemaName(), table.tableName())
				: dbContext.quoteObjectNames(table.tableName());
	}
}
