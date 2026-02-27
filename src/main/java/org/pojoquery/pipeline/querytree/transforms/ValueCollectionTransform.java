package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Link;

import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.LinkedValueNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

import static org.pojoquery.pipeline.QueryModel.determineSqlFieldName;
import static org.pojoquery.pipeline.QueryModel.determineIdField;

/**
 * Transform 6: @Link(fetchColumn) → select scalar values from link table.
 * Single LEFT JOIN, fetches value column (e.g., for enum lists).
 */
public class ValueCollectionTransform implements QueryTreeTransform {
    
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
        
        for (FieldModel f : FieldFilters.valueCollectionFields(node.type())) {
            if (alreadyJoined(node, f)) {
                continue;
            }
            
            Link linkAnn = f.getAnnotation(Link.class);
            TypeModel componentType = FieldFilters.getComponentType(f);
            
            String joinAlias = AliasNaming.childAlias(node.alias(), rootAlias, f.getName());
            
            String parentId = determineSqlFieldName(determineIdField(node.type()));
            String linkField = resolveLinkField(linkAnn, node.tableName());
            
            SqlExpression condition = new SqlExpression(
                "{" + node.alias() + "." + parentId + "} = {" + joinAlias + "." + linkField + "}"
            );
            
            // Create a LinkedValueNode for value collections
            LinkedValueNode valueNode = new LinkedValueNode(
                joinAlias,
                componentType,
                linkAnn.linkschema().isEmpty() ? null : linkAnn.linkschema(),
                linkAnn.linktable(),
                linkAnn.fetchColumn()
            );
            
            newJoins.add(JoinedNode.leftJoinMany(condition, valueNode, f));
        }
        
        return node.withJoins(newJoins);
    }
    
    private String resolveLinkField(Link linkAnn, String parentTableName) {
        if (!Link.NONE.equals(linkAnn.foreignlinkfield())) {
            return linkAnn.foreignlinkfield();
        }
        return parentTableName + "_id";
    }
    
    private boolean alreadyJoined(TableNode node, FieldModel field) {
        return node.joins().stream()
            .anyMatch(j -> j.linkField() != null && j.linkField().getName().equals(field.getName()));
    }
}
