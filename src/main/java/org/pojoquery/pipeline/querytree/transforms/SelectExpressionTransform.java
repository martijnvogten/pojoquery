package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Select;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;

/**
 * Transform 11: @Select → replace field expression with custom SQL.
 */
public class SelectExpressionTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        if (node.type() == null) {
            return node;
        }
        
        // Override existing field expressions with @Select
        List<FieldSelection> newFields = node.fields().stream()
            .map(f -> overrideWithSelect(f, node.alias()))
            .collect(Collectors.toCollection(ArrayList::new));
        
        // Add @Select fields that weren't in the original field list
        Set<String> existingFieldNames = newFields.stream()
            .filter(f -> f.field() != null)
            .map(f -> f.field().getName())
            .collect(Collectors.toSet());
        
        for (FieldModel f : FieldFilters.selectFields(node.type())) {
            if (!existingFieldNames.contains(f.getName())) {
                Select selectAnn = f.getAnnotation(Select.class);
                String resolved = ExpressionResolver.resolve(selectAnn.value(), node.alias());
                String fieldAlias = node.alias() + "." + f.getName();
                newFields.add(new FieldSelection(fieldAlias, new SqlExpression(resolved), f, null));
            }
        }
        
        return node.withFields(newFields);
    }
    
    private FieldSelection overrideWithSelect(FieldSelection fs, String alias) {
        if (fs.field() == null) {
            return fs;
        }
        
        Select selectAnn = fs.field().getAnnotation(Select.class);
        if (selectAnn != null) {
            String resolved = ExpressionResolver.resolve(selectAnn.value(), alias);
            return new FieldSelection(fs.alias(), new SqlExpression(resolved), fs.field(), fs.customMapping());
        }
        return fs;
    }
}
