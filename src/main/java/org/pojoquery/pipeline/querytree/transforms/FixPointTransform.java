package org.pojoquery.pipeline.querytree.transforms;

import org.pojoquery.pipeline.querytree.QueryTree;

/**
 * A composite transform that runs a set of transforms repeatedly until a fixed point is reached.
 * Useful for transforms that may introduce new nodes that need processing by other transforms.
 */
public class FixPointTransform implements QueryTreeTransform {
    
    private final QueryTreePipeline inner;
    
    public FixPointTransform(QueryTreeTransform... transforms) {
        QueryTreePipeline pipeline = QueryTreePipeline.empty();
        for (QueryTreeTransform t : transforms) {
            pipeline = pipeline.with(t);
        }
        this.inner = pipeline;
    }
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return inner.runToFixPoint(tree);
    }
}
