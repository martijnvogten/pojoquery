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
import org.pojoquery.pipeline.AbstractQueryTree.SubQueryJoin;
import org.pojoquery.pipeline.AbstractQueryTree.TPSSubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.AbstractQueryTree.ValueCollection;
import org.pojoquery.pipeline.SqlQuery.SqlField;
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

}
