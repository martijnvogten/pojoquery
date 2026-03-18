package org.pojoquery.pipeline;

import static org.pojoquery.pipeline.PojoMetadata.collectFieldsOfClass;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.AbstractQueryTree.*;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.pipeline.querytree.transforms.AliasNaming;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;
import org.pojoquery.util.Strings;

public class AQTTransformer {

	public static class Transformers {

		public static QueryNode addDeclaredFields(QueryNode node) {
			return (node instanceof TableNode tableNode && tableNode.children() == null)
					? tableNode.withChildren(getFieldsOfEntity(tableNode.type()).stream()
							.map(f -> new EmptyFieldNodeImpl(f))
							.toList())
					: node;
		}

		public static QueryNode addSuperClassTableNodes(QueryNode node) {
			if (node instanceof TableNode tableNode
					&& PojoMetadata.determineTableMapping(tableNode.type()).size() > 1) {
				List<TableMapping> mappings = PojoMetadata.determineTableMapping(tableNode.type());
				TableMapping superMapping = mappings.get(mappings.size() - 2);
				ForeignKeyInfo join = new ForeignKeyInfo(
						tableNode.tableInfo(), tableNode.alias(),
						new TableInfo(superMapping.schemaName, superMapping.tableName),
						AliasNaming.superclassAlias(tableNode.alias(), tableNode.alias(), superMapping.tableName),
						PojoMetadata.determineIdField(tableNode.type()), null,
						PojoMetadata.determineIdField(superMapping.type), null, null);
				return new TPSSuperClassNode(tableNode.alias(), tableNode.type(), determinTableInfo(tableNode.type()),
						null, join, null);
			} else {
				return node;
			}
		}

		public static QueryNode addEmbeddedEntities(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof EmptyFieldNode emptyFieldNode
							&& emptyFieldNode.field().getAnnotation(Embedded.class).isPresent(),
					(TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
						TypeModel embeddedType = emptyFieldNode.field().getType();
						String alias = AliasNaming.childAlias(
								parentNode instanceof RootNode,
								parentNode.alias(),
								emptyFieldNode.field().getName());

						return EmbeddedEntity.fromEmptyFieldNode(
							emptyFieldNode, alias, embeddedType, (parentNode instanceof Embedding emb ? emb.sourceAlias() : parentNode.alias()), parentNode.tableInfo());
					});
		}

