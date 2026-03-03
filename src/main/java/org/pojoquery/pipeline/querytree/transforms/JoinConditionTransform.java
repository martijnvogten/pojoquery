package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Link;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
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
                          c.joinInfo().condition() == null);
    }
    
    private TableNode processNode(TableNode node, String rootAlias) {
        List<QueryNode> updatedChildren = node.children().stream()
            .map(c -> updateCondition(c, node))
            .toList();
        
        return node.withChildren(updatedChildren);
    }
    
    private QueryNode updateCondition(QueryNode child, TableNode parent) {
        JoinInfo joinInfo = child.joinInfo();
        if (joinInfo == null || joinInfo.condition() != null) {
            return child;
        }
        
        SqlExpression newCondition;
        FieldModel linkField = joinInfo.linkField();
        
        JoinCondition condAnn = linkField != null ? linkField.getAnnotation(JoinCondition.class) : null;
        if (condAnn != null) {
            String resolved = ExpressionResolver.resolve(
                condAnn.value(),
                parent.alias(),
                child.alias(),
                null
            );
            newCondition = new SqlExpression(resolved);
        } else if (joinInfo.isCollection()) {
            newCondition = JoinConditions.forCollection(
                parent.alias(), child.alias(), parent.type(), parent.tableInfo().tableName(), 
                getForeignLinkField(linkField));
        } else {
            newCondition = JoinConditions.forEntityReference(
                parent.alias(), child.alias(), linkField, child.type());
        }
        
        JoinInfo updatedJoinInfo = joinInfo.withCondition(newCondition);
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
        Link linkAnn = field.getAnnotation(Link.class);
        if (linkAnn != null && !Link.NONE.equals(linkAnn.foreignlinkfield())) {
            return linkAnn.foreignlinkfield();
        }
        return null;
    }
}
