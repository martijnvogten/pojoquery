package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.PojoMetadata.determineIdFields;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.pojoquery.pipeline.querytree.FieldSelectionBase;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.pipeline.querytree.UnresolvedFieldSelection;
import org.pojoquery.typemodel.FieldModel;

/**
 * Adds id fields to TPS super/subclass nodes.
 * 
 * <p>TPS super/subclass tables need id fields for:</p>
 * <ul>
 *   <li>Join condition (shared primary key)</li>
 *   <li>Row processor to detect which subclass exists</li>
 * </ul>
 * 
 * <p>These fields may not be included by BasicTableTransform when superType
 * excludes them (id inherited from a higher class).</p>
 */
public class TPSIdFieldTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        // Only for TPS super/subclass nodes
        if (!(node instanceof JoinedNode jn) || (!jn.isSubClass() && !jn.isSuperClass())) {
            return node;
        }
        
        // Get existing field names
        Set<String> existingFieldNames = node.fields().stream()
            .map(FieldSelectionBase::field)
            .filter(f -> f != null)
            .map(FieldModel::getName)
            .collect(Collectors.toSet());
        
        // Add missing id fields
        List<FieldSelectionBase> newFields = new ArrayList<>(node.fields());
        boolean changed = false;
        
        for (FieldModel idField : determineIdFields(node.type())) {
            if (!existingFieldNames.contains(idField.getName())) {
                // Add id field at the beginning
                newFields.add(0, UnresolvedFieldSelection.of(node.alias(), node.alias(), idField));
                changed = true;
            }
        }
        
        return changed ? node.withFields(newFields) : node;
    }
}
