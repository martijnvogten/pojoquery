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
        boolean changed = false;
        
        // Process @GroupBy
        var groupByAnnOpt = type.getAnnotation(GroupBy.class);
        if (groupByAnnOpt.isPresent()) {
            for (String groupBy : groupByAnnOpt.get().getStringValues("value")) {
                String resolved = ExpressionResolver.resolve(groupBy, rootAlias);
                if (!groupByClauses.contains(resolved)) {
                    groupByClauses.add(resolved);
                    changed = true;
                }
            }
        }
        
        // Process @OrderBy
        var orderByAnnOpt = type.getAnnotation(OrderBy.class);
        if (orderByAnnOpt.isPresent()) {
            for (String orderBy : orderByAnnOpt.get().getStringValues("value")) {
                String resolved = ExpressionResolver.resolve(orderBy, rootAlias);
                if (!orderByClauses.contains(resolved)) {
                    orderByClauses.add(resolved);
                    changed = true;
                }
            }
        }
        
        if (!changed) {
            return tree;
        }
        
        return tree.withGroupByClauses(groupByClauses).withOrderByClauses(orderByClauses);
    }
}
