package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;

/**
 * Transform 10: @Column, @FieldName, @JoinColumn → override column names.
 * 
 * Note: Basic column names are already handled in TableNodeFactory.fieldSelection().
 * This transform handles @JoinColumn for FK columns in join conditions.
 */
public class ColumnNameTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        // Update join conditions that might use @JoinColumn
        List<QueryNode> updatedChildren = node.children().stream()
            .map(this::updateJoinColumnName)
            .toList();
        
        return node.withChildren(updatedChildren);
    }
    
    private QueryNode updateJoinColumnName(QueryNode child) {
        JoinInfo joinInfo = child.joinInfo();
        if (joinInfo == null || joinInfo.linkField() == null || joinInfo.condition() == null) {
            return child;
        }
        
        // Check if linkField has @JoinColumn override
        String joinColumn = AnnotationHelper.getJoinColumnName(joinInfo.linkField());
        if (joinColumn == null) {
            return child;
        }
        
        // The join condition was built with default FK naming (field_id)
        // We need to rewrite it to use the @JoinColumn name
        String defaultFk = joinInfo.linkField().getName() + "_id";
        String currentCondition = joinInfo.condition().getSql();
        
        if (currentCondition.contains(defaultFk)) {
            String newCondition = currentCondition.replace(defaultFk, joinColumn);
            JoinInfo updatedJoinInfo = joinInfo.withCondition(new SqlExpression(newCondition));
            return updateNodeJoinInfo(child, updatedJoinInfo);
        }
        
        return child;
    }
    
    private QueryNode updateNodeJoinInfo(QueryNode node, JoinInfo newJoinInfo) {
        if (node instanceof EmptyTableNode empty) {
            return empty.withJoinInfo(newJoinInfo);
        } else if (node instanceof JoinedNode table) {
            return table.withJoinInfo(newJoinInfo);
        }
        return node;
    }
}
