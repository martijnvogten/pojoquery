package org.pojoquery.pipeline;

import static org.pojoquery.util.Strings.isNullOrEmpty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.GroupBy;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.OrderBy;
import org.pojoquery.annotations.Select;
import org.pojoquery.internal.MappingException;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.AbstractQueryTree.Column;
import org.pojoquery.pipeline.AbstractQueryTree.EmbeddedEntity;
import org.pojoquery.pipeline.AbstractQueryTree.Embedding;
import org.pojoquery.pipeline.AbstractQueryTree.EmptyFieldNode;
import org.pojoquery.pipeline.AbstractQueryTree.EmptyFieldNodeImpl;
import org.pojoquery.pipeline.AbstractQueryTree.EntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.EntityReference;
import org.pojoquery.pipeline.AbstractQueryTree.FieldNode;
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
import org.pojoquery.pipeline.AbstractQueryTree.ValueCollection;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.pipeline.querytree.transforms.AliasNaming;
import org.pojoquery.pipeline.querytree.transforms.ExpressionResolver;
import org.pojoquery.typemodel.AnnotationModel;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;
import org.pojoquery.util.Strings;
import org.pojoquery.util.Types;

public class AQTTransformer {

	public static class Transformers {

		public static QueryNode checkForCycles(QueryNode tree) {
			if (tree instanceof TableNode rootTableNode) {
				 checkForCyclesRecursively(rootTableNode, new ArrayList<>());
			}
			return tree;
		}


		public static QueryNode addDeclaredFieldsToEmbeddings(QueryNode node) {
			return (node instanceof Embedding tableNode && tableNode.children() == null)
					? tableNode.withChildren(PojoMetadata.collectFieldsOfClass(tableNode.type()).stream()
							.map(fieldModel -> new EmptyFieldNodeImpl(fieldModel))
							.toList())
					: node;
		}

		public static QueryNode addDeclaredFields(QueryNode node) {
			if (node instanceof TableNode tableNode && tableNode.children() == null) {
				List<TableMapping> mappings = PojoMetadata.determineTableMapping(tableNode.type());
				TypeModel superClass = mappings.size() > 1 ? mappings.get(mappings.size() - 2).type : null;
				return tableNode.withChildren(PojoMetadata.collectFieldsOfClass(tableNode.type(), superClass).stream()
						.map(fieldModel -> new EmptyFieldNodeImpl(fieldModel))
						.toList());
			}
			return node;
		}

		public static QueryNode addIdFieldToSubClassTableNodes(QueryNode node) {
			if (node instanceof TPSSubClassNode subClassNode && 
						(subClassNode.children() != null && subClassNode.children().stream().noneMatch(PrimaryKey.class::isInstance))) {
				FieldModel idField = PojoMetadata.determineIdField(subClassNode.type());
				List<QueryNode> newChildren = subClassNode.children() == null ? new ArrayList<>() : new ArrayList<>(subClassNode.children());
				newChildren.add(0, PrimaryKey.fromField(idField));
				return subClassNode.withChildren(newChildren);
			} else {
				return node;
			}
		}

		public static QueryNode addSuperClassTableNodes(QueryNode node) {
			if (node instanceof TableNode tableNode && 
					!(node instanceof TPSSubClassNode) && 
					(tableNode.children() != null && tableNode.children().stream().noneMatch(TPSSuperClassNode.class::isInstance))
					&& PojoMetadata.determineTableMapping(tableNode.type()).size() > 1) {
				
				List<QueryNode> newChildren = tableNode.children() == null ? new ArrayList<>() : new ArrayList<>(tableNode.children());

				List<TableMapping> mappings = PojoMetadata.determineTableMapping(tableNode.type());
				TableMapping superMapping = mappings.get(mappings.size() - 2);
				String superAlias = AliasNaming.superclassAlias(tableNode.alias(), superMapping.tableName);
				TableInfo superTable = new TableInfo(superMapping.schemaName, superMapping.tableName);
				ForeignKeyInfo join = new ForeignKeyInfo(
						tableNode.tableInfo(), tableNode.alias(),
						superTable,
						superAlias,
						PojoMetadata.determineIdField(tableNode.type()), null,
						PojoMetadata.determineIdField(superMapping.type), null, null);
				newChildren.add(new TPSSuperClassNode(superAlias, superMapping.getType(), superTable,
						null, join, null));

				return tableNode.withChildren(newChildren);
			} else {
				return node;
			}
		}

