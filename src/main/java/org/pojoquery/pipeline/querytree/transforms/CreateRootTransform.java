package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.QueryModel.determineTableMapping;

import java.util.List;

import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.QueryTree;

/**
 * Creates root TableNode for the specified result type.
 */
public class CreateRootTransform implements QueryTreeTransform {

    @Override
    public QueryTree apply(QueryTree tree) {
        if (tree.root() != null) {
            return tree;
        }
        List<TableMapping> tableMapping = determineTableMapping(tree.resultType());
        if (tableMapping.isEmpty()) {
            throw new IllegalArgumentException(
                    "No @Table annotation found on " + tree.resultType().getQualifiedName() + " or its superclasses");
        }
        TableMapping mapping = tableMapping.get(tableMapping.size() - 1);
        return tree.withRoot(EmptyTableNode.of(mapping.tableName, tree.resultType()));
    }
}
