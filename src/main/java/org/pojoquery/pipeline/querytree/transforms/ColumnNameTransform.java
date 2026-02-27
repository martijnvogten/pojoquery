package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.querytree.JoinedNode;
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
        List<JoinedNode> updatedJoins = node.joins().stream()
            .map(this::updateJoinColumnName)
            .toList();
        
        return node.withJoins(updatedJoins);
    }
    
    private JoinedNode updateJoinColumnName(JoinedNode join) {
        if (join.linkField() == null || join.condition() == null) {
            return join;
        }
        
        // Check if linkField has @JoinColumn override
        String joinColumn = AnnotationHelper.getJoinColumnName(join.linkField());
        if (joinColumn == null) {
            return join;
        }
        
        // The join condition was built with default FK naming (field_id)
        // We need to rewrite it to use the @JoinColumn name
        String defaultFk = join.linkField().getName() + "_id";
        String currentCondition = join.condition().getSql();
        
        if (currentCondition.contains(defaultFk)) {
            String newCondition = currentCondition.replace(defaultFk, joinColumn);
            return join.withCondition(new SqlExpression(newCondition));
        }
        
        return join;
    }
}
