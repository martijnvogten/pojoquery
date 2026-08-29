package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableJoin;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKey;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RecursionInfo;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.STISubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.STISuperClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.SubQueryCollection;
import org.pojoquery.pipeline.AbstractQueryTree.SubQueryJoin;
import org.pojoquery.pipeline.querytree.transforms.ExpressionResolver;
import org.pojoquery.pipeline.AbstractQueryTree.TPSSubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TPSSuperClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableInfo;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.AbstractQueryTree.ValueCollection;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.SqlQuery.WithClause;
import org.pojoquery.typemodel.FieldModel;

/**
 * Transforms an Abstract Query Tree into a {@link JsonSqlQuery} that returns
 * each root entity as a single JSON document, built by the database itself.
 *
 * <p>This is the JSON counterpart of {@link AQTTransformer#toSql}. Scalar
 * fields become JSON object properties, references and embedded entities
 * become nested objects, and collections become nested arrays. Collections are
 * pushed into derived-table joins, so the outer query stays at one row per root
 * entity and multiple collections don't multiply into a cross product.</p>
 *
 * <p>A collection's derived table is correlated with the outer row through
 * {@code LATERAL} where the dialect has it, and otherwise aggregates the whole
 * child table grouped by the foreign key. The two produce identical documents;
 * they differ in cost. The grouped form's work is independent of how many roots
 * the WHERE selects, so fetching one root still aggregates every child row,
 * while the correlated form reaches the child by its foreign key index - the
 * access path a joined query uses, without the fan-out. See
 * {@link DbContext#supportsLateralJoins()}.</p>
 *
 * <p>Inheritance is supported for both strategies: subclass-specific fields
 * are only present in the JSON output for rows of that subclass, and a
 * {@code _type} property carries the concrete type's simple name.</p>
 *
 * <p>{@code @Recursive} collections are supported through a recursive CTE that
 * is hoisted to the top-level statement; the element array holds the transitive
 * closure, in unspecified order (no dialect-portable {@code ORDER BY} exists
 * inside a JSON array aggregate).</p>
 *
 * <p>Not yet supported (throws {@link UnsupportedOperationException}):
 * subquery joins, custom query nodes, and non-scalar fields declared on
 * subclasses.</p>
 */
public class AQTJsonDirectTransformer {

	/** Property name carrying the concrete type of a polymorphic entity. */
	public static final String TYPE_PROPERTY = "_type";

	/**
	 * Slot name carrying a single-table-inheritance discriminator value in
	 * {@link DocumentShape#ARRAY} documents. Its row key is the one
	 * {@link AQTRowProcessor} reads to decide a row's concrete subclass.
	 */
	public static final String DISCRIMINATOR_PROPERTY = "_discriminator";

	/**
	 * A subclass encountered while walking a polymorphic node, in both
	 * representations: conditional properties for {@link DocumentShape#OBJECT},
	 * unconditional slots for {@link DocumentShape#ARRAY}.
	 */
	private record SubClassVariant(SqlExpression condition, String typeName, List<JsonProperty> properties,
			List<DocumentSlot> slots) {
	}

	/**
	 * One value position of a document: the SQL that fills it, plus what it means
	 * to a reader. Collected once per node, in tree order; the document
	 * expression and the {@link DocumentLayout} are both projections of this
	 * list, so the two can never disagree about slot order.
	 */
	private record DocumentSlot(JsonProperty property, DocumentLayout.Slot layoutSlot) {

		static DocumentSlot scalar(String alias, String name, SqlExpression value, Class<?> javaType) {
			return new DocumentSlot(new JsonProperty(name, value),
					DocumentLayout.Slot.scalar(name, alias + "." + name, javaType));
		}

		static DocumentSlot nested(String name, SqlExpression value, DocumentLayout nested) {
			return new DocumentSlot(new JsonProperty(name, value), DocumentLayout.Slot.nested(name, nested));
		}

		static DocumentSlot collection(String name, SqlExpression value, DocumentLayout nested) {
			return new DocumentSlot(new JsonProperty(name, value), DocumentLayout.Slot.collection(name, nested));
		}

