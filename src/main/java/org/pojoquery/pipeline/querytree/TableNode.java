package org.pojoquery.pipeline.querytree;

import java.util.List;

/**
 * Sealed interface for all query tree nodes.
 * A TableNode represents a table, or embedded structure in the query.
 */
public sealed interface TableNode extends QueryNode permits JoinedNode, EmbeddedNode {

    TableNode withFields(List<FieldSelection> newFields);

    TableNode withChildren(List<QueryNode> newChildren);
    
    List<FieldSelection> fields();

    /**
     * Join information describing how this node joins to its parent.
     * Returns null for root nodes.
     */
    default JoinInfo joinInfo() {
        return null;
    }

    default EmbedInfo embedInfo() {
        return null;
    }

    default TableInfo tableInfo() {
        return null;
    }
}
