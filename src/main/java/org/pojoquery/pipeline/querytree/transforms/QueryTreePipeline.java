package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Builds QueryTree through a pipeline of transforms.
 * 
 * <p>The pipeline applies transforms in sequence, each handling a specific
 * feature or annotation. This allows for modular, testable query building.</p>
 * 
 * <p>Usage:</p>
 * <pre>
 * QueryTree tree = QueryTreePipeline.standard().build(MyEntity.class);
 * </pre>
 */
public class QueryTreePipeline {
    
    private final List<QueryTreeTransform> transforms;
    
    public QueryTreePipeline(List<QueryTreeTransform> transforms) {
        this.transforms = new ArrayList<>(transforms);
    }
    
    /**
     * Creates the standard pipeline with all transforms.
     */
    public static QueryTreePipeline standard() {
        return new QueryTreePipeline(List.of(
            new CreateRootTransform(),
            new SuperclassTableTransform(),
            new SubclassExpansionTransform(),
            new BasicTableTransform(),
            new EntityReferenceTransform(),
            new CollectionTransform(),
            new JoinTableTransform(),
            new JoinConditionTransform(),
            new SingleTableInheritanceTransform(),
            
            // ═══════════════════════════════════════════════════════════════
            // PHASE 3: FIELD MODIFIERS
            // ═══════════════════════════════════════════════════════════════
            
            // 5. @Embedded → inline fields with prefix
            new EmbeddedTransform(),
            
            // 6. @Column, @JoinColumn → column name overrides
            new ColumnNameTransform(),
            
            // 7. @Select → custom SQL expression
            new SelectExpressionTransform(),
            
            // 8. @Aggregate → aggregate SQL expression
            new AggregateExpressionTransform(),
            
            // 9. @Other → dynamic column capture
            new OtherFieldTransform(),
            
            // ═══════════════════════════════════════════════════════════════
            // PHASE 4: CUSTOM JOINS
            // ═══════════════════════════════════════════════════════════════
            
            // 10. @JoinCondition → override auto-generated join conditions
            new JoinConditionTransform(),
            
            // 11. @Join, @Joins → class-level extra joins
            new ClassLevelJoinTransform(),
            
            // 12. @Subquery → derived table (subquery) joins
            new SubqueryTransform(),
            
            // ═══════════════════════════════════════════════════════════════
            // PHASE 5: QUERY MODIFIERS
            // ═══════════════════════════════════════════════════════════════
            
            // 13. @GroupBy, @OrderBy → query-level clauses (includes auto GROUP BY)
            new GroupByOrderByTransform()
            
            // Note: FromProjectionTransform and JoinPruningTransform are NOT included
            // in the standard pipeline - they are used via forProjection() instead
        ));
    }
    
    /**
     * Creates a minimal pipeline for subqueries.
     * Subqueries typically need fewer transforms.
     */
    public static QueryTreePipeline forSubquery() {
        return new QueryTreePipeline(List.of(
            new BasicTableTransform(),
            new SuperclassTableTransform(),
            new EntityReferenceTransform(),
            new CollectionTransform(),
            new SelectExpressionTransform(),
            new AggregateExpressionTransform(),
            new GroupByOrderByTransform()
        ));
    }
    
    /**
     * Creates an empty pipeline (useful for testing).
     */
    public static QueryTreePipeline empty() {
        return new QueryTreePipeline(List.of());
    }
    
    /**
     * Builds a QueryTree for the given class.
     * Automatically handles @From projection if present.
     * 
     * @param clazz The entity class
     * @return The built QueryTree
     */
    public QueryTree build(Class<?> clazz) {
        return build(new ReflectionTypeModel(clazz));
    }
    
    /**
     * Builds a QueryTree for the given type.
     * Automatically handles @From projection if present.
     * 
     * @param type The entity type
     * @return The built QueryTree
     */
    public QueryTree build(TypeModel type) {
        // Check for @From - if present, use FromProjectionTransform
        org.pojoquery.annotations.From fromAnn = type.getAnnotation(org.pojoquery.annotations.From.class);
        if (fromAnn != null) {
            // Build from source type, then apply projection
            TypeModel sourceType = new ReflectionTypeModel(fromAnn.value());
            QueryTree sourceTree = buildDirect(sourceType);
            return new FromProjectionTransform().apply(
                new QueryTree(type, sourceTree.root(), sourceTree.groupBy(), 
                    sourceTree.orderBy(), sourceTree.wheres())
            );
        }
        
        return buildDirect(type);
    }
    
    /**
     * Builds a QueryTree directly without checking @From.
     */
    private QueryTree buildDirect(TypeModel type) {
        // Start with an empty tree (first transform creates the actual structure)
        QueryTree tree = QueryTree.of(type);
        
        // Apply transforms to fixpoint (repeatedly until no changes)
        return runToFixPoint(tree);
    }
    
    /**
     * Runs all transforms repeatedly until a fixed point is reached
     * (no transform changes the tree anymore).
     * Useful for testing individual transforms in isolation.
     * 
     * @param tree The starting tree
     * @return The tree after reaching fixed point
     */
    public QueryTree runToFixPoint(QueryTree tree) {
        return runToFixPoint(tree, 1000);
    }
    
    public QueryTree runToFixPoint(QueryTree tree, int maxIterations) {
        QueryTree before;
        int iterations = 0;
        do {
            before = tree;
            for (QueryTreeTransform transform : transforms) {
                tree = transform.apply(tree);
            }
            iterations++;
            if (iterations >= maxIterations) {
                throw new IllegalStateException("Max iterations reached without reaching fixed point");
            }
        } while (!tree.equals(before));
        return tree;
    }
    
    /**
     * Returns a new pipeline with an additional transform at the end.
     */
    public QueryTreePipeline with(QueryTreeTransform transform) {
        List<QueryTreeTransform> newTransforms = new ArrayList<>(transforms);
        newTransforms.add(transform);
        return new QueryTreePipeline(newTransforms);
    }
    
    /**
     * Returns a new pipeline with the specified transform replaced.
     */
    public QueryTreePipeline replacing(Class<? extends QueryTreeTransform> toReplace, 
                                        QueryTreeTransform replacement) {
        List<QueryTreeTransform> newTransforms = new ArrayList<>();
        for (QueryTreeTransform t : transforms) {
            if (toReplace.isInstance(t)) {
                newTransforms.add(replacement);
            } else {
                newTransforms.add(t);
            }
        }
        return new QueryTreePipeline(newTransforms);
    }
    
    /**
     * Returns a new pipeline without the specified transform.
     */
    public QueryTreePipeline without(Class<? extends QueryTreeTransform> toRemove) {
        List<QueryTreeTransform> newTransforms = transforms.stream()
            .filter(t -> !toRemove.isInstance(t))
            .toList();
        return new QueryTreePipeline(newTransforms);
    }
    
    /**
     * Returns the list of transforms in this pipeline.
     */
    public List<QueryTreeTransform> getTransforms() {
        return List.copyOf(transforms);
    }
}