		static DocumentSlot discriminator(String alias, SqlExpression value) {
			String rowKey = alias + "." + DISCRIMINATOR_PROPERTY;
			return new DocumentSlot(new JsonProperty(DISCRIMINATOR_PROPERTY, value),
					DocumentLayout.Slot.discriminator(DISCRIMINATOR_PROPERTY, rowKey));
		}

		static DocumentSlot valueCollection(String alias, String name, SqlExpression value, Class<?> javaType) {
			return new DocumentSlot(new JsonProperty(name, value),
					DocumentLayout.Slot.valueCollection(name, alias + ".value", javaType));
		}
	}

	/** The two products of building one document: its SQL expression and its slot layout. */
	private record Document(SqlExpression value, DocumentLayout layout) {
	}

	/**
	 * Aliases whose columns cannot be referenced from a WHERE or ORDER BY clause
	 * of a JSON query: collections, the junction tables they walk through, and
	 * everything nested inside them.
	 *
	 * <p>A collection is aggregated inside a derived table that exposes only its
	 * foreign key and the JSON document, so its element columns are gone by the
	 * time the outer query is evaluated. References, embedded objects, superclass
	 * and subclass tables are plain joins, so those stay addressable.</p>
	 *
	 * @param tree the query tree
	 * @return the aliases a clause must not reference, in encounter order
	 */
	public static Set<String> aliasesHiddenByGrouping(RootNode tree) {
		Set<String> hidden = new LinkedHashSet<>();
		collectHiddenAliases(tree, false, hidden);
		return hidden;
	}

	private static void collectHiddenAliases(TableNode node, boolean insideCollection, Set<String> hidden) {
		for (QueryNode child : node.children()) {
			boolean childIsCollection = child instanceof EntityCollection || child instanceof ValueCollection
					|| child instanceof JoinTableEntityCollection || child instanceof SubQueryCollection;
			if (child instanceof JoinTableEntityCollection joinTable) {
				hidden.add(joinTable.join().joinTableInfo().joinTableAlias());
			}
			if (child instanceof TableNode tableChild) {
				if (insideCollection || childIsCollection) {
					hidden.add(tableChild.alias());
				}
				collectHiddenAliases(tableChild, insideCollection || childIsCollection, hidden);
			} else if (child instanceof ValueCollection valueCollection) {
				hidden.add(valueCollection.alias());
			}
		}
	}

	/**
	 * Builds the JSON statement for {@code tree} into {@code query}.
	 *
	 * @return the layout of the root document; readers of named JSON objects can
	 *         ignore it
	 */
	public static DocumentLayout toSql(RootNode tree, JsonSqlQuery query) {
		query.setTable(tree.tableInfo(), tree.alias());
		if (tree.groupBy() != null) {
			query.setGroupBy(tree.groupBy());
		}
		if (tree.orderBy() != null) {
			query.setOrderBy(tree.orderBy());
		}
		Document document = buildDocument(tree, query);
		query.setJsonValue(document.value());
		return document.layout();
	}

	/**
	 * Builds the document for one row of {@code node}, adding any joins its slots
	 * need to {@code query}.
	 */
	private static Document buildDocument(TableNode node, JsonSqlQuery query) {
		List<DocumentSlot> slots = new ArrayList<>();
		List<SubClassVariant> variants = new ArrayList<>();
		processChildren(node, query, slots, variants);

		DbContext context = query.getDbContext();
		if (query.getDocumentShape() == DocumentShape.ARRAY) {
			// Subclass fields are slots of the layout, not conditional properties:
			// the hydrator decides a row's type from the discriminator or the
			// subclass primary key, exactly as it does for a flat query.
			for (SubClassVariant variant : variants) {
				slots.addAll(variant.slots());
			}
			return new Document(context.jsonArray(slots.stream().map(slot -> slot.property().value()).toList()),
					layoutOf(node, slots));
		}

		List<JsonProperty> properties = new ArrayList<>();
		for (DocumentSlot slot : slots) {
			properties.add(slot.property());
		}
		DocumentLayout layout = layoutOf(node, slots);
		if (variants.isEmpty()) {
			return new Document(context.jsonObject(properties), layout);
		}
		properties.add(new JsonProperty(TYPE_PROPERTY, buildTypeCase(context, variants, node.type().getSimpleName())));
		return new Document(context.jsonObjectWithVariants(properties,
				variants.stream().map(v -> new JsonVariant(v.condition(), v.properties())).toList()), layout);
	}

