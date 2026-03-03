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
import org.pojoquery.pipeline.querytree.TableInfo;
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
        List<FieldSelection> fields = new ArrayList<>();
        List<String> idFields = new ArrayList<>();
        boolean isEmbedded = node.embedInfo() != null;

        for (FieldModel f : FieldFilters.simpleFields(node.type())) {
            String colName = determineSqlFieldName(f);
            String sourceAlias = node.alias();
            if (isEmbedded) {
                colName = node.embedInfo().fieldPrefix() + colName;
                sourceAlias = node.embedInfo().sourceAlias();
            }
            fields.add(FieldSelection.column(sourceAlias, node.alias(), colName, f));
        }

        for (FieldModel f : determineIdFields(node.type())) {
            idFields.add(determineSqlFieldName(f));
        }

        if (isEmbedded) {
            return node.toEmbeddedNode(fields);
        } else {
            List<TableMapping> tableMapping = determineTableMapping(node.type()); // Validate @Table presence and hierarchy
            TableMapping mapping = tableMapping.get(tableMapping.size() - 1);
            return node.toJoinedNode(new TableInfo(mapping.schemaName, mapping.tableName), fields, idFields);
        }
    }
}
