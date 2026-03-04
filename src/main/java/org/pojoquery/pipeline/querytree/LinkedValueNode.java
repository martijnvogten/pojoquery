package org.pojoquery.pipeline.querytree;

import java.util.List;
import java.util.Objects;

import org.pojoquery.typemodel.TypeModel;

/**
 * Represents a scalar value collection fetched via a link table.
 * Used for @Link(fetchColumn) when collecting simple values like enums or strings.
 *
 * @param alias The alias used to reference this value collection
 * @param type The Java type of the collection element (e.g., enum type, String)
 * @param linkTableSchema The schema of the link table (may be null)
 * @param linkTableName The link table name
 * @param fetchColumn The column in the link table to fetch values from
 * @param joinInfo Join information describing how this node joins to its parent
 */
public record LinkedValueNode(
    String alias,
    TypeModel type,
    String linkTableSchema,
    String linkTableName,
    String fetchColumn,
    JoinInfo joinInfo
) implements QueryNode, HasToStringWithIndent {
    
    public LinkedValueNode {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(linkTableName, "linkTableName");
        Objects.requireNonNull(fetchColumn, "fetchColumn");
    }
    
    @Override
    public List<QueryNode> children() {
        return List.of();
    }
    
    @Override
    public LinkedValueNode withChildren(List<QueryNode> newChildren) {
        // LinkedValueNode has no children, ignore
        return this;
    }

    @Override
    public String toString() {
        return toStringWithIndent("");
    }

    @Override
    public String toStringWithIndent(String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("LinkedValueNode {\n");
        sb.append(indent).append("  alias: \"").append(alias).append("\"\n");
        sb.append(indent).append("  linkTable: ");
        if (linkTableSchema != null) {
            sb.append(linkTableSchema).append(".");
        }
        sb.append(linkTableName).append("\n");
        sb.append(indent).append("  fetchColumn: ").append(fetchColumn).append("\n");
        sb.append(indent).append("  valueType: ").append(type.getSimpleName()).append("\n");
        if (joinInfo != null) {
            sb.append(indent).append("  joinInfo: ").append(joinInfo.joinType());
            if (joinInfo.joinCondition() != null) {
                sb.append(" ON ").append(joinInfo.joinCondition());
            }
            sb.append("\n");
        }
        sb.append(indent).append("}\n");
        return sb.toString();
    }
}
