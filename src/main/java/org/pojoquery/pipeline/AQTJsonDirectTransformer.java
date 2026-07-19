package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.DbContext;
import org.pojoquery.DbContext.JsonProperty;
import org.pojoquery.DbContext.JsonVariant;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.AbstractQueryTree.AggregateScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.CustomJoin;
import org.pojoquery.pipeline.AbstractQueryTree.EmbeddedEntity;
import org.pojoquery.pipeline.AbstractQueryTree.EntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.EntityReference;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKey;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.STISubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.STISuperClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.TPSSubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TPSSuperClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.AbstractQueryTree.ValueCollection;
import org.pojoquery.pipeline.SqlQuery.JoinType;

/**
 * Transforms an Abstract Query Tree into a {@link JsonSqlQuery} that returns
 * each root entity as a single JSON document, built by the database itself.
 *
 * <p>This is the JSON counterpart of {@link AQTTransformer#toSql}. Scalar
 * fields become JSON object properties, references and embedded entities
 * become nested objects, and collections become nested arrays. Collections are
 * pushed into derived-table joins that group by the foreign key, so the outer
 * query stays at one row per root entity and multiple collections don't
 * multiply into a cross product.</p>
 *
 * <p>Inheritance is supported for both strategies: subclass-specific fields
 * are only present in the JSON output for rows of that subclass, and a
 * {@code _type} property carries the concrete type's simple name.</p>
 *
 * <p>Not yet supported (throws {@link UnsupportedOperationException}):
 * recursive ({@code @Recursive}) collections, subquery joins, custom query
 * nodes, and non-scalar fields declared on subclasses.</p>
 */
public class AQTJsonDirectTransformer {

	/** Property name carrying the concrete type of a polymorphic entity. */
	public static final String TYPE_PROPERTY = "_type";

	private record SubClassVariant(SqlExpression condition, String typeName, List<JsonProperty> properties) {
	}

	public static void toSql(RootNode tree, JsonSqlQuery query) {
		query.setTable(tree.tableInfo(), tree.alias());
		if (tree.groupBy() != null) {
			query.setGroupBy(tree.groupBy());
		}
		if (tree.orderBy() != null) {
			query.setOrderBy(tree.orderBy());
		}
		query.setJsonValue(buildJsonValue(tree, query));
	}

	/**
	 * Builds the JSON object expression for one row of {@code node}, adding any
	 * joins the object's properties need to {@code query}.
	 */
	private static SqlExpression buildJsonValue(TableNode node, JsonSqlQuery query) {
		List<JsonProperty> properties = new ArrayList<>();
		List<SubClassVariant> variants = new ArrayList<>();
		processChildren(node, query, properties, variants);
		DbContext context = query.getDbContext();
		if (variants.isEmpty()) {
			return context.jsonObject(properties);
		}
		properties.add(new JsonProperty(TYPE_PROPERTY, buildTypeCase(context, variants, node.type().getSimpleName())));
		return context.jsonObjectWithVariants(properties,
				variants.stream().map(v -> new JsonVariant(v.condition(), v.properties())).toList());
	}

