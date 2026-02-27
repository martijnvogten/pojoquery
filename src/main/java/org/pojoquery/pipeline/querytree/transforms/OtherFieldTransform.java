package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.annotations.Other;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;

/**
 * Transform 13: @Other → capture dynamic/extra columns into Map field.
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
        String prefix = (otherAnn != null && !otherAnn.prefix().isEmpty()) ? otherAnn.prefix() : null;
        
        return node.withOtherField(otherField, prefix);
    }
}
