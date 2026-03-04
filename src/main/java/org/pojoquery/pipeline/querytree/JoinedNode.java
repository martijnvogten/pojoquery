package org.pojoquery.pipeline.querytree;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Represents a database table in the query tree.
 * This is the primary node type for both root tables and joined tables.
 *
 * @param alias The alias used to reference this table in the query
 * @param type The Java type that will be instantiated for rows from this table
 * @param tableInfo The table information (schema and table name)
 * @param fields Fields to select from this table
 * @param children Child nodes from this table (each carries its own JoinInfo)
 * @param idFieldNames Names of the primary key fields (for deduplication)
 * @param isSingleTableInheritance True if using single-table inheritance strategy
 * @param discriminatorColumn Column name for discriminator (STI only)
 * @param discriminatorValues Map of discriminator values to subtypes (STI only)
 * @param otherField Field annotated with @Other for dynamic columns (may be null)
 * @param otherColumnPrefix Prefix filter for @Other columns (may be null)
 * @param joinInfo Join information (null for root tables)
 */
public record JoinedNode(
    String alias,
    TypeModel type,
    TableInfo tableInfo,
    List<FieldSelection> fields,
    List<QueryNode> children,
    List<String> idFieldNames,
    boolean isSingleTableInheritance,
    String discriminatorColumn,
    Map<String, TypeModel> discriminatorValues,
    FieldModel otherField,
    String otherColumnPrefix,
    JoinInfo joinInfo,
    List<BareJoinInfo> extraJoins,
    boolean isSuperClass,
    boolean isSubClass
) implements TableNode, HasToStringWithIndent {
    
    /**
     * Compact constructor for validation.
     */
    public JoinedNode {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(tableInfo, "tableInfo");
        children = children == null ? List.of() : children;
    }
    
    /**
     * Creates a simple TableNode without inheritance or @Other support (as root).
     */
    public static JoinedNode simple(
            String alias,
            TypeModel type,
            TableInfo tableInfo,
            List<FieldSelection> fields,
            List<QueryNode> children,
            List<String> idFieldNames) {
        return new JoinedNode(
            alias, type, tableInfo, fields, children, idFieldNames,
            false, null, null, null, null, null, List.of(), false, false
        );
    }
    
    /**
     * Creates a joined TableNode with JoinInfo.
     */
    public static JoinedNode joined(
            String alias,
            TypeModel type,
            TableInfo tableInfo,
            List<FieldSelection> fields,
            List<QueryNode> children,
            List<String> idFieldNames,
            JoinInfo joinInfo) {
        return new JoinedNode(
            alias, type, tableInfo, fields, children, idFieldNames,
            false, null, null, null, null, joinInfo, List.of(), false, false
        );
    }

    // --- With methods for immutable transformations ---
    
    /**
     * Returns a new TableNode with different fields.
     */
    @Override
    public JoinedNode withFields(List<FieldSelection> newFields) {
        return new JoinedNode(alias, type, tableInfo,
            newFields, children, idFieldNames,
            isSingleTableInheritance, discriminatorColumn, discriminatorValues,
            otherField, otherColumnPrefix, joinInfo, extraJoins, isSuperClass, isSubClass);
    }
    
    /**
     * Returns a new TableNode with different children.
     */
    public JoinedNode withChildren(List<QueryNode> newChildren) {
        return new JoinedNode(alias, type, tableInfo,
            fields, newChildren, idFieldNames,
            isSingleTableInheritance, discriminatorColumn, discriminatorValues,
            otherField, otherColumnPrefix, joinInfo, extraJoins, isSuperClass, isSubClass);
    }
    
    /**
     * Returns a new JoinedNode with additional fields appended.
     */
    public JoinedNode withAddedFields(List<FieldSelection> additionalFields) {
        List<FieldSelection> newFields = new java.util.ArrayList<>(fields);
        newFields.addAll(additionalFields);
        return withFields(newFields);
    }
    
    /**
     * Returns a new JoinedNode with additional children appended.
     */
    public JoinedNode withAddedChildren(List<QueryNode> additionalChildren) {
        List<QueryNode> newChildren = new java.util.ArrayList<>(children);
        newChildren.addAll(additionalChildren);
        return withChildren(newChildren);
    }
    
    /**
     * Returns a new JoinedNode with single-table inheritance configured.
     */
    public JoinedNode withSingleTableInheritance(String discColumn, Map<String, TypeModel> discValues) {
        return new JoinedNode(alias, type, tableInfo,
            fields, children, idFieldNames,
            true, discColumn, discValues,
            otherField, otherColumnPrefix, joinInfo, extraJoins, isSuperClass, isSubClass);
    }
    
    /**
     * Returns a new JoinedNode with @Other field configured.
     */
    public JoinedNode withOtherField(FieldModel other, String prefix) {
        return new JoinedNode(alias, type, tableInfo,
            fields, children, idFieldNames,
            isSingleTableInheritance, discriminatorColumn, discriminatorValues,
            other, prefix, joinInfo, extraJoins, isSuperClass, isSubClass);
    }
    
    /**
     * Returns a new JoinedNode with a different alias.
     */
    public JoinedNode withAlias(String newAlias) {
        return new JoinedNode(newAlias, type, tableInfo,
            fields, children, idFieldNames,
            isSingleTableInheritance, discriminatorColumn, discriminatorValues,
            otherField, otherColumnPrefix, joinInfo, extraJoins, isSuperClass, isSubClass);
    }

    public JoinedNode withTableName(String newSchema, String newTable) {
        return new JoinedNode(alias, type, new TableInfo(newSchema, newTable),
            fields, children, idFieldNames,
            isSingleTableInheritance, discriminatorColumn, discriminatorValues,
            otherField, otherColumnPrefix, joinInfo, extraJoins, isSuperClass, isSubClass);
    }
    
    /**
     * Returns a new TableNode with different join info.
     */
    public JoinedNode withJoinInfo(JoinInfo newJoinInfo) {
        return new JoinedNode(alias, type, tableInfo,
            fields, children, idFieldNames,
            isSingleTableInheritance, discriminatorColumn, discriminatorValues,
            otherField, otherColumnPrefix, newJoinInfo, extraJoins, isSuperClass, isSubClass);
    }

    public JoinedNode withExtraJoins(List<BareJoinInfo> newExtraJoins) {
        return new JoinedNode(alias, type, tableInfo,
            fields, children, idFieldNames,
            isSingleTableInheritance, discriminatorColumn, discriminatorValues,
            otherField, otherColumnPrefix, joinInfo, newExtraJoins, isSuperClass, isSubClass);
    }

    public JoinedNode withIsSuperClass(boolean isSuperClass) {
        return new JoinedNode(alias, type, tableInfo,
            fields, children, idFieldNames,
            isSingleTableInheritance, discriminatorColumn, discriminatorValues,
            otherField, otherColumnPrefix, joinInfo, extraJoins, isSuperClass, isSubClass);
    }

    public JoinedNode withIsSubClass(boolean isSubClass) {
        return new JoinedNode(alias, type, tableInfo,
            fields, children, idFieldNames,
            isSingleTableInheritance, discriminatorColumn, discriminatorValues,
            otherField, otherColumnPrefix, joinInfo, extraJoins, isSuperClass, isSubClass);
    }

    @Override
    public String toString() {
        return toStringWithIndent("");
    }

    @Override
    public String toStringWithIndent(String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("JoinedNode {\n");
        sb.append(indent).append("  alias: \"").append(alias).append("\"\n");
        sb.append(indent).append("  table: ");
        if (tableInfo.schemaName() != null) {
            sb.append(tableInfo.schemaName()).append(".");
        }
        sb.append(tableInfo.tableName()).append("\n");
		if (type != null) {
        	sb.append(indent).append("  type: ").append(type.getQualifiedName()).append("\n");
		}
        if (!(idFieldNames == null || idFieldNames.isEmpty())) {
            sb.append(indent).append("  idFields: ").append(idFieldNames).append("\n");
        }
        if (isSingleTableInheritance) {
            sb.append(indent).append("  STI discriminator: ").append(discriminatorColumn).append("\n");
        }
        if (isSuperClass) {
            sb.append(indent).append("  isSuperClass: true\n");
        }
        if (isSubClass) {
            sb.append(indent).append("  isSubClass: true\n");
        }
        if (joinInfo != null) {
            sb.append(indent).append("  joinInfo: ").append(joinInfo.joinType());
            if (joinInfo.condition() != null) {
                sb.append(" ON ").append(joinInfo.condition().getSql());
            }
            sb.append("\n");
        }
        if (!(fields == null || fields.isEmpty())) {
            sb.append(indent).append("  fields: [\n");
            for (FieldSelection f : fields) {
                sb.append(indent).append("    ").append(f).append("\n");
            }
            sb.append(indent).append("  ]\n");
        }
        if (!(children == null || children.isEmpty())) {
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
