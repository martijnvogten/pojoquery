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
        String rootAlias = ((TableNode) tree.root()).alias();
        return tree.transformTableNodes(node -> processNode(node, rootAlias));
    }
    
    private TableNode processNode(TableNode node, String rootAlias) {
        TypeModel type = node.type();
        
        if (type == null) {
            return node;
        }
        
        // Walk up class hierarchy looking for @Table annotations
        List<TableMapping> mappings = determineTableMapping(type);
        if (mappings.size() <= 1) {
            return node; // No superclass tables
        }
        
        // Add superclass tables as INNER JOINs
        List<JoinedNode> newJoins = new ArrayList<>(node.joins());
        List<FieldSelection> nodeFields = new ArrayList<>(node.fields());
        
        // Process from most specific to most general (skip the first which is the node's own table)
        for (int i = mappings.size() - 2; i >= 0; i--) {
            TableMapping parentMapping = mappings.get(i);
            
            String parentAlias = AliasNaming.superclassAlias(node.alias(), rootAlias, parentMapping.tableName);
            
            // Skip if already joined (idempotence)
            if (alreadyJoined(newJoins, parentAlias)) {
                continue;
            }
            
            FieldModel idFieldModel = determineIdField(parentMapping.type);
            String idField = determineSqlFieldName(idFieldModel);
            
            // Collect parent's fields, aliased to child's alias prefix
            List<FieldSelection> parentFields = new ArrayList<>();
            for (FieldModel f : FieldFilters.simpleFields(parentMapping.type)) {
                // Skip if already in node fields (field override in subclass)
                if (!hasField(nodeFields, f.getName())) {
                    // Use node.alias() as alias prefix so fields resolve to child's namespace
                    parentFields.add(TableNodeFactory.fieldSelection(parentAlias, node.alias(), f));
                }
            }
            
            // INNER JOIN parent ON parent.id = child.id (superclass first in condition)
            SqlExpression condition = JoinConditions.forInheritance(parentAlias, node.alias(), idField);
            
            String schemaName = (parentMapping.schemaName == null || parentMapping.schemaName.isEmpty()) 
                ? null : parentMapping.schemaName;
            // Set type to null - superclass table nodes are just for field selection,
            // not for further structural expansion (no @SubClasses expansion)
            TableNode parentNode = TableNode.simple(
                parentAlias, null, schemaName, parentMapping.tableName,
                parentFields, List.of(), List.of(idField)
            );
            
            // Root uses INNER JOIN (entity must exist), nested uses LEFT JOIN (may have no children)
            JoinType joinType = node.alias().equals(rootAlias) ? JoinType.INNER : JoinType.LEFT;
            
            newJoins.add(new JoinedNode(joinType, condition, parentNode, null, false));
        }
        
        return node.withJoins(newJoins);
    }
    
    private boolean alreadyJoined(List<JoinedNode> joins, String alias) {
        return joins.stream()
            .anyMatch(j -> j.node() instanceof TableNode t && t.alias().equals(alias));
    }
    
    private boolean hasField(List<FieldSelection> fields, String fieldName) {
        return fields.stream().anyMatch(f -> f.field() != null && f.field().getName().equals(fieldName));
    }
}