		public static QueryNode addSubClassTableNodes(QueryNode node) {
			if (node instanceof TableNode tableNode && !(node instanceof TPSSuperClassNode) && (tableNode.children() != null && !tableNode.children().stream().anyMatch(child -> child instanceof TPSSubClassNode))
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
							new TableInfo(subTableMapping.schemaName, subTableMapping.tableName), subAlias,
							tableNode.tableInfo(), tableNode.alias(),
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
							&& emptyFieldNode.field().hasAnnotation(Embedded.class),
					(TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
						TypeModel embeddedType = emptyFieldNode.field().getType();
						String alias = AliasNaming.childAlias(
								parentNode instanceof RootNode,
								parentNode.alias(),
								emptyFieldNode.field().getName());

						String prefixAnnotationValue = emptyFieldNode.field().getAnnotationAttributeValue(Embedded.class, "prefix", String.class);
						if (Embedded.DEFAULT.equals(prefixAnnotationValue)) {
							prefixAnnotationValue = emptyFieldNode.field().getName() + "_";
						}

						if (parentNode instanceof EmbeddedEntity parentEmbedding) {
							// If the parent is also an embedded entity, we need to combine the prefixes
							String parentPrefix = parentEmbedding.fieldPrefix() != null ? parentEmbedding.fieldPrefix() : "";
							prefixAnnotationValue = parentPrefix + prefixAnnotationValue;
						}

						return EmbeddedEntity.fromEmptyFieldNode(
							emptyFieldNode, 
							alias, 
							embeddedType, 
							(parentNode instanceof Embedding emb ? emb.sourceAlias() : parentNode.alias()), 
							parentNode.tableInfo(), 
							prefixAnnotationValue);
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
								ForeignKeyInfo.fkInParent(parentNode.tableInfo(), 
										parentNode instanceof EmbeddedEntity emb ? emb.sourceAlias() : parentNode.alias(),
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

						FieldModel idField = PojoMetadata.determineIdField(parentNode.type());
						ForeignKeyInfo join = ForeignKeyInfo.fkInChild(parentNode.tableInfo(), parentNode.alias(),
								tableInfo, alias, idField);
						return EntityCollection.fromEmptyFieldNode(emptyFieldNode, alias, componentType, tableInfo,
								parentNode.alias(), join);
					});
		}

		public static QueryNode addValueCollections(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof EmptyFieldNode emptyFieldNode &&
							isValueCollection(emptyFieldNode.field().getType()) &&
							!Strings.isNullOrEmpty(emptyFieldNode.field().getAnnotationAttributeValue(Link.class,
									"linktable", String.class)) &&
							!Strings.isNullOrEmpty(emptyFieldNode.field().getAnnotationAttributeValue(Link.class,
									"fetchColumn", String.class)),
					(TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
						FieldModel field = emptyFieldNode.field();

						TypeModel componentType = Types.getCollectionComponentType(field);
						
						// This is confusing.. linktable is not a join table
						// as this table contains the values of the collection and a foreign key to the parent entity
						// We should probably rename the annotations.
						String childTableName = field.getAnnotationAttributeValue(Link.class, "linktable", String.class);
						String childTableSchema = field.getAnnotationAttributeValue(Link.class, "linkschema", String.class);
						TableInfo childTable = new TableInfo(childTableSchema.isEmpty() ? null : childTableSchema, childTableName);

						String alias = AliasNaming.childAlias(node instanceof RootNode, parentNode.alias(), field.getName());
						String fetchColumn = field.getAnnotationAttributeValue(Link.class, "fetchColumn", String.class);

						ForeignKeyInfo join = ForeignKeyInfo.fkInChild(parentNode.tableInfo(), parentNode.alias(),
								childTable, alias, PojoMetadata.determineIdField(parentNode.type()));

						return (QueryNode)ValueCollection.fromEmptyFieldNode(emptyFieldNode, alias, componentType, childTable, fetchColumn, parentNode.alias(), join);
					}
				);
		}

		// public static QueryNode addJointableValueCollections(QueryNode node) {
		// 	return transformChildren(node,
		// 			child -> child instanceof EmptyFieldNode emptyFieldNode &&
		// 					!Strings.isNullOrEmpty(emptyFieldNode.field().getAnnotationAttributeValue(Link.class, "linktable", String.class)) &&
		// 					!isEntityCollection(emptyFieldNode.field().getType()),
		// 			(TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
		// 				FieldModel field = emptyFieldNode.field();
		// 				TypeModel componentType = Types.getCollectionComponentType(field);
		// 				String alias = AliasNaming.childAlias(node instanceof RootNode, parentNode.alias(), field.getName());
						