	private static DocumentLayout layoutOf(TableNode node, List<DocumentSlot> slots) {
		return new DocumentLayout(node.alias(), slots.stream().map(DocumentSlot::layoutSlot).toList());
	}

	private static void processChildren(TableNode node, JsonSqlQuery query, List<DocumentSlot> slots,
			List<SubClassVariant> variants) {
		for (QueryNode child : node.children()) {
			if (child instanceof PrimaryKey pk) {
				slots.add(scalarSlot(query, node.alias(), pk.field(), pk.expression()));
			} else if (child instanceof AggregateScalarValue agg) {
				slots.add(scalarSlot(query, node.alias(), agg.field(), agg.expression()));
			} else if (child instanceof ScalarValue scalar) {
				slots.add(scalarSlot(query, node.alias(), scalar.field(), scalar.expression()));
			} else if (child instanceof EmbeddedEntity embedded) {
				// Embedded fields live in the parent's table: a nested object, no join.
				Document document = buildDocument(embedded, query);
				slots.add(DocumentSlot.nested(embedded.field().getName(), document.value(), document.layout()));
			} else if (child instanceof TPSSuperClassNode superClass) {
				// Inherited fields from the superclass table are plain properties.
				query.addTableJoin(JoinType.LEFT, superClass.tableInfo(), superClass.alias(),
						superClass.join().joinCondition());
				processChildren(superClass, query, slots, variants);
			} else if (child instanceof STISuperClassNode superClass) {
				// Same table as the subclass; fields are plain properties.
				processChildren(superClass, query, slots, variants);
			} else if (child instanceof TPSSubClassNode tps) {
				// LEFT JOIN the subclass table; a row is of this subclass when its
				// primary key in that table is non-null.
				query.addTableJoin(JoinType.LEFT, tps.tableInfo(), tps.alias(), tps.join().joinCondition());
				List<JsonProperty> extras = new ArrayList<>();
				List<DocumentSlot> extraSlots = new ArrayList<>();
				SqlExpression pkExpression = collectSubClassProperties(tps, query, extras, extraSlots);
				if (pkExpression == null) {
					throw new IllegalStateException("Subclass table node '" + tps.alias() + "' has no primary key");
				}
				SqlExpression condition = SqlExpression.implode("",
						List.of(pkExpression, SqlExpression.sql(" IS NOT NULL")));
				variants.add(new SubClassVariant(condition, tps.type().getSimpleName(), extras, extraSlots));
			} else if (child instanceof STISubClassNode sti) {
				List<JsonProperty> extras = new ArrayList<>();
				List<DocumentSlot> extraSlots = new ArrayList<>();
				extraSlots.add(DocumentSlot.discriminator(sti.alias(),
						SqlExpression.sql("{" + sti.sourceAlias() + "." + sti.discriminatorColumn() + "}")));
				collectSubClassProperties(sti, query, extras, extraSlots);
				SqlExpression condition = SqlExpression.sql(
						"{" + sti.sourceAlias() + "." + sti.discriminatorColumn() + "} = '"
								+ DbContext.escapeSqlStringLiteral(String.valueOf(sti.discriminatorValue())) + "'");
				variants.add(new SubClassVariant(condition, sti.type().getSimpleName(), extras, extraSlots));
			} else if (child instanceof SubQueryJoin subQueryJoin) {
				slots.add(addSubQueryJoin(subQueryJoin, query));
			} else if (child instanceof SubQueryCollection subQueryCollection) {
				slots.add(addSubQueryCollection(subQueryCollection, query));
			} else if (child instanceof EntityReference reference) {
				slots.add(addEntityReference(reference, query));
			} else if (child instanceof ValueCollection collection) {
				slots.add(addValueCollection(collection, query));
			} else if (child instanceof EntityCollection collection) {
				slots.add(collection.recursionInfo() != null
						? addRecursiveCollection(node, collection, query)
						: addEntityCollection(collection, query));
			} else if (child instanceof JoinTableEntityCollection collection) {
				slots.add(collection.recursionInfo() != null
						? addRecursiveCollection(node, collection, query)
						: addJoinTableEntityCollection(node, collection, query));
			} else if (child instanceof CustomJoin customJoin) {
				// A join, not a value: contributes no slot.
				query.addTableJoin(customJoin.joinType(), customJoin.joinedTable(), customJoin.alias(),
						customJoin.joinCondition());
			} else {
				throw unsupported(child, child.getClass().getSimpleName() + " nodes");
			}
		}
	}

