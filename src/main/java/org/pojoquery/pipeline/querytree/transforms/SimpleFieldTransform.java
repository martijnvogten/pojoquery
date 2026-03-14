package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.PojoMetadata.determineSqlFieldName;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.pipeline.querytree.EmbeddedNode;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.FieldSelectionBase;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.pipeline.querytree.UnresolvedFieldSelection;

/**
 * Final transform that converts remaining UnresolvedFieldSelection to FieldSelection.
 * 
 * <p>This transform runs AFTER all relationship transforms (EntityReference, Collection, 
 * Embedded, Link) have had a chance to process their fields. Any remaining unresolved
 * fields are assumed to be simple column selections.</p>
 * 
 * <p>For each UnresolvedFieldSelection:</p>
 * <ul>
 *   <li>Creates SQL expression: {sourceAlias.columnName}</li>
 *   <li>Creates result alias: fieldAlias.fieldName</li>
 *   <li>Handles field prefix for embedded nodes</li>
 * </ul>
 */
public class SimpleFieldTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        List<FieldSelectionBase> newFields = new ArrayList<>();
        boolean changed = false;
        
        String fieldPrefix = "";
        if (node instanceof EmbeddedNode en && en.embedInfo() != null) {
            fieldPrefix = en.embedInfo().fieldPrefix();
        }
        
        for (FieldSelectionBase fsb : node.fields()) {
            if (fsb instanceof UnresolvedFieldSelection ufs) {
                // Convert to resolved FieldSelection
                String columnName = fieldPrefix + determineSqlFieldName(ufs.field());
                FieldSelection resolved = FieldSelection.column(
                    ufs.sourceAlias(), 
                    ufs.fieldAlias(), 
                    columnName, 
                    ufs.field()
                );
                newFields.add(resolved);
                changed = true;
            // } else if (fsb instanceof FieldSelection fs) {
            //     if (fs.columnName() == null) {
            //         // Default to the field name if columnName is not set
            //         String columnName = fieldPrefix + determineSqlFieldName(fs.field());
            //         fs = new FieldSelection(fs.alias(), columnName, fs.expression(), fs.field(), fs.customMapping());
            //         changed = true;
            //     }
            //     newFields.add(fs);
            } else {
                newFields.add(fsb);
            }
        }
        
        return changed ? node.withFields(newFields) : node;
    }
}
