package org.pojoquery.pipeline.querytree;

import java.util.List;

import org.pojoquery.typemodel.TypeModel;

/**
 * Sealed interface for all query tree nodes.
 * A QueryNode represents a table, subquery, or embedded structure in the query.
 * 
 * <p>Each node (except the root) carries its own {@link JoinInfo} describing
 * how it joins to its parent. The root node returns null for joinInfo().</p>
 */
public sealed interface QueryNode permits EmptyTableNode, TableNode, LinkedValueNode {
    
    /**
     * The alias used to reference this node in the query.
     */
    String alias();
    
    /**
     * The result type that will be instantiated for this node.
     */
    TypeModel type();
    
    /**
     * Child nodes from this node. Each child carries its own join information.
     */
    List<QueryNode> children();
    
    /**
     * Returns a copy of this node with the given children.
     */
    QueryNode withChildren(List<QueryNode> children);
    
    default JoinInfo joinInfo() {
        return null;
    }

    default EmbedInfo embedInfo() {
        return null;
    }
}
