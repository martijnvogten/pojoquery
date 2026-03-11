package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.annotations.Transient;
import org.pojoquery.pipeline.querytree.FieldSelectionBase;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;

/**
 * Removes fields annotated with @Transient from query tree nodes.
 * 
 * <p>This transform runs AFTER annotation normalization transforms (e.g., JakartaAnnotationsTransform)
 * so that @Transient annotations from JPA/Jakarta are properly recognized.</p>
 * 
 * <p>Transient fields are excluded from:</p>
 * <ul>
 *   <li>SELECT clause generation</li>
 *   <li>Result set mapping</li>
 * </ul>
 */
public class RemoveTransientFieldsTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::removeTransientFields);
    }
    
    private TableNode removeTransientFields(TableNode node) {
        List<FieldSelectionBase> filtered = node.fields().stream()
            .filter(f -> f.field() == null || !f.field().hasAnnotation(Transient.class))
            .toList();
        
        if (filtered.size() != node.fields().size()) {
            return node.withFields(filtered);
        }
        return node;
    }
}
