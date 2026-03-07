package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.pipeline.querytree.EmbeddedNode;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.FieldSelectionBase;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.TypeModel;

/**
 * Adds discriminator column and discriminator values map to STI root nodes.
 * 
 * <p>This transform runs AFTER BasicTableTransform and STIInheritanceTransform.
 * By that point, the STI structure (EmbeddedNodes for subclasses) already exists.
 * This transform only adds:</p>
 * <ul>
 *   <li>The discriminator column field selection</li>
 *   <li>The discriminatorValues map for type resolution</li>
 * </ul>
 */
public class SingleTableInheritanceTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        // Only process JoinedNodes with @DiscriminatorColumn
        if (!(node instanceof JoinedNode jn)) {
            return node;
        }

        var discAnnOpt = node.type().getAnnotation(DiscriminatorColumn.class);
        if (discAnnOpt.isEmpty()) {
            return node;
        }

        // Already processed (idempotency)
        if (jn.discriminatorValues() != null) {
            return node;
        }

        String discColumn = discAnnOpt.get().getStringValue("name").orElse("dtype");

        // Add discriminator column field
        List<FieldSelectionBase> newFields = new ArrayList<>(node.fields());
        String discAlias = node.alias() + "." + discColumn;
        newFields.add(new FieldSelection(
            discAlias,
            discColumn,
            new SqlExpression("{" + node.sourceAlias() + "." + discColumn + "}"),
            null, null
        ));
        
        // Build discriminator values map from existing children
        Map<String, TypeModel> discriminatorValues = new HashMap<>();
        discriminatorValues.put(node.type().getSimpleName(), node.type());
        
        collectDiscriminatorValues(node.children(), discriminatorValues);
        
        return jn.withFields(newFields)
            .withSingleTableInheritance(discAlias, discriminatorValues);
    }

    /**
     * Recursively collects discriminator values from EmbeddedNode children (STI subclasses).
     */
    private void collectDiscriminatorValues(List<QueryNode> children, Map<String, TypeModel> discriminatorValues) {
        for (QueryNode child : children) {
            if (child instanceof EmbeddedNode en && en.isSubClass()) {
                discriminatorValues.put(en.type().getSimpleName(), en.type());
                // Recurse for nested subclasses
                collectDiscriminatorValues(en.children(), discriminatorValues);
            }
        }
    }
}
