package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.annotations.Other;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;

/**
 * @Other → capture dynamic/extra columns into Map field.
 */
public class OtherFieldTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        if (node.type() == null) {
            return node;
        }
        
        List<FieldModel> otherFields = FieldFilters.otherFields(node.type());
        if (otherFields.isEmpty()) {
            return node;
        }

        FieldModel otherField = otherFields.get(0);
        Other otherAnn = otherField.getAnnotation(Other.class);
        if (otherAnn != null && !(node instanceof JoinedNode)) {
            throw new IllegalArgumentException("@Other field is not supported on embedded types: " + otherField);
        }
        if (node instanceof JoinedNode joinedNode) {
            if (joinedNode.otherField() != null) {
                return node; // Already has an @Other field, skip
            }
            String prefix = (otherAnn != null && !otherAnn.prefix().isEmpty()) ? otherAnn.prefix() : null;
            return ((JoinedNode) node).withOtherField(otherField, prefix);
        }
        return node;
    }
}
