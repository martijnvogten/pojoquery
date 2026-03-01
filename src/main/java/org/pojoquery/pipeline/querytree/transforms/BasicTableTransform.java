package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.QueryModel.determineIdFields;
import static org.pojoquery.pipeline.QueryModel.determineSqlFieldName;
import static org.pojoquery.pipeline.QueryModel.determineTableMapping;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;

/**
 * Creates root TableNode for the specified result type, collects @Id fields.
 * Only handles primitive/wrapper/common types - no relationships.
 */
public class BasicTableTransform implements QueryTreeTransform {

    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformNodes(n -> n instanceof EmptyTableNode, this::transformTableNode);
    }

    private QueryNode transformTableNode(EmptyTableNode node) {
        List<TableMapping> tableMapping = determineTableMapping(node.type()); // Validate @Table presence and hierarchy
        if (tableMapping.isEmpty()) {
            throw new IllegalArgumentException(
                    "No @Table annotation found on " + node.type().getQualifiedName() + " or its superclasses");
        }

        TableMapping mapping = tableMapping.get(tableMapping.size() - 1);
        
        List<FieldSelection> fields = new ArrayList<>();
        List<String> idFields = new ArrayList<>();

        for (FieldModel f : FieldFilters.simpleFields(node.type())) {
            String colName = determineSqlFieldName(f);
            fields.add(FieldSelection.column(node.alias(), colName, f));
        }

        for (FieldModel f : determineIdFields(node.type())) {
            idFields.add(determineSqlFieldName(f));
        }

        // Preserve joins from EmptyTableNode (e.g., superclass joins)
        return TableNode.simple(node.alias(), node.type(), mapping.schemaName, mapping.tableName, fields, node.joins(), idFields);
    }
}
