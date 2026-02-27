package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;


import org.pojoquery.SqlExpression;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

import static org.pojoquery.pipeline.QueryModel.determineTableMapping;
import static org.pojoquery.pipeline.QueryModel.determineSqlFieldName;
import static org.pojoquery.pipeline.QueryModel.determineIdField;

/**
 * Transform 2: Handles class hierarchies where parent classes have @Table.
 * Each @Table in hierarchy → INNER JOIN on shared ID.
 */
public class SuperclassTableTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        TableNode root = (TableNode) tree.root();
        TypeModel type = root.type();
        
        if (type == null) {
            return tree;
        }
        
        // Walk up class hierarchy looking for @Table annotations
        List<TableMapping> mappings = determineTableMapping(type);
        if (mappings.size() <= 1) {
            return tree; // No superclass tables
        }
        
        // Add superclass tables as INNER JOINs
        List<JoinedNode> newJoins = new ArrayList<>(root.joins());
        List<FieldSelection> rootFields = new ArrayList<>(root.fields());
        
        // Process from most specific to most general (skip the first which is root)
        for (int i = mappings.size() - 2; i >= 0; i--) {
            TableMapping parentMapping = mappings.get(i);
            
            String parentAlias = AliasNaming.superclassAlias(root.alias(), root.alias(), parentMapping.tableName);
            FieldModel idFieldModel = determineIdField(parentMapping.type);
            String idField = determineSqlFieldName(idFieldModel);
            
            // Collect parent's fields
            List<FieldSelection> parentFields = new ArrayList<>();
            for (FieldModel f : FieldFilters.simpleFields(parentMapping.type)) {
                // Skip if already in root fields (field override in subclass)
                if (!hasField(rootFields, f.getName())) {
                    parentFields.add(TableNodeFactory.fieldSelection(parentAlias, f));
                }
            }
            
            // INNER JOIN parent ON child.id = parent.id
            SqlExpression condition = JoinConditions.forInheritance(root.alias(), parentAlias, idField);
            
            String schemaName = (parentMapping.schemaName == null || parentMapping.schemaName.isEmpty()) 
                ? null : parentMapping.schemaName;
            TableNode parentNode = TableNode.simple(
                parentAlias, parentMapping.type, schemaName, parentMapping.tableName,
                parentFields, List.of(), List.of(idField)
            );
            
            newJoins.add(new JoinedNode(JoinType.INNER, condition, parentNode, null, false));
        }
        
        TableNode newRoot = root.withJoins(newJoins);
        return QueryTree.of(newRoot, tree.resultType());
    }
    
    private boolean hasField(List<FieldSelection> fields, String fieldName) {
        return fields.stream().anyMatch(f -> f.field() != null && f.field().getName().equals(fieldName));
    }
}
