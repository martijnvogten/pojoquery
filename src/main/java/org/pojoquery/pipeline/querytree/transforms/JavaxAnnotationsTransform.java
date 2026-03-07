package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.pipeline.querytree.EmbeddedNode;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.typemodel.JavaxAnnotations;
import org.pojoquery.typemodel.TypeModel;

/**
 * Pipeline transform that applies javax.persistence annotation mappings to all query nodes.
 * 
 * <p>This transform should run early in the pipeline, before other transforms that
 * depend on PojoQuery annotations. It converts javax.persistence annotations to
 * canonical PojoQuery annotations so downstream transforms see a uniform annotation model.
 * 
 * <p>Transforms applied:
 * <ul>
 *   <li>TypeModel on each node: javax.persistence.Table → @Table</li>
 *   <li>FieldModel on each field selection: javax.persistence.Id → @Id, etc.</li>
 * </ul>
 * 
 * @see JavaxAnnotations
 */
public class JavaxAnnotationsTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        if (!JavaxAnnotations.isAvailable()) {
            return tree;
        }
        
        // Transform the result type
        TypeModel transformedResultType = JavaxAnnotations.transformType(tree.resultType());
        
        // Transform all nodes in the tree
        QueryNode newRoot = transformNode(tree.root());
        
        return new QueryTree(transformedResultType, newRoot, tree.groupBy(), tree.orderBy(), tree.wheres());
    }
    
    private QueryNode transformNode(QueryNode node) {
        if (node == null) {
            return null;
        }
        
        // Transform based on node type
        QueryNode transformed;
        if (node instanceof JoinedNode jn) {
            transformed = transformJoinedNode(jn);
        } else if (node instanceof EmbeddedNode en) {
            transformed = transformEmbeddedNode(en);
        } else {
            transformed = node;
        }
        
        // Recursively transform children
        List<QueryNode> transformedChildren = transformed.children().stream()
            .map(this::transformNode)
            .toList();
        
        return transformed.withChildren(transformedChildren);
    }
    
    private JoinedNode transformJoinedNode(JoinedNode node) {
        // Transform field models in field selections
        List<FieldSelection> transformedFields = node.fields().stream()
            .map(JavaxAnnotations::transformFieldSelection)
            .toList();
        
        // Transform the type annotations and fields
        return node
            .withTransformedType(JavaxAnnotations::transformType)
            .withFields(transformedFields);
    }
    
    private EmbeddedNode transformEmbeddedNode(EmbeddedNode node) {
        // Transform field models in field selections
        List<FieldSelection> transformedFields = node.fields().stream()
            .map(JavaxAnnotations::transformFieldSelection)
            .toList();
        
        // Transform the type annotations and fields
        return node
            .withTransformedType(JavaxAnnotations::transformType)
            .withFields(transformedFields);
    }
}
