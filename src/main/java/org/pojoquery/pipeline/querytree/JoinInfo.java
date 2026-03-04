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

    @Override
    public String toString() {
        return toStringWithIndent("");
    }

    public String toStringWithIndent(String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("JoinInfo {\n");
        sb.append(indent).append("  joinType: ").append(joinType).append("\n");
        if (isCollection) {
            sb.append(indent).append("  isCollection: true\n");
        }
        if (linkField != null) {
            sb.append(indent).append("  linkField: \"").append(linkField.getName()).append("\"\n");
        }
        if (childTable != null) {
            sb.append(indent).append("  childTable: ").append(childTable.tableName()).append("\n");
        }
        if (condition != null) {
            sb.append(indent).append("  condition: ").append(condition.getSql()).append("\n");
        }
        if (joinTableInfo != null) {
            sb.append(indent).append("  joinTableInfo: {\n");
            sb.append(indent).append("    joinTable: ").append(joinTableInfo.joinTable().tableName()).append("\n");
            sb.append(indent).append("    alias: \"").append(joinTableInfo.joinTableAlias()).append("\"\n");
            if (joinTableInfo.parentCondition() != null) {
                sb.append(indent).append("    parentCondition: ").append(joinTableInfo.parentCondition().getSql()).append("\n");
            }
            if (joinTableInfo.targetCondition() != null) {
                sb.append(indent).append("    targetCondition: ").append(joinTableInfo.targetCondition().getSql()).append("\n");
            }
            sb.append(indent).append("  }\n");
        }
        if (subquery != null) {
            sb.append(indent).append("  subquery: ").append(subquery.toStringWithIndent(indent + "  ")).append("\n");
        }
        sb.append(indent).append("}");
        return sb.toString();
    }
}
