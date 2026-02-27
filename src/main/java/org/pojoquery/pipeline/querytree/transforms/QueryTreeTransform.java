package org.pojoquery.pipeline.querytree.transforms;

import org.pojoquery.pipeline.querytree.QueryTree;

/**
 * Transforms a QueryTree, returning a new (possibly modified) tree.
 * Implementations handle specific features/annotations.
 * 
 * <p>Transforms are applied in sequence by {@link QueryTreePipeline}.
 * Each transform should be focused on a single concern.</p>
 */
@FunctionalInterface
public interface QueryTreeTransform {
    
    /**
     * Applies this transformation to the query tree.
     * 
     * @param tree The input tree
     * @return A new tree with the transformation applied (or the same tree if unchanged)
     */
    QueryTree apply(QueryTree tree);
    
    /**
     * Compose transforms: this then other.
     */
    default QueryTreeTransform andThen(QueryTreeTransform other) {
        return tree -> other.apply(this.apply(tree));
    }
    
    /**
     * Returns a no-op transform that returns the input unchanged.
     */
    static QueryTreeTransform identity() {
        return tree -> tree;
    }
}
