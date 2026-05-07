package org.pojoquery.pipeline;

import static org.pojoquery.util.Strings.isNullOrEmpty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Aggregate;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.DiscriminatorValue;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.From;
import org.pojoquery.annotations.GroupBy;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Joins;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.OrderBy;
import org.pojoquery.annotations.Select;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.internal.MappingException;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.AbstractQueryTree.AggregateScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.ColumnFieldNode;
import org.pojoquery.pipeline.AbstractQueryTree.CustomJoin;
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
import org.pojoquery.pipeline.AbstractQueryTree.STISubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.SubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.SubQueryCollection;
import org.pojoquery.pipeline.AbstractQueryTree.SubQueryJoin;
import org.pojoquery.pipeline.AbstractQueryTree.SuperClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TPSSubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TPSSuperClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableInfo;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.AbstractQueryTree.ValueCollection;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.TransformPipeline.RecursiveTransform;
import org.pojoquery.pipeline.TransformPipeline.TreeTransform;
import org.pojoquery.pipeline.querytree.transforms.AliasNaming;
import org.pojoquery.pipeline.querytree.transforms.ExpressionResolver;
import org.pojoquery.typemodel.AnnotationModel;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;
import org.pojoquery.util.Strings;

/**
 * Contains all standard transform step classes for the query tree pipeline.
 */
public final class Transforms {
    
    private Transforms() {} // Utility class
    
    // ========== Validation Transforms ==========
    
    public static class CheckForCycles implements TreeTransform {
        @Override
        public RootNode transform(RootNode tree) {
            checkForCyclesRecursively(tree, new ArrayList<>());
            return tree;
        }
        
        private void checkForCyclesRecursively(TableNode node, List<TypeModel> ancestorTypes) {
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
        
        private String buildCycleDetectedMessage(List<TypeModel> cyclePath) {
            String pathStr = cyclePath.stream()
                .map(TypeModel::getSimpleName)
                .collect(Collectors.joining(" → "));
            return "Cycle detected in entity hierarchy: " + pathStr + 
                ". PojoQuery requires cycle-free type hierarchies to prevent infinite query expansion.";
        }
    }
    
    // ========== Structure Building Transforms ==========

	public static class ProcessFromAnnotation implements TransformPipeline.TreeTransform {
		@Override
		public RootNode transform(RootNode rootNode) {
			if (rootNode.type().hasAnnotation(From.class) && rootNode.tableInfo() == null) {
				Class<?> sourceClass = rootNode.type().getAnnotationAttributeValue(From.class, "value", Class.class);
				TypeModel sourceType = new ReflectionTypeModel(sourceClass);
				RootNode sourceTree = removeScalarNodesFromTree(AQTTransformer.buildQueryTreeForType(sourceType, TransformPipeline.defaultPipeline()));

                List<QueryNode> children = new ArrayList<>(PojoMetadata.collectFieldsOfClass(rootNode.type(), null).stream()
                        .<QueryNode>map(fieldModel -> new EmptyFieldNodeImpl(fieldModel))
                        .toList()); // use the source's children (joins)

                children.addAll(sourceTree.children());

				// Merge sourceTree into rootNode, replacing rootNode's table info and children with sourceTree's
				return new RootNode(
					sourceTree.alias(),
					rootNode.type(),
					sourceTree.tableInfo(),
                    children,
					null,
					null
				);
			}
			return rootNode;
		}
	}

    public static class DetermineSourceTable implements TreeTransform {
        @Override
        public RootNode transform(RootNode rootNode) {
            if (rootNode.tableInfo() == null) {
                TableInfo tableInfo = determineTableInfo(rootNode.type());
                String alias = tableInfo.tableName();
                return rootNode.withTableInfo(tableInfo).withAlias(alias);
            }
            return rootNode;
        }
    }

    public static class AddDeclaredFields implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            if (node instanceof TableNode tableNode && tableNode.children() == null) {
                List<TableMapping> mappings = PojoMetadata.determineTableMapping(tableNode.type());
                TypeModel superClass = 
                    tableNode instanceof STISubClassNode subClassNode ? subClassNode.superClass() : 
                    mappings.size() > 1 ? mappings.get(mappings.size() - 2).type : null;

