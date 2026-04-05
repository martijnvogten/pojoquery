package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;

/**
 * A configurable pipeline of transform steps for building query trees.
 * The pipeline is immutable - modification methods return new instances.
 * 
 * <p>Example usage:
 * <pre>
 * TransformPipeline pipeline = TransformPipeline.defaultPipeline()
 *     .insertBefore(AddScalarValues.class, MyCustomTransform.class)
 *     .remove(CheckForCycles.class);
 * 
 * RootNode result = pipeline.apply(initialTree);
 * </pre>
 */
public class TransformPipeline {

    public static abstract class TransformStep {
        public abstract QueryNode transform(QueryNode node);
    }

    public static abstract class RecursiveTransform extends TransformStep {
    }
   
    private final List<Class<? extends TransformStep>> steps;
    
    public TransformPipeline(List<Class<? extends TransformStep>> steps) {
        this.steps = new ArrayList<>(steps);
    }
    
    /**
     * Returns the default pipeline with all standard transforms.
     */
    public static TransformPipeline defaultPipeline() {
        return new TransformPipeline(List.of(
            Transforms.CheckForCycles.class,
            Transforms.AddDeclaredFields.class,
            Transforms.AddOtherFields.class,
            Transforms.AddIdFieldToSubClassTableNodes.class,
            Transforms.AddSuperClassTableNodes.class,
            Transforms.AddEmbeddedEntities.class,
            Transforms.AddValueCollections.class,
            Transforms.AddJoinTableEntityCollections.class,
            Transforms.AddEntityCollections.class,
            Transforms.AddEntityReferences.class,
            Transforms.AddSubClassTableNodes.class,
            Transforms.AddIdFields.class,
            Transforms.AddScalarValues.class,
            Transforms.ApplyCustomJoinTableColumnNames.class,
            Transforms.ApplyCustomForeignKeyColumnNames.class,
            Transforms.ApplyCustomValueCollectionJoinConditions.class,
            Transforms.ApplyCustomJoinTableJoinConditions.class,
            Transforms.ApplyCustomJoinConditions.class,
            Transforms.ApplyCustomColumnNames.class,
            Transforms.ApplyCustomSelectExpressions.class,
            Transforms.ApplyDiscriminatorColumnFromParent.class,
            Transforms.ApplyDefaultIdFieldNames.class,
            Transforms.ApplyDefaultForeignKeyColumnNames.class,
            Transforms.ApplyDefaultJoinConditions.class,
            Transforms.ApplyDefaultValueCollectionExpressions.class,
            Transforms.ApplyEmbeddedFieldsColumnPrefix.class,
            Transforms.ApplyDefaultColumnNames.class,
            Transforms.ApplyDefaultPrimaryKeyExpressions.class,
            Transforms.ApplyDefaultDiscriminatorExpressions.class,
            Transforms.ApplyDefaultScalarExpressions.class,
            Transforms.MakeSingleIdFieldsAutoIncrement.class,
            Transforms.ApplyClassLevelGroupBy.class,
            Transforms.ApplyClassLevelOrderBy.class,
            Transforms.AddDefaultValueTransformers.class
        ));
    }
    
    /**
     * Insert a new transform step before an existing one.
     * @param existing the class of the existing step to insert before
     * @param newStep the class of the new step to insert
     * @return a new pipeline with the step inserted
     * @throws IllegalArgumentException if the existing step is not found
     */
    public TransformPipeline insertBefore(
            Class<? extends TransformStep> existing, 
            Class<? extends TransformStep> newStep) {
        int idx = steps.indexOf(existing);
        if (idx < 0) {
            throw new IllegalArgumentException("Transform step not found: " + existing.getSimpleName());
        }
        List<Class<? extends TransformStep>> newSteps = new ArrayList<>(steps);
        newSteps.add(idx, newStep);
        return new TransformPipeline(newSteps);
    }
    
    /**
     * Insert a new transform step after an existing one.
     * @param existing the class of the existing step to insert after
     * @param newStep the class of the new step to insert
     * @return a new pipeline with the step inserted
     * @throws IllegalArgumentException if the existing step is not found
     */
    public TransformPipeline insertAfter(
            Class<? extends TransformStep> existing, 
            Class<? extends TransformStep> newStep) {
        int idx = steps.indexOf(existing);
        if (idx < 0) {
            throw new IllegalArgumentException("Transform step not found: " + existing.getSimpleName());
        }
        List<Class<? extends TransformStep>> newSteps = new ArrayList<>(steps);
        newSteps.add(idx + 1, newStep);
        return new TransformPipeline(newSteps);
    }
    
