package org.pojoquery.pipeline.querytree;

import java.util.Objects;

import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.typemodel.FieldModel;

/**
 * Encapsulates join metadata for a node in the query tree.
 * This record captures how a node is joined to its parent.
 *
 * @param joinType The SQL join type (LEFT, INNER, etc.)
 * @param condition The join condition SQL expression (may be null if not yet resolved)
 * @param linkField The Java field that created this join (may be null for manual joins)
 * @param isCollection True if this is a one-to-many relationship (List/Set/Array)
 */
public record JoinInfo(
    JoinType joinType,
    boolean isCollection,
    FieldModel linkField,
    TableInfo childTable,
    SqlExpression condition,
    JoinTableInfo joinTableInfo,
    QueryTree subquery
) {
    public JoinInfo {
        Objects.requireNonNull(joinType, "joinType");
    }

    public static JoinInfo manyToMany(TableInfo childTable, FieldModel linkField, TableInfo joinTable, String joinTableAlias, SqlExpression parentCondition, SqlExpression targetCondition) {
        return new JoinInfo(JoinType.LEFT, true, linkField, childTable, null, new JoinTableInfo(joinTable, joinTableAlias, parentCondition, targetCondition), null);
    }

    public static JoinInfo leftJoinMany(TableInfo childTable, FieldModel linkField) {
        return new JoinInfo(JoinType.LEFT, true, linkField, childTable, null, null, null);
    }

    public static JoinInfo leftJoinOne(TableInfo childTable, FieldModel linkField) {
        return new JoinInfo(JoinType.LEFT, false, linkField, childTable, null, null, null);
    }

    public static JoinInfo leftJoinSubClass(TableInfo childTable, SqlExpression condition) {
        return new JoinInfo(JoinType.LEFT, false, null, childTable, condition, null, null);
    }

    public static JoinInfo leftJoinSuperClass(TableInfo childTable, SqlExpression condition) {
        return new JoinInfo(JoinType.LEFT, false, null, childTable, condition, null, null);
    }

    public JoinInfo withCondition(SqlExpression condition) {
        return new JoinInfo(joinType, isCollection, linkField, childTable, condition, joinTableInfo, subquery);
    }

    public JoinInfo withSubQuery(QueryTree subquery) {
        return new JoinInfo(joinType, isCollection, linkField, childTable, condition, joinTableInfo, subquery);
    }

    public boolean isManyToMany() {
        return isCollection && joinTableInfo != null;
    }
}
