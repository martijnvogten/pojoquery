package org.pojoquery.pipeline.querytree;

import java.util.Objects;

import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.SqlQuery.JoinType;

public record BareJoinInfo(
    String alias,
    JoinType joinType,
    TableInfo childTable,
    SqlExpression condition
) {
    public BareJoinInfo {
        Objects.requireNonNull(joinType, "joinType");
    }

    public static BareJoinInfo of(String alias, JoinType joinType, TableInfo childTable, SqlExpression condition) {
        return new BareJoinInfo(alias, joinType, childTable, condition);
    }
}