                return tableNode.withChildren(PojoMetadata.collectFieldsOfClass(tableNode.type(), superClass).stream()
                        .map(fieldModel -> new EmptyFieldNodeImpl(fieldModel))
                        .toList());
            }
            return node;
        }
    }
    
    public static class AddIdFieldToSubClassTableNodes implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            if (node instanceof TPSSubClassNode subClassNode && 
                    (subClassNode.children() != null && subClassNode.children().stream().noneMatch(PrimaryKey.class::isInstance))) {
                FieldModel idField = PojoMetadata.determineIdField(subClassNode.type());
                List<QueryNode> newChildren = subClassNode.children() == null ? new ArrayList<>() : new ArrayList<>(subClassNode.children());
                newChildren.add(0, PrimaryKey.fromField(idField));
                return subClassNode.withChildren(newChildren);
            }
            return node;
        }
    }
    
    public static class AddSuperClassTableNodes implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
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
            }
            return node;
        }
    }
    
    public static class AddEmbeddedEntities implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
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
    }
    
    public static class AddValueCollections implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof EmptyFieldNode emptyFieldNode &&
                            isValueCollection(emptyFieldNode.field().getType()) &&
                            !Strings.isNullOrEmpty(emptyFieldNode.field().getAnnotationAttributeValue(Link.class,
                                    "linktable", String.class)) &&
                            !Strings.isNullOrEmpty(emptyFieldNode.field().getAnnotationAttributeValue(Link.class,
                                    "fetchColumn", String.class)),
                    (TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
                        FieldModel field = emptyFieldNode.field();

                        TypeModel componentType = getCollectionComponentType(field.getType());
                        
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
    }
    
    public static class AddJoinTableEntityCollections implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof EmptyFieldNode emptyFieldNode &&
                            isEntityCollection(emptyFieldNode.field().getType()) &&
                            !Strings.isNullOrEmpty(emptyFieldNode.field().getAnnotationAttributeValue(Link.class,
                                    "linktable", String.class)),
                    (TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
                        TypeModel componentType = getCollectionComponentType(emptyFieldNode.field().getType());
                        String alias = AliasNaming.childAlias(node instanceof RootNode, parentNode.alias(),
                                emptyFieldNode.field().getName());
                        TableInfo tableInfo = determineTableInfo(componentType);

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
    }
    
    public static class AddEntityCollections implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof EmptyFieldNode emptyFieldNode &&
                            isEntityCollection(emptyFieldNode.field().getType()),
                    (TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
                        TypeModel componentType = getCollectionComponentType(emptyFieldNode.field().getType());
                        String alias = AliasNaming.childAlias(node instanceof RootNode, parentNode.alias(),
                                emptyFieldNode.field().getName());
                        TableInfo tableInfo = determineTableInfo(componentType);

                        FieldModel idField = PojoMetadata.determineIdField(parentNode.type());
                        ForeignKeyInfo join = ForeignKeyInfo.fkInChild(parentNode.tableInfo(), parentNode.alias(),
                                tableInfo, alias, idField);
                        return EntityCollection.fromEmptyFieldNode(emptyFieldNode, alias, componentType, tableInfo,
                                parentNode.alias(), join);
                    });
        }
    }
    
    public static class AddSubQueryEntityReferences implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof EmptyFieldNode emptyFieldNode
                            && emptyFieldNode.field().getType().hasAnnotation(From.class),
                    (TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
                        TypeModel referencedType = emptyFieldNode.field().getType();
                        String alias = AliasNaming.childAlias(
                                parentNode instanceof RootNode,
                                parentNode.alias(),
                                emptyFieldNode.field().getName());

                        RootNode subQueryTree = AQTTransformer.buildQueryTreeForType(referencedType, TransformPipeline.defaultPipeline());

                        PrimaryKey primaryKeyField = subQueryTree.children().stream()
                            .filter(child -> child instanceof PrimaryKey).map(PrimaryKey.class::cast).findFirst()
                            .orElseThrow(() -> new MappingException("Referenced type " + referencedType.getQualifiedName() + " must have an @Id field"));

                        String idFieldName = PojoMetadata.determineIdField(parentNode.type()).getName();
                        SqlExpression joinCondition = SqlExpression.sql("{" + parentNode.alias() + "." + idFieldName + "} = {" + alias + "." + primaryKeyField.field().getName() + "}");

                        List<QueryNode> children = PojoMetadata.collectFieldsOfClass(referencedType).stream()
                            .<QueryNode>map(fieldModel -> new ScalarValue(fieldModel, fieldModel.getName(), SqlExpression.sql("{" + alias + "." + fieldModel.getName() + "}"), null))
                            .toList();
                        return new SubQueryJoin(alias, null, referencedType, children, emptyFieldNode.field(), parentNode.alias(), SqlQuery.JoinType.LEFT, subQueryTree, joinCondition);
                    });
        }   
    }

    public static class AddSubQueryEntityCollections implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof EmptyFieldNode emptyFieldNode
                            && isCollection(emptyFieldNode.field().getType())
                            && getCollectionComponentType(emptyFieldNode.field().getType()).hasAnnotation(From.class),
                    (TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
                        TypeModel collectionType = emptyFieldNode.field().getType();
                        TypeModel componentType = getCollectionComponentType(collectionType);
                        String alias = AliasNaming.childAlias(node instanceof RootNode, parentNode.alias(),
                                emptyFieldNode.field().getName());

                        RootNode subQueryTree = AQTTransformer.buildQueryTreeForType(componentType, TransformPipeline.defaultPipeline());

                        String joinConditionValue = emptyFieldNode.field().getAnnotationAttributeValue(JoinCondition.class, "value", String.class);
                        if (joinConditionValue == null || joinConditionValue.isEmpty()) {
                            String idFieldName = PojoMetadata.determineIdField(parentNode.type()).getName();
                            joinConditionValue = "{" + parentNode.alias() + "." + idFieldName + "} = {" + alias + "." + idFieldName + "}";
                        }
                        SqlExpression joinCondition = SqlExpression.sql(joinConditionValue);

                        List<QueryNode> children = PojoMetadata.collectFieldsOfClass(componentType).stream()
                            .<QueryNode>map(fieldModel -> new ScalarValue(fieldModel, fieldModel.getName(), SqlExpression.sql("{" + alias + "." + fieldModel.getName() + "}"), null))
                            .toList();
                        return new SubQueryCollection(alias, null, componentType, children, emptyFieldNode.field(), parentNode.alias(), SqlQuery.JoinType.LEFT, subQueryTree, joinCondition);
                    });
        }   
    }

    public static class AddEntityReferences implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
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
                                determineTableInfo(referencedType),
                                emptyFieldNode.field(),
                                parentNode.alias(),
                                ForeignKeyInfo.fkInParent(parentNode.tableInfo(), 
                                        parentNode instanceof EmbeddedEntity emb ? emb.sourceAlias() : parentNode.alias(),
                                        determineTableInfo(referencedType), alias, emptyFieldNode.field(),
                                        childIdField));
                    });
        }
    }
    
    public static class AddSubClassTableNodes implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            if (node instanceof TableNode tableNode && !(node instanceof SuperClassNode) && 
                    tableNode.type().hasAnnotation(SubClasses.class) &&
                    (tableNode.children() != null && !tableNode.children().stream().anyMatch(child -> child instanceof SubClassNode))
                    ) {
                TypeModel[] subClasses = tableNode.type().getAnnotationAttributeValue(SubClasses.class, "value", TypeModel[].class);
                List<TableMapping> superMapping = PojoMetadata.determineTableMapping(tableNode.type());

                List<QueryNode> newChildren = tableNode.children() == null ? new ArrayList<>() : new ArrayList<>(tableNode.children());
                for (TypeModel subClass : subClasses) {
                    List<TableMapping> subMappings = PojoMetadata.determineTableMapping(subClass);
                    if (subMappings.size() > superMapping.size()) {
                        TableMapping subTableMapping = subMappings.get(subMappings.size() - 1);
                        String subAlias = AliasNaming.subclassAlias(tableNode.alias(), subTableMapping.tableName);
                        ForeignKeyInfo join = new ForeignKeyInfo(
                            new TableInfo(subTableMapping.schemaName, subTableMapping.tableName), subAlias,
                            tableNode.tableInfo(), tableNode.alias(),
                                PojoMetadata.determineIdField(tableNode.type()), null,
                                PojoMetadata.determineIdField(subClass), null, null);
                        newChildren.add(new TPSSubClassNode(subAlias, subClass, determineTableInfo(subClass), null, join, tableNode.alias()));
                    } else {
                        String discriminatorColumn = subClass.getAnnotationAttributeValue(DiscriminatorColumn.class, "name", String.class);
                        String discriminatorValue = subClass.getAnnotationAttributeValue(DiscriminatorValue.class, "value", String.class);

                        newChildren.add(new STISubClassNode(
                            AliasNaming.subclassAlias(tableNode.alias(), subClass.getSimpleName().toLowerCase()),
                            subClass,
                            tableNode.tableInfo(),
                            null, tableNode.alias(), tableNode.type(), discriminatorColumn, discriminatorValue, tableNode.alias())
                        );
                    }
                }

                return tableNode.withChildren(newChildren);
            }
            return node;
        }
    }
    
    public static class AddIdFields implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof EmptyFieldNode emptyFieldNode
                            && emptyFieldNode.field().hasAnnotation(Id.class),
                    (TableNode parentNode, EmptyFieldNode emptyFieldNode) -> {
                        FieldModel idField = emptyFieldNode.field();
                        return PrimaryKey.fromField(idField);
                    });
        }
    }
    
    public static class AddScalarValues implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return node instanceof TableNode parentNode && parentNode.children() != null
                    ? parentNode.withChildren(parentNode.children().stream()
                            .map(child -> (child instanceof EmptyFieldNode emptyFieldNode)
                                    ? ScalarValue.ofEmptyFieldNode(emptyFieldNode)
                                    : child)
                            .toList())
                    : node;
        }
    }
    
    // ========== Custom Configuration Transforms ==========
    
    public static class ApplyCustomSelectExpressions implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
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
    }
    
    public static class ApplyAggregateExpressions implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof ScalarValue scalar && 
                        scalar.field().hasAnnotation(Aggregate.class) &&
                        scalar.expression() == null,
                    (TableNode parentNode, ScalarValue scalar) -> {
                        String expression = scalar.field().getAnnotationAttributeValue(Aggregate.class, "value", String.class);
                        expression = ExpressionResolver.resolve(expression, 
                            parentNode instanceof Embedding emb ? emb.sourceAlias() : parentNode.alias());
                        return AggregateScalarValue.fromScalarValue(scalar)
                                .withExpression(SqlExpression.sql(expression));
                    });
        }
    }
    
    public static class ApplyCustomJoinTableColumnNames implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
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
    }
    
    public static class ApplyCustomForeignKeyColumnNames implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
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
    }
    
    public static class ApplyCustomIdColumnNames implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof Join join && join.join().idColumnName() == null && join.join().idField() != null,
                    (TableNode parentNode, QueryNode child) -> {
                        if (child instanceof Join join) {
                            String customIdField = join.join().idField().hasAnnotation(FieldName.class) ?
                                    join.join().idField().getAnnotationAttributeValue(FieldName.class, "value", String.class) :
                                    null;
                            return customIdField == null || customIdField.isEmpty() ?
                                child :
                                join.withJoin(join.join().withIdColumnName(customIdField));
                        }
                        return child;
                    });
        }
    }
    
    public static class ApplyCustomValueCollectionJoinConditions implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
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
    }
    
    public static class ApplyCustomJoinTableJoinConditions implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
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
    }
    
    public static class ApplyCustomJoinConditions implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                child -> child instanceof FieldNode &&
                    child instanceof Join join && join.join().joinCondition() == null,
                (TableNode parentNode, QueryNode child) -> {
                    Join join = (Join) child;
                    String condition = ((FieldNode)child).field().getAnnotationAttributeValue(JoinCondition.class, "value", String.class);
                    if (!isNullOrEmpty(condition)) {
                        if (parentNode instanceof RootNode) {
                            condition = ExpressionResolver.resolve(condition, join.parentAlias());
                        } else {
                            condition = ExpressionResolver.resolveAndPrefix(condition, join.parentAlias());
                        }
                        return join.withJoin(join.join().withJoinCondition(SqlExpression.sql(condition)));
                    }
                    return child;
                });
        }
    }
    
    public static class ApplyCustomColumnNames implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof ColumnFieldNode column && 
                        column.field().hasAnnotation(FieldName.class) &&
                        column.columnName() == null,
                    (TableNode parentNode, ColumnFieldNode column) -> {
                        String prefix = "";
                        if (parentNode instanceof EmbeddedEntity emb) {
                            prefix = emb.fieldPrefix() != null ? emb.fieldPrefix() : "";
                        }
                        String columnName = column.field().getAnnotationAttributeValue(FieldName.class, "value", String.class);
                        return column.withColumnName(prefix + columnName);
                    });
        }
    }
    
    public static class ApplyDiscriminatorColumnFromParent implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node, 
                child -> child instanceof STISubClassNode stiSub && stiSub.discriminatorColumn() == null, 
                (TableNode parentNode, QueryNode child) -> {
                    if (parentNode.type().hasAnnotation(DiscriminatorColumn.class)) {
                        return ((STISubClassNode) child).withDiscriminatorColumn(parentNode.type().getAnnotationAttributeValue(DiscriminatorColumn.class, "name", String.class));
                    } else if (parentNode instanceof STISubClassNode parentStiSub && parentStiSub.discriminatorColumn() != null) {
                        return ((STISubClassNode) child).withDiscriminatorColumn(parentStiSub.discriminatorColumn());
                    }
                    return child;
                });
        }
    }
    
    // ========== Default Value Transforms ==========
    
    public static class ApplyDefaultIdFieldNames implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
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
    }
    
    public static class ApplyDefaultForeignKeyColumnNames implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode n) {
            return transformChildren(n,
                child -> true,
                (TableNode parentNode, QueryNode node) -> {
                    if (node instanceof HasJoinTableJoin jtj && jtj.join().childKey().fkColumnName() == null
                            && jtj.join().parentKey().fkColumnName() == null) {
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
    }
    
    public static class ApplyDefaultJoinConditions implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
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
    }
    
    public static class ApplyDefaultValueCollectionExpressions implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof ValueCollection vc && vc.expression() == null,
                    (TableNode parentNode, ValueCollection vc) -> vc.withExpression(
                            SqlExpression.sql("{" + (parentNode instanceof Embedding emb ? emb.sourceAlias() : vc.alias()) + "." + vc.fetchColumn() + "}")));
        }
    }
    
    public static class ApplyEmbeddedFieldsColumnPrefix implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return node instanceof EmbeddedEntity emb && emb.children() != null ? emb.withChildren(
                    emb.children().stream().map(child -> {
                        if (child instanceof ColumnFieldNode column && column.columnName() == null) {
                            String prefix = emb.fieldPrefix() != null ? emb.fieldPrefix() : "";
                            return column.withColumnName(prefix + column.field().getName());
                        }
                        return child;
                    }).toList())
                    : node;
        }
    }
    
    public static class ApplyDefaultColumnNames implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof ColumnFieldNode scalar && scalar.columnName() == null,
                    (TableNode parentNode, ColumnFieldNode scalar) -> scalar.withColumnName(scalar.field().getName()));
        }
    }
    
    public static class ApplyDefaultPrimaryKeyExpressions implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof PrimaryKey pk && pk.expression() == null,
                    (TableNode parentNode, PrimaryKey pk) -> pk.withExpression(
                            SqlExpression.sql("{" + (parentNode instanceof Embedding emb ? emb.sourceAlias() : parentNode.alias()) + "." + pk.columnName() + "}")));
        }
    }
    
    public static class ApplyDefaultDiscriminatorExpressions implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            // Set default discriminatorValue to the simple class name when it's null
            return transformChildren(node,
                    child -> child instanceof STISubClassNode stiSub && stiSub.discriminatorValue() == null,
                    (TableNode parentNode, QueryNode child) -> {
                        STISubClassNode stiSub = (STISubClassNode) child;
                        // Default discriminator value is the simple class name
                        return stiSub.withDiscriminatorValue(stiSub.type().getSimpleName());
                    });
        }
    }
    
    public static class ApplyDefaultScalarExpressions implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof ScalarValue scalar && scalar.expression() == null,
                    (TableNode parentNode, ScalarValue scalar) -> scalar.withExpression(
                            SqlExpression.sql("{" + (parentNode instanceof Embedding emb ? emb.sourceAlias() : parentNode.alias()) + "." + scalar.columnName() + "}")));
        }
    }
    
    public static class MakeSingleIdFieldsAutoIncrement implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return transformChildren(node,
                    child -> child instanceof PrimaryKey pk && pk.isAutoGenerated() == null,
                    (TableNode parentNode, PrimaryKey pk) -> parentNode.children().stream()
                            .filter(c -> c instanceof PrimaryKey).count() == 1 ? pk.setAutoGenerated(true)
                                    : pk.setAutoGenerated(false));
        }
    }
    
    // ========== Class-Level Annotation Transforms ==========
    
    public static class ApplyClassLevelJoins implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            if (node instanceof TableNode parent && parent.children() != null &&
                (parent.type().hasAnnotation(org.pojoquery.annotations.Join.class) || parent.type().hasAnnotation(Joins.class)) &&
                !parent.children().stream().anyMatch(child -> child instanceof AbstractQueryTree.CustomJoin)
            ) {
                List<AnnotationModel> joinAnnotations = collectJoinAnnotations(parent.type());
                if (!joinAnnotations.isEmpty()) {
                    List<QueryNode> newChildren = new ArrayList<>(parent.children());
                    for (AnnotationModel joinAnn : joinAnnotations) {
                        String joinCondition = joinAnn.getStringValues("joinCondition").get(0);
                        String resolvedCondition = ExpressionResolver.resolve(joinCondition, parent.alias());
                        String schemaName = joinAnn.getStringValues("schemaName").get(0);
                        String tableName = joinAnn.getStringValues("tableName").get(0);
                        JoinType type = JoinType.valueOf(joinAnn.getEnumValues("type").get(0));
                        TableInfo joinedTable = new TableInfo(schemaName.isEmpty() ? null : schemaName, tableName);
                        newChildren.add(new CustomJoin(
                            AliasNaming.childAlias(parent instanceof RootNode, parent.alias(), joinAnn.getStringValues("alias").get(0)),
                            parent.alias(),
                            joinedTable,
                            type,
                            SqlExpression.sql(resolvedCondition))
                         );
                    }
                    return parent.withChildren(newChildren);
                }
            }
            return node;
        }
        
        private List<AnnotationModel> collectJoinAnnotations(TypeModel type) {
            List<AnnotationModel> result = new ArrayList<>();
            // Check for @Joins container (multiple @Join via @Repeatable)
            if (type.hasAnnotation(org.pojoquery.annotations.Joins.class)) {
                AnnotationModel[] joins = type.getAnnotationAttributeValue(
                    org.pojoquery.annotations.Joins.class, "value", AnnotationModel[].class);
                if (joins != null) {
                    result.addAll(Arrays.asList(joins));
                }
            }
            // Check for single @Join (only if not already collected via @Joins)
            else if (type.hasAnnotation(org.pojoquery.annotations.Join.class)) {
                result.add(type.getAnnotation(org.pojoquery.annotations.Join.class).orElseThrow());
            }
            return result;
        }
    }
    
    public static class ApplyClassLevelGroupBy implements TreeTransform {
        @Override
        public RootNode transform(RootNode rootNode) {
            if (rootNode.type().hasAnnotation(GroupBy.class) && rootNode.groupBy() == null) {
                String[] clauses = rootNode.type().getAnnotationAttributeValue(GroupBy.class, "value", String[].class);
                return (RootNode)rootNode.withGroupByClauses(Arrays.asList(clauses));
            }
            return rootNode;
        }
    }
    
    /**
     * Auto-generates GROUP BY when AggregateScalarValue fields are present.
     * Collects expressions from all non-aggregate scalar fields and primary keys.
     * Only applies if no explicit GROUP BY is already set.
     */
    public static class AutoGenerateGroupBy implements TreeTransform {
        @Override
        public RootNode transform(RootNode rootNode) {
            if (rootNode.groupBy() != null) {
                return rootNode;
            }
            if (!hasAggregateFields(rootNode)) {
                return rootNode;
            }
            List<String> groupByExpressions = new ArrayList<>();
            collectNonAggregateExpressions(rootNode, groupByExpressions);
            return groupByExpressions.isEmpty() ? rootNode : (RootNode)rootNode.withGroupByClauses(groupByExpressions);
        }
        
        private boolean hasAggregateFields(QueryNode node) {
            if (node instanceof AggregateScalarValue) {
                return true;
            }
            if (node instanceof TableNode t && t.children() != null) {
                return t.children().stream().anyMatch(this::hasAggregateFields);
            }
            return false;
        }
        
        private void collectNonAggregateExpressions(QueryNode node, List<String> expressions) {
            if (node instanceof ScalarValue sv && sv.expression() != null) {
                expressions.add(sv.expression().getSql());
            } else if (node instanceof PrimaryKey pk && pk.expression() != null) {
                expressions.add(pk.expression().getSql());
            }
            if (node instanceof TableNode t && t.children() != null) {
                t.children().forEach(c -> collectNonAggregateExpressions(c, expressions));
            }
        }
    }
    
    public static class ApplyClassLevelOrderBy implements TreeTransform {
        @Override
        public RootNode transform(RootNode rootNode) {
            if (rootNode.type().hasAnnotation(OrderBy.class) && rootNode.orderBy() == null) {
                String[] clauses = rootNode.type().getAnnotationAttributeValue(OrderBy.class, "value", String[].class);
                return (RootNode)rootNode.withOrderByClauses(Arrays.asList(clauses));
            }
            return rootNode;
        }
    }
    
    // ========== Value Mapper Transforms (for reflection-based usage) ==========

    public static class AddDefaultValueTransformers implements RecursiveTransform {
        @Override
        public QueryNode transform(QueryNode node) {
            return Optional.of(node)
            .map(n -> transformChildren(n,
                    child -> child instanceof EntityCollection ec && ec.valueMapper() == null,
                    (TableNode parentNode, EntityCollection ec) -> ec.withValueMapper(
                            DefaultValueMappers.createMapper(ec.type()))))
            .map(n -> transformChildren(n,
                    child -> child instanceof JoinTableEntityCollection jtec && jtec.valueMapper() == null,
                    (TableNode parentNode, JoinTableEntityCollection jtec) -> jtec.withValueMapper(
                            DefaultValueMappers.createMapper(jtec.type()))))
            .map(n -> transformChildren(n,
                    child -> child instanceof ValueCollection vc && vc.valueMapper() == null,
                    (TableNode parentNode, ValueCollection vc) -> vc.withValueMapper(
                            DefaultValueMappers.createMapper(vc.componentType()))))
            .map(n -> transformChildren(n,
                    child -> child instanceof ColumnFieldNode cfn && cfn.valueMapper() == null,
                    (TableNode parentNode, ColumnFieldNode cfn) -> cfn.withValueMapper(
                            DefaultValueMappers.forField(cfn.field()))))
            .orElse(node);
        }
    }
    
    // ========== Helper Methods ==========
    
    @SuppressWarnings("unchecked")
    public static <C extends QueryNode> QueryNode transformChildren(QueryNode node,
            Predicate<QueryNode> childCondition, BiFunction<TableNode, C, QueryNode> transform) {
        return node instanceof TableNode parentNode && parentNode.children() != null
                ? parentNode.withChildren(parentNode.children().stream()
                        .map(child -> childCondition.test(child) ? transform.apply(parentNode, (C) child) : child)
                        .toList())
                : node;
    }
    
    private static boolean isEntity(TypeModel type) {
        return PojoMetadata.determineTableMapping(type).size() > 0;
    }

    private static boolean isEntityCollection(TypeModel type) {
        return type.getArrayComponentType() != null && isEntity(type.getArrayComponentType()) ||
                type.getTypeArgument() != null && isEntity(type.getTypeArgument());
    }

    private static boolean isCollection(TypeModel type) {
        return type.getArrayComponentType() != null || type.getTypeArgument() != null;
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
    
    private static RootNode removeScalarNodesFromTree(RootNode root) {
        return (RootNode) removeScalarNodesRecursively(root);
    }

    private static TableNode removeScalarNodesRecursively(TableNode node) {
        if (node.children() == null) {
            return node;
        }
        List<QueryNode> filtered = node.children().stream()
                .filter(child -> !(child instanceof ScalarNode))
                .map(child -> child instanceof TableNode tableChild ? removeScalarNodesRecursively(tableChild) : child)
                .toList();
        return node.withChildren(filtered);
    }

    private static TableInfo determineTableInfo(TypeModel type) {
        return PojoMetadata.determineTableMapping(type).stream().reduce((first, second) -> second)
                .map(m -> new TableInfo(m.schemaName, m.tableName))
                .orElseThrow(() -> new IllegalArgumentException("Missing @Table annotation on type " + type.getQualifiedName()));
    }

}
