package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.annotations.FieldName;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.FieldSelectionBase;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.pipeline.querytree.UnresolvedFieldSelection;

/**
 * \@FieldName override column names.
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
        List<FieldSelectionBase> newFields = new ArrayList<>();
        boolean changed = false;
        
        for (UnresolvedFieldSelection fsb : node.unresolvedFields()) {
            if (fsb.field().hasAnnotation(FieldName.class)) {
                String columnName = fsb.field().getAnnotationAttributeValue(FieldName.class, "value", String.class);
                FieldSelection renamed = FieldSelection.column(
                    fsb.sourceAlias(), 
                    fsb.fieldAlias(), 
                    columnName, 
                    fsb.field()
                );
                newFields.add(renamed);
                changed = true;
            } else {
                newFields.add(fsb);
            }
        }
        
        return changed ? node.withFields(newFields) : node;
    }
}
