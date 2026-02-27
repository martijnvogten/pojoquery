package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Link;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Transform 4: List/Set/Array of Entity without @Link → LEFT JOIN (one-to-many).
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
            AnnotationHelper.TableInfo targetTable = AnnotationHelper.getTableInfo(componentType);
            if (targetTable == null) {
                continue;
            }
            
            String joinAlias = AliasNaming.childAlias(node.alias(), rootAlias, f.getName());
            
            // Build the joined table node
            TableNode joinedNode = TableNodeFactory.forType(componentType, joinAlias);
            
            // Build join condition: {parent.id} = {child.parent_id}
            // Check for @Link.foreignlinkfield to customize FK column name
            String foreignLinkField = getForeignLinkField(f);
            SqlExpression condition = JoinConditions.forCollection(
                node.alias(), joinAlias, node.type(), node.tableName(), foreignLinkField);
            
            newJoins.add(JoinedNode.leftJoinMany(condition, joinedNode, f));
        }
        
        return node.withJoins(newJoins);
    }
    
    private boolean alreadyJoined(TableNode node, FieldModel field) {
        return node.joins().stream()
            .anyMatch(j -> j.linkField() != null && j.linkField().getName().equals(field.getName()));
    }
    
    private String getForeignLinkField(FieldModel field) {
        Link linkAnn = field.getAnnotation(Link.class);
        if (linkAnn != null && !Link.NONE.equals(linkAnn.foreignlinkfield())) {
            return linkAnn.foreignlinkfield();
        }
        return null;
    }
}
