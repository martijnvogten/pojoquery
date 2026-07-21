package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.internal.MappingException;
import org.pojoquery.pipeline.AbstractQueryTree.EntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.EntityReference;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableInfo;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableJoin;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableInfo;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;

/**
 * Builds correlated {@code EXISTS} / {@code NOT EXISTS} sub-queries for the
 * {@code whereExists} / {@code whereNotExists} semi-join filters.
 *
 * <p>
 * Unlike a plain {@code addWhere} against a LEFT-joined collection alias (which
 * both filters the root rows <em>and</em> truncates the hydrated collection),
 * the generated sub-query only decides whether a root row is returned. The
 * root's collection is still loaded in full by the outer LEFT JOINs.
 *
 * <p>
 * The sub-query re-uses the same alias names as the outer query tree. SQL
 * scoping makes the inner {@code FROM} aliases shadow the outer LEFT JOIN
 * aliases, so a user condition such as {@code {roles.rolename} = ?} binds to the
 * sub-query's {@code roles} table, while the correlation predicate referencing
 * the root alias (e.g. {@code {user.id}}) binds to the outer query.
 */
public final class ExistsSubqueryBuilder {

	private ExistsSubqueryBuilder() {
	}

	/**
	 * Builds an {@code EXISTS}/{@code NOT EXISTS} condition for the collection
	 * reachable at {@code collectionAlias} in the given query tree.
	 *
	 * @param ctx             the database context used for identifier quoting
	 * @param tree            the root of the query tree
	 * @param collectionAlias the alias of the collection to test for existence
	 * @param userCondition   an optional extra condition applied inside the
	 *                        sub-query (may be {@code null} for a pure existence
	 *                        test)
	 * @param negate          {@code true} to emit {@code NOT EXISTS}, {@code false}
	 *                        for {@code EXISTS}
	 * @return a {@link SqlExpression} carrying the sub-query SQL (with curly
	 *         markers) and the user parameters
	 * @throws MappingException if the alias does not exist in the tree or does not
	 *                          refer to a collection
	 */
	public static SqlExpression buildExists(DbContext ctx, RootNode tree, String collectionAlias,
			SqlExpression userCondition, boolean negate) {
		Map<String, TableNode> byAlias = new HashMap<>();
		collectTableNodes(tree, byAlias);

		TableNode target = byAlias.get(collectionAlias);
		if (target == null) {
			throw new MappingException("Cannot build EXISTS sub-query: no alias '" + collectionAlias
					+ "' found in the query tree of " + tree.type().getSimpleName());
		}
		if (!(target instanceof EntityCollection || target instanceof JoinTableEntityCollection)) {
			throw new MappingException("Cannot build EXISTS sub-query: alias '" + collectionAlias
					+ "' does not refer to a collection");
		}

		// Walk from the target collection up to (but excluding) the root, collecting
		// every intermediate table on the path.
		List<TableNode> path = new ArrayList<>();
		TableNode current = target;
		while (true) {
			path.add(current);
			String parentAlias = parentAliasOf(current);
			if (parentAlias == null) {
				throw new MappingException("Cannot build EXISTS sub-query: node '" + current.alias()
						+ "' has no parent to correlate against");
			}
			if (parentAlias.equals(tree.alias())) {
				break;
			}
			TableNode parent = byAlias.get(parentAlias);
			if (parent == null) {
				throw new MappingException("Cannot build EXISTS sub-query: broken path at alias '" + parentAlias + "'");
			}
			current = parent;
		}

		List<String> fromParts = new ArrayList<>();
		List<SqlExpression> conditions = new ArrayList<>();
		for (TableNode node : path) {
			appendNode(ctx, node, fromParts, conditions);
		}
		if (userCondition != null) {
			conditions.add(userCondition);
		}

		SqlExpression where = SqlExpression.implode(" AND ", conditions);
		String sql = (negate ? "NOT EXISTS (SELECT 1 FROM " : "EXISTS (SELECT 1 FROM ")
				+ String.join(", ", fromParts)
				+ " WHERE " + where.getSql()
				+ ")";
		return new SqlExpression(sql, where.getParameters());
	}

	private static void appendNode(DbContext ctx, TableNode node, List<String> fromParts,
			List<SqlExpression> conditions) {
		if (node instanceof JoinTableEntityCollection jte) {
			JoinTableJoin join = jte.join();
			JoinTableInfo joinTable = join.joinTableInfo();
			fromParts.add(fromPart(ctx, joinTable.tableInfo(), joinTable.joinTableAlias()));
			fromParts.add(fromPart(ctx, jte.tableInfo(), jte.alias()));
			conditions.add(join.parentKey().joinCondition());
			conditions.add(join.childKey().joinCondition());
		} else if (node instanceof EntityCollection ec) {
			fromParts.add(fromPart(ctx, ec.tableInfo(), ec.alias()));
			conditions.add(ec.join().joinCondition());
		} else if (node instanceof EntityReference ref) {
			fromParts.add(fromPart(ctx, ref.tableInfo(), ref.alias()));
			conditions.add(ref.join().joinCondition());
		} else {
			throw new MappingException("Cannot build EXISTS sub-query: node '" + node.alias() + "' of type "
					+ node.getClass().getSimpleName() + " on the path is not a supported join");
		}
	}

	private static String fromPart(DbContext ctx, TableInfo table, String alias) {
		return DB.prefixAndQuoteTableName(ctx, table.schemaName(), table.tableName()) + " AS " + ctx.quoteAlias(alias);
	}

	private static String parentAliasOf(TableNode node) {
		if (node instanceof EntityCollection ec) {
			return ec.parentAlias();
		}
		if (node instanceof JoinTableEntityCollection jte) {
			return jte.parentAlias();
		}
		if (node instanceof EntityReference ref) {
			return ref.parentAlias();
		}
		return null;
	}

	private static void collectTableNodes(TableNode node, Map<String, TableNode> out) {
		if (node.alias() != null) {
			out.put(node.alias(), node);
		}
		List<QueryNode> children = node.children();
		if (children == null) {
			return;
		}
		for (QueryNode child : children) {
			if (child instanceof TableNode tableNode) {
				collectTableNodes(tableNode, out);
			}
		}
	}
}