	/**
	 * Builds a scalar slot. In {@link DocumentShape#ARRAY} the value is cast to
	 * text unless it already is text, so no precision is lost on the way through
	 * JSON: a JSON number would round a {@code DECIMAL} or a long beyond 2^53,
	 * and dates would arrive in whatever shape the driver's JSON encoder picks.
	 * The reader converts back to {@code javaType}.
	 */
	private static DocumentSlot scalarSlot(JsonSqlQuery query, String alias, FieldModel field,
			SqlExpression expression) {
		// Register before any cast: join conditions and user clauses address the
		// bare column through {alias.fieldName}, as they do in a flat query.
		query.addColumnMarker(alias, field.getName(), expression);
		Class<?> javaType = field.getType() == null ? null : field.getType().getReflectionClass();
		if (query.getDocumentShape() == DocumentShape.ARRAY && !isTextInJson(javaType)) {
			if (javaType == byte[].class) {
				throw new UnsupportedOperationException(
						"Positional JSON documents cannot carry binary values yet (field '" + field.getName() + "')");
			}
			expression = query.getDbContext().castToStringExpression(expression);
		}
		return DocumentSlot.scalar(alias, field.getName(), expression, javaType);
	}

	/** Whether a value of this type is already character data in a JSON document. */
	private static boolean isTextInJson(Class<?> javaType) {
		return javaType == null || javaType == String.class || javaType.isEnum();
	}

	/**
	 * A many-to-one reference is a plain {@code LEFT JOIN}: it yields one row, so
	 * nothing needs grouping, and the target's columns stay addressable by WHERE
	 * and ORDER BY - which a derived table would hide.
	 *
	 * <p>In {@link DocumentShape#OBJECT} the document is guarded by the target's
	 * primary key, so an unmatched reference reads as JSON {@code null} rather
	 * than an object of nulls. {@link DocumentShape#ARRAY} needs no guard: a
	 * document of all nulls is how an absent node already reaches the hydrator
	 * from an unmatched LEFT JOIN in a flat query.</p>
	 */
	/**
	 * A field whose type carries {@code @From}: an aggregate projection over its
	 * own source relation, joined as a derived table on the parent's key.
	 *
	 * <p>The subquery aggregates rows, not documents, so it is built as a flat
	 * statement by {@link AQTTransformer} and joined here unchanged. It yields one
	 * row per parent, so nothing needs grouping in the outer query and its columns
	 * stay addressable by WHERE and ORDER BY - the projection's fields nest as a
	 * plain object, exactly as a many-to-one reference does.</p>
	 */
	private static DocumentSlot addSubQueryJoin(SubQueryJoin subQueryJoin, JsonSqlQuery query) {
		DefaultSqlQuery plainSubQuery = new DefaultSqlQuery(query.getDbContext());
		AQTTransformer.toSql(subQueryJoin.subQueryTree(), plainSubQuery, true);
		query.addSubQueryJoin(subQueryJoin.joinType(), plainSubQuery.toStatement(), subQueryJoin.alias(),
				subQueryJoin.joinCondition());
		Document document = buildDocument(subQueryJoin, query);
		return DocumentSlot.nested(subQueryJoin.field().getName(), document.value(), document.layout());
	}

