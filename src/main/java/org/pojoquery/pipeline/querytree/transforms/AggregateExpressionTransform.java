package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Aggregate;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;

/**
 * Transform 12: @Aggregate → aggregate SQL expression.
 * Similar to @Select but marks for GROUP BY handling.
 */
public class AggregateExpressionTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        if (node.type() == null) {
            return node;
        }
        
        // Override existing field expressions with @Aggregate
        List<FieldSelection> newFields = node.fields().stream()
            .map(f -> overrideWithAggregate(f, node.alias()))
            .collect(Collectors.toCollection(ArrayList::new));
        
        // Add @Aggregate fields that weren't in the original field list
        Set<String> existingFieldNames = newFields.stream()
            .filter(f -> f.field() != null)
            .map(f -> f.field().getName())
            .collect(Collectors.toSet());
        
        for (FieldModel f : FieldFilters.aggregateFields(node.type())) {
            if (!existingFieldNames.contains(f.getName())) {
                String expression = f.getAnnotation(Aggregate.class).flatMap(ann -> ann.getStringValue("value")).orElseThrow();
                String resolved = ExpressionResolver.resolve(expression, node.alias());
                String fieldAlias = node.alias() + "." + f.getName();
                newFields.add(new FieldSelection(fieldAlias, null, new SqlExpression(resolved), f, null));
            }
        }
        
        return node.withFields(newFields);
    }
    
    private FieldSelection overrideWithAggregate(FieldSelection fs, String alias) {
        if (fs.field() == null) {
            return fs;
        }
        
        String expression = fs.field().getAnnotation(Aggregate.class).flatMap(ann -> ann.getStringValue("value")).orElse(null);
        if (expression != null) {
            String resolved = ExpressionResolver.resolve(expression, alias);
            return new FieldSelection(fs.alias(), fs.columnName(), new SqlExpression(resolved), fs.field(), fs.customMapping());
        }
        return fs;
    }
}
