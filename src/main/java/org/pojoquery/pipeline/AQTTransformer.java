package org.pojoquery.pipeline;

import java.util.ArrayList;
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
import org.pojoquery.pipeline.AbstractQueryTree.EmbeddedEntity;
import org.pojoquery.pipeline.AbstractQueryTree.Embedding;
import org.pojoquery.pipeline.AbstractQueryTree.EmptyFieldNode;
import org.pojoquery.pipeline.AbstractQueryTree.EmptyFieldNodeImpl;
import org.pojoquery.pipeline.AbstractQueryTree.EntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.EntityReference;
import org.pojoquery.pipeline.AbstractQueryTree.ForeignKeyInfo;
import org.pojoquery.pipeline.AbstractQueryTree.HasJoinTableJoin;
import org.pojoquery.pipeline.AbstractQueryTree.Join;
import org.pojoquery.pipeline.AbstractQueryTree.JoinMany;
import org.pojoquery.pipeline.AbstractQueryTree.JoinOne;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableInfo;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableJoin;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKey;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.TPSSubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TPSSuperClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.pipeline.querytree.transforms.AliasNaming;
import org.pojoquery.typemodel.AnnotationModel;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;
import org.pojoquery.util.Strings;

public class AQTTransformer {

	public static class Transformers {

		public static QueryNode addDeclaredFields(QueryNode node) {
			return (node instanceof TableNode tableNode && tableNode.children() == null)
					? tableNode.withChildren(PojoMetadata.determineTableMapping(tableNode.type()).stream()
							.reduce((first, second) -> second) // get the last mapping
							.map(mapping -> mapping.getFields().stream().map(f -> new EmptyFieldNodeImpl(f)).toList())
							.orElse(List.of()))
					: node;
		}

		public static QueryNode addIdFieldToSubClassTableNodes(QueryNode node) {
			if (node instanceof TPSSubClassNode subClassNode && 
						(subClassNode.children() != null && subClassNode.children().stream().noneMatch(PrimaryKey.class::isInstance))) {
				FieldModel idField = PojoMetadata.determineIdField(subClassNode.type());
				List<QueryNode> newChildren = subClassNode.children() == null ? new ArrayList<>() : new ArrayList<>(subClassNode.children());
				newChildren.add(0, new PrimaryKey(idField, null, null, null));
				return subClassNode.withChildren(newChildren);
			} else {
				return node;
			}
		}

		public static QueryNode addSuperClassTableNodes(QueryNode node) {
			if (node instanceof TableNode subClassNode && !(node instanceof TPSSubClassNode) && (subClassNode.children() == null || subClassNode.children().stream().noneMatch(TPSSuperClassNode.class::isInstance))
					&& PojoMetadata.determineTableMapping(subClassNode.type()).size() > 1) {
				
				List<QueryNode> newChildren = subClassNode.children() == null ? new ArrayList<>() : new ArrayList<>(subClassNode.children());

				List<TableMapping> mappings = PojoMetadata.determineTableMapping(subClassNode.type());
				TableMapping superMapping = mappings.get(mappings.size() - 2);
				String superAlias = AliasNaming.superclassAlias(subClassNode.alias(), superMapping.tableName);
				TableInfo superTable = new TableInfo(superMapping.schemaName, superMapping.tableName);
				ForeignKeyInfo join = new ForeignKeyInfo(
						subClassNode.tableInfo(), subClassNode.alias(),
						superTable,
						superAlias,
						PojoMetadata.determineIdField(subClassNode.type()), null,
						PojoMetadata.determineIdField(superMapping.type), null, null);
				newChildren.add(new TPSSuperClassNode(superAlias, superMapping.getType(), superTable,
						null, join, null));

				return subClassNode.withChildren(newChildren);
			} else {
				return node;
			}
		}

		public static QueryNode addSubClassTableNodes(QueryNode node) {
			if (node instanceof TableNode tableNode && !(node instanceof TPSSuperClassNode) && (tableNode.children() == null || !tableNode.children().stream().anyMatch(child -> child instanceof TPSSubClassNode))
					&& tableNode.type().hasAnnotation(org.pojoquery.annotations.SubClasses.class)) {
				AnnotationModel subClassesAnn = tableNode.type().getAnnotation(org.pojoquery.annotations.SubClasses.class).orElseThrow();
				List<TypeModel> subClasses = tableNode.type().getTypeValuesFromAnnotation(subClassesAnn, "value");

				List<QueryNode> newChildren = tableNode.children() == null ? new ArrayList<>() : new ArrayList<>(tableNode.children());
				for (TypeModel subClass : subClasses) {
					if (subClass.hasAnnotation(org.pojoquery.annotations.DiscriminatorColumn.class)) {
						continue; // Skip if subclass uses single-table inheritance - handled by SingleTableInheritanceTransform
					}
					
					List<TableMapping> subMappings = PojoMetadata.determineTableMapping(subClass);
					if (!subMappings.isEmpty()) {
						TableMapping subTableMapping = subMappings.get(subMappings.size() - 1);
						String subAlias = AliasNaming.subclassAlias(tableNode.alias(), subTableMapping.tableName);
						ForeignKeyInfo join = new ForeignKeyInfo(
							tableNode.tableInfo(), tableNode.alias(),
							new TableInfo(subTableMapping.schemaName, subTableMapping.tableName), subAlias,
								PojoMetadata.determineIdField(tableNode.type()), null,
								PojoMetadata.determineIdField(subClass), null, null);
						newChildren.add(new TPSSubClassNode(subAlias, subClass, determinTableInfo(subClass), null, join, tableNode.alias()));
					}
				}

				return tableNode.withChildren(newChildren);
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
			} else if (node instanceof Join join && (node instanceof TPSSubClassNode || node instanceof TPSSuperClassNode) && join.join().fkColumnName() == null) {
				ForeignKeyInfo fk = join.join();
				String fkColumnName = fk.idField().getName();
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
				SqlExpression joinCondition = 
					SqlExpression.sql(
						"{" + fk.referringAlias() + "." + fk.fkColumnName() + "} = " + 
						"{" + fk.targetAlias() + "." + fk.idColumnName() + "}"
					);
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
			} else if (child instanceof Join ref) {
				sqlQuery.addJoin(SqlQuery.JoinType.LEFT, ref.join().targetTable().tableName(), ref.join().targetAlias(),
						ref.join().joinCondition());
				toSql((TableNode) ref, sqlQuery);
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
					.map(transformNodesRecursively(Transformers::addIdFieldToSubClassTableNodes))
					.map(transformNodesRecursively(Transformers::addSuperClassTableNodes))
					.map(transformNodesRecursively(Transformers::addEmbeddedEntities))
					.map(transformNodesRecursively(Transformers::addJointableEntityCollections))
					.map(transformNodesRecursively(Transformers::addEntityCollections))
					.map(transformNodesRecursively(Transformers::addEntityReferences))
					.map(transformNodesRecursively(Transformers::addSubClassTableNodes))
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
		return PojoMetadata.determineTableMapping(type).stream().reduce((first, second) -> second)
				.map(m -> new TableInfo(m.schemaName, m.tableName))
				.orElseThrow(() -> new IllegalArgumentException("Type " + type.getQualifiedName() + " is not an entity"));
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