		// 				String joinTableName = field.getAnnotationAttributeValue(Link.class, "linktable", String.class);
		// 				String joinTableSchema = field.getAnnotationAttributeValue(Link.class, "linkschema", String.class);

		// 			}
		// 		);
		// }

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
						return PrimaryKey.fromField(idField);
					});
		}

		public static QueryNode addScalarValues(QueryNode node) {
			return node instanceof TableNode parentNode && parentNode.children() != null
					? parentNode.withChildren(parentNode.children().stream()
							.map(child -> (child instanceof EmptyFieldNode emptyFieldNode)
									? ScalarValue.ofEmptyFieldNode(emptyFieldNode)
									: child)
							.toList())
					: node;
		}

		public static QueryNode applyCustomSelectExpressions(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof ScalarValue scalar && 
						scalar.field().hasAnnotation(Select.class) &&
						scalar.expression() == null,
					(TableNode parentNode, ScalarValue scalar) -> {
						String expression = scalar.field().getAnnotationAttributeValue(Select.class, "value", String.class);
						expression = ExpressionResolver.resolve(expression, 
							parentNode instanceof Embedding emb ? emb.sourceAlias() : parentNode.alias());
						return scalar.withExpression(SqlExpression.sql(expression));
					});
		}

		// @Link(linktable="event_person", linkfield="eventID", foreignlinkfield="personID")
		// @JoinCondition("{this.eventID}={linktable.eventID} AND {linktable.role}='organizer'")
		// public List<Person> organizers;
		public static QueryNode applyCustomJoinTableColumnNames(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof HasJoinTableJoin join && 
						(join.join().parentKey().fkColumnName() == null || join.join().childKey().fkColumnName() == null) &&
						((FieldNode)join).field().hasAnnotation(Link.class),
					(TableNode parentNode, QueryNode child) -> {
						if (child instanceof HasJoinTableJoin join) {
							FieldModel field = ((FieldNode)join).field();
							String parentCustomLinkfield = field.getAnnotationAttributeValue(Link.class, "linkfield", String.class);
							String childCustomLinkfield = field.getAnnotationAttributeValue(Link.class, "foreignlinkfield", String.class);

							return join.withJoinTableJoin(join.join().withJoinForeignKeyInfo(
								Strings.isNullOrEmpty(parentCustomLinkfield) ? join.join().parentKey() : join.join().parentKey().withFkColumnName(parentCustomLinkfield),
								Strings.isNullOrEmpty(childCustomLinkfield) ? join.join().childKey() : join.join().childKey().withFkColumnName(childCustomLinkfield)
							));
						}
						return child;
					}
				);
		}
		public static QueryNode applyCustomForeignKeyColumnNames(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof Join join && join.join().fkColumnName() == null &&
						(join instanceof JoinOne || join instanceof JoinMany) &&
						((FieldNode)join).field().hasAnnotation(Link.class),
					(TableNode parentNode, QueryNode child) -> {
						if (child instanceof Join join) {
							String customLinkfield = 
									join instanceof JoinOne joinOne && joinOne.field().hasAnnotation(Link.class) ?
										joinOne.field().getAnnotationAttributeValue(Link.class, "linkfield", String.class) :
									join instanceof JoinMany joinMany && joinMany.field().hasAnnotation(Link.class) ?
										joinMany.field().getAnnotationAttributeValue(Link.class, "foreignlinkfield", String.class) :
									null;
							if (customLinkfield == null || customLinkfield.isEmpty()) {
								return child;
							}
							String prefix = parentNode instanceof EmbeddedEntity emb ? emb.fieldPrefix() : "";
							return join.withJoin(join.join().withFkColumnName(prefix + customLinkfield));
						}
						return child;
					});
		}

		public static QueryNode applyCustomJoinTableJoinConditions(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof HasJoinTableJoin join && 
						(join.join().parentKey().joinCondition() == null) &&
						((FieldNode)join).field().hasAnnotation(Link.class),
					(TableNode parentNode, QueryNode child) -> {
						if (child instanceof HasJoinTableJoin join) {
							FieldModel field = ((FieldNode)join).field();
							String condition = field.getAnnotationAttributeValue(JoinCondition.class, "value", String.class);
							if (Strings.isNullOrEmpty(condition)) {
								return child;
							}
							condition = ExpressionResolver.resolve(condition, 
								parentNode instanceof Embedding emb ? emb.sourceAlias() : parentNode.alias(), ((TableNode)child).alias(),
								join.join().joinTableInfo().joinTableAlias(), null);
							return join.withJoinTableJoin(join.join().withJoinForeignKeyInfo(
								join.join().parentKey().withJoinCondition(SqlExpression.sql(condition)),
								join.join().childKey()
							));
						}
						return child;
					});
		}

		public static QueryNode applyCustomValueCollectionJoinConditions(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof ValueCollection valueCollection && 
						(valueCollection.join().joinCondition() == null) &&
						((FieldNode)valueCollection).field().hasAnnotation(JoinCondition.class),
					(TableNode parentNode, QueryNode child) -> {
						if (child instanceof ValueCollection valueCollection) {
							FieldModel field = ((FieldNode)child).field();
							String condition = field.getAnnotationAttributeValue(JoinCondition.class, "value", String.class);
							if (Strings.isNullOrEmpty(condition)) {
								return child;
							}
							condition = ExpressionResolver.resolve(condition, 
								parentNode instanceof Embedding emb ? emb.sourceAlias() : parentNode.alias(), 
								valueCollection.join().referringAlias(),
								valueCollection.join().referringAlias(),
								null);
							return valueCollection.withJoin(valueCollection.join().withJoinCondition(SqlExpression.sql(condition)));
						}
						return child;
					}
				);
		}

		public static QueryNode applyCustomJoinConditions(QueryNode node) {
			return transformChildren(node,
				child -> child instanceof FieldNode &&
					child instanceof Join join && join.join().joinCondition() == null,
				(TableNode parentNode, QueryNode child) -> {
					Join join = (Join) child;
					String condition = ((FieldNode)child).field().getAnnotationAttributeValue(JoinCondition.class, "value", String.class);
					if (!isNullOrEmpty(condition)) {
						if (parentNode instanceof RootNode) {
							// If we're not at the root, we need to prefix all
							// aliases with the parent alias.
							condition = ExpressionResolver.resolve(condition, join.parentAlias());
						} else {
							condition = ExpressionResolver.resolveAndPrefix(condition, join.parentAlias());
						}
						return join.withJoin(join.join().withJoinCondition(SqlExpression.sql(condition)));
					} else {
						return child;
					}
				});

		}

		public static QueryNode applyCustomColumnNames(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof Column column && 
						column.field().hasAnnotation(FieldName.class) &&
						column.columnName() == null,
					(TableNode parentNode, Column column) -> {
						String prefix = "";
						if (parentNode instanceof EmbeddedEntity emb) {
							// If the parent is an embedded entity, we need to prefix the column name with the embedding's prefix
							prefix = emb.fieldPrefix() != null ? emb.fieldPrefix() : "";
						}
						String columnName = column.field().getAnnotationAttributeValue(FieldName.class, "value", String.class);
						return column.withColumnName(prefix + columnName);
					});
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

		public static QueryNode applyDefaultForeignKeyColumnNames(QueryNode n) {
			return transformChildren(n,
				child -> true,
				(TableNode parentNode, QueryNode node) -> {
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
						String prefix = parentNode instanceof EmbeddedEntity emb ? emb.fieldPrefix() : "";
						String fkColumnName = prefix + fk.foreignKeyField().getName() + "_id";
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
				});
		}

		public static QueryNode applyDefaultValueCollectionExpressions(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof ValueCollection vc && vc.expression() == null,
					(TableNode parentNode, ValueCollection vc) -> vc.withExpression(
							SqlExpression.sql("{" + (parentNode instanceof Embedding emb ? emb.sourceAlias() : vc.alias()) + "." + vc.fetchColumn() + "}")));
		}

		public static QueryNode applyEmbeddedFieldsColumnPrefix(QueryNode node) {
			return node instanceof EmbeddedEntity emb && emb.children() != null ? emb.withChildren(
					emb.children().stream().map(child -> {
						if (child instanceof Column column && column.columnName() == null) {
							String prefix = emb.fieldPrefix() != null ? emb.fieldPrefix() : "";
							return column.withColumnName(prefix + column.field().getName());
						} else {
							return child;
						}
					}).toList())
					: node;
		}

		public static QueryNode applyDefaultColumnNames(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof Column scalar && scalar.columnName() == null,
					(TableNode parentNode, Column scalar) -> scalar.withColumnName(scalar.field().getName()));
		}

		public static QueryNode applyDefaultScalarExpressions(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof ScalarValue scalar && scalar.expression() == null,
					(TableNode parentNode, ScalarValue scalar) -> scalar.withExpression(
							SqlExpression.sql("{" + (parentNode instanceof Embedding emb ? emb.sourceAlias() : parentNode.alias()) + "." + scalar.columnName() + "}")));
		}

		public static QueryNode applyDefaultPrimaryKeyExpressions(QueryNode node) {
			return transformChildren(node,
					child -> child instanceof PrimaryKey pk && pk.expression() == null,
					(TableNode parentNode, PrimaryKey pk) -> pk.withExpression(
							SqlExpression.sql("{" + (parentNode instanceof Embedding emb ? emb.sourceAlias() : parentNode.alias()) + "." + pk.columnName() + "}")));
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
			} else if (node instanceof HasJoinTableJoin jtj && 
				(jtj.join().parentKey().joinCondition() == null || jtj.join().childKey().joinCondition() == null)) {
				ForeignKeyInfo parentKey = jtj.join().parentKey();
				ForeignKeyInfo childKey = jtj.join().childKey();
				SqlExpression parentJoinCondition = jtj.join().parentKey().joinCondition() == null ? SqlExpression
						.sql("{" + parentKey.referringAlias() + "." + parentKey.fkColumnName() + "} = {"
								+ parentKey.targetAlias() + "." + parentKey.idColumnName() + "}") : jtj.join().parentKey().joinCondition();
				SqlExpression childJoinCondition = jtj.join().childKey().joinCondition() == null ? SqlExpression
						.sql("{" + childKey.referringAlias() + "." + childKey.fkColumnName() + "} = {"
								+ childKey.targetAlias() + "." + childKey.idColumnName() + "}") : jtj.join().childKey().joinCondition();
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

		public static QueryNode applyClassLevelGroupBy(QueryNode node) {
			if (node instanceof RootNode rootNode && rootNode.type().hasAnnotation(GroupBy.class) && rootNode.groupBy() == null) {
				String[] clauses = rootNode.type().getAnnotationAttributeValue(GroupBy.class, "value", String[].class);
				return rootNode.withGroupByClauses(Arrays.asList(clauses));
			} else {
				return node;
			}
		}

		public static QueryNode applyClassLevelOrderBy(QueryNode node) {
			if (node instanceof RootNode rootNode && rootNode.type().hasAnnotation(OrderBy.class) && rootNode.orderBy() == null) {
				String[] clauses = rootNode.type().getAnnotationAttributeValue(OrderBy.class, "value", String[].class);
				return rootNode.withOrderByClauses(Arrays.asList(clauses));
			} else {
				return node;
			}
		}

		public static QueryNode addDefaultValueTransformers(QueryNode node) {
			return Optional.of(node)
			.map(n -> transformChildren(n,
					child -> child instanceof EntityCollection scalar && scalar.valueMapper() == null,
					(TableNode parentNode, EntityCollection scalar) -> scalar.withValueMapper(
							DefaultValueMappers.createMapper(scalar.type()))))
			.map(n -> transformChildren(n,
					child -> child instanceof JoinTableEntityCollection jtec && jtec.valueMapper() == null,
					(TableNode parentNode, JoinTableEntityCollection jtec) -> jtec.withValueMapper(
							DefaultValueMappers.createMapper(jtec.type()))))
			.map(n -> transformChildren(n,
					child -> child instanceof ValueCollection scalar && scalar.valueMapper() == null,
					(TableNode parentNode, ValueCollection scalar) -> scalar.withValueMapper(
							DefaultValueMappers.createMapper(scalar.componentType()))))
			.map(n -> transformChildren(n,
					child -> child instanceof Column scalar && scalar.valueMapper() == null,
					(TableNode parentNode, Column scalar) -> scalar.withValueMapper(
							DefaultValueMappers.forField(scalar.field()))))
			.orElse(node);
		}
	}

	private static boolean isEntity(TypeModel type) {
		return PojoMetadata.determineTableMapping(type).size() > 0;
	}

	private static boolean isEntityCollection(TypeModel type) {
		return type.getArrayComponentType() != null && isEntity(type.getArrayComponentType()) ||
				type.getTypeArgument() != null && isEntity(type.getTypeArgument());
	}

	private static boolean isValueCollection(TypeModel type) {
		return type.getArrayComponentType() != null && !isEntity(type.getArrayComponentType()) ||
				type.getTypeArgument() != null && !isEntity(type.getTypeArgument());
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
			if (child instanceof ScalarValue scalar) {
				sqlQuery.addField(scalar.expression(), node.alias() + "." + scalar.field().getName());
			} else if (child instanceof PrimaryKey pk) {
				sqlQuery.addField(pk.expression(), node.alias() + "." + pk.field().getName());
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
		TableInfo tableInfo = determinTableInfo(rootType);
		QueryNode newTree = RootNode.createEmptyRootNode(tableInfo.tableName(), rootType, tableInfo);
		QueryNode oldTree = null;
		do {
			oldTree = newTree;
			newTree = Optional.<QueryNode>ofNullable(oldTree)
					.map(Transformers::checkForCycles)
					.map(transformNodesRecursively(Transformers::addDeclaredFieldsToEmbeddings))
					.map(transformNodesRecursively(Transformers::addDeclaredFields))
					.map(transformNodesRecursively(Transformers::addIdFieldToSubClassTableNodes))
					.map(transformNodesRecursively(Transformers::addSuperClassTableNodes))
					.map(transformNodesRecursively(Transformers::addEmbeddedEntities))
					// .map(transformNodesRecursively(Transformers::addJointableValueCollections))
					.map(transformNodesRecursively(Transformers::addValueCollections))
					.map(transformNodesRecursively(Transformers::addJointableEntityCollections))
					.map(transformNodesRecursively(Transformers::addEntityCollections))
					.map(transformNodesRecursively(Transformers::addEntityReferences))
					.map(transformNodesRecursively(Transformers::addSubClassTableNodes))
					.map(transformNodesRecursively(Transformers::addIdFields))
					.map(transformNodesRecursively(Transformers::addScalarValues))
					.map(transformNodesRecursively(Transformers::applyCustomJoinTableColumnNames))
					.map(transformNodesRecursively(Transformers::applyCustomForeignKeyColumnNames))
					.map(transformNodesRecursively(Transformers::applyCustomValueCollectionJoinConditions))
					.map(transformNodesRecursively(Transformers::applyCustomJoinTableJoinConditions))
					.map(transformNodesRecursively(Transformers::applyCustomJoinConditions))
					.map(transformNodesRecursively(Transformers::applyCustomColumnNames))
					.map(transformNodesRecursively(Transformers::applyCustomSelectExpressions))
					.map(transformNodesRecursively(Transformers::applyDefaultIdFieldNames))
					.map(transformNodesRecursively(Transformers::applyDefaultForeignKeyColumnNames))
					.map(transformNodesRecursively(Transformers::applyDefaultJoinConditions))
					.map(transformNodesRecursively(Transformers::applyDefaultValueCollectionExpressions))
					.map(transformNodesRecursively(Transformers::applyEmbeddedFieldsColumnPrefix))
					.map(transformNodesRecursively(Transformers::applyDefaultColumnNames))
					.map(transformNodesRecursively(Transformers::applyDefaultPrimaryKeyExpressions))
					.map(transformNodesRecursively(Transformers::applyDefaultScalarExpressions))
					.map(transformNodesRecursively(Transformers::makeSingleIdFieldsAutoIncrement))
					.map(Transformers::applyClassLevelGroupBy)
					.map(Transformers::applyClassLevelOrderBy)
					.map(rootType instanceof ReflectionTypeModel ? transformNodesRecursively(Transformers::addDefaultValueTransformers) : Function.identity())
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

	private static void checkForCyclesRecursively(TableNode node, List<TypeModel> ancestorTypes) {
		List<TypeModel> newPath = Stream.concat(ancestorTypes.stream(), Stream.of(node.type())).toList();
		if (ancestorTypes.contains(node.type())) {
			throw new MappingException(buildCycleDetectedMessage(newPath));
		}
		if (node.children() != null) {
			for (QueryNode child : node.children()) {
				if (child instanceof TableNode tableChild) {
					checkForCyclesRecursively(tableChild, newPath);
				}
			}
		}
    }
    
    private static String buildCycleDetectedMessage(List<TypeModel> cyclePath) {
        String pathStr = cyclePath.stream()
            .map(TypeModel::getSimpleName)
            .collect(Collectors.joining(" → "));
        
        return "Cycle detected in entity hierarchy: " + pathStr + 
            ". PojoQuery requires cycle-free type hierarchies to prevent infinite query expansion.";
    }

}
