package org.pojoquery.pipeline.querytree.transforms;

import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;

/**
 * Transform 10: @Column, @FieldName, @JoinColumn → override column names.
 * 
 * Note: Basic column names are already handled in TableNodeFactory.fieldSelection().
 * Join column names (@JoinColumn) are now handled directly in JoinConditionTransform
 * via JoinConditions.determineFkColumn(), so this transform is now a no-op for joins.
 */
public class ColumnNameTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        // Column name overrides are now handled at construction time:
        // - Field columns: TableNodeFactory.fieldSelection() uses AnnotationHelper.getColumnName()
        // - Join FK columns: JoinConditions.determineFkColumn() uses AnnotationHelper.getJoinColumnName()
        // This transform is kept for backward compatibility but is effectively a no-op.
        return node;
    }
}
