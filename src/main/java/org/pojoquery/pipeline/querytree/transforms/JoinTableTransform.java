package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.PojoMetadata.determineIdField;
import static org.pojoquery.pipeline.PojoMetadata.determineSqlFieldName;
import static org.pojoquery.pipeline.PojoMetadata.determineTableMapping;

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
import org.pojoquery.typemodel.AnnotationModel;
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
            
            AnnotationModel linkAnn = f.getAnnotation(Link.class).orElseThrow();
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
            String linkSchema = linkAnn.getStringValue("linkschema").filter(s -> !s.isEmpty()).orElse(null);
            String linkTable = linkAnn.getStringValue("linktable").orElseThrow();
            JoinTableInfo joinTableInfo = JoinTableInfo.of(
                TableInfo.of(linkSchema, linkTable),
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
    
    private String resolveLinkField(AnnotationModel linkAnn, String parentTableName) {
        return linkAnn.getStringValue("linkfield")
            .filter(s -> !s.isEmpty())
            .orElse(parentTableName + "_id");
    }
    
    private String resolveForeignLinkField(AnnotationModel linkAnn, TypeModel targetType) {
        return linkAnn.getStringValue("foreignlinkfield")
            .filter(s -> !s.isEmpty())
            .orElseGet(() -> {
                List<TableMapping> mappings = determineTableMapping(targetType);
                return mappings.isEmpty() ? targetType.getSimpleName().toLowerCase() + "_id" : mappings.get(mappings.size() - 1).tableName + "_id";
            });
    }
    
    private boolean alreadyJoined(TableNode node, FieldModel field) {
        return node.children().stream()
            .filter(c -> c.joinInfo() != null)
            .anyMatch(c -> field.equals(c.joinInfo().linkField()));
    }
}
