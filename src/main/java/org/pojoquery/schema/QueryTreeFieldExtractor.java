package org.pojoquery.schema;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.pojoquery.pipeline.querytree.EmbeddedNode;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.JoinCondition;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.JoinTableInfo;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;
import org.pojoquery.util.CurlyMarkers;

/**
 * Extracts and merges field definitions from multiple QueryTrees.
 * When the same (table, column) appears in multiple trees, keeps the richest definition.
 */
public class QueryTreeFieldExtractor {

    /** Unique identifier for a field within a schema */
    public record FieldKey(String schemaName, String tableName, String columnName) {
        public FieldKey {
            tableName = Objects.requireNonNull(tableName);
            columnName = Objects.requireNonNull(columnName);
        }
        
        public String tableKey() {
            return (schemaName != null ? schemaName + "." : "") + tableName;
        }
    }

    /** Extracted field information with richness tracking */
    public record FieldDef(
        FieldKey key,
        FieldModel field,          // may be null for synthetic columns (FK, discriminator)
        boolean isPrimaryKey,
        boolean isAutoIncrement,
        boolean isDiscriminator
    ) {

        /** Merge two definitions, keeping the richer one */
        public FieldDef mergeWith(FieldDef other) {
			return new FieldDef(
				key,
				field != null ? field : other.field,
				isPrimaryKey || other.isPrimaryKey,
				isAutoIncrement || other.isAutoIncrement,
				isDiscriminator || other.isDiscriminator
			);
        }
    }

    /** Collected table information */
    public record TableDef(
        String schemaName,
        String tableName,
        List<FieldDef> columns,
        List<String> primaryKey,
        String discriminatorColumn
    ) {
        public String tableKey() {
            return (schemaName != null ? schemaName + "." : "") + tableName;
        }
    }

    /** Collect all field definitions from multiple trees */
    public static Map<FieldKey, FieldDef> extract(QueryTree... trees) {
        return extract(List.of(trees));
    }

    public static Map<FieldKey, FieldDef> extract(Collection<QueryTree> trees) {
        Map<FieldKey, FieldDef> result = new LinkedHashMap<>();
        for (QueryTree tree : trees) {
            collectFromNode(tree.root(), result);
        }
        return result;
    }

    /** Group extracted fields by table */
    public static Map<String, TableDef> extractTables(QueryTree... trees) {
        return extractTables(List.of(trees));
    }

    public static Map<String, TableDef> extractTables(Collection<QueryTree> trees) {
        Map<FieldKey, FieldDef> allFields = extract(trees);
        
        // Group by table
        Map<String, List<FieldDef>> byTable = allFields.values().stream()
            .collect(Collectors.groupingBy(
                f -> f.key().tableKey(),
                LinkedHashMap::new,
                Collectors.toList()
            ));
        
        // Convert to TableDef
        Map<String, TableDef> tables = new LinkedHashMap<>();
        for (var entry : byTable.entrySet()) {
            List<FieldDef> columns = entry.getValue();
            if (columns.isEmpty()) continue;
            
            FieldKey firstKey = columns.get(0).key();
            List<String> primaryKey = columns.stream()
                .filter(FieldDef::isPrimaryKey)
                .map(f -> f.key().columnName())
                .toList();
            String discriminator = columns.stream()
                .filter(FieldDef::isDiscriminator)
                .map(f -> f.key().columnName())
                .findFirst()
                .orElse(null);
            
            tables.put(entry.getKey(), new TableDef(
                firstKey.schemaName(),
                firstKey.tableName(),
                columns,
                primaryKey,
                discriminator
            ));
        }
        
        return tables;
    }

