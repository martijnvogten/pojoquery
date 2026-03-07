package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.PojoMetadata.determineIdFields;
import static org.pojoquery.pipeline.PojoMetadata.determineSqlFieldName;
import static org.pojoquery.pipeline.PojoMetadata.determineTableMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Creates root TableNode for the specified result type, collects @Id fields.
 * Only handles primitive/wrapper/common types - no relationships.
 */
public class BasicTableTransform implements QueryTreeTransform {

    @Override
    public QueryTree apply(QueryTree tree) {
        Map<String, QueryNode> parentNodes = tree.parentNodes();
        return tree.transformNodes(n -> n instanceof EmptyTableNode, (EmptyTableNode tn) -> transformTableNode(tn, parentNodes));
    }

    private QueryNode transformTableNode(EmptyTableNode node, Map<String, QueryNode> parentNodes) {
        List<FieldSelection> fields = new ArrayList<>();
        List<String> idFields = new ArrayList<>();
        boolean isEmbedded = node.embedInfo() != null;

        List<TableMapping> tableMappings = determineTableMapping(node.type());
        boolean hasInheritedTables = tableMappings.size() > 1 
            && !node.type().hasAnnotation(DiscriminatorColumn.class);

        // Determine alias prefix - for superclass nodes, use the first non-superclass ancestor's alias
        String aliasPrefix = node.alias();
        if (node.isSuperClass()) {
            aliasPrefix = findFirstNonSuperclassAncestor(node.alias(), parentNodes);
        }

        // For subclass nodes or nodes with superclass tables (table-per-subclass inheritance),
        // only select fields that belong to this specific table
        List<FieldModel> fieldsToSelect;
        if (node.isSubClass() || hasInheritedTables) {
            // Get fields belonging to this table (excludes inherited non-ID fields)
            List<FieldModel> tableSpecificFields = FieldFilters.tableFields(node.type()).stream()
                .filter(FieldFilters::isSimple)
                .toList();
            
            // For subclass nodes (not root), also include ID fields for the join
            if (node.isSubClass()) {
                List<FieldModel> idFieldModels = determineIdFields(node.type());
                fieldsToSelect = new ArrayList<>(tableSpecificFields);
                for (FieldModel idFieldModel : idFieldModels) {
                    if (FieldFilters.isSimple(idFieldModel) && !tableSpecificFields.contains(idFieldModel)) {
                        fieldsToSelect.add(0, idFieldModel);
                    }
                }
            } else {
                fieldsToSelect = new ArrayList<>(tableSpecificFields);
            }
        } else if (isEmbedded) {
            TypeModel superType = node.embedInfo().superType();
            fieldsToSelect = FieldFilters.fieldsDeclaredIn(node.type(), superType).stream()
                .filter(FieldFilters::isSimple)
                .toList();
        } else {
            fieldsToSelect = FieldFilters.fieldsDeclaredIn(node.type(), null).stream()
                .filter(FieldFilters::isSimple)
                .toList();
        }

        for (FieldModel f : fieldsToSelect) {
            String colName = determineSqlFieldName(f);
            String sourceAlias = node.alias();
            if (isEmbedded) {
                colName = node.embedInfo().fieldPrefix() + colName;
                sourceAlias = node.embedInfo().sourceAlias();
            }
            fields.add(FieldSelection.column(sourceAlias, aliasPrefix, colName, f));
        }

        for (FieldModel f : determineIdFields(node.type())) {
            idFields.add(determineSqlFieldName(f));
        }

        if (isEmbedded) {
            return node.toEmbeddedNode(fields);
        } else {
            TableMapping mapping = tableMappings.get(tableMappings.size() - 1);
            return node.toJoinedNode(new TableInfo(mapping.schemaName, mapping.tableName), fields, idFields);
        }
    }
    
    /**
     * Traverses up the parent chain to find the first non-superclass ancestor.
     * For table-per-subclass inheritance, superclass nodes should use the subclass alias for field aliasing.
     */
    private String findFirstNonSuperclassAncestor(String alias, Map<String, QueryNode> parentNodes) {
        QueryNode current = parentNodes.get(alias);
        while (current != null) {
            if (current instanceof EmptyTableNode etn && !etn.isSuperClass()) {
                return etn.alias();
            }
            if (current instanceof JoinedNode jn && !jn.isSuperClass()) {
                return jn.alias();
            }
            current = parentNodes.get(current.alias());
        }
        return alias; // Fallback to original alias
    }
}
