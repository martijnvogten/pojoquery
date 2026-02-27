package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;


import static org.pojoquery.pipeline.QueryModel.determineIdField;

/**
 * Transform 7: @SubClasses (without @DiscriminatorColumn) → table-per-subclass inheritance.
 * Each subclass has its own table, LEFT JOINed by ID.
 */
public class TablePerSubclassTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        if (node.type() == null) {
            return node;
        }
        
        SubClasses subClassesAnn = node.type().getAnnotation(SubClasses.class);
        if (subClassesAnn == null) {
            return node;
        }
        
        // Skip if using single-table inheritance (handled by different transform)
        if (node.type().getAnnotation(DiscriminatorColumn.class) != null) {
            return node;
        }
        
        List<JoinedNode> newJoins = new ArrayList<>(node.joins());
        String idField = node.idFieldNames().isEmpty() ? "id" : node.idFieldNames().get(0);
        
        for (Class<?> subClass : subClassesAnn.value()) {
            TypeModel subType = new ReflectionTypeModel(subClass);
            AnnotationHelper.TableInfo subTable = AnnotationHelper.getTableInfo(subType);
            if (subTable == null) {
                continue;
            }
            
            String subAlias = AliasNaming.subclassAlias(node.alias(), subTable.name);
            
            // LEFT JOIN subclass_table ON parent.id = subclass.id
            SqlExpression condition = JoinConditions.forInheritance(node.alias(), subAlias, idField);
            
            // Collect subclass-specific fields (not inherited from parent)
            List<FieldSelection> subFields = new ArrayList<>();
            
            // Add ID field first (for null-checking to determine actual type)
            FieldModel idFieldModel = determineIdField(subType);
            subFields.add(TableNodeFactory.fieldSelection(subAlias, idFieldModel));
            
            // Add subclass-declared fields
            for (FieldModel f : FieldFilters.fieldsDeclaredIn(subType, node.type())) {
                if (FieldFilters.isSimple(f) && !f.getName().equals(idFieldModel.getName())) {
                    subFields.add(TableNodeFactory.fieldSelection(subAlias, f));
                }
            }
            
            String schemaName = (subTable.schema == null || subTable.schema.isEmpty()) ? null : subTable.schema;
            TableNode subNode = TableNode.simple(
                subAlias, subType, schemaName, subTable.name,
                subFields, List.of(), List.of(idField)
            );
            
            newJoins.add(new JoinedNode(JoinType.LEFT, condition, subNode, null, false));
        }
        
        return node.withJoins(newJoins);
    }
}