	/**
	 * A collection of an aggregate projection: one element per group the
	 * projection produces, restricted to the parent by the collection's join
	 * condition.
	 *
	 * <p>Two derived tables, nested. The inner one is the projection's own flat
	 * statement, which does the grouping; the outer one selects from it and
	 * aggregates the surviving rows into an array.</p>
	 *
	 * <p>This shape requires {@code LATERAL}. The grouped fallback would have to
	 * {@code GROUP BY} the parent's key inside the subquery, and the key is only
	 * known when the join condition is the default {@code {parent.id} =
	 * {child.id}} - a {@link org.pojoquery.annotations.JoinCondition} may relate
	 * the two on anything at all. Rather than guess at the key, the correlated
	 * form puts the condition in the subquery's WHERE, where its shape does not
	 * matter.</p>
	 */
	private static DocumentSlot addSubQueryCollection(SubQueryCollection collection, JsonSqlQuery query) {
		if (!query.getDbContext().supportsLateralJoins()) {
			throw new UnsupportedOperationException(
					"A JSON query cannot gather a collection of an @From projection without LATERAL (field '"
							+ collection.field().getName() + "'). The grouped form would have to GROUP BY the "
							+ "parent's key inside the subquery, and a @JoinCondition need not expose one. "
							+ "Use a context whose dialect has LATERAL, or execute() this query instead.");
		}
		DefaultSqlQuery plainSubQuery = new DefaultSqlQuery(query.getDbContext());
		AQTTransformer.toSql(collection.subQueryTree(), plainSubQuery, true);

		JsonSqlQuery subQuery = query.startSubQuery();
		subQuery.setTable(plainSubQuery.toStatement(), collection.alias());
		subQuery.setAggregated(true);
		// The condition is written against the outer query, where {this} is the
		// parent. Inside the subquery {this} would name the collection itself, so
		// it is resolved here rather than by the subquery that carries it.
		subQuery.addWhere(new SqlExpression(
				ExpressionResolver.resolve(collection.joinCondition().getSql(), collection.parentAlias()),
				collection.joinCondition().getParameters()));
		// An aggregate over an empty set still returns a row, and what its JSON
		// aggregate holds there is a dialect's own business - HSQLDB yields the
		// text 'null', which COALESCE would not catch. Suppressing the row instead
		// makes the outer LEFT JOIN produce a real NULL, the one thing every
		// dialect agrees on. The collections that group by a foreign key get this
		// for free; this one has no key to group by.
		subQuery.addHaving(SqlExpression.sql("COUNT(*) > 0"));

		Document document = buildDocument(collection, subQuery);
		subQuery.setJsonValue(document.value());

		query.addLateralSubQueryJoin(JoinType.LEFT, subQuery.toStatement(), collection.alias());
		return DocumentSlot.collection(collection.field().getName(),
				coalesceEmptyArray(query, collection.alias()), document.layout());
	}

	private static DocumentSlot addEntityReference(EntityReference reference, JsonSqlQuery query) {
		query.addTableJoin(JoinType.LEFT, reference.tableInfo(), reference.alias(),
				reference.join().joinCondition());
		Document document = buildDocument(reference, query);
		SqlExpression value = query.getDocumentShape() == DocumentShape.OBJECT
				? SqlExpression.implode("", List.of(
						SqlExpression.sql("CASE WHEN {" + reference.alias() + "." + reference.join().idColumnName()
								+ "} IS NULL THEN NULL ELSE "),
						document.value(),
						SqlExpression.sql(" END")))
				: document.value();
		return DocumentSlot.nested(reference.field().getName(), jsonRef(query, value), document.layout());
	}