		public static QueryNode addEntityReferences(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof EmptyFieldNode emptyFieldNode
							&& isEntity(emptyFieldNode.field().getType()),
					(TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
						String alias = AliasNaming.childAlias(
								parentNode instanceof RootNode,
								parentNode.alias(),
								emptyFieldNode.field().getName());

						TypeModel referencedType = emptyFieldNode.field().getType();

						FieldModel childIdField = PojoMetadata.determineIdField(referencedType);

						return EntityReference.fromEmptyFieldNode(
								emptyFieldNode,
								alias,
								determinTableInfo(referencedType),
								emptyFieldNode.field(),
								parentNode.alias(),
								ForeignKeyInfo.fkInParent(parentNode.tableInfo(), parentNode.alias(),
										determinTableInfo(referencedType), alias, emptyFieldNode.field(),
										childIdField));
					});
		}

		public static QueryNode addEntityCollections(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof EmptyFieldNode emptyFieldNode &&
							isEntityCollection(emptyFieldNode.field().getType()),
					(TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
						TypeModel componentType = getCollectionComponentType(emptyFieldNode.field().getType());
						String alias = AliasNaming.childAlias(node instanceof RootNode, parentNode.alias(),
								emptyFieldNode.field().getName());
						TableInfo tableInfo = determinTableInfo(componentType);

						FieldModel idField = PojoMetadata.determineIdField(componentType);
						ForeignKeyInfo join = ForeignKeyInfo.fkInChild(parentNode.tableInfo(), parentNode.alias(),
								tableInfo, alias, idField);
						return EntityCollection.fromEmptyFieldNode(emptyFieldNode, alias, componentType, tableInfo,
								parentNode.alias(), join);
					});
		}

		public static QueryNode addJointableEntityCollections(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof EmptyFieldNode emptyFieldNode &&
							isEntityCollection(emptyFieldNode.field().getType()) &&
							!Strings.isNullOrEmpty(emptyFieldNode.field().getAnnotationAttributeValue(Link.class,
									"linktable", String.class)),
					(TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
						TypeModel componentType = getCollectionComponentType(emptyFieldNode.field().getType());
						String alias = AliasNaming.childAlias(node instanceof RootNode, parentNode.alias(),
								emptyFieldNode.field().getName());
						TableInfo tableInfo = determinTableInfo(componentType);

						String linkTableName = emptyFieldNode.field().getAnnotationAttributeValue(Link.class,
								"linktable", String.class);
						String linkTableSchema = emptyFieldNode.field().getAnnotationAttributeValue(Link.class,
								"linkschema", String.class);

						String joinTableAlias = AliasNaming.childAlias(false, alias, linkTableName);

						TableInfo joinTable = new TableInfo(linkTableSchema.isEmpty() ? null : linkTableSchema,
								linkTableName);
						JoinTableInfo joinTableInfo = new JoinTableInfo(
								joinTable,
								joinTableAlias);

						JoinTableJoin join = new JoinTableJoin(
								joinTableInfo,
								ForeignKeyInfo.fkInChild(parentNode.tableInfo(), parentNode.alias(), joinTable,
										joinTableAlias, PojoMetadata.determineIdField(parentNode.type())),
								ForeignKeyInfo.fkInChild(tableInfo, alias, joinTable, joinTableAlias,
										PojoMetadata.determineIdField(componentType)));
						return JoinTableEntityCollection.fromEmptyFieldNode(emptyFieldNode, alias, componentType,
								tableInfo, join, parentNode.alias());
					});
		}

		public static QueryNode addIdFields(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof EmptyFieldNode emptyFieldNode
							&& emptyFieldNode.field().getAnnotation(Id.class).isPresent(),
					(TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
						FieldModel idField = emptyFieldNode.field();
						return new PrimaryKey(idField, idField.getName(), null, null);
					});
		}

		public static QueryNode addScalarValues(QueryNode node) {
			return node instanceof TableNode parentNode && parentNode.children() != null
					? parentNode.withChildren(parentNode.children().stream()
							.map(child -> (child instanceof EmptyFieldNode emptyFieldNode)
									? new ScalarValue(emptyFieldNode.field(), emptyFieldNode.field().getName(), null)
									: child)
							.toList())
					: node;
		}

		public static QueryNode applyDefaultIdFieldNames(QueryNode node) {
			if (node instanceof HasJoinTableJoin jtj && (jtj.join().parentKey().idColumnName() == null
					|| jtj.join().childKey().idColumnName() == null)) {
				ForeignKeyInfo parentKey = jtj.join().parentKey();
				parentKey = parentKey.idColumnName() == null ? parentKey.withIdColumnName(parentKey.idField().getName())
						: parentKey;
				ForeignKeyInfo childKey = jtj.join().childKey();
				childKey = childKey.idColumnName() == null ? childKey.withIdColumnName(childKey.idField().getName())
						: childKey;

				return jtj.withJoinTableJoin(jtj.join().withJoinForeignKeyInfo(
						parentKey.withIdColumnName(parentKey.idField().getName()),
						childKey.withIdColumnName(childKey.idField().getName())));
			} else if (node instanceof Join join && join.join().idColumnName() == null) {
				ForeignKeyInfo fk = join.join();
				return join.withJoin(fk.withIdColumnName(fk.idField().getName()));
			}

			return node;
		}

		public static QueryNode applyDefaultForeignKeyColumnNames(QueryNode node) {
			if (node instanceof HasJoinTableJoin jtj && jtj.join().childKey().fkColumnName() == null
					&& jtj.join().parentKey().fkColumnName() == null) {
				// Default foreign key column names for join tables are [target tablename]_id
				JoinTableJoin join = jtj.join();
				String parentFkColumn = join.parentKey().targetTable().tableName() + "_id";
				String childFkColumn = join.childKey().targetTable().tableName() + "_id";
				return jtj.withJoinTableJoin(join.withJoinForeignKeyInfo(
						join.parentKey().withFkColumnName(parentFkColumn),
						join.childKey().withFkColumnName(childFkColumn)));
			} else if (node instanceof JoinOne join && join.join().fkColumnName() == null) {
				ForeignKeyInfo fk = join.join();
				String fkColumnName = fk.foreignKeyField().getName() + "_id";
				return join.withJoin(fk.withFkColumnName(fkColumnName));
			} else if (node instanceof JoinMany join && join.join().fkColumnName() == null) {
				ForeignKeyInfo fk = join.join();
				String fkColumnName = fk.targetTable().tableName() + "_id";
				return join.withJoin(fk.withFkColumnName(fkColumnName));
			}

			return node;
		}

		public static QueryNode applyDefaultScalarExpressions(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof ScalarValue scalar && scalar.expression() == null,
					(TableNode parentNode, ScalarValue scalar) -> scalar.withExpression(
							SqlExpression.sql("{" + (parentNode instanceof Embedding emb ? emb.sourceAlias() : parentNode.alias()) + "." + scalar.field().getName() + "}")));
		}

		public static QueryNode applyDefaultPrimaryKeyExpressions(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof PrimaryKey pk && pk.expression() == null,
					(TableNode parentNode, PrimaryKey pk) -> pk.withExpression(
							SqlExpression.sql("{" + (parentNode instanceof Embedding emb ? emb.sourceAlias() : parentNode.alias()) + "." + pk.field().getName() + "}")));
		}

		public static QueryNode applyDefaultJoinConditions(QueryNode node) {
			if (node instanceof Join join && join.join().joinCondition() == null) {
				ForeignKeyInfo fk = join.join();
				SqlExpression joinCondition = SqlExpression.sql("{" + fk.referringAlias() + "." + fk.fkColumnName()
						+ "} = {" + fk.targetAlias() + "." + fk.idColumnName() + "}");
				ForeignKeyInfo newJoin = fk.withJoinCondition(joinCondition);
				return join.withJoin(newJoin);
			} else if (node instanceof HasJoinTableJoin jtj && jtj.join().parentKey().joinCondition() == null
					&& jtj.join().childKey().joinCondition() == null) {
				ForeignKeyInfo parentKey = jtj.join().parentKey();
				ForeignKeyInfo childKey = jtj.join().childKey();
				SqlExpression parentJoinCondition = SqlExpression
						.sql("{" + parentKey.referringAlias() + "." + parentKey.fkColumnName() + "} = {"
								+ parentKey.targetAlias() + "." + parentKey.idColumnName() + "}");
				SqlExpression childJoinCondition = SqlExpression
						.sql("{" + childKey.referringAlias() + "." + childKey.fkColumnName() + "} = {"
								+ childKey.targetAlias() + "." + childKey.idColumnName() + "}");
				JoinTableJoin newJoin = jtj.join().withJoinForeignKeyInfo(
						parentKey.withJoinCondition(parentJoinCondition),
						childKey.withJoinCondition(childJoinCondition));
				return jtj.withJoinTableJoin(newJoin);
			}
			return node;
		}

		public static QueryNode makeSingleIdFieldsAutoIncrement(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof PrimaryKey pk && pk.isAutoGenerated() == null,
					(TableNode parentNode, PrimaryKey pk) -> parentNode.children().stream()
							.filter(c -> c instanceof PrimaryKey).count() == 1 ? pk.setAutoGenerated(true)
									: pk.setAutoGenerated(false));
		}
	}

	private static boolean isEntity(TypeModel type) {
		return PojoMetadata.determineTableMapping(type).size() > 0;
	}

	private static boolean isEntityCollection(TypeModel type) {
		return type.getArrayComponentType() != null && isEntity(type.getArrayComponentType()) ||
				type.getTypeArgument() != null && isEntity(type.getTypeArgument());
	}

	private static TypeModel getCollectionComponentType(TypeModel type) {
		if (type.getArrayComponentType() != null) {
			return type.getArrayComponentType();
		} else if (type.getTypeArgument() != null) {
			return type.getTypeArgument();
		} else {
			throw new IllegalArgumentException("Not a collection type: " + type);
		}
	}

	public static void toSql(TableNode node, SqlQuery<?> sqlQuery) {
		if (node instanceof RootNode) {
			sqlQuery.setTable(node.tableInfo().schemaName(), node.tableInfo().tableName());
		}
		// sqlQuery.addField(SqlExpression.sql("{" + node.alias() + ".id}")); // select
		// id by default for root
		for (QueryNode child : node.children()) {
			if (child instanceof ScalarValue scalar) {
				sqlQuery.addField(scalar.expression(), node.alias() + "." + scalar.field().getName());
			} else if (child instanceof PrimaryKey pk) {
				sqlQuery.addField(pk.expression(), node.alias() + "." + pk.field().getName());
			} else if (child instanceof Embedding embedded) {
				toSql((TableNode) embedded, sqlQuery);
			} else if (child instanceof EntityReference ref) {
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, ref.tableInfo().tableName(), ref.alias(),
						ref.join().joinCondition());
				toSql((TableNode) ref, sqlQuery);
			} else if (child instanceof EntityCollection col) {
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, col.tableInfo().tableName(), col.alias(),
						col.join().joinCondition());
				toSql((TableNode) col, sqlQuery);
			} else if (child instanceof JoinTableEntityCollection jte) {
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, jte.join().joinTableInfo().tableInfo().tableName(),
						jte.join().joinTableInfo().joinTableAlias(), jte.join().parentKey().joinCondition());
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, jte.tableInfo().tableName(), jte.alias(),
						jte.join().childKey().joinCondition());
				toSql((TableNode) jte, sqlQuery);
			}
		}
	}

	public static RootNode buildQueryTreeForType(Class<?> clz) {
		ReflectionTypeModel rootType = new ReflectionTypeModel(clz);
		TableInfo tableInfo = determinTableInfo(rootType);
		QueryNode newTree = new RootNode(tableInfo.tableName(), rootType, tableInfo, null);
		QueryNode oldTree = null;
		do {
			oldTree = newTree;
			newTree = Optional.<QueryNode>ofNullable(oldTree)
					.map(transformNodesRecursively(Transformers::addDeclaredFields))
					.map(transformNodesRecursively(Transformers::addEmbeddedEntities))
					.map(transformNodesRecursively(Transformers::addJointableEntityCollections))
					.map(transformNodesRecursively(Transformers::addEntityCollections))
					.map(transformNodesRecursively(Transformers::addEntityReferences))
					.map(transformNodesRecursively(Transformers::addSuperClassTableNodes))
					.map(transformNodesRecursively(Transformers::addIdFields))
					.map(transformNodesRecursively(Transformers::addScalarValues))
					.map(transformNodesRecursively(Transformers::applyDefaultIdFieldNames))
					.map(transformNodesRecursively(Transformers::applyDefaultPrimaryKeyExpressions))
					.map(transformNodesRecursively(Transformers::applyDefaultForeignKeyColumnNames))
					.map(transformNodesRecursively(Transformers::applyDefaultJoinConditions))
					.map(transformNodesRecursively(Transformers::applyDefaultScalarExpressions))
					.map(transformNodesRecursively(Transformers::makeSingleIdFieldsAutoIncrement))
					.orElse(null);
		} while (!oldTree.equals(newTree));

		return (RootNode) newTree;
	}

	private static TableInfo determinTableInfo(TypeModel type) {
		List<TableMapping> mapping = PojoMetadata.determineTableMapping(type);
		if (mapping.isEmpty()) {
			throw new IllegalArgumentException("Type " + type.getQualifiedName() + " is not an entity");
		}
		return new TableInfo(mapping.get(0).schemaName, mapping.get(0).tableName);
	}

	private static Function<QueryNode, QueryNode> transformNodesRecursively(Function<QueryNode, QueryNode> transform) {
		return node -> {
			QueryNode transformed = transform.apply(node);
			if (transformed instanceof TableNode tableNode && tableNode.children() != null) {
				List<QueryNode> newChildren = tableNode.children().stream()
						.map(transformNodesRecursively(transform))
						.toList();
				return tableNode.withChildren(newChildren);
			} else {
				return transformed;
			}
		};
	}

	private static List<FieldModel> getFieldsOfEntity(TypeModel type) {
		return PojoMetadata.collectFieldsOfClass(type);
	}

	@SuppressWarnings("unchecked")
	private static <C extends QueryNode> QueryNode transformChildren(QueryNode node,
			Predicate<QueryNode> childCondition, BiFunction<TableNode, C, QueryNode> transform) {
		return node instanceof TableNode parentNode && parentNode.children() != null
				? parentNode.withChildren(parentNode.children().stream()
						.map(child -> childCondition.test(child) ? transform.apply(parentNode, (C) child) : child)
						.toList())
				: node;
	}

}
