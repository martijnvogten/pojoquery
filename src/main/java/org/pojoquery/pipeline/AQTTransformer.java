package org.pojoquery.pipeline;

import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.AbstractQueryTree.AggregateScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.CustomJoin;
import org.pojoquery.pipeline.AbstractQueryTree.CustomQueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.Embedding;
import org.pojoquery.pipeline.AbstractQueryTree.EntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.Join;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKey;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.STISubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.SubQueryCollection;
import org.pojoquery.pipeline.AbstractQueryTree.SubQueryJoin;
import org.pojoquery.pipeline.AbstractQueryTree.TPSSubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.AbstractQueryTree.ValueCollection;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.SqlQuery.WithClause;
import org.pojoquery.typemodel.JakartaAnnotations;
import org.pojoquery.typemodel.JavaxAnnotations;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TransformedTypeModel;
import org.pojoquery.typemodel.TypeModel;

public class AQTTransformer {

	public static void toSql(TableNode node, SqlQuery<?> sqlQuery) {
		toSql(node, sqlQuery, false);
	}

	public interface PlainQueryBuilder {
		void setTable(String schemaName, String tableName);
		void addWithClause(String cteAlias, List<String> of, SqlExpression cte, boolean b);
		void addField(SqlExpression expression, String alias);
		void addJoin(JoinType joinType, String schemaName, String tableName, String alias, SqlExpression joinCondition);
		void addSubqueryJoin(JoinType joinType, SqlExpression statement, String alias, SqlExpression joinCondition);
		void addWhere(SqlExpression where);
		void setGroupBy(List<String> groupBy);
		void setOrderBy(List<String> orderBy);
		PlainQueryBuilder startSubQuery();
		SqlExpression toStatement();
	}

