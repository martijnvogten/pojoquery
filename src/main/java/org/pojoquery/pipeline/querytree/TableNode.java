package org.pojoquery.pipeline.querytree;

import java.util.List;

/**
 * Sealed interface for all query tree nodes.
 * A TableNode represents a table, or embedded structure in the query.
 */
public sealed interface TableNode extends QueryNode permits JoinedNode, EmbeddedNode {

    TableNode withFields(List<FieldSelectionBase> newFields);

    TableNode withChildren(List<QueryNode> newChildren);
    
    List<FieldSelectionBase> fields();
    
    /**
     * Returns only resolved fields (ready for SQL generation).
     * Filters out UnresolvedFieldSelection entries.
     */
    default List<FieldSelection> resolvedFields() {
        return fields().stream()
            .filter(f -> f instanceof FieldSelection)
            .map(f -> (FieldSelection) f)
            .toList();
    }
    
    /**
     * Returns only unresolved fields (need further processing).
     */
    default List<UnresolvedFieldSelection> unresolvedFields() {
        return fields().stream()
            .filter(f -> f instanceof UnresolvedFieldSelection)
            .map(f -> (UnresolvedFieldSelection) f)
            .toList();
    }
    
    /**
     * Returns the alias to use as the source for SQL column references.
     * <ul>
     *   <li>For JoinedNode: returns alias() (has its own table)</li>
     *   <li>For EmbeddedNode: returns embedInfo().sourceAlias() (shares parent's table)</li>
     * </ul>
     */
    String sourceAlias();

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
