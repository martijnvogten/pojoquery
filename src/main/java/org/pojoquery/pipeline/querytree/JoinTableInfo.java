package org.pojoquery.pipeline.querytree;

import java.util.Objects;

import org.pojoquery.SqlExpression;

/**
 * Encapsulates join metadata for a node in the query tree.
 * This record captures how a node is joined to its parent.
 *
 * @param joinType The SQL join type (LEFT, INNER, etc.)
 * @param condition The join condition SQL expression (may be null if not yet resolved)
 * @param linkField The Java field that created this join (may be null for manual joins)
 * @param isCollection True if this is a one-to-many relationship (List/Set/Array)
 */
public record JoinTableInfo(
    TableInfo joinTable,
    String joinTableAlias,
    SqlExpression parentCondition,
    SqlExpression targetCondition
) {
    public JoinTableInfo {
        Objects.requireNonNull(joinTable, "joinTable");
        Objects.requireNonNull(joinTableAlias, "joinTableAlias");
    }
}
