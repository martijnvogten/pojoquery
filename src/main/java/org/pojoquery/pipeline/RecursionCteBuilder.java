package org.pojoquery.pipeline;

import java.util.List;
import java.util.function.Supplier;

import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Recursive;
import org.pojoquery.pipeline.AQTTransformer.PlainQueryBuilder;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableJoin;
import org.pojoquery.pipeline.AbstractQueryTree.RecursionInfo;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.SqlQuery.WithClause;

/**
 * Builds the recursive common table expression that populates a
 * {@link Recursive @Recursive} collection.
 *
 * <p>The CTE emits {@code (root_id, id, depth)} tuples mapping each driving row
 * to all elements reachable from it, so consumers can treat it as a junction
 * table: join the element table onto {@code id} and correlate {@code root_id}
 * with the driving row's primary key. Because the CTE erases the difference
 * between adjacency-list ({@code parentLink}) and junction ({@code @Link})
 * recursion, consumers need only one code path.</p>
 *
 * <p>The CTE is uncorrelated with the query that uses it - the anchor selects
 * from the element table without referring to the driving row - so it can be
 * hoisted to the outermost statement and named from inside a derived table.</p>
 *
 * @see AQTTransformer#toSql(TableNode, PlainQueryBuilder, boolean)
 * @see AQTJsonDirectTransformer
 */
public final class RecursionCteBuilder {

	/** Column carrying the driving row's primary key. */
	public static final String ROOT_ID_COLUMN = "root_id";

	/** Column carrying the primary key of a reachable element. */
	public static final String ID_COLUMN = "id";

	/** Column carrying the number of steps taken to reach the element. */
	public static final String DEPTH_COLUMN = "depth";

	private static final List<String> CTE_COLUMNS = List.of(ROOT_ID_COLUMN, ID_COLUMN, DEPTH_COLUMN);

	private RecursionCteBuilder() {
	}

	/** The CTE alias for a recursive collection node. */
	public static String cteAlias(TableNode collection) {
		return collection.alias() + "_cte";
	}

	/** The alias of the deduplicating wrapper around {@link #cteAlias}. */
	public static String distinctCteAlias(TableNode collection) {
		return collection.alias() + "_cte_distinct";
	}

