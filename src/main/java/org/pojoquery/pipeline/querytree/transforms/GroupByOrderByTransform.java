package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;

import java.util.List;

import org.pojoquery.annotations.GroupBy;
import org.pojoquery.annotations.OrderBy;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.TypeModel;

/**
 * Transform: @GroupBy and @OrderBy class-level annotations.
 * Adds GROUP BY and ORDER BY clauses to the QueryTree.
 */
public class GroupByOrderByTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        TypeModel type = tree.resultType();
        String rootAlias = ((TableNode) tree.root()).alias();
        
        List<String> groupByClauses = new ArrayList<>(tree.groupBy());
        List<String> orderByClauses = new ArrayList<>(tree.orderBy());
        
        // Process @GroupBy
        GroupBy groupByAnn = type.getAnnotation(GroupBy.class);
        if (groupByAnn != null) {
            for (String groupBy : groupByAnn.value()) {
                String resolved = ExpressionResolver.resolve(groupBy, rootAlias);
                groupByClauses.add(resolved);
            }
        }
        
        // Process @OrderBy
        OrderBy orderByAnn = type.getAnnotation(OrderBy.class);
        if (orderByAnn != null) {
            for (String orderBy : orderByAnn.value()) {
                String resolved = ExpressionResolver.resolve(orderBy, rootAlias);
                orderByClauses.add(resolved);
            }
        }
        
        if (groupByClauses.isEmpty() && orderByClauses.isEmpty()) {
            return tree;
        }
        
        return tree.withGroupByClauses(groupByClauses).withOrderByClauses(orderByClauses);
    }
}
