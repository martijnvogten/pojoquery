package org.pojoquery.pipeline;

import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.JoinTableInfo;
import org.pojoquery.pipeline.querytree.LinkedValueNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;

/**
 * Applies a QueryTree structure to a SqlQuery.
 */
public class SQLQueryFromTree {

	/**
	 * Applies a QueryTree to the SqlQuery.
	 */
	public static void applyQueryTreeToQuery(SqlQuery<?> query, QueryTree tree) {
		QueryNode root = tree.root();
		
		if (root instanceof TableNode tableNode) {
			// Set table
			query.setTable(tableNode.tableInfo().schemaName(), tableNode.tableInfo().tableName());
			
			// Add fields from root table
			for (FieldSelection field : tableNode.resolvedFields()) {
				query.addField(field.expression(), field.alias());
			}
			
			// Recursively add joins
			addJoinsFromNode(query, tableNode);
		}
		
		// Add group by
		for (String groupBy : tree.groupBy()) {
			query.addGroupBy(groupBy);
		}
		
		// Add order by
		for (String orderBy : tree.orderBy()) {
			query.addOrderBy(orderBy);
		}
		
		// Add where conditions
		for (SqlExpression where : tree.wheres()) {
			query.addWhere(where);
		}
	}
	
	/**
	 * Recursively adds joins from a TableNode to the query.
	 */
	private static void addJoinsFromNode(SqlQuery<?> query, TableNode tableNode) {
		for (QueryNode child : tableNode.children()) {
			JoinInfo joinInfo = child.joinInfo();
			
			if (child instanceof TableNode childTable) {
				// Add the join
				if (joinInfo != null) {

					if (joinInfo.subquery() != null) {
						DefaultSqlQuery subQuery = new DefaultSqlQuery(query.getDbContext());
						applyQueryTreeToQuery(subQuery, joinInfo.subquery());
						SqlExpression condition = joinInfo.toSqlCondition(tableNode.alias(), child.alias());
						query.addSubqueryJoin(joinInfo.joinType(), subQuery.toStatement(), child.alias(), condition);
						continue;
					}

					// Many to many
					if (joinInfo.joinTableInfo() != null) {
						JoinTableInfo joinTableInfo = joinInfo.joinTableInfo();
						// add join to join table
						query.addJoin(JoinType.LEFT, joinTableInfo.joinTable().schemaName(), joinTableInfo.joinTable().tableName(), 
							joinTableInfo.joinTableAlias(), joinTableInfo.parentCondition(tableNode.alias()));
						// add join to target table
						query.addJoin(JoinType.LEFT, joinInfo.childTable().schemaName(), joinInfo.childTable().tableName(), 
							child.alias(), joinTableInfo.targetCondition(child.alias()));
					} else {
						SqlExpression condition = joinInfo.toSqlCondition(tableNode.alias(), child.alias());
						query.addJoin(joinInfo.joinType(), joinInfo.childTable().schemaName(), joinInfo.childTable().tableName(), 
							child.alias(), condition);
					}
				}
				
				// Add fields from the joined table
				for (FieldSelection field : childTable.resolvedFields()) {
					query.addField(field.expression(), field.alias());
				}
				
				// Recursively add nested joins
				addJoinsFromNode(query, childTable);
			} else if (child instanceof LinkedValueNode valueNode) {
				// Handle value collections (e.g., @Link(fetchColumn))
				if (joinInfo != null) {
					SqlExpression condition = joinInfo.toSqlCondition(tableNode.alias(), valueNode.alias());
					query.addJoin(joinInfo.joinType(), valueNode.linkTableSchema(), valueNode.linkTableName(),
						valueNode.alias(), condition);
				}
				
				// Add the fetch column as a field
				String fieldExpr = query.getDbContext().quoteObjectNames(valueNode.alias(), valueNode.fetchColumn());
				query.addField(SqlExpression.sql(fieldExpr), valueNode.alias() + ".value");
			}
		}
	}
}
