package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.PojoMetadata.determineIdField;
import static org.pojoquery.pipeline.PojoMetadata.determineSqlFieldName;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.annotations.Link;
import org.pojoquery.pipeline.querytree.FieldSelectionBase;
import org.pojoquery.pipeline.querytree.JoinCondition;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.LinkedValueNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.pipeline.querytree.UnresolvedFieldSelection;
import org.pojoquery.typemodel.AnnotationModel;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

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
        
        List<QueryNode> newChildren = new ArrayList<>(node.children());
        List<FieldModel> processedFields = new ArrayList<>();
        
        for (FieldModel f : FieldFilters.valueCollectionFields(node.type())) {
            if (alreadyJoined(node, f)) {
                continue;
            }
            
            AnnotationModel linkAnn = f.getAnnotation(Link.class).orElseThrow();
            TypeModel componentType = FieldFilters.getComponentType(f);
            
            String joinAlias = AliasNaming.childAlias(node.alias(), rootAlias, f.getName());
            
            String parentId = determineSqlFieldName(determineIdField(node.type()));
            String linkField = resolveLinkField(linkAnn, node.tableInfo().tableName());
            
            // FK is in the link table (child), pointing to parent's ID
            JoinCondition condition = new JoinCondition.ForeignKeyInChild(linkField, parentId);
            
            String linkSchema = linkAnn.getStringValue("linkschema").filter(s -> !s.isEmpty()).orElse(null);
            String linkTable = linkAnn.getStringValue("linktable").orElseThrow();
            String fetchColumn = linkAnn.getStringValue("fetchColumn").orElseThrow();
            
            // Create a LinkedValueNode for value collections
            LinkedValueNode valueNode = new LinkedValueNode(
                joinAlias,
                componentType,
                linkSchema,
                linkTable,
                fetchColumn,
                JoinInfo.leftJoinMany(TableInfo.of(linkSchema, linkTable), f).withJoinCondition(condition)
            );
            
            newChildren.add(valueNode);
            processedFields.add(f);
        }
        
        if (processedFields.isEmpty()) {
            return node;
        }
        
        // Remove UnresolvedFieldSelection for processed fields
        List<FieldSelectionBase> newFields = node.fields().stream()
            .filter(fsb -> !(fsb instanceof UnresolvedFieldSelection ufs && processedFields.contains(ufs.field())))
            .toList();
        
        return node.withChildren(newChildren).withFields(newFields);
    }
    
    private String resolveLinkField(AnnotationModel linkAnn, String parentTableName) {
        return linkAnn.getStringValue("foreignlinkfield")
            .filter(s -> !s.isEmpty())
            .orElse(parentTableName + "_id");
    }
    
    private boolean alreadyJoined(TableNode node, FieldModel field) {
        return node.children().stream()
            .filter(c -> c.joinInfo() != null && c.joinInfo().linkField() != null)
            .anyMatch(c -> c.joinInfo().linkField().getName().equals(field.getName()));
    }
}
