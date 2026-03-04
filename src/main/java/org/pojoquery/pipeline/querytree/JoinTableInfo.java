package org.pojoquery.pipeline.querytree;

import java.util.Objects;

import org.pojoquery.SqlExpression;

/**
 * Encapsulates many-to-many join metadata via a junction/link table.
 * 
 * <p>Example: Article ↔ Tag via article_tag junction table
 * <pre>
 * SELECT * FROM article a
 * LEFT JOIN article_tag at ON a.id = at.article_id      -- parent join
 * LEFT JOIN tag t ON at.tag_id = t.id                   -- target join
 * </pre>
 *
 * @param joinTable The junction/link table info (e.g., "article_tag")
 * @param joinTableAlias The alias for the junction table in the query
 * @param parentFkColumn FK column in junction table pointing to parent (e.g., "article_id")
 * @param parentRefColumn Referenced column in parent table (e.g., "id")
 * @param targetFkColumn FK column in junction table pointing to target (e.g., "tag_id")
 * @param targetRefColumn Referenced column in target table (e.g., "id")
 */
public record JoinTableInfo(
    TableInfo joinTable,
    String joinTableAlias,
    String parentFkColumn,
    String parentRefColumn,
    String targetFkColumn,
    String targetRefColumn
) {
    public JoinTableInfo {
        Objects.requireNonNull(joinTable, "joinTable");
        Objects.requireNonNull(joinTableAlias, "joinTableAlias");
        Objects.requireNonNull(parentFkColumn, "parentFkColumn");
        Objects.requireNonNull(parentRefColumn, "parentRefColumn");
        Objects.requireNonNull(targetFkColumn, "targetFkColumn");
        Objects.requireNonNull(targetRefColumn, "targetRefColumn");
    }
    
    /**
     * Creates a JoinTableInfo with the given parameters.
     */
    public static JoinTableInfo of(TableInfo joinTable, String joinTableAlias,
            String parentFkColumn, String parentRefColumn,
            String targetFkColumn, String targetRefColumn) {
        return new JoinTableInfo(joinTable, joinTableAlias, 
            parentFkColumn, parentRefColumn, targetFkColumn, targetRefColumn);
    }
    
    /**
     * Returns the SQL condition for joining parent to junction table.
     * Pattern: {parent.parentRefColumn} = {joinTable.parentFkColumn}
     */
    public SqlExpression parentCondition(String parentAlias) {
        return new SqlExpression(
            "{" + parentAlias + "." + parentRefColumn + "} = {" + 
            joinTableAlias + "." + parentFkColumn + "}"
        );
    }
    
    /**
     * Returns the SQL condition for joining junction table to target.
     * Pattern: {joinTable.targetFkColumn} = {target.targetRefColumn}
     */
    public SqlExpression targetCondition(String targetAlias) {
        return new SqlExpression(
            "{" + joinTableAlias + "." + targetFkColumn + "} = {" + 
            targetAlias + "." + targetRefColumn + "}"
        );
    }
    
    public String toStringWithIndent(String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("JoinTableInfo {\n");
        sb.append(indent).append("  joinTable: ").append(joinTable.tableName()).append("\n");
        sb.append(indent).append("  alias: \"").append(joinTableAlias).append("\"\n");
        sb.append(indent).append("  parentFkColumn: \"").append(parentFkColumn).append("\"\n");
        sb.append(indent).append("  parentRefColumn: \"").append(parentRefColumn).append("\"\n");
        sb.append(indent).append("  targetFkColumn: \"").append(targetFkColumn).append("\"\n");
        sb.append(indent).append("  targetRefColumn: \"").append(targetRefColumn).append("\"\n");
        sb.append(indent).append("}");
        return sb.toString();
    }
}