	/**
	 * Builds the {@code (root_id, id, depth)} CTE for a recursive collection.
	 *
	 * @param collection      the collection node carrying the recursion
	 * @param info            the recursion metadata of that node
	 * @param junctionJoin    the many-to-many link table join, or null for
	 *                        parent-link (adjacency list) recursion
	 * @param subQueryFactory creates the builders for the anchor and step queries
	 */
	public static WithClause buildCte(TableNode collection, RecursionInfo info, JoinTableJoin junctionJoin,
			Supplier<PlainQueryBuilder> subQueryFactory) {
		String cteAlias = cteAlias(collection);
		String tbl = collection.tableInfo().tableName();
		String idCol = info.idColumn();

		PlainQueryBuilder anchor = subQueryFactory.get();
		PlainQueryBuilder step = subQueryFactory.get();
		anchor.setTable(collection.tableInfo().schemaName(), tbl);

		if (junctionJoin != null) {
			// Junction-mode: walk through a many-to-many link table.
			String linkTbl = junctionJoin.joinTableInfo().tableInfo().tableName();
			String linkSchema = junctionJoin.joinTableInfo().tableInfo().schemaName();
			String srcCol = junctionJoin.parentKey().fkColumnName();
			String tgtCol = junctionJoin.childKey().fkColumnName();
			boolean down = info.direction() != Recursive.Direction.UP;
			String anchorLinkJoin = down
					? "{link." + srcCol + "} = {" + tbl + "." + idCol + "}"
					: "{link." + tgtCol + "} = {" + tbl + "." + idCol + "}";
			anchor.addJoin(SqlQuery.JoinType.INNER, linkSchema, linkTbl, "link",
					SqlExpression.sql(anchorLinkJoin));
			anchor.addField(SqlExpression.sql("{" + tbl + "." + idCol + "}"), ROOT_ID_COLUMN);
			anchor.addField(SqlExpression.sql(down ? "{link." + tgtCol + "}" : "{link." + srcCol + "}"), ID_COLUMN);
			anchor.addField(SqlExpression.sql("1"), DEPTH_COLUMN);

			step.setTable(linkSchema, linkTbl);
			String stepCteJoin = down
					? "{r.id} = {" + linkTbl + "." + srcCol + "}"
					: "{r.id} = {" + linkTbl + "." + tgtCol + "}";
			step.addJoin(SqlQuery.JoinType.INNER, null, cteAlias, "r", SqlExpression.sql(stepCteJoin));
			step.addField(SqlExpression.sql("{r.root_id}"), ROOT_ID_COLUMN);
			step.addField(SqlExpression.sql(down
					? "{" + linkTbl + "." + tgtCol + "}"
					: "{" + linkTbl + "." + srcCol + "}"), ID_COLUMN);
			step.addField(SqlExpression.sql("{r.depth} + 1"), DEPTH_COLUMN);
		} else {
			String parentCol = info.parentLinkColumn();

			// Anchor subquery
			if (info.direction() == Recursive.Direction.UP) {
				// Anchor row = each child's parent (the first ancestor).
				anchor.addField(SqlExpression.sql("{" + tbl + "." + idCol + "}"), ROOT_ID_COLUMN);
				anchor.addField(SqlExpression.sql("{" + tbl + "." + parentCol + "}"), ID_COLUMN);
				anchor.addWhere(SqlExpression.sql("{" + tbl + "." + parentCol + "} IS NOT NULL"));
			} else {
				// Anchor row = each parent's immediate child (the first descendant).
				// Self-join the table as `child` so that "root_id" is the parent
				// and "id" is its direct child; this excludes the root itself.
				anchor.addJoin(SqlQuery.JoinType.INNER, collection.tableInfo().schemaName(), tbl, "child",
						SqlExpression.sql("{child." + parentCol + "} = {" + tbl + "." + idCol + "}"));
				anchor.addField(SqlExpression.sql("{" + tbl + "." + idCol + "}"), ROOT_ID_COLUMN);
				anchor.addField(SqlExpression.sql("{child." + idCol + "}"), ID_COLUMN);
			}
			anchor.addField(SqlExpression.sql("1"), DEPTH_COLUMN);

			// Step subquery: current element row is the FROM table (alias = table name),
			// previous CTE row is joined as alias `r`.
			step.setTable(collection.tableInfo().schemaName(), tbl);
			SqlExpression joinCond = new SqlExpression(
					org.pojoquery.pipeline.querytree.transforms.ExpressionResolver.resolve(
							info.recursionJoinCondition().getSql(), tbl),
					info.recursionJoinCondition().getParameters());
			step.addJoin(SqlQuery.JoinType.INNER, null, cteAlias, "r", joinCond);
			step.addField(SqlExpression.sql("{r.root_id}"), ROOT_ID_COLUMN);
			if (info.direction() == Recursive.Direction.UP) {
				step.addField(SqlExpression.sql("{" + tbl + "." + parentCol + "}"), ID_COLUMN);
				step.addWhere(SqlExpression.sql("{" + tbl + "." + parentCol + "} IS NOT NULL"));
			} else {
				step.addField(SqlExpression.sql("{" + tbl + "." + idCol + "}"), ID_COLUMN);
			}
			step.addField(SqlExpression.sql("{r.depth} + 1"), DEPTH_COLUMN);
		}

		return new WithClause(cteAlias, CTE_COLUMNS,
				SqlExpression.implode("\nUNION ALL\n", List.of(anchor.toStatement(), step.toStatement())), true);
	}

	/**
	 * Builds a non-recursive CTE selecting the distinct {@code (root_id, id)}
	 * pairs of {@code sourceCteAlias}.
	 *
	 * <p>The recursive CTE emits one tuple per <em>path</em>, so an element that
	 * a graph reaches more than once appears more than once. Consumers that
	 * cannot deduplicate while collecting - the JSON document path, where the
	 * array aggregate takes every row - select from this wrapper instead.</p>
	 */
	public static WithClause buildDistinctPairsCte(DbContext dbContext, String sourceCteAlias, String alias) {
		String sql = SqlQuery.quoteMarkers(dbContext, "SELECT DISTINCT"
				+ "\n {" + sourceCteAlias + "." + ROOT_ID_COLUMN + "},"
				+ "\n {" + sourceCteAlias + "." + ID_COLUMN + "}"
				+ "\nFROM {" + sourceCteAlias + "} AS {" + sourceCteAlias + "}");
		return new WithClause(alias, List.of(ROOT_ID_COLUMN, ID_COLUMN), SqlExpression.sql(sql), false);
	}
}