	public static void toSql(TableNode node, PlainQueryBuilder sqlQuery, boolean useShortFieldAliases) {
		if (node instanceof RootNode rootNode) {
			sqlQuery.setTable(node.tableInfo().schemaName(), node.tableInfo().tableName());
			if (rootNode.groupBy() != null) {
				sqlQuery.setGroupBy(rootNode.groupBy());
			}
			if (rootNode.orderBy() != null) {
				sqlQuery.setOrderBy(rootNode.orderBy());
			}
		}
		// sqlQuery.addField(SqlExpression.sql("{" + node.alias() + ".id}")); // select
		// id by default for root
		for (QueryNode child : node.children()) {
			if (child instanceof CustomQueryNode customNode) {
				customNode.applyToSqlQuery(node, sqlQuery);
			} else if (child instanceof CustomJoin customJoin) {
				sqlQuery.addJoin(customJoin.joinType(), customJoin.joinedTable().schemaName(), customJoin.joinedTable().tableName(), customJoin.alias(), customJoin.joinCondition());
			} else if (child instanceof AggregateScalarValue agg) {
				sqlQuery.addField(agg.expression(), useShortFieldAliases ? agg.field().getName() : node.alias() + "." + agg.field().getName());
			} else if (child instanceof ScalarValue scalar) {
				sqlQuery.addField(scalar.expression(), useShortFieldAliases ? scalar.field().getName() : node.alias() + "." + scalar.field().getName());
			} else if (child instanceof PrimaryKey pk) {
				sqlQuery.addField(pk.expression(), useShortFieldAliases ? pk.field().getName() : node.alias() + "." + pk.field().getName());
			} else if (child instanceof STISubClassNode stiSubClass) {
				sqlQuery.addField(SqlExpression.sql("{" + node.alias() + "." + stiSubClass.discriminatorColumn() + "}"), stiSubClass.alias() + "._discriminator");
				toSql((TableNode) stiSubClass, sqlQuery, false);
			} else if (child instanceof SubQueryJoin subQuery) {
				PlainQueryBuilder subSqlQuery = sqlQuery.startSubQuery();
				toSql(subQuery.subQueryTree(), subSqlQuery, true);
				sqlQuery.addSubqueryJoin(subQuery.joinType(), subSqlQuery.toStatement(), subQuery.alias(), subQuery.joinCondition());
				toSql((TableNode) subQuery, sqlQuery, false);
			} else if (child instanceof Embedding embedded) {
				toSql((TableNode) embedded, sqlQuery, false);
			} else if (child instanceof SubQueryCollection col) {
				PlainQueryBuilder subSqlQuery = sqlQuery.startSubQuery();
				toSql(col.subQueryTree(), subSqlQuery, true);
				sqlQuery.addSubqueryJoin(col.joinType(), subSqlQuery.toStatement(), col.alias(), col.joinCondition());
				toSql((TableNode) col, sqlQuery, false);
			} else if (child instanceof ValueCollection col) {
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, col.joinTable().schemaName(), col.joinTable().tableName(), col.alias(), col.join().joinCondition());
				sqlQuery.addField(col.expression(), col.alias() + ".value");
			} else if (child instanceof EntityCollection col) {
				if (col.recursionInfo() == null) {
					sqlQuery.addJoin(SqlQuery.JoinType.LEFT, col.tableInfo().schemaName(), col.tableInfo().tableName(), col.alias(),
							col.join().joinCondition());
					toSql((TableNode) col, sqlQuery, false);
				} else {
					addRecursiveCollection(node, col, col.recursionInfo(), null, sqlQuery);
				}
			} else if (child instanceof JoinTableEntityCollection jte) {
				if (jte.recursionInfo() == null) {
					sqlQuery.addJoin(SqlQuery.JoinType.LEFT, jte.join().joinTableInfo().tableInfo().schemaName(), jte.join().joinTableInfo().tableInfo().tableName(),
							jte.join().joinTableInfo().joinTableAlias(), jte.join().parentKey().joinCondition());
					sqlQuery.addJoin(SqlQuery.JoinType.LEFT, jte.tableInfo().schemaName(), jte.tableInfo().tableName(), jte.alias(),
							jte.join().childKey().joinCondition());
					toSql((TableNode) jte, sqlQuery, false);
				} else {
					addRecursiveCollection(node, jte, jte.recursionInfo(), jte.join(), sqlQuery);
				}
			} else if (child instanceof TPSSubClassNode subClass) {
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, subClass.join().referringTable().schemaName(), subClass.join().referringTable().tableName(), subClass.alias(),
						subClass.join().joinCondition());
				toSql((TableNode) subClass, sqlQuery, false);
			} else if (child instanceof Join ref) {
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, ref.join().targetTable().schemaName(), ref.join().targetTable().tableName(), ref.join().targetAlias(),
						ref.join().joinCondition());
				toSql((TableNode) ref, sqlQuery, false);
			}
		}
	}

	/**
	 * Emits a recursive CTE for a collection node carrying {@link RecursionInfo}
	 * and joins the element table onto it like a junction table.
	 *
	 * @param junctionJoin the many-to-many link table join, or null for
	 *                     parent-link (adjacency list) recursion
	 * @see RecursionCteBuilder
	 */
	private static void addRecursiveCollection(TableNode node, TableNode col, AbstractQueryTree.RecursionInfo info,
			AbstractQueryTree.JoinTableJoin junctionJoin, PlainQueryBuilder sqlQuery) {
		WithClause cte = RecursionCteBuilder.buildCte(col, info, junctionJoin, sqlQuery::startSubQuery);
		sqlQuery.addWithClause(cte.alias, cte.columnNames, cte.body, cte.recursive);

		String cteAlias = cte.alias;
		SqlExpression cteJoinCondition = SqlExpression.sql(
				"{" + cteAlias + "." + RecursionCteBuilder.ROOT_ID_COLUMN + "} = {" + node.alias() + "."
						+ PojoMetadata.determineIdField(node.type()).getName() + "}");
		sqlQuery.addJoin(SqlQuery.JoinType.LEFT, null, cteAlias, cteAlias, cteJoinCondition);

		String elementIdName = PojoMetadata.determineIdField(col.type()).getName();
		SqlExpression elementJoinCondition = SqlExpression.sql(
				"{" + col.alias() + "." + elementIdName + "} = {" + cteAlias + "."
						+ RecursionCteBuilder.ID_COLUMN + "}");
		sqlQuery.addJoin(SqlQuery.JoinType.LEFT, col.tableInfo().schemaName(), col.tableInfo().tableName(),
				col.alias(), elementJoinCondition);
		toSql(col, sqlQuery, false);
	}

	public static RootNode buildQueryTreeForType(Class<?> clz) {
		return buildQueryTreeForType(new ReflectionTypeModel(clz));
	}

	public static RootNode buildQueryTreeForType(TypeModel rootType) {
		return buildQueryTreeForType(rootType, TransformPipeline.defaultPipeline());
	}
	
	public static RootNode buildQueryTreeForType(TypeModel rootType, TransformPipeline pipeline) {
		// Wrap the root type with transformers so annotation queries are transparently transformed
		TypeModel transformedType = new TransformedTypeModel(rootType, List.of(new JakartaAnnotations(), new JavaxAnnotations()));
		RootNode initialTree = RootNode.createEmptyRootNode(transformedType);
		return pipeline.apply(initialTree);
	}
}
