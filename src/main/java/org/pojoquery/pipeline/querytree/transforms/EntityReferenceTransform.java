package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Transform 3: Entity references → LEFT JOIN (one-to-one).
 * Fields whose type has @Table annotation get a LEFT JOIN.
 * FK convention: fieldName_id on parent table.
 */
public class EntityReferenceTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        String rootAlias = ((TableNode) tree.root()).alias();
        return tree.transformTableNodes(node -> processNode(node, rootAlias));
    }
    
    private TableNode processNode(TableNode node, String rootAlias) {
        List<JoinedNode> newJoins = new ArrayList<>(node.joins());
        for (FieldModel f : FieldFilters.entityReferences(node.type())) {
            if (alreadyJoined(node, f)) {
                continue;
            }
            
            TypeModel targetType = f.getType();
            String joinAlias = AliasNaming.childAlias(node.alias(), rootAlias, f.getName());
            EmptyTableNode joinedNode = EmptyTableNode.of(joinAlias, targetType);
            
            newJoins.add(JoinedNode.leftJoinOne(null, joinedNode, f));
        }
        
        return newJoins.size() > 0 ? node.withJoins(newJoins) : node;
    }
    
    private boolean alreadyJoined(TableNode node, FieldModel field) {
        return node.joins().stream()
            .anyMatch(j -> field.equals(j.linkField()));
    }
}