    private static void collectFromNode(QueryNode node, Map<FieldKey, FieldDef> result) {
        if (node == null) return;
        
        if (node instanceof JoinedNode joined && joined.tableInfo() != null) {
            TableInfo tableInfo = joined.tableInfo();
            String schema = tableInfo.schemaName();
            String table = tableInfo.tableName();

            // Extract columns from field selections
            for (FieldSelection field : joined.fields()) {
                String column = field.columnName();
                if (column == null) continue;
                
                // Skip entity references and embedded fields
                if (field.field() != null && isEntityType(field.field().getType())) continue;
                if (isEmbeddedField(field.field())) continue;
                
                FieldKey key = new FieldKey(schema, table, column);
                boolean isPK = joined.idFieldNames() != null && 
                    field.field() != null &&
                    joined.idFieldNames().contains(field.field().getName());
                boolean isAutoInc = isPK && joined.idFieldNames().size() == 1;
                
                FieldDef def = new FieldDef(key, field.field(), isPK, isAutoInc, false);
                result.merge(key, def, FieldDef::mergeWith);
            }

            // Add discriminator column for STI
            if (joined.isSingleTableInheritance() && joined.discriminatorColumn() != null) {
                String discAlias = joined.discriminatorColumn();
                int dotIdx = discAlias.lastIndexOf('.');
                String discColumn = dotIdx >= 0 ? discAlias.substring(dotIdx + 1) : discAlias;
                
                FieldKey key = new FieldKey(schema, table, discColumn);
                FieldDef def = new FieldDef(key, null, false, false, true);
                result.merge(key, def, FieldDef::mergeWith);
            }

            // Add foreign key columns from join conditions
            for (QueryNode child : joined.children()) {
                JoinInfo joinInfo = child.joinInfo();
                if (joinInfo != null && joinInfo.joinCondition() != null) {
                    // Handle FK in parent table (entity references / many-to-one)
                    JoinCondition condition = joinInfo.joinCondition();
                    String fkColumn = null;
                    if (condition instanceof JoinCondition.ForeignKeyInParent fk) {
                        fkColumn = fk.foreignKeyColumn();
                    } else if (condition instanceof JoinCondition.Custom custom) {
                        fkColumn = CurlyMarkers.extractColumnForAlias(custom.condition().getSql(), joined.alias());
                    }
                    // ForeignKeyInChild and SharedPrimaryKey don't add FK to parent table
                    
                    if (fkColumn != null) {
                        FieldKey key = new FieldKey(schema, table, fkColumn);
                        FieldDef def = new FieldDef(key, null, false, false, false);
                        result.merge(key, def, FieldDef::mergeWith);
                    }
                    
                    // Handle many-to-many junction tables
                    if (joinInfo.joinTableInfo() != null) {
                        collectJunctionTableColumns(joinInfo.joinTableInfo(), result);
                    }
                }
                
                // Add columns from embedded nodes
                if (child instanceof EmbeddedNode embedded) {
                    collectEmbeddedColumns(embedded, schema, table, result);
                }
            }
            
            // Check if this node is a child with FK in its own table (one-to-many / FK in child)
            if (joined.joinInfo() != null && joined.joinInfo().joinCondition() instanceof JoinCondition.ForeignKeyInChild fkChild) {
                FieldKey key = new FieldKey(schema, table, fkChild.foreignKeyColumn());
                FieldDef def = new FieldDef(key, null, false, false, false);
                result.merge(key, def, FieldDef::mergeWith);
            }
        }

        // Recurse into children
        for (QueryNode child : node.children()) {
            collectFromNode(child, result);
        }
    }
    
    /**
     * Collects FK columns from a junction table (many-to-many relationship).
     */
    private static void collectJunctionTableColumns(JoinTableInfo joinTableInfo, Map<FieldKey, FieldDef> result) {
        String schema = joinTableInfo.joinTable().schemaName();
        String table = joinTableInfo.joinTable().tableName();
        
        // Parent FK column
        FieldKey parentFkKey = new FieldKey(schema, table, joinTableInfo.parentFkColumn());
        result.merge(parentFkKey, new FieldDef(parentFkKey, null, false, false, false), FieldDef::mergeWith);
        
        // Target FK column  
        FieldKey targetFkKey = new FieldKey(schema, table, joinTableInfo.targetFkColumn());
        result.merge(targetFkKey, new FieldDef(targetFkKey, null, false, false, false), FieldDef::mergeWith);
    }

    private static void collectEmbeddedColumns(EmbeddedNode node, String schema, String table,
            Map<FieldKey, FieldDef> result) {
        for (FieldSelection field : node.fields()) {
            String column = field.columnName();
            if (column == null) continue;
            if (isEmbeddedField(field.field())) continue;
            
            FieldKey key = new FieldKey(schema, table, column);
            FieldDef def = new FieldDef(key, field.field(), false, false, false);
            result.merge(key, def, FieldDef::mergeWith);
        }
        
        // Recurse into nested embedded nodes
        for (QueryNode child : node.children()) {
            if (child instanceof EmbeddedNode nested) {
                collectEmbeddedColumns(nested, schema, table, result);
            }
        }
    }

    private static boolean isEntityType(TypeModel type) {
        if (type == null) return false;
        if (type.hasAnnotation(org.pojoquery.annotations.Table.class)) return true;
        TypeModel parent = type.getSuperclass();
        while (parent != null && !"java.lang.Object".equals(parent.getQualifiedName())) {
            if (parent.hasAnnotation(org.pojoquery.annotations.Table.class)) return true;
            parent = parent.getSuperclass();
        }
        return false;
    }

    private static boolean isEmbeddedField(FieldModel field) {
        return field != null && field.hasAnnotation(org.pojoquery.annotations.Embedded.class);
    }
}
