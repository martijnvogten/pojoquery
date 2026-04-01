package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Link;
import org.pojoquery.pipeline.PojoMetadata;
import org.pojoquery.pipeline.querytree.EmbeddedNode;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.JoinCondition;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Transform that resolves join conditions for nodes that don't have them yet.
 */
public class JoinConditionTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        String rootAlias = ((TableNode) tree.root()).alias();
        return tree.transformNodes(
            n -> n instanceof TableNode && hasChildrenNeedingConditions((TableNode) n), 
            (TableNode n) -> processNode(n, rootAlias));
    }
    
    private boolean hasChildrenNeedingConditions(TableNode node) {
        return node.children().stream()
            .anyMatch(c -> c.joinInfo() != null && 
                          c.joinInfo().joinCondition() == null);
    }
    
    private TableNode processNode(TableNode node, String rootAlias) {
        List<QueryNode> updatedChildren = node.children().stream()
            .map(c -> updateCondition(c, node))
            .toList();
        
        return node.withChildren(updatedChildren);
    }
    
    private QueryNode updateCondition(QueryNode child, TableNode parent) {
        JoinInfo joinInfo = child.joinInfo();
        if (joinInfo == null || joinInfo.joinCondition() != null) {
            return child;
        }
        
        JoinCondition newCondition;
        FieldModel parentField = joinInfo.linkField();
        if (parentField == null) {
            // This transform only handles join conditions from actual Java properties.
            return child;
        }

        var condAnnOpt = parentField.getAnnotation(org.pojoquery.annotations.JoinCondition.class);
        if (condAnnOpt.isPresent()) {
            // Custom join condition annotation - use Custom variant
            String resolved = ExpressionResolver.resolve(
                condAnnOpt.get().getStringValue().orElseThrow(),
                parent.alias(),
                child.alias(),
                null,
                null
            );
            newCondition = new JoinCondition.Custom(new SqlExpression(resolved));
        } else if (joinInfo.joinTableInfo() != null) {
            // Many-to-many: FK is in the link table, no direct join condition needed
            // The join condition will be generated separately for the link table
            return child;
        } else if (joinInfo.isCollection()) {
            // One-to-many: FK column is in child table

            String fkColumn = parentField.getAnnotation(Link.class)
                .flatMap(linkAnn -> linkAnn.getStringValue("foreignlinkfield"))
                .filter(s -> !s.isEmpty())
                .orElse(parent.tableInfo().tableName() + "_id"); // Default FK column name if not specified
            String targetId = PojoMetadata.determineSqlFieldName(determineIdField(parent.type()));
            ; // Ensure link field is present for collection joins
            newCondition = new JoinCondition.ForeignKeyInChild(fkColumn, targetId);
        } else {
            // Entity reference (many-to-one): FK column is in parent table
            String fkColumn = PojoMetadata.determineLinkFieldName(parentField);
            String targetId = PojoMetadata.determineSqlFieldName(determineIdField(child.type()));
            JoinCondition.ForeignKeyInParent baseCondition = new JoinCondition.ForeignKeyInParent(fkColumn, targetId);
            // If parent is embedded, prepend the embedded prefix to the FK column
            if (parent instanceof EmbeddedNode en) {
                String prefixedFkColumn = en.embedInfo().fieldPrefix() + baseCondition.foreignKeyColumn();
                newCondition = new JoinCondition.ForeignKeyInParent(prefixedFkColumn, baseCondition.referencedColumn());
            } else {
                newCondition = baseCondition;
            }
        }
        
        JoinInfo updatedJoinInfo = joinInfo.withJoinCondition(newCondition);
        return updateNodeJoinInfo(child, updatedJoinInfo);
    }
    
    private QueryNode updateNodeJoinInfo(QueryNode node, JoinInfo newJoinInfo) {
        if (node instanceof EmptyTableNode empty) {
            return empty.withJoinInfo(newJoinInfo);
        } else if (node instanceof JoinedNode table) {
            return table.withJoinInfo(newJoinInfo);
        }
        // For other node types, return as-is (they should implement withJoinInfo if needed)
        return node;
    }

    private FieldModel determineIdField(TypeModel type) {
        return org.pojoquery.pipeline.PojoMetadata.determineIdField(type);
    }  

}
