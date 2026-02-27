package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;

/**
 * Transform 14: @JoinCondition → override auto-generated join ON clause.
 */
public class JoinConditionTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        String rootAlias = ((TableNode) tree.root()).alias();
        return tree.transformTableNodes(node -> processNode(node, rootAlias));
    }
    
    private TableNode processNode(TableNode node, String rootAlias) {
        List<JoinedNode> updatedJoins = node.joins().stream()
            .map(j -> updateCondition(j, node.alias()))
            .toList();
        
        return node.withJoins(updatedJoins);
    }
    
    private JoinedNode updateCondition(JoinedNode join, String parentAlias) {
        if (join.linkField() == null || join.condition() == null) {
            return join;
        }
        
        JoinCondition condAnn = join.linkField().getAnnotation(JoinCondition.class);
        if (condAnn != null) {
            String resolved = ExpressionResolver.resolve(
                condAnn.value(),
                parentAlias,
                join.node().alias(),
                null
            );
            return join.withCondition(new SqlExpression(resolved));
        }
        
        return join;
    }
}
