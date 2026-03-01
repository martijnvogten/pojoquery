package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.QueryModel.determineTableMapping;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Link;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * List/Set/Array of Entity without @Link → LEFT JOIN (one-to-many).
 * Expects child table has parent_id FK column (customizable via @Link.foreignlinkfield).
 */
public class CollectionTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        String rootAlias = ((TableNode) tree.root()).alias();
        return tree.transformTableNodes(node -> processNode(node, rootAlias));
    }
    
    private TableNode processNode(TableNode node, String rootAlias) {
        if (node.type() == null) {
            return node;
        }
        
        List<JoinedNode> newJoins = new ArrayList<>(node.joins());
        
        for (FieldModel f : FieldFilters.simpleCollections(node.type())) {
            if (alreadyJoined(node, f)) {
                continue;
            }
            
            TypeModel componentType = FieldFilters.getComponentType(f);
            // List<TableMapping> tableMappings = determineTableMapping(componentType);
            // TableMapping tableMapping = tableMappings.get(tableMappings.size() - 1);
            
            String joinAlias = AliasNaming.childAlias(node.alias(), rootAlias, f.getName());
            
            // Build the joined table node
            EmptyTableNode joinedNode = EmptyTableNode.of(joinAlias, componentType);
            
            newJoins.add(JoinedNode.leftJoinMany(null, joinedNode, f));
        }
        
        return node.withJoins(newJoins);
    }
    
    private boolean alreadyJoined(TableNode node, FieldModel field) {
        return node.joins().stream()
            .anyMatch(j -> field.equals(j.linkField()));
    }
}