    /**
     * Replace an existing transform step with a new one.
     * @param existing the class of the existing step to replace
     * @param newStep the class of the new step
     * @return a new pipeline with the step replaced
     * @throws IllegalArgumentException if the existing step is not found
     */
    public TransformPipeline replace(
            Class<? extends TransformStep> existing, 
            Class<? extends TransformStep> newStep) {
        int idx = steps.indexOf(existing);
        if (idx < 0) {
            throw new IllegalArgumentException("Transform step not found: " + existing.getSimpleName());
        }
        List<Class<? extends TransformStep>> newSteps = new ArrayList<>(steps);
        newSteps.set(idx, newStep);
        return new TransformPipeline(newSteps);
    }
    
    /**
     * Remove a transform step from the pipeline.
     * @param step the class of the step to remove
     * @return a new pipeline without the step
     * @throws IllegalArgumentException if the step is not found
     */
    public TransformPipeline remove(Class<? extends TransformStep> step) {
        int idx = steps.indexOf(step);
        if (idx < 0) {
            throw new IllegalArgumentException("Transform step not found: " + step.getSimpleName());
        }
        List<Class<? extends TransformStep>> newSteps = new ArrayList<>(steps);
        newSteps.remove(idx);
        return new TransformPipeline(newSteps);
    }
    
    /**
     * Append a transform step to the end of the pipeline.
     * @param step the class of the step to append
     * @return a new pipeline with the step appended
     */
    public TransformPipeline append(Class<? extends TransformStep> step) {
        List<Class<? extends TransformStep>> newSteps = new ArrayList<>(steps);
        newSteps.add(step);
        return new TransformPipeline(newSteps);
    }
    
    /**
     * Prepend a transform step to the beginning of the pipeline.
     * @param step the class of the step to prepend
     * @return a new pipeline with the step prepended
     */
    public TransformPipeline prepend(Class<? extends TransformStep> step) {
        List<Class<? extends TransformStep>> newSteps = new ArrayList<>(steps);
        newSteps.add(0, step);
        return new TransformPipeline(newSteps);
    }
    
    /**
     * Check if the pipeline contains a specific transform step.
     * @param step the class of the step to check for
     * @return true if the pipeline contains the step
     */
    public boolean contains(Class<? extends TransformStep> step) {
        return steps.contains(step);
    }
    
    /**
     * Get the list of transform step classes in this pipeline.
     * @return an unmodifiable view of the steps
     */
    public List<Class<? extends TransformStep>> getSteps() {
        return List.copyOf(steps);
    }
    
    /**
     * Apply the pipeline to a query tree until it reaches a fixed point.
     * The pipeline iterates until no more changes occur.
     * @param tree the initial query tree
     * @return the fully transformed tree
     */
    public AbstractQueryTree.RootNode apply(QueryNode tree) {
        QueryNode newTree = tree;
        QueryNode oldTree = null;
        
        do {
            oldTree = newTree;
            newTree = applyOnce(newTree);
        } while (!oldTree.equals(newTree));
        
        return (AbstractQueryTree.RootNode) newTree;
    }
    
    /**
     * Apply each transform in the pipeline once.
     */
    private QueryNode applyOnce(QueryNode tree) {
        QueryNode result = tree;
        for (Class<? extends TransformStep> stepClass : steps) {
            TransformStep step = instantiate(stepClass);
            Function<QueryNode, QueryNode> fn = step instanceof RecursiveTransform
                ? transformNodesRecursively(step::transform)
                : step::transform;
            result = fn.apply(result);
        }
        return result;
    }
    
    private TransformStep instantiate(Class<? extends TransformStep> clz) {
        try {
            return clz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot instantiate transform step: " + clz.getSimpleName(), e);
        }
    }
    
    private static Function<QueryNode, QueryNode> transformNodesRecursively(Function<QueryNode, QueryNode> transform) {
        return node -> {
            QueryNode transformed = transform.apply(node);
            if (transformed instanceof TableNode tableNode && tableNode.children() != null) {
                List<QueryNode> newChildren = tableNode.children().stream()
                        .map(transformNodesRecursively(transform))
                        .toList();
                return tableNode.withChildren(newChildren);
            } else {
                return transformed;
            }
        };
    }
}
