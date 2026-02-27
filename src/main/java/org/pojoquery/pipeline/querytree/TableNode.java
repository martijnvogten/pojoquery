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
 * @param schemaName The database schema name (may be null)
 * @param tableName The database table name
 * @param fields Fields to select from this table
 * @param joins Child joins from this table
 * @param idFieldNames Names of the primary key fields (for deduplication)
 * @param isSingleTableInheritance True if using single-table inheritance strategy
 * @param discriminatorColumn Column name for discriminator (STI only)
 * @param discriminatorValues Map of discriminator values to subtypes (STI only)
 * @param otherField Field annotated with @Other for dynamic columns (may be null)
 * @param otherColumnPrefix Prefix filter for @Other columns (may be null)
 */
public record TableNode(
    String alias,
    TypeModel type,
    String schemaName,
    String tableName,
    List<FieldSelection> fields,
    List<JoinedNode> joins,
    List<String> idFieldNames,
    boolean isSingleTableInheritance,
    String discriminatorColumn,
    Map<String, TypeModel> discriminatorValues,
    FieldModel otherField,
    String otherColumnPrefix
) implements QueryNode {
    
    /**
     * Compact constructor for validation.
     */
    public TableNode {
        Objects.requireNonNull(alias, "alias");
        // Note: type may be null for link/junction tables that have no Java class
        Objects.requireNonNull(tableName, "tableName");
        fields = fields == null ? List.of() : List.copyOf(fields);
        joins = joins == null ? List.of() : List.copyOf(joins);
        idFieldNames = idFieldNames == null ? List.of() : List.copyOf(idFieldNames);
        discriminatorValues = discriminatorValues == null ? Map.of() : Map.copyOf(discriminatorValues);
    }
    
    /**
     * Creates a simple TableNode without inheritance or @Other support.
     */
    public static TableNode simple(
            String alias,
            TypeModel type,
            String schemaName,
            String tableName,
            List<FieldSelection> fields,
            List<JoinedNode> joins,
            List<String> idFieldNames) {
        return new TableNode(
            alias, type, schemaName, tableName, fields, joins, idFieldNames,
            false, null, null, null, null
        );
    }
    
    // --- With methods for immutable transformations ---
    
    /**
     * Returns a new TableNode with different fields.
     */
    public TableNode withFields(List<FieldSelection> newFields) {
        return new TableNode(alias, type, schemaName, tableName,
            newFields, joins, idFieldNames,
            isSingleTableInheritance, discriminatorColumn, discriminatorValues,
            otherField, otherColumnPrefix);
    }
    
    /**
     * Returns a new TableNode with different joins.
     */
    public TableNode withJoins(List<JoinedNode> newJoins) {
        return new TableNode(alias, type, schemaName, tableName,
            fields, newJoins, idFieldNames,
            isSingleTableInheritance, discriminatorColumn, discriminatorValues,
            otherField, otherColumnPrefix);
    }
    
    /**
     * Returns a new TableNode with additional fields appended.
     */
    public TableNode withAddedFields(List<FieldSelection> additionalFields) {
        List<FieldSelection> newFields = new java.util.ArrayList<>(fields);
        newFields.addAll(additionalFields);
        return withFields(newFields);
    }
    
    /**
     * Returns a new TableNode with additional joins appended.
     */
    public TableNode withAddedJoins(List<JoinedNode> additionalJoins) {
        List<JoinedNode> newJoins = new java.util.ArrayList<>(joins);
        newJoins.addAll(additionalJoins);
        return withJoins(newJoins);
    }
    
    /**
     * Returns a new TableNode with single-table inheritance configured.
     */
    public TableNode withSingleTableInheritance(String discColumn, Map<String, TypeModel> discValues) {
        return new TableNode(alias, type, schemaName, tableName,
            fields, joins, idFieldNames,
            true, discColumn, discValues,
            otherField, otherColumnPrefix);
    }
    
    /**
     * Returns a new TableNode with @Other field configured.
     */
    public TableNode withOtherField(FieldModel other, String prefix) {
        return new TableNode(alias, type, schemaName, tableName,
            fields, joins, idFieldNames,
            isSingleTableInheritance, discriminatorColumn, discriminatorValues,
            other, prefix);
    }
    
    /**
     * Returns a new TableNode with a different alias.
     */
    public TableNode withAlias(String newAlias) {
        return new TableNode(newAlias, type, schemaName, tableName,
            fields, joins, idFieldNames,
            isSingleTableInheritance, discriminatorColumn, discriminatorValues,
            otherField, otherColumnPrefix);
    }

    @Override
    public String toString() {
        return toStringWithIndent("");
    }

    String toStringWithIndent(String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("TableNode {\n");
        sb.append(indent).append("  alias: \"").append(alias).append("\"\n");
        sb.append(indent).append("  table: ");
        if (schemaName != null) {
            sb.append(schemaName).append(".");
        }
        sb.append(tableName).append("\n");
		if (type != null) {
        	sb.append(indent).append("  type: ").append(type.getQualifiedName()).append("\n");
		}
        if (!idFieldNames.isEmpty()) {
            sb.append(indent).append("  idFields: ").append(idFieldNames).append("\n");
        }
        if (isSingleTableInheritance) {
            sb.append(indent).append("  STI discriminator: ").append(discriminatorColumn).append("\n");
        }
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
