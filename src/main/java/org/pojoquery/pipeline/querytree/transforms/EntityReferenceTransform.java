package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.PojoMetadata.determineTableMapping;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.querytree.EmbeddedNode;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.FieldSelectionBase;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.pipeline.querytree.UnresolvedFieldSelection;
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
        List<QueryNode> newChildren = new ArrayList<>(node.children());
        List<FieldModel> processedFields = new ArrayList<>();
        
        for (FieldModel f : FieldFilters.entityReferences(node.type())) {
            if (alreadyJoined(node, f)) {
                continue;
            }
            
            TypeModel targetType = f.getType();
            List<TableMapping> tableMapping = determineTableMapping(targetType);
            if (tableMapping.isEmpty()) {
                continue; // Not an entity, skip
            }

            String fieldName = f.getName();
            if (node instanceof EmbeddedNode en) {
                // For embedded nodes, we want to use the source alias for join alias generation
                // to maintain correct aliasing in nested scenarios
                fieldName = en.embedInfo().fieldPrefix() + fieldName;
            }
            String joinAlias = AliasNaming.childAlias(node instanceof EmbeddedNode en ? en.embedInfo().sourceAlias() : node.alias(), rootAlias, 
            fieldName);
            TableMapping childTableMapping = tableMapping.get(tableMapping.size() - 1);
            EmptyTableNode joinedNode = EmptyTableNode.ofJoined(joinAlias, targetType, 
                JoinInfo.leftJoinOne(TableInfo.of(childTableMapping.schemaName, childTableMapping.tableName), f));
            
            newChildren.add(joinedNode);
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
    
    private boolean alreadyJoined(TableNode node, FieldModel field) {
        return node.children().stream()
            .filter(c -> c.joinInfo() != null)
            .anyMatch(c -> field.equals(c.joinInfo().linkField()));
    }
}
