package org.pojoquery.pipeline.querytree;

import java.util.List;
import java.util.Objects;

import org.pojoquery.typemodel.TypeModel;

/**
 * Represents an embedded value object in the query tree.
 * Embedded objects are mapped to columns in the parent table with a prefix,
 * rather than having their own table.
 *
 * @param alias The alias used to reference this embedded object
 * @param type The Java type of the embedded object
 * @param fieldPrefix The column name prefix for embedded fields
 * @param fields Fields to select for this embedded object
 * @param joins Child joins from embedded fields (e.g., linked entities within embedded)
 */
public record EmbeddedNode(
    String alias,
    TypeModel type,
    String fieldPrefix,
    List<FieldSelection> fields,
    List<JoinedNode> joins
) implements QueryNode {
    
    public EmbeddedNode {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(type, "type");
        fieldPrefix = fieldPrefix == null ? "" : fieldPrefix;
        fields = fields == null ? List.of() : List.copyOf(fields);
        joins = joins == null ? List.of() : List.copyOf(joins);
    }

    @Override
    public String toString() {
        return toStringWithIndent("");
    }

    String toStringWithIndent(String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("EmbeddedNode {\n");
        sb.append(indent).append("  alias: \"").append(alias).append("\"\n");
        sb.append(indent).append("  type: ").append(type.getSimpleName()).append("\n");
        sb.append(indent).append("  prefix: \"").append(fieldPrefix).append("\"\n");
        if (!fields.isEmpty()) {
            sb.append(indent).append("  fields: [\n");
            for (FieldSelection f : fields) {
                sb.append(indent).append("    ").append(f).append("\n");
            }
            sb.append(indent).append("  ]\n");
        }
        if (!joins.isEmpty()) {
            sb.append(indent).append("  joins: [\n");
            for (JoinedNode j : joins) {
                sb.append(j.toStringWithIndent(indent + "    "));
            }
            sb.append(indent).append("  ]\n");
        }
        sb.append(indent).append("}\n");
        return sb.toString();
    }
}