	private static void processChildren(TableNode node, JsonSqlQuery query, List<JsonProperty> properties,
			List<SubClassVariant> variants) {
		for (QueryNode child : node.children()) {
			if (child instanceof PrimaryKey pk) {
				properties.add(new JsonProperty(pk.field().getName(), pk.expression()));
			} else if (child instanceof AggregateScalarValue agg) {
				properties.add(new JsonProperty(agg.field().getName(), agg.expression()));
			} else if (child instanceof ScalarValue scalar) {
				properties.add(new JsonProperty(scalar.field().getName(), scalar.expression()));
			} else if (child instanceof EmbeddedEntity embedded) {
				// Embedded fields live in the parent's table: a nested object, no join.
				properties.add(new JsonProperty(embedded.field().getName(), buildJsonValue(embedded, query)));
			} else if (child instanceof TPSSuperClassNode superClass) {
				// Inherited fields from the superclass table are plain properties.
				query.addTableJoin(JoinType.LEFT, superClass.tableInfo(), superClass.alias(),
						superClass.join().joinCondition());
				processChildren(superClass, query, properties, variants);
			} else if (child instanceof STISuperClassNode superClass) {
				// Same table as the subclass; fields are plain properties.
				processChildren(superClass, query, properties, variants);
			} else if (child instanceof TPSSubClassNode tps) {
				// LEFT JOIN the subclass table; a row is of this subclass when its
				// primary key in that table is non-null.
				query.addTableJoin(JoinType.LEFT, tps.tableInfo(), tps.alias(), tps.join().joinCondition());
				List<JsonProperty> extras = new ArrayList<>();
				SqlExpression pkExpression = collectSubClassProperties(tps, extras);
				if (pkExpression == null) {
					throw new IllegalStateException("Subclass table node '" + tps.alias() + "' has no primary key");
				}
				SqlExpression condition = SqlExpression.implode("",
						List.of(pkExpression, SqlExpression.sql(" IS NOT NULL")));
				variants.add(new SubClassVariant(condition, tps.type().getSimpleName(), extras));
			} else if (child instanceof STISubClassNode sti) {
				List<JsonProperty> extras = new ArrayList<>();
				collectSubClassProperties(sti, extras);
				SqlExpression condition = SqlExpression.sql(
						"{" + sti.sourceAlias() + "." + sti.discriminatorColumn() + "} = '"
								+ DbContext.escapeSqlStringLiteral(String.valueOf(sti.discriminatorValue())) + "'");
				variants.add(new SubClassVariant(condition, sti.type().getSimpleName(), extras));
			} else if (child instanceof EntityReference reference) {
				addEntityReference(reference, query, properties);
			} else if (child instanceof ValueCollection collection) {
				addValueCollection(collection, query, properties);
			} else if (child instanceof EntityCollection collection) {
				if (collection.recursionInfo() != null) {
					throw unsupported(collection, "@Recursive collections");
				}
				addEntityCollection(collection, query, properties);
			} else if (child instanceof JoinTableEntityCollection collection) {
				if (collection.recursionInfo() != null) {
					throw unsupported(collection, "@Recursive collections");
				}
				addJoinTableEntityCollection(node, collection, query, properties);
			} else if (child instanceof CustomJoin customJoin) {
				query.addTableJoin(customJoin.joinType(), customJoin.joinedTable(), customJoin.alias(),
						customJoin.joinCondition());
			} else {
				throw unsupported(child, child.getClass().getSimpleName() + " nodes");
			}
		}
	}

	/**
	 * A many-to-one reference becomes a derived table producing one JSON object
	 * per target row, keyed by the target's id column. Joining on the original
	 * join condition yields the nested object, or JSON {@code null} when the
	 * reference is null.
	 */
	private static void addEntityReference(EntityReference reference, JsonSqlQuery query,
			List<JsonProperty> properties) {
		JsonSqlQuery subQuery = query.startSubQuery();
		subQuery.setTable(reference.tableInfo(), reference.alias());
		String idColumn = reference.join().idColumnName();
		subQuery.addField(SqlExpression.sql("{" + reference.alias() + "." + idColumn + "}"), idColumn);
		subQuery.setJsonValue(buildJsonValue(reference, subQuery));

		query.addSubQueryJoin(JoinType.LEFT, subQuery.toStatement(), reference.alias(),
				reference.join().joinCondition());
		properties.add(new JsonProperty(reference.field().getName(),
				query.getDbContext().jsonValueRef(
						SqlExpression.sql("{" + reference.alias() + "." + JsonSqlQuery.JSON_COLUMN + "}"))));
	}

	/**
	 * One-to-many: the foreign key lives in the child table. Wrap the child in a
	 * subquery that groups by the FK so the outer query stays at one row per
	 * parent.
	 */
	private static void addEntityCollection(EntityCollection collection, JsonSqlQuery query,
			List<JsonProperty> properties) {
		JsonSqlQuery subQuery = query.startSubQuery();
		subQuery.setTable(collection.tableInfo(), collection.alias());
		subQuery.setAggregated(true);

		String fkColumn = collection.join().fkColumnName();
		String fkExpression = "{" + collection.alias() + "." + fkColumn + "}";
		subQuery.addField(SqlExpression.sql(fkExpression), fkColumn);
		subQuery.setGroupBy(List.of(fkExpression));
		subQuery.setJsonValue(buildJsonValue(collection, subQuery));

		query.addSubQueryJoin(JoinType.LEFT, subQuery.toStatement(), collection.alias(),
				collection.join().joinCondition());
		properties.add(new JsonProperty(collection.field().getName(),
				coalesceEmptyArray(query.getDbContext(), collection.alias())));
	}

