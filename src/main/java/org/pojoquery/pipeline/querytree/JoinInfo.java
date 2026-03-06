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
 * @param joinCondition The structured join condition (may be null if not yet resolved)
 * @param linkField The Java field that created this join (may be null for manual joins)
 * @param isCollection True if this is a one-to-many relationship (List/Set/Array)
 */
public record JoinInfo(
    JoinType joinType,
    boolean isCollection,
    FieldModel linkField,
    TableInfo childTable,
    JoinCondition joinCondition,
    JoinTableInfo joinTableInfo,
    QueryTree subquery
) {
    public JoinInfo {
        Objects.requireNonNull(joinType, "joinType");
    }

    public static JoinInfo manyToMany(TableInfo childTable, FieldModel linkField, JoinTableInfo joinTableInfo) {
        return new JoinInfo(JoinType.LEFT, true, linkField, childTable, null, joinTableInfo, null);
    }

    public static JoinInfo leftJoinMany(TableInfo childTable, FieldModel linkField) {
        return new JoinInfo(JoinType.LEFT, true, linkField, childTable, null, null, null);
    }

    public static JoinInfo leftJoinOne(TableInfo childTable, FieldModel linkField) {
        return new JoinInfo(JoinType.LEFT, false, linkField, childTable, null, null, null);
    }

    public static JoinInfo leftJoinSubClass(TableInfo childTable, JoinCondition.SharedPrimaryKey condition) {
        return new JoinInfo(JoinType.LEFT, false, null, childTable, condition, null, null);
    }

    public static JoinInfo leftJoinSuperClass(TableInfo childTable, JoinCondition.SharedPrimaryKey condition) {
        return new JoinInfo(JoinType.LEFT, false, null, childTable, condition, null, null);
    }

    public static JoinInfo innerJoinSuperClass(TableInfo childTable, JoinCondition.SharedPrimaryKey condition) {
        return new JoinInfo(JoinType.INNER, false, null, childTable, condition, null, null);
    }

    public JoinInfo withJoinCondition(JoinCondition joinCondition) {
        return new JoinInfo(joinType, isCollection, linkField, childTable, joinCondition, joinTableInfo, subquery);
    }

    /**
     * @deprecated Use {@link #withJoinCondition(JoinCondition)} instead
     */
    @Deprecated
    public JoinInfo withCondition(SqlExpression condition) {
        return new JoinInfo(joinType, isCollection, linkField, childTable, new JoinCondition.Custom(condition), joinTableInfo, subquery);
    }

    public JoinInfo withSubQuery(QueryTree subquery) {
        return new JoinInfo(joinType, isCollection, linkField, childTable, joinCondition, joinTableInfo, subquery);
    }

    public boolean isManyToMany() {
        return isCollection && joinTableInfo != null;
    }
    
    /**
     * Returns the join condition as a SQL expression.
     * @param parentAlias The parent table alias
     * @param childAlias The child table alias
     * @return The SQL expression, or null if no condition is set
     */
    public SqlExpression toSqlCondition(String parentAlias, String childAlias) {
        return joinCondition != null ? joinCondition.toSqlExpression(parentAlias, childAlias) : null;
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
        if (joinCondition != null) {
            sb.append(indent).append("  joinCondition: ").append(joinCondition).append("\n");
        }
        if (joinTableInfo != null) {
            sb.append(indent).append("  joinTableInfo: ").append(joinTableInfo.toStringWithIndent(indent + "  ")).append("\n");
        }
        if (subquery != null) {
            sb.append(indent).append("  subquery: ").append(subquery.toStringWithIndent(indent + "  ")).append("\n");
        }
        sb.append(indent).append("}");
        return sb.toString();
    }
}
