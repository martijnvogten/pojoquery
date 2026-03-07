package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Join;
import org.pojoquery.internal.MappingException;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.querytree.BareJoinInfo;
import org.pojoquery.pipeline.querytree.EmbeddedNode;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.AnnotationModel;

/**
 * Transform 15: @Join, @Joins on class → extra joins not from field structure.
 */
public class ClassLevelJoinTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformNodes(
            node -> node instanceof JoinedNode && node.type().hasAnnotation(Join.class), 
            this::processNode
        );
    }
    
    private TableNode processNode(TableNode node) {
        if (node instanceof EmbeddedNode && !((EmbeddedNode)node).isSuperClass()) {
            throw new MappingException("@Join annotations are not supported on @Embedded types: " + node.type().getQualifiedName());
        }
        return node instanceof JoinedNode joined ? processJoinedNode(joined) : node;
    }
    
    private TableNode processJoinedNode(JoinedNode node) {
        if (node.type() == null) {
            return node;
        }
        
        List<AnnotationModel> joinAnns = node.type().getAnnotationsByType(Join.class);
        if (joinAnns.isEmpty()) {
            return node;
        }

        List<BareJoinInfo> newJoins = new ArrayList<>(node.extraJoins());
        
        for (AnnotationModel joinAnn : joinAnns) {
            String alias = joinAnn.getStringValue("alias").filter(s -> !s.isEmpty()).orElse(joinAnn.getStringValue("tableName").orElseThrow());
            if ((node.extraJoins().stream().anyMatch(j -> j.alias().equals(alias)))) {
                continue; // Skip if a join with this alias already exists
            }
            JoinType joinType = joinAnn.getEnumValue(JoinType.class, "type");
            
            String schemaName = joinAnn.getStringValue("schemaName").orElse(null);
            String tableName = joinAnn.getStringValue("tableName").orElseThrow();
            newJoins.add(BareJoinInfo.of(alias, joinType, TableInfo.of(schemaName, tableName), 
                new SqlExpression(joinAnn.getStringValue("joinCondition").orElseThrow())));
        }
        return node.withExtraJoins(newJoins);
    }
    
}
