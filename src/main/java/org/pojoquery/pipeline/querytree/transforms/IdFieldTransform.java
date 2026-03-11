package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.annotations.Id;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;

/**
 * Collects ID field names for JoinedNodes.
 * 
 * <p>This transform runs AFTER annotation normalization transforms (e.g., JakartaAnnotationsTransform)
 * so that @Id annotations from JPA/Jakarta are properly recognized.</p>
 * 
 * <p>ID fields are used for:</p>
 * <ul>
 *   <li>Deduplication when collecting entities from result sets</li>
 *   <li>Join conditions for entity references</li>
 * </ul>
 */
public class IdFieldTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(node -> {
            if (node instanceof JoinedNode jn && (jn.idFieldNames() == null || jn.idFieldNames().isEmpty())) {
				List<String> idFields = node.fields().stream()
					.filter(f -> f.field().hasAnnotation(Id.class))
					.map(f -> {
						System.out.println("Found @Id field: " + f.field().getName() +
							" in node: " + jn.alias() + " of type: " + jn.type().getSimpleName());
						return f.field().getName();
					}).toList();
				// Collect ID field names from the node's type
                if (!idFields.isEmpty()) {
                    return jn.withIdFieldNames(idFields);
                }
            }
            return node;
        });
    }
}
