package org.pojoquery.pipeline.querytree;

import java.util.List;

import org.pojoquery.typemodel.TypeModel;

/**
 * Sealed interface for all query tree nodes.
 * A QueryNode represents a table, subquery, or embedded structure in the query.
 */
public sealed interface QueryNode permits TableNode, SubqueryNode, EmbeddedNode, LinkedValueNode {
    
    /**
     * The alias used to reference this node in the query.
     */
    String alias();
    
    /**
     * The result type that will be instantiated for this node.
     */
    TypeModel type();
    
    /**
     * Fields selected from this node.
     */
    List<FieldSelection> fields();
    
    /**
     * Child joins from this node.
     */
    List<JoinedNode> joins();
}
