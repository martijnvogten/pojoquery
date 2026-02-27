package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Link;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

import static org.pojoquery.pipeline.QueryModel.determineSqlFieldName;
import static org.pojoquery.pipeline.QueryModel.determineIdField;

/**
 * Transform 5: @Link(linktable) → many-to-many via junction table.
 * Creates two LEFT JOINs: parent → linktable → target.
 */
public class LinkTableTransform implements QueryTreeTransform {
    
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
        
        for (FieldModel f : FieldFilters.linkTableFields(node.type())) {
            if (alreadyJoined(node, f)) {
                continue;
            }
            
            Link linkAnn = f.getAnnotation(Link.class);
            TypeModel componentType = FieldFilters.getComponentType(f);
            
            String linkTableAlias = AliasNaming.linkTableAlias(node.alias(), f.getName());
            String targetAlias = AliasNaming.childAlias(node.alias(), rootAlias, f.getName());
            
            // Determine column names
            String parentId = determineSqlFieldName(determineIdField(node.type()));
            String linkField = resolveLinkField(linkAnn, node.tableName());
            String foreignLinkField = resolveForeignLinkField(linkAnn, componentType);
            String targetId = determineSqlFieldName(determineIdField(componentType));
            
            // JOIN 1: parent → linktable
            SqlExpression linkCondition = JoinConditions.forLinkTableParent(
                node.alias(), linkTableAlias, parentId, linkField);
            
            // JOIN 2: linktable → target
            SqlExpression targetCondition = JoinConditions.forLinkTableTarget(
                linkTableAlias, targetAlias, foreignLinkField, targetId);
            
            // Build link table node (no fields, just for joining)
            TableNode linkTableNode = TableNodeFactory.forLinkTable(
                linkTableAlias, linkAnn.linkschema(), linkAnn.linktable());
            
            // Build target entity node
            TableNode targetNode = TableNodeFactory.forType(componentType, targetAlias);
            
            // Chain: linkTable contains join to target
            JoinedNode targetJoin = JoinedNode.leftJoinMany(targetCondition, targetNode, f);
            TableNode linkWithTarget = linkTableNode.withJoins(List.of(targetJoin));
            
            newJoins.add(new JoinedNode(
                org.pojoquery.pipeline.SqlQuery.JoinType.LEFT, 
                linkCondition, linkWithTarget, f, true));
        }
        
        return node.withJoins(newJoins);
    }
    
    private String resolveLinkField(Link linkAnn, String parentTableName) {
        if (!Link.NONE.equals(linkAnn.linkfield())) {
            return linkAnn.linkfield();
        }
        return parentTableName + "_id";
    }
    
    private String resolveForeignLinkField(Link linkAnn, TypeModel targetType) {
        if (!Link.NONE.equals(linkAnn.foreignlinkfield())) {
            return linkAnn.foreignlinkfield();
        }
        var tableInfo = org.pojoquery.AnnotationHelper.getTableInfo(targetType);
        return tableInfo != null ? tableInfo.name + "_id" : targetType.getSimpleName().toLowerCase() + "_id";
    }
    
    private boolean alreadyJoined(TableNode node, FieldModel field) {
        return node.joins().stream()
            .anyMatch(j -> j.linkField() != null && j.linkField().getName().equals(field.getName()));
    }
}
