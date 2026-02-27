package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
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
            // ═══════════════════════════════════════════════════════════════
            // PHASE 1: STRUCTURE (tables and joins)
            // ═══════════════════════════════════════════════════════════════
            
            // 1. Create root table node from @Table, collect @Id fields, simple fields
            new BasicTableTransform(),
            
            // 2. Handle superclass tables: Parent @Table → INNER JOIN
            new SuperclassTableTransform(),
            
            // 3. Entity references → LEFT JOIN (one-to-one)
            new EntityReferenceTransform(),
            
            // 4. List<Entity> without @Link → one-to-many joins
            new CollectionTransform(),
            
            // 5. @Link(linktable) → many-to-many via junction table
            new LinkTableTransform(),
            
            // 6. @Link(fetchColumn) → value collection from link table
            new ValueCollectionTransform(),
            
            // 7. Validate no cyclic entity references
            new CycleDetectionTransform(),
            
            // ═══════════════════════════════════════════════════════════════
            // PHASE 2: INHERITANCE
            // ═══════════════════════════════════════════════════════════════
            
            // 8. @SubClasses (without @DiscriminatorColumn) → table-per-subclass
            new TablePerSubclassTransform(),
            
            // 9. @SubClasses + @DiscriminatorColumn → single-table inheritance
            new SingleTableInheritanceTransform(),
            
            // ═══════════════════════════════════════════════════════════════
            // PHASE 3: FIELD MODIFIERS
            // ═══════════════════════════════════════════════════════════════
            
            // 10. @Embedded → inline fields with prefix
            new EmbeddedTransform(),
            
            // 11. @Column, @JoinColumn → column name overrides
            new ColumnNameTransform(),
            
            // 12. @Select → custom SQL expression
            new SelectExpressionTransform(),
            
            // 13. @Aggregate → aggregate SQL expression
            new AggregateExpressionTransform(),
            
            // 14. @Other → dynamic column capture
            new OtherFieldTransform(),
            
            // ═══════════════════════════════════════════════════════════════
            // PHASE 4: CUSTOM JOINS
            // ═══════════════════════════════════════════════════════════════
            
            // 15. @JoinCondition → override auto-generated join conditions
            new JoinConditionTransform(),
            
            // 16. @Join, @Joins → class-level extra joins
            new ClassLevelJoinTransform(),
            
            // 17. @Subquery → derived table (subquery) joins
            new SubqueryTransform(),
            
            // ═══════════════════════════════════════════════════════════════
            // PHASE 5: QUERY MODIFIERS
            // ═══════════════════════════════════════════════════════════════
            
            // 18. @GroupBy, @OrderBy → query-level clauses (includes auto GROUP BY)
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
                new QueryTree(sourceTree.root(), type, sourceTree.groupBy(), 
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
        QueryTree tree = QueryTree.of(
            TableNode.simple("_placeholder_", type, null, "_placeholder_", 
                List.of(), List.of(), List.of()),
            type
        );
        
        // Apply transforms in sequence
        for (QueryTreeTransform transform : transforms) {
            tree = transform.apply(tree);
        }
        
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
