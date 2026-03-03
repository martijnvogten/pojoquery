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
 * @param children Child nodes from embedded fields (e.g., linked entities within embedded)
 * @param joinInfo Join information (tracks the linkField, even though joinType is NULL)
 */
public record EmbeddedNode(
    String alias,
    TypeModel type,
    List<FieldSelection> fields,
    List<QueryNode> children,
    EmbedInfo embedInfo
) implements TableNode, HasToStringWithIndent {
    
    public EmbeddedNode {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(embedInfo, "embedInfo");
        fields = fields == null ? List.of() : List.copyOf(fields);
        children = children == null ? List.of() : List.copyOf(children);
    }
    
    /**
     * Creates an EmbeddedNode with the given embed info.
     */
    public static EmbeddedNode of(String alias, TypeModel type, List<FieldSelection> fields, EmbedInfo embedInfo) {
        return new EmbeddedNode(alias, type, fields, List.of(), embedInfo);
    }
    
    @Override
    public EmbeddedNode withChildren(List<QueryNode> newChildren) {
        return new EmbeddedNode(alias, type, fields, newChildren, embedInfo);
    }

    @Override
    public EmbeddedNode withFields(List<FieldSelection> newFields) {
        return new EmbeddedNode(alias, type, newFields, children, embedInfo);
    }

    @Override
    public String toString() {
        return toStringWithIndent("");
    }

    public String toStringWithIndent(String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("EmbeddedNode {\n");
        sb.append(indent).append("  alias: \"").append(alias).append("\"\n");
        sb.append(indent).append("  type: ").append(type.getSimpleName()).append("\n");
        sb.append(indent).append("  prefix: \"").append(embedInfo.fieldPrefix()).append("\"\n");
        if (!fields.isEmpty()) {
            sb.append(indent).append("  fields: [\n");
            for (FieldSelection f : fields) {
                sb.append(indent).append("    ").append(f).append("\n");
            }
            sb.append(indent).append("  ]\n");
        }
        if (!children.isEmpty()) {
            sb.append(indent).append("  children: [\n");
            for (QueryNode child : children) {
                sb.append(QueryTree.toStringNode(child, indent + "    "));
            }
            sb.append(indent).append("  ]\n");
        }
        sb.append(indent).append("}\n");
        return sb.toString();
    }
}
