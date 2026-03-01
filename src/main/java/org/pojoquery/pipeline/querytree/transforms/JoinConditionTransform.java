package org.pojoquery.pipeline.querytree.transforms;

import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Link;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;

/**
 */
public class JoinConditionTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        String rootAlias = ((TableNode) tree.root()).alias();
        return tree.transformNodes(n -> n instanceof TableNode && n.joins().stream().anyMatch(it -> it.condition() == null), 
            (TableNode n) -> processNode(n, rootAlias));
    }
    
    private TableNode processNode(TableNode node, String rootAlias) {
        List<JoinedNode> updatedJoins = node.joins().stream()
            .filter(it -> it.condition() == null)
            .map(j -> updateCondition(j, node))
            .toList();
        
        return node.withJoins(updatedJoins);
    }
    
    private JoinedNode updateCondition(JoinedNode join, TableNode parent) {
        JoinCondition condAnn = join.linkField().getAnnotation(JoinCondition.class);
        if (condAnn != null) {
            String resolved = ExpressionResolver.resolve(
                condAnn.value(),
                parent.alias(),
                join.node().alias(),
                null
            );
            return join.withCondition(new SqlExpression(resolved));
        } else if (join.isCollection()) {
            SqlExpression cond = JoinConditions.forCollection(parent.alias(), join.node().alias(), parent.type(), parent.tableName(), getForeignLinkField(join.linkField()));
            return join.withCondition(cond);
        } else {
            return join.withCondition(JoinConditions.forEntityReference(parent.alias(), join.node().alias(), join.linkField(), join.node().type()));
        }
    }

    private String getForeignLinkField(FieldModel field) {
        Link linkAnn = field.getAnnotation(Link.class);
        if (linkAnn != null && !Link.NONE.equals(linkAnn.foreignlinkfield())) {
            return linkAnn.foreignlinkfield();
        }
        return null;
    }

}