	/**
	 * Many-to-many: push the junction table into the subquery so the outer query
	 * stays at one row per parent. Otherwise the outer array aggregate would emit
	 * one element per row in the parent x junction fan-out, duplicating the
	 * parent.
	 */
	private static void addJoinTableEntityCollection(TableNode parent, JoinTableEntityCollection collection,
			JsonSqlQuery query, List<JsonProperty> properties) {
		JsonSqlQuery subQuery = query.startSubQuery();
		subQuery.setAggregated(true);

		String junctionAlias = collection.join().joinTableInfo().joinTableAlias();
		String parentFkColumn = collection.join().parentKey().fkColumnName();
		String parentIdColumn = collection.join().parentKey().idColumnName();

		subQuery.setTable(collection.join().joinTableInfo().tableInfo(), junctionAlias);
		subQuery.addTableJoin(JoinType.LEFT, collection.join().childKey().targetTable(), collection.alias(),
				collection.join().childKey().joinCondition());
		String fkExpression = "{" + junctionAlias + "." + parentFkColumn + "}";
		subQuery.addField(SqlExpression.sql(fkExpression), parentFkColumn);
		subQuery.setGroupBy(List.of(fkExpression));
		subQuery.setJsonValue(buildJsonValue(collection, subQuery));

		SqlExpression onCondition = SqlExpression.sql(
				"{" + collection.alias() + "." + parentFkColumn + "} = {" + parent.alias() + "." + parentIdColumn + "}");
		query.addSubQueryJoin(JoinType.LEFT, subQuery.toStatement(), collection.alias(), onCondition);
		properties.add(new JsonProperty(collection.field().getName(),
				coalesceEmptyArray(query.getDbContext(), collection.alias())));
	}

	/**
	 * A collection of scalar values becomes a subquery aggregating the value
	 * expression into a JSON array, grouped by the foreign key.
	 */
	private static void addValueCollection(ValueCollection collection, JsonSqlQuery query,
			List<JsonProperty> properties) {
		JsonSqlQuery subQuery = query.startSubQuery();
		subQuery.setTable(collection.joinTable(), collection.alias());
		subQuery.setAggregated(true);

		String fkColumn = collection.join().fkColumnName();
		String fkExpression = "{" + collection.alias() + "." + fkColumn + "}";
		subQuery.addField(SqlExpression.sql(fkExpression), fkColumn);
		subQuery.setGroupBy(List.of(fkExpression));
		subQuery.setJsonValue(collection.expression());

		query.addSubQueryJoin(JoinType.LEFT, subQuery.toStatement(), collection.alias(),
				collection.join().joinCondition());
		properties.add(new JsonProperty(collection.field().getName(),
				coalesceEmptyArray(query.getDbContext(), collection.alias())));
	}

	/**
	 * Wraps a subquery's JSON column in COALESCE so parents without collection
	 * elements get {@code []} rather than {@code null}.
	 */
	private static SqlExpression coalesceEmptyArray(DbContext context, String subQueryAlias) {
		return context.jsonValueRef(SqlExpression.implode("", List.of(
				SqlExpression.sql("COALESCE({" + subQueryAlias + "." + JsonSqlQuery.JSON_COLUMN + "}, "),
				context.emptyJsonArray(),
				SqlExpression.sql(")"))));
	}

	/**
	 * Walks a subclass node's scalar children and appends them to
	 * {@code properties}. Returns the subclass primary key expression, used as
	 * the discriminator for table-per-subclass inheritance.
	 */
	private static SqlExpression collectSubClassProperties(TableNode subClassNode, List<JsonProperty> properties) {
		SqlExpression pkExpression = null;
		for (QueryNode child : subClassNode.children()) {
			if (child instanceof PrimaryKey pk) {
				pkExpression = pk.expression();
			} else if (child instanceof AggregateScalarValue agg) {
				properties.add(new JsonProperty(agg.field().getName(), agg.expression()));
			} else if (child instanceof ScalarValue scalar) {
				properties.add(new JsonProperty(scalar.field().getName(), scalar.expression()));
			} else {
				throw unsupported(child, "non-scalar fields on subclasses");
			}
		}
		return pkExpression;
	}

	private static SqlExpression buildTypeCase(DbContext context, List<SubClassVariant> variants,
			String defaultTypeName) {
		List<SqlExpression> parts = new ArrayList<>();
		parts.add(SqlExpression.sql("CASE"));
		for (SubClassVariant variant : variants) {
			parts.add(SqlExpression.implode("", List.of(
					SqlExpression.sql(" WHEN "),
					variant.condition(),
					SqlExpression.sql(" THEN " + context.stringLiteralExpression(variant.typeName())))));
		}
		parts.add(SqlExpression.sql(
				" ELSE " + context.stringLiteralExpression(defaultTypeName != null ? defaultTypeName : "") + " END"));
		return SqlExpression.implode("", parts);
	}

	private static UnsupportedOperationException unsupported(QueryNode node, String what) {
		String field = node instanceof AbstractQueryTree.FieldNode fieldNode && fieldNode.field() != null
				? " (field '" + fieldNode.field().getName() + "')"
				: "";
		return new UnsupportedOperationException(
				"JSON queries do not support " + what + " yet" + field);
	}
}
