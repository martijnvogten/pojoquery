package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.pipeline.querytree.EmbeddedNode;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.FieldSelectionBase;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.JakartaAnnotations;

/**
 * Pipeline transform that applies jakarta.persistence annotation mappings to all query nodes.
 * 
 * <p>This transform should run early in the pipeline, before other transforms that
 * depend on PojoQuery annotations. It converts jakarta.persistence annotations to
 * canonical PojoQuery annotations so downstream transforms see a uniform annotation model.
 * 
 * <p>Transforms applied:
 * <ul>
 *   <li>TypeModel on each node: jakarta.persistence.Table → @Table</li>
 *   <li>FieldModel on each field selection: jakarta.persistence.Id → @Id, etc.</li>
 * </ul>
 * 
 * @see JakartaAnnotations
 */
public class JakartaAnnotationsTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        if (!JakartaAnnotations.isAvailable()) {
            return tree;
        }

        if (tree.root() == null) {
            return tree.withType(JakartaAnnotations.transformType(tree.resultType()));
        }
        
        return tree.transformNodes(it -> true, this::transformNode);
    }
    
    private QueryNode transformNode(QueryNode node) {
        if (node == null) {
            return null;
        }

        // Transform field models in field selections (only resolved ones)
        
        // Transform based on node type
        if (node instanceof TableNode tn) {
            return 
                (node instanceof EmbeddedNode en ? 
                    en.withTransformedType(JakartaAnnotations::transformType) 
                    : 
                    (node instanceof JoinedNode jn ? jn.withTransformedType(JakartaAnnotations::transformType) : tn)
                )
                .withFields(transformFields(tn.fields()));
        } else if (node instanceof EmptyTableNode en) {
            return en.withType(JakartaAnnotations.transformType(en.type()));
        } else {
            return node;
        }
    }
    
    private List<FieldSelectionBase> transformFields(List<FieldSelectionBase> fields) {
        return fields.stream()
            .map(fs -> {
                FieldModel transformedField = JakartaAnnotations.transformField(fs.field());
                if (transformedField == fs.field()) {
                    return fs; // No changes, return original
                }
                return fs.withField(transformedField);
            })
            .toList();

    }
    
}
