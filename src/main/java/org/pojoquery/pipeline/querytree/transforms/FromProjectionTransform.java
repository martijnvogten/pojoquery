package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Aggregate;
import org.pojoquery.annotations.Select;
import org.pojoquery.annotations.Transient;
import org.pojoquery.pipeline.QueryModel;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionFieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Transform for @From projections.
 * 
 * <p>When a type has @From annotation, this transform projects the fields from
 * the result type onto the source type's query structure. The source type provides
 * the tables and joins; the result type specifies which fields to select.</p>
 * 
 * <p>Supports:</p>
 * <ul>
 *   <li>Simple fields - mapped to source table columns by name</li>
 *   <li>@Select fields - custom SQL expressions</li>
 *   <li>@Aggregate fields - aggregate expressions with auto GROUP BY</li>
 * </ul>
 * 
 * <p>Runs after all structure transforms, before JoinPruningTransform.</p>
 */
public class FromProjectionTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        TypeModel resultType = tree.resultType();
        TableNode root = (TableNode) tree.root();
        String rootAlias = root.alias();
        
        // Clear fields from all nodes and add projection fields to root only
        TableNode clearedRoot = clearAllFields(root);
        
        // Build new field list from result type
        List<FieldSelection> projectedFields = new ArrayList<>();
        List<String> groupByClauses = new ArrayList<>(tree.groupBy());
        
        boolean hasAggregates = false;
        List<String> nonAggregateExpressions = new ArrayList<>();
        
        for (FieldModel f : FieldFilters.allFields(resultType)) {
            // Make field accessible
            if (f instanceof ReflectionFieldModel rfm) {
                rfm.getReflectionField().setAccessible(true);
            }
            
            // Skip transient and static fields
            if (f.getAnnotation(Transient.class) != null || f.isTransient() || f.isStatic()) {
                continue;
            }
            
            Aggregate aggAnn = f.getAnnotation(Aggregate.class);
            Select selectAnn = f.getAnnotation(Select.class);
            
            String fieldAlias = rootAlias + "." + f.getName();
            SqlExpression selectExpression;
            
            if (aggAnn != null) {
                // @Aggregate field
                hasAggregates = true;
                String resolved = ExpressionResolver.resolve(aggAnn.value(), rootAlias);
                selectExpression = new SqlExpression(resolved);
            } else if (selectAnn != null) {
                // @Select field
                String resolved = ExpressionResolver.resolve(selectAnn.value(), rootAlias);
                selectExpression = new SqlExpression(resolved);
                nonAggregateExpressions.add(resolved);
            } else {
                // Simple field - map to source column
                String columnName = QueryModel.determineSqlFieldName(f);
                selectExpression = new SqlExpression("{" + rootAlias + "." + columnName + "}");
                nonAggregateExpressions.add(selectExpression.getSql());
            }
            
            projectedFields.add(new FieldSelection(fieldAlias, null, selectExpression, f, null));
        }
        
        // Auto-add GROUP BY for non-aggregate fields when aggregates are present
        if (hasAggregates && !nonAggregateExpressions.isEmpty()) {
            for (String expr : nonAggregateExpressions) {
                if (!groupByClauses.contains(expr)) {
                    groupByClauses.add(expr);
                }
            }
        }
        
        // Create root with projected fields
        TableNode projectedRoot = clearedRoot.withFields(projectedFields);
        
        return QueryTree.of(resultType, projectedRoot)
            .withGroupByClauses(groupByClauses)
            .withOrderByClauses(tree.orderBy());
    }
    
    /**
     * Clears all fields from all TableNodes in the tree.
     * Preserves the join structure.
     */
    private TableNode clearAllFields(TableNode node) {
        // Recursively clear fields from all child nodes
        List<QueryNode> clearedChildren = node.children().stream()
            .map(child -> {
                if (child instanceof TableNode childTable) {
                    return clearAllFields(childTable);
                }
                return child;
            })
            .toList();
        
        return node.withFields(List.of()).withChildren(clearedChildren);
    }
}
