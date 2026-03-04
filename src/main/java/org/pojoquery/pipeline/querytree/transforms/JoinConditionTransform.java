package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.JoinCondition;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;

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
        FieldModel linkField = joinInfo.linkField();
        
        org.pojoquery.annotations.JoinCondition condAnn = linkField != null ? linkField.getAnnotation(org.pojoquery.annotations.JoinCondition.class) : null;
        if (condAnn != null) {
            // Custom join condition annotation - use Custom variant
            String resolved = ExpressionResolver.resolve(
                condAnn.value(),
                parent.alias(),
                child.alias(),
                null
            );
            newCondition = new JoinCondition.Custom(new SqlExpression(resolved));
        } else if (joinInfo.isCollection()) {
            // One-to-many: FK column is in child table
            newCondition = JoinConditions.forCollectionStructured(
                parent.type(), parent.tableInfo().tableName(), 
                getForeignLinkField(linkField));
        } else {
            // Entity reference (many-to-one): FK column is in parent table
            newCondition = JoinConditions.forEntityReferenceStructured(linkField, child.type());
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

    private String getForeignLinkField(FieldModel field) {
        if (field == null) return null;
        org.pojoquery.annotations.Link linkAnn = field.getAnnotation(org.pojoquery.annotations.Link.class);
        if (linkAnn != null && !org.pojoquery.annotations.Link.NONE.equals(linkAnn.foreignlinkfield())) {
            return linkAnn.foreignlinkfield();
        }
        return null;
    }
}