	/**
	 * Ties a collection's subquery to its parent, correlated where the dialect
	 * has {@code LATERAL} and grouped otherwise.
	 *
	 * <p>Both forms leave the outer query at one row per parent; they differ in
	 * what the database does to get there. Grouped, the subquery aggregates the
	 * whole child table and the join picks out the matching group, so its cost
	 * does not fall when the WHERE selects fewer parents. Correlated, the
	 * aggregate runs per parent row and reaches the child through its foreign
	 * key index - the access path a joined query uses, without the fan-out.</p>
	 *
	 * <p>Both forms keep the {@code GROUP BY}. It is not there to separate
	 * parents in the correlated form - the WHERE has already done that - but to
	 * make an empty collection produce <em>no row</em>, so the outer
	 * {@link #coalesceEmptyArray} sees SQL NULL and yields {@code []}. Without
	 * it the aggregate runs over an empty set, and dialects disagree about what
	 * that returns: HSQLDB gives JSON {@code null}, which is not SQL NULL and so
	 * passes straight through COALESCE.</p>
	 *
	 * @param correlation relates child to parent: the ON condition of the
	 *                    grouped form, the WHERE of the correlated one
	 * @param fkColumn    the foreign key, which only the grouped form selects -
	 *                    the correlated form's parent is already known
	 */
	private static void correlateOrGroup(JsonSqlQuery subQuery, SqlExpression correlation, String fkColumn,
			String fkExpression, boolean lateral) {
		if (lateral) {
			subQuery.addWhere(correlation);
		} else {
			subQuery.addField(SqlExpression.sql(fkExpression), fkColumn);
		}
		subQuery.setGroupBy(List.of(fkExpression));
	}

	/** Joins a built collection subquery, matching the strategy chosen above. */
	private static void attachCollection(JsonSqlQuery query, JsonSqlQuery subQuery, String alias,
			SqlExpression joinCondition, boolean lateral) {
		if (lateral) {
			query.addLateralSubQueryJoin(JoinType.LEFT, subQuery.toStatement(), alias);
		} else {
			query.addSubQueryJoin(JoinType.LEFT, subQuery.toStatement(), alias, joinCondition);
		}
	}

	/**
	 * One-to-many: the foreign key lives in the child table.
	 *
	 * <p>The join condition reads {@code {child.fk} = {parent.id}} and the
	 * subquery's own table alias <em>is</em> {@code child}, so the same
	 * expression serves as the correlated WHERE and as the grouped form's ON
	 * condition.</p>
	 */
	private static DocumentSlot addEntityCollection(EntityCollection collection, JsonSqlQuery query) {
		boolean lateral = query.getDbContext().supportsLateralJoins();
		JsonSqlQuery subQuery = query.startSubQuery();
		subQuery.setTable(collection.tableInfo(), collection.alias());
		subQuery.setAggregated(true);

		String fkColumn = collection.join().fkColumnName();
		correlateOrGroup(subQuery, collection.join().joinCondition(), fkColumn,
				"{" + collection.alias() + "." + fkColumn + "}", lateral);
		Document document = buildDocument(collection, subQuery);
		subQuery.setJsonValue(document.value());

		attachCollection(query, subQuery, collection.alias(), collection.join().joinCondition(), lateral);
		return DocumentSlot.collection(collection.field().getName(),
				coalesceEmptyArray(query, collection.alias()), document.layout());
	}

	/**
	 * Many-to-many: push the junction table into the subquery so the outer query
	 * stays at one row per parent. Otherwise the outer array aggregate would emit
	 * one element per row in the parent x junction fan-out, duplicating the
	 * parent.
	 */
	private static DocumentSlot addJoinTableEntityCollection(TableNode parent, JoinTableEntityCollection collection,
			JsonSqlQuery query) {
		boolean lateral = query.getDbContext().supportsLateralJoins();
		JsonSqlQuery subQuery = query.startSubQuery();
		subQuery.setAggregated(true);

		String junctionAlias = collection.join().joinTableInfo().joinTableAlias();
		String parentFkColumn = collection.join().parentKey().fkColumnName();
		String parentIdColumn = collection.join().parentKey().idColumnName();

		subQuery.setTable(collection.join().joinTableInfo().tableInfo(), junctionAlias);
		subQuery.addTableJoin(JoinType.LEFT, collection.join().childKey().targetTable(), collection.alias(),
				collection.join().childKey().joinCondition());

		// The two conditions differ in which table carries the parent's key.
		// Outside, the derived table republishes it under the collection alias;
		// inside, it is still a column of the junction table.
		SqlExpression onCondition = SqlExpression.sql(
				"{" + collection.alias() + "." + parentFkColumn + "} = {" + parent.alias() + "." + parentIdColumn + "}");
		SqlExpression correlation = SqlExpression.sql(
				"{" + junctionAlias + "." + parentFkColumn + "} = {" + parent.alias() + "." + parentIdColumn + "}");

		correlateOrGroup(subQuery, correlation, parentFkColumn,
				"{" + junctionAlias + "." + parentFkColumn + "}", lateral);
		Document document = buildDocument(collection, subQuery);
		subQuery.setJsonValue(document.value());

		attachCollection(query, subQuery, collection.alias(), onCondition, lateral);
		return DocumentSlot.collection(collection.field().getName(),
				coalesceEmptyArray(query, collection.alias()), document.layout());
	}

