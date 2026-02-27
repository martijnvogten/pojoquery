package org.pojoquery.pipeline.querytree;

import java.util.Objects;

import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.typemodel.FieldModel;

/**
 * Represents a join relationship to a child node in the query tree.
 *
 * @param joinType The SQL join type (LEFT, INNER, etc.)
 * @param condition The join condition SQL expression
 * @param node The child node being joined
 * @param linkField The Java field that created this join (may be null for manual joins)
 * @param isCollection True if this is a one-to-many relationship (List/Set/Array)
 */
public record JoinedNode(
    JoinType joinType,
    SqlExpression condition,
    QueryNode node,
    FieldModel linkField,
    boolean isCollection
) {
    
    public JoinedNode {
        Objects.requireNonNull(joinType, "joinType");
        Objects.requireNonNull(node, "node");
        // condition may be null for CROSS JOIN
    }
    
    /**
     * Creates a LEFT JOIN for a single entity reference.
     */
    public static JoinedNode leftJoinOne(SqlExpression condition, QueryNode node, FieldModel linkField) {
        return new JoinedNode(JoinType.LEFT, condition, node, linkField, false);
    }
    
    /**
     * Creates a LEFT JOIN for a collection.
     */
    public static JoinedNode leftJoinMany(SqlExpression condition, QueryNode node, FieldModel linkField) {
        return new JoinedNode(JoinType.LEFT, condition, node, linkField, true);
    }
    
    // --- With methods for immutable transformations ---
    
    /**
     * Returns a new JoinedNode with a different child node.
     */
    public JoinedNode withNode(QueryNode newNode) {
        return new JoinedNode(joinType, condition, newNode, linkField, isCollection);
    }
    
    /**
     * Returns a new JoinedNode with a different condition.
     */
    public JoinedNode withCondition(SqlExpression newCondition) {
        return new JoinedNode(joinType, newCondition, node, linkField, isCollection);
    }
    
    /**
     * Returns a new JoinedNode with a different join type.
     */
    public JoinedNode withJoinType(JoinType newJoinType) {
        return new JoinedNode(newJoinType, condition, node, linkField, isCollection);
    }

    @Override
    public String toString() {
        return toStringWithIndent("");
    }

    String toStringWithIndent(String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("JoinedNode {\n");
        sb.append(indent).append("  joinType: ").append(joinType).append("\n");
        sb.append(indent).append("  isCollection: ").append(isCollection).append("\n");
        if (condition != null) {
            sb.append(indent).append("  condition: \"").append(condition.getSql()).append("\"\n");
        }
        if (linkField != null) {
            sb.append(indent).append("  linkField: ").append(linkField.getName()).append("\n");
        }
        sb.append(indent).append("  node:\n");
        sb.append(QueryTree.toStringNode(node, indent + "    "));
        sb.append(indent).append("}\n");
        return sb.toString();
    }
}
