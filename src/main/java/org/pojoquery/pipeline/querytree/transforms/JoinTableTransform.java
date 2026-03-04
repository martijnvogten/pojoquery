package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.QueryModel.determineIdField;
import static org.pojoquery.pipeline.QueryModel.determineSqlFieldName;
import static org.pojoquery.pipeline.QueryModel.determineTableMapping;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.annotations.Link;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.JoinTableInfo;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * @Link(linktable) → many-to-many via junction table.
 * Creates two LEFT JOINs: parent → jointable → target.
 */
public class JoinTableTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        String rootAlias = ((TableNode) tree.root()).alias();
        return tree.transformTableNodes(node -> processNode(node, rootAlias));
    }
    
    private TableNode processNode(TableNode node, String rootAlias) {
        List<QueryNode> newChildren = new ArrayList<>(node.children());
        
        for (FieldModel f : FieldFilters.linkTableFields(node.type())) {
            if (alreadyJoined(node, f)) {
                continue;
            }
            
            Link linkAnn = f.getAnnotation(Link.class);
            TypeModel componentType = FieldFilters.getComponentType(f);
            List<TableMapping> tableMappings = determineTableMapping(componentType);
            TableMapping targetMapping = tableMappings.get(tableMappings.size() - 1);
            
            String linkTableAlias = AliasNaming.linkTableAlias(node.alias(), f.getName());
            String targetAlias = AliasNaming.childAlias(node.alias(), rootAlias, f.getName());
            
            // Determine column names
            String parentId = determineSqlFieldName(determineIdField(node.type()));
            String linkField = resolveLinkField(linkAnn, node.tableInfo().tableName());
            String foreignLinkField = resolveForeignLinkField(linkAnn, componentType);
            String targetId = determineSqlFieldName(determineIdField(componentType));
            
            // Create JoinTableInfo with explicit column names
            JoinTableInfo joinTableInfo = JoinTableInfo.of(
                TableInfo.of(linkAnn.linkschema(), linkAnn.linktable()),
                linkTableAlias,
                linkField,      // parentFkColumn: e.g., "article_id" in junction table
                parentId,       // parentRefColumn: e.g., "id" in parent table
                foreignLinkField, // targetFkColumn: e.g., "tag_id" in junction table
                targetId        // targetRefColumn: e.g., "id" in target table
            );
            
            // Chain: linkTable contains join to target
            JoinInfo joinInfo = JoinInfo.manyToMany(
                TableInfo.of(targetMapping.schemaName, targetMapping.tableName), 
                f, 
                joinTableInfo);
            
            newChildren.add(EmptyTableNode.ofJoined(targetAlias, FieldFilters.getComponentType(f), joinInfo));
        }
        
        return node.withChildren(newChildren);
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
        return node.children().stream()
            .filter(c -> c.joinInfo() != null)
            .anyMatch(c -> field.equals(c.joinInfo().linkField()));
    }
}
