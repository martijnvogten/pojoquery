package org.pojoquery.pipeline;

import java.util.List;

import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Recursive;
import org.pojoquery.pipeline.AbstractQueryTree.AggregateScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.CustomJoin;
import org.pojoquery.pipeline.AbstractQueryTree.CustomQueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.Embedding;
import org.pojoquery.pipeline.AbstractQueryTree.EntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.Join;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKey;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RecursiveCollection;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.STISubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.SubQueryCollection;
import org.pojoquery.pipeline.AbstractQueryTree.SubQueryJoin;
import org.pojoquery.pipeline.AbstractQueryTree.TPSSubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.AbstractQueryTree.ValueCollection;
import org.pojoquery.typemodel.JakartaAnnotations;
import org.pojoquery.typemodel.JavaxAnnotations;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TransformedTypeModel;
import org.pojoquery.typemodel.TypeModel;

public class AQTTransformer {

	public static void toSql(TableNode node, SqlQuery<?> sqlQuery) {
		toSql(node, sqlQuery, false);
	}

	public static void toSql(TableNode node, SqlQuery<?> sqlQuery, boolean useShortFieldAliases) {
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
				toSql((TableNode) stiSubClass, sqlQuery);
			} else if (child instanceof SubQueryJoin subQuery) {
				DefaultSqlQuery subSqlQuery = new DefaultSqlQuery(sqlQuery.getDbContext());
				toSql(subQuery.subQueryTree(), subSqlQuery, true);
				sqlQuery.addSubqueryJoin(subQuery.joinType(), subSqlQuery.toStatement(), subQuery.alias(), subQuery.joinCondition());
				toSql((TableNode) subQuery, sqlQuery);
			} else if (child instanceof Embedding embedded) {
				toSql((TableNode) embedded, sqlQuery);
			} else if (child instanceof SubQueryCollection col) {
				DefaultSqlQuery subSqlQuery = new DefaultSqlQuery(sqlQuery.getDbContext());
				toSql(col.subQueryTree(), subSqlQuery, true);
				sqlQuery.addSubqueryJoin(col.joinType(), subSqlQuery.toStatement(), col.alias(), col.joinCondition());
				toSql((TableNode) col, sqlQuery);
			} else if (child instanceof ValueCollection col) {
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, col.joinTable().schemaName(), col.joinTable().tableName(), col.alias(), col.join().joinCondition());
				sqlQuery.addField(col.expression(), col.alias() + ".value");
			} else if (child instanceof EntityCollection col) {
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, col.tableInfo().schemaName(), col.tableInfo().tableName(), col.alias(),
						col.join().joinCondition());
				toSql((TableNode) col, sqlQuery);
			} else if (child instanceof JoinTableEntityCollection jte) {
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, jte.join().joinTableInfo().tableInfo().schemaName(), jte.join().joinTableInfo().tableInfo().tableName(),
						jte.join().joinTableInfo().joinTableAlias(), jte.join().parentKey().joinCondition());
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, jte.tableInfo().schemaName(), jte.tableInfo().tableName(), jte.alias(),
						jte.join().childKey().joinCondition());
				toSql((TableNode) jte, sqlQuery);
			} else if (child instanceof RecursiveCollection rc) {
				String cteAlias = rc.alias() + "_cte";
				SqlExpression cte = buildRecursiveCteSubquery(rc, sqlQuery.getDbContext(), node.alias());
				sqlQuery.addWithClause(cteAlias, List.of("root_id", "id", "depth"), cte, true);

				SqlExpression cteJoinCondition = SqlExpression.sql(
						"{" + cteAlias + ".root_id} = {" + node.alias() + "."
								+ PojoMetadata.determineIdField(node.type()).getName() + "}");
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, null, cteAlias, cteAlias, cteJoinCondition);

				String elementIdName = PojoMetadata.determineIdField(rc.type()).getName();
				SqlExpression elementJoinCondition = SqlExpression.sql(
						"{" + rc.alias() + "." + elementIdName + "} = {" + cteAlias + ".id}");
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, rc.tableInfo().schemaName(), rc.tableInfo().tableName(),
						rc.alias(), elementJoinCondition);
				toSql((TableNode) rc, sqlQuery);
			} else if (child instanceof TPSSubClassNode subClass) {
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, subClass.join().referringTable().schemaName(), subClass.join().referringTable().tableName(), subClass.alias(),
						subClass.join().joinCondition());
				toSql((TableNode) subClass, sqlQuery);
			} else if (child instanceof Join ref) {
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, ref.join().targetTable().schemaName(), ref.join().targetTable().tableName(), ref.join().targetAlias(),
						ref.join().joinCondition());
				toSql((TableNode) ref, sqlQuery);
			}
		}
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

	private static SqlExpression buildRecursiveCteSubquery(RecursiveCollection rc, DbContext db, String parentAlias) {
		String tableQ = db.quoteObjectNames(rc.tableInfo().tableName());
		String pkColumn = columnNameOf(PojoMetadata.determineIdField(rc.type()));
		String pkQ = db.quoteObjectNames(pkColumn);
		String parentLinkQ = db.quoteObjectNames(rc.parentLinkColumn());

		String rootIdQ = db.quoteObjectNames("root_id");
		String idQ = db.quoteObjectNames("id");
		String depthQ = db.quoteObjectNames("depth");
		String cteAliasQ = db.quoteAlias(rc.alias() + "_cte");

		String anchor;
		String step;
		if (rc.direction() == Recursive.Direction.UP) {
			anchor = "SELECT c." + pkQ + ", c." + parentLinkQ + ", 1"
					+ " FROM " + tableQ + " c WHERE c." + parentLinkQ + " IS NOT NULL";
			step = "SELECT r." + rootIdQ + ", c." + parentLinkQ + ", r." + depthQ + " + 1"
					+ " FROM " + tableQ + " c JOIN " + cteAliasQ + " r ON r." + idQ + " = c." + pkQ
					+ " WHERE c." + parentLinkQ + " IS NOT NULL";
		} else {
			anchor = "SELECT c." + parentLinkQ + ", c." + pkQ + ", 1"
					+ " FROM " + tableQ + " c WHERE c." + parentLinkQ + " IS NOT NULL";
			step = "SELECT r." + rootIdQ + ", c." + pkQ + ", r." + depthQ + " + 1"
					+ " FROM " + tableQ + " c JOIN " + cteAliasQ + " r ON r." + idQ + " = c." + parentLinkQ;
		}

		String sql = anchor + "\n  UNION ALL\n  " + step;
		return SqlExpression.sql(sql);
	}

	private static String columnNameOf(org.pojoquery.typemodel.FieldModel field) {
		String custom = field.getAnnotationAttributeValue(
				org.pojoquery.annotations.FieldName.class, "value", String.class);
		return (custom != null && !custom.isEmpty()) ? custom : field.getName();
	}

}