	/**
	 * A {@code @Recursive} collection: a recursive CTE emits
	 * {@code (root_id, id, depth)} tuples mapping each root row to every element
	 * reachable from it, and then acts as the junction table of an ordinary
	 * collection subquery. This covers both adjacency-list and junction
	 * recursion, since the CTE has already erased the difference.
	 *
	 * <p>The CTE is uncorrelated with the outer query, so it is hoisted to the
	 * top-level statement and named from inside the derived table.</p>
	 */
	private static DocumentSlot addRecursiveCollection(TableNode parent, TableNode collection, JsonSqlQuery query) {
		RecursionInfo info;
		JoinTableJoin junctionJoin;
		String parentIdColumn;
		String fieldName;
		if (collection instanceof EntityCollection entityCollection) {
			info = entityCollection.recursionInfo();
			junctionJoin = null;
			parentIdColumn = entityCollection.join().idColumnName();
			fieldName = entityCollection.field().getName();
		} else if (collection instanceof JoinTableEntityCollection joinTableCollection) {
			info = joinTableCollection.recursionInfo();
			junctionJoin = joinTableCollection.join();
			parentIdColumn = joinTableCollection.join().parentKey().idColumnName();
			fieldName = joinTableCollection.field().getName();
		} else {
			throw unsupported(collection, "@Recursive collections on " + collection.getClass().getSimpleName());
		}

		DbContext context = query.getDbContext();
		WithClause cte = RecursionCteBuilder.buildCte(collection, info, junctionJoin,
				() -> new DefaultSqlQuery(context));
		query.addWithClause(cte.alias, cte.columnNames, cte.body, cte.recursive);

		String junctionAlias = cte.alias;
		if (junctionJoin != null) {
			// The CTE emits one tuple per path, so a graph that reaches the same
			// element twice would duplicate it in the array. The object path
			// deduplicates while collecting; here the array aggregate takes every
			// row, so deduplicate up front.
			WithClause distinct = RecursionCteBuilder.buildDistinctPairsCte(context, cte.alias,
					RecursionCteBuilder.distinctCteAlias(collection));
			query.addWithClause(distinct.alias, distinct.columnNames, distinct.body, distinct.recursive);
			junctionAlias = distinct.alias;
		}

		JsonSqlQuery subQuery = query.startSubQuery();
		subQuery.setTable(new TableInfo(null, junctionAlias), junctionAlias);
		subQuery.setAggregated(true);
		subQuery.addTableJoin(JoinType.INNER, collection.tableInfo(), collection.alias(),
				SqlExpression.sql("{" + collection.alias() + "." + info.idColumn() + "} = {" + junctionAlias + "."
						+ RecursionCteBuilder.ID_COLUMN + "}"));
		String rootIdExpression = "{" + junctionAlias + "." + RecursionCteBuilder.ROOT_ID_COLUMN + "}";
		subQuery.addField(SqlExpression.sql(rootIdExpression), RecursionCteBuilder.ROOT_ID_COLUMN);
		subQuery.setGroupBy(List.of(rootIdExpression));
		Document document = buildDocument(collection, subQuery);
		subQuery.setJsonValue(document.value());

		SqlExpression onCondition = SqlExpression.sql("{" + collection.alias() + "."
				+ RecursionCteBuilder.ROOT_ID_COLUMN + "} = {" + parent.alias() + "." + parentIdColumn + "}");
		query.addSubQueryJoin(JoinType.LEFT, subQuery.toStatement(), collection.alias(), onCondition);
		return DocumentSlot.collection(fieldName, coalesceEmptyArray(query, collection.alias()), document.layout());
	}

