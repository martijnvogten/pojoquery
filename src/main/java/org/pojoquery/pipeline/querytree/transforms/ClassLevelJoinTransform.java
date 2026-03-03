package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.pojoquery.annotations.Join;
import org.pojoquery.annotations.Joins;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.TypeModel;

/**
 * Transform 15: @Join, @Joins on class → extra joins not from field structure.
 */
public class ClassLevelJoinTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        if (node.type() == null) {
            return node;
        }
        
        List<Join> joinAnns = getJoinAnnotations(node.type());
        if (joinAnns.isEmpty()) {
            return node;
        }
        
        List<QueryNode> newChildren = new ArrayList<>(node.children());
        
        for (Join joinAnn : joinAnns) {
            // String alias = joinAnn.alias().isEmpty() ? joinAnn.tableName() : joinAnn.alias();
            // JoinType joinType = joinAnn.type();
            
            // SqlExpression condition = new SqlExpression(
            //     ExpressionResolver.resolve(joinAnn.joinCondition(), node.alias())
            // );

            // // String schemaName = joinAnn.schemaName().isEmpty() ? null : joinAnn.schemaName();
            // // TableNode joinedTable = TableNodeFactory.forLinkTable(alias, schemaName, joinAnn.tableName())
            // //     .withJoinInfo(new JoinInfo(joinType, condition, null, false, null));
            
            // newChildren.add(joinedTable);
        }
        
        return node.withChildren(newChildren);
    }
    
    private List<Join> getJoinAnnotations(TypeModel type) {
        Joins joins = type.getAnnotation(Joins.class);
        if (joins != null) {
            return Arrays.asList(joins.value());
        }
        Join join = type.getAnnotation(Join.class);
        if (join != null) {
            return List.of(join);
        }
        return List.of();
    }
}
