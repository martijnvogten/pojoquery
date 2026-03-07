package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.PojoMetadata.determineIdFields;
import static org.pojoquery.pipeline.PojoMetadata.determineTableMapping;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.FieldSelectionBase;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.pipeline.querytree.UnresolvedFieldSelection;
import org.pojoquery.typemodel.FieldModel;

/**
 * Converts EmptyTableNode to JoinedNode/EmbeddedNode with unresolved fields.
 * 
 * <p>This transform is intentionally simple:</p>
 * <ul>
 *   <li>Creates UnresolvedFieldSelection for ALL non-transient fields</li>
 *   <li>Does NOT filter by field type (entity ref, collection, etc.)</li>
 *   <li>Later transforms will consume UnresolvedFieldSelection and either:
 *       <ul>
 *         <li>Add child nodes (EntityRef, Collection, Embedded)</li>
 *         <li>Convert to FieldSelection (SimpleFieldTransform)</li>
 *       </ul>
 *   </li>
 * </ul>
 */
public class BasicTableTransform implements QueryTreeTransform {

    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformNodes(n -> n instanceof EmptyTableNode, (EmptyTableNode tn) -> transformTableNode(tn));
    }

    private QueryNode transformTableNode(EmptyTableNode node) {
        boolean isEmbedded = node.embedInfo() != null;
        
        // Field alias is always the node alias
        String fieldAlias = node.alias();
        
        // Get fields declared in this type, stopping at superType if set
        List<FieldModel> declaredFields = FieldFilters.fieldsDeclaredIn(node.type(), node.superType());
        
        // Create unresolved field selection for each field
        List<FieldSelectionBase> fields = new ArrayList<>();
        for (FieldModel f : declaredFields) {
            fields.add(UnresolvedFieldSelection.of(node.sourceAlias(), fieldAlias, f));
        }
        
        // Collect ID field names
        List<String> idFields = new ArrayList<>();
        for (FieldModel f : determineIdFields(node.type())) {
            idFields.add(f.getName());
        }
        
        if (isEmbedded) {
            return node.toEmbeddedNode(fields);
        } else {
            List<TableMapping> tableMappings = determineTableMapping(node.type());
            TableMapping mapping = tableMappings.get(tableMappings.size() - 1);
            return node.toJoinedNode(new TableInfo(mapping.schemaName, mapping.tableName), fields, idFields);
        }
    }
}