	/**
	 * A collection of scalar values becomes a subquery aggregating the value
	 * expression into a JSON array, grouped by the foreign key.
	 */
	private static DocumentSlot addValueCollection(ValueCollection collection, JsonSqlQuery query) {
		boolean lateral = query.getDbContext().supportsLateralJoins();
		JsonSqlQuery subQuery = query.startSubQuery();
		subQuery.setTable(collection.joinTable(), collection.alias());
		subQuery.setAggregated(true);

		String fkColumn = collection.join().fkColumnName();
		correlateOrGroup(subQuery, collection.join().joinCondition(), fkColumn,
				"{" + collection.alias() + "." + fkColumn + "}", lateral);
		subQuery.setJsonValue(collection.expression());

		attachCollection(query, subQuery, collection.alias(), collection.join().joinCondition(), lateral);
		return DocumentSlot.valueCollection(collection.alias(), collection.field().getName(),
				coalesceEmptyArray(query, collection.alias()),
				collection.componentType() == null ? null : collection.componentType().getReflectionClass());
	}

	/**
	 * Wraps a subquery's JSON column in COALESCE so parents without collection
	 * elements get {@code []} rather than {@code null}.
	 */
	private static SqlExpression coalesceEmptyArray(JsonSqlQuery query, String subQueryAlias) {
		DbContext context = query.getDbContext();
		return jsonRef(query, SqlExpression.implode("", List.of(
				SqlExpression.sql("COALESCE({" + subQueryAlias + "." + JsonSqlQuery.JSON_COLUMN + "}, "),
				context.emptyJsonArray(),
				SqlExpression.sql(")"))));
	}

	/**
	 * Marks a nested document for embedding as JSON, where the dialect can say
	 * so in this shape's element position. HSQLDB cannot inside a JSON array, so
	 * array-mode documents nest as JSON text there and readers parse one level
	 * per depth.
	 */
	private static SqlExpression jsonRef(JsonSqlQuery query, SqlExpression jsonValue) {
		DbContext context = query.getDbContext();
		if (query.getDocumentShape() == DocumentShape.ARRAY && !context.jsonArrayNestsDocuments()) {
			return jsonValue;
		}
		return context.jsonValueRef(jsonValue);
	}

	/**
	 * Walks a subclass node's scalar children, appending the object-mode
	 * properties to {@code properties} and the array-mode slots to
	 * {@code slots}. Returns the subclass primary key expression, used as the
	 * discriminator for table-per-subclass inheritance.
	 *
	 * <p>The primary key is a slot but not a property: object mode spends it on
	 * the variant condition, while array mode needs it in the document because
	 * the hydrator identifies subclass rows by it.</p>
	 */
	private static SqlExpression collectSubClassProperties(TableNode subClassNode, JsonSqlQuery query,
			List<JsonProperty> properties, List<DocumentSlot> slots) {
		SqlExpression pkExpression = null;
		for (QueryNode child : subClassNode.children()) {
			if (child instanceof PrimaryKey pk) {
				pkExpression = pk.expression();
				slots.add(scalarSlot(query, subClassNode.alias(), pk.field(), pk.expression()));
			} else if (child instanceof AggregateScalarValue agg) {
				properties.add(new JsonProperty(agg.field().getName(), agg.expression()));
				slots.add(scalarSlot(query, subClassNode.alias(), agg.field(), agg.expression()));
			} else if (child instanceof ScalarValue scalar) {
				properties.add(new JsonProperty(scalar.field().getName(), scalar.expression()));
				slots.add(scalarSlot(query, subClassNode.alias(), scalar.field(), scalar.expression()));
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
