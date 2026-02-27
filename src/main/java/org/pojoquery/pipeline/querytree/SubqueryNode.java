package org.pojoquery.pipeline.querytree;

import java.util.List;
import java.util.Objects;

import org.pojoquery.typemodel.TypeModel;

/**
 * Represents a derived table (subquery) in the query tree.
 * Used for @Subquery fields that join aggregated data.
 *
 * @param alias The alias used to reference this subquery in the query
 * @param type The Java type that will be instantiated for rows from this subquery
 * @param subquery The inner query tree that forms the subquery
 * @param fields Fields to select from this subquery result
 * @param joins Child joins from this subquery (typically empty)
 */
public record SubqueryNode(
    String alias,
    TypeModel type,
    QueryTree subquery,
    List<FieldSelection> fields,
    List<JoinedNode> joins
) implements QueryNode {
    
    public SubqueryNode {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subquery, "subquery");
        fields = fields == null ? List.of() : List.copyOf(fields);
        joins = joins == null ? List.of() : List.copyOf(joins);
    }

    @Override
    public String toString() {
        return toStringWithIndent("");
    }

    String toStringWithIndent(String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("SubqueryNode {\n");
        sb.append(indent).append("  alias: \"").append(alias).append("\"\n");
        sb.append(indent).append("  type: ").append(type.getSimpleName()).append("\n");
        sb.append(indent).append("  subquery:\n");
        sb.append(QueryTree.toStringNode(subquery.root(), indent + "    "));
        sb.append(indent).append("}\n");
        return sb.toString();
    }
}
