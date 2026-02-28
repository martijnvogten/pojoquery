package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.pipeline.querytree.QueryTree;

/**
 * Recursively expands the query tree structure until fixed point.
 * 
 * <p>This transform applies all structure-related transforms (superclass tables,
 * entity references, collections, link tables, and subclass expansion) in a loop
 * until no new tables are added. This ensures that:</p>
 * 
 * <ul>
 *   <li>Joined tables get their subclasses expanded</li>
 *   <li>Subclass tables get their entity references and collections</li>
 *   <li>Those joined tables get their subclasses, and so on...</li>
 * </ul>
 * 
 * <p>Example: Article → List&lt;Comment&gt; → Comment has @SubClasses({SpecialComment})
 * → SpecialComment has Author reference → all get properly expanded.</p>
 */
public class StructureExpansionTransform implements QueryTreeTransform {
    
    private static final int MAX_ITERATIONS = 100; // Safety limit
    
    private final List<QueryTreeTransform> structureTransforms;
    
    public StructureExpansionTransform() {
        // Order matters: Collections/entity refs must be processed BEFORE subclass expansion
        // so that entity refs appear before subclasses in the join list (matching QueryModel)
        this.structureTransforms = List.of(
            new SuperclassTableTransform(),
            new CollectionTransform(),
            new LinkTableTransform(),
            new ValueCollectionTransform(),
            new EntityReferenceTransform(),
            new SubclassExpansionTransform()
        );
    }
    
    @Override
    public QueryTree apply(QueryTree tree) {
        int iterations = 0;
        
        while (iterations < MAX_ITERATIONS) {
            QueryTree before = tree;
            
            // Apply all structure transforms
            for (QueryTreeTransform transform : structureTransforms) {
                tree = transform.apply(tree);
            }
            
            // Fixed point reached - no changes made
            if (tree.equals(before)) {
                break;
            }
            
            iterations++;
        }
        
        if (iterations >= MAX_ITERATIONS) {
            throw new IllegalStateException(
                "Structure expansion exceeded " + MAX_ITERATIONS + " iterations. " +
                "Possible cycle in entity relationships.");
        }
        
        return tree;
    }
}
