package org.pojoquery.schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.PojoMetadata;
import org.pojoquery.schema.ForeignKeyInfo.DeferredForeignKey;
import org.pojoquery.schema.ForeignKeyInfo.InferredForeignKey;
import org.pojoquery.schema.ForeignKeyInfo.LinkTableInfo;
import org.pojoquery.schema.ForeignKeyInfo.MergedColumnAnnotations;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates CREATE TABLE statements based on entity classes annotated with PojoQuery annotations.
 * 
 * <p>Example usage:</p>
 * <pre>
 * String createTableSql = SchemaGenerator.generateCreateTable(MyEntity.class);
 * </pre>
 */
public class SchemaGenerator {
    
    private static final Logger LOG = LoggerFactory.getLogger(SchemaGenerator.class);

    /**
     * Generates a list of CREATE TABLE statements for the given entity class using default DbContext.
     * Each statement in the list represents one table.
     * 
     * @param entityClass the entity class annotated with @Table
     * @return list of CREATE TABLE statements
     * @throws IllegalArgumentException if the class does not have a @Table annotation
     */
    public static List<String> generateCreateTableStatements(Class<?> entityClass) {
        return generateCreateTableStatements(entityClass, DbContext.getDefault());
    }
    
    /**
     * Generates a list of CREATE TABLE statements for the given entity class with custom DbContext.
     * Each statement in the list represents one table.
     * 
     * @param entityClass the entity class annotated with @Table
     * @param dbContext the database context for dialect-specific generation
     * @return list of CREATE TABLE statements
     * @throws IllegalArgumentException if the class does not have a @Table annotation
     */
    public static List<String> generateCreateTableStatements(Class<?> entityClass, DbContext dbContext) {
        LOG.debug("Generating CREATE TABLE statements for {}", entityClass.getName());
        Set<String> generatedTables = new HashSet<>();
        List<String> statements = new ArrayList<>();
        Map<TypeModel, List<InferredForeignKey>> inferredForeignKeys = new HashMap<>();
        List<LinkTableInfo> linkTables = new ArrayList<>();
        List<DeferredForeignKey> deferredForeignKeys = new ArrayList<>();
        generateCreateTableStatements(new ReflectionTypeModel(entityClass), dbContext, generatedTables, statements, inferredForeignKeys, linkTables, deferredForeignKeys);
        
        // Generate link tables (without FK constraints)
        for (LinkTableInfo linkTable : linkTables) {
            String fullTableName = linkTable.schemaName != null && !linkTable.schemaName.isEmpty()
                ? dbContext.quoteObjectNames(linkTable.schemaName) + "." + dbContext.quoteObjectNames(linkTable.tableName)
                : dbContext.quoteObjectNames(linkTable.tableName);
            if (!generatedTables.contains(fullTableName)) {
                generatedTables.add(fullTableName);
                statements.add(generateCreateLinkTable(linkTable, dbContext, deferredForeignKeys));
            }
        }
        
        // Generate ALTER TABLE statements for FK constraints (after all tables are created)
        // Deduplicate by (table + column) to avoid duplicate FK constraints
        Set<String> generatedFkConstraints = new HashSet<>();
        for (DeferredForeignKey dfk : deferredForeignKeys) {
            String fkKey = (dfk.tableSchema != null ? dfk.tableSchema + "." : "") + dfk.tableName + "." + dfk.columnName;
            if (!generatedFkConstraints.contains(fkKey.toLowerCase())) {
                generatedFkConstraints.add(fkKey.toLowerCase());
                statements.add(generateAlterTableAddForeignKey(dfk, dbContext));
            }
        }
        
        return statements;
    }
    
    private static void generateCreateTableStatements(TypeModel entityClass, DbContext dbContext, 
            Set<String> generatedTables, List<String> statements, Map<TypeModel, List<InferredForeignKey>> inferredForeignKeys,
            List<LinkTableInfo> linkTables, List<DeferredForeignKey> deferredForeignKeys) {
        // Delegate to the internal method with null for merged annotations (single-class case)
        generateCreateTableStatementsInternal(entityClass, dbContext, generatedTables, statements, 
            inferredForeignKeys, linkTables, deferredForeignKeys, null);
    }
    
    /**
     * Generates a list of CREATE TABLE statements for multiple entity classes using default DbContext.
     * 
     * @param entityClasses the entity classes
     * @return list of CREATE TABLE statements
     */
    public static List<String> generateCreateTableStatements(Class<?>... entityClasses) {
        return generateCreateTableStatements(DbContext.getDefault(), entityClasses);
    }
    
    /**
     * Generates a list of CREATE TABLE statements for multiple entity classes with custom DbContext.
     * 
     * <p>When multiple classes map to the same table, their column annotations are merged.
     * For conflicting annotations on the same column, the most restrictive constraint wins:
     * <ul>
     *   <li>If any class has unique=true, the column will be UNIQUE</li>
     *   <li>If any class has nullable=false, the column will be NOT NULL</li>
     * </ul>
     * 
     * @param dbContext the database context for dialect-specific generation
     * @param entityClasses the entity classes
     * @return list of CREATE TABLE statements
     */
    public static List<String> generateCreateTableStatements(DbContext dbContext, Class<?>... entityClasses) {
        TypeModel[] typeModels = Arrays.stream(entityClasses)
                                       .map(ReflectionTypeModel::new)
                                       .toArray(TypeModel[]::new);
        Set<String> generatedTables = new HashSet<>();
        List<String> statements = new ArrayList<>();
        Map<TypeModel, List<InferredForeignKey>> inferredForeignKeys = new HashMap<>();
        List<LinkTableInfo> linkTables = new ArrayList<>();
        List<DeferredForeignKey> deferredForeignKeys = new ArrayList<>();
        
        // Collect merged column annotations for tables that have multiple class mappings
        Map<String, Map<String, MergedColumnAnnotations>> tableColumnAnnotations = new HashMap<>();
        collectMergedColumnAnnotations(typeModels, tableColumnAnnotations);
        
        // First pass: scan all classes for collection fields to build the inferred foreign keys map
        for (TypeModel entityClass : typeModels) {
            ForeignKeyScanner.scanForInferredForeignKeys(entityClass, inferredForeignKeys, linkTables);
        }
        
        // Second pass: generate CREATE TABLE statements (without FK constraints)
        for (TypeModel entityClass : typeModels) {
            generateCreateTableStatementsInternal(entityClass, dbContext, generatedTables, statements, 
                inferredForeignKeys, linkTables, deferredForeignKeys, tableColumnAnnotations);
        }
        
        // Generate link tables (without FK constraints)
        for (LinkTableInfo linkTable : linkTables) {
            String fullTableName = getFullTableName(linkTable.schemaName, linkTable.tableName, dbContext);
            if (!generatedTables.contains(fullTableName)) {
                generatedTables.add(fullTableName);
                statements.add(generateCreateLinkTable(linkTable, dbContext, deferredForeignKeys));
            }
        }
        
        // Generate ALTER TABLE statements for FK constraints (after all tables are created)
        // Deduplicate by (table + column) to avoid duplicate FK constraints
        Set<String> generatedFkConstraints = new HashSet<>();
        for (DeferredForeignKey dfk : deferredForeignKeys) {
            String fkKey = (dfk.tableSchema != null ? dfk.tableSchema + "." : "") + dfk.tableName + "." + dfk.columnName;
            if (!generatedFkConstraints.contains(fkKey.toLowerCase())) {
                generatedFkConstraints.add(fkKey.toLowerCase());
                statements.add(generateAlterTableAddForeignKey(dfk, dbContext));
            }
        }
        
        return statements;
    }
    
    /**
     * Internal method that generates CREATE TABLE statements for a single entity class.
     */
    private static void generateCreateTableStatementsInternal(TypeModel entityClass, DbContext dbContext, 
            Set<String> generatedTables, List<String> statements, Map<TypeModel, List<InferredForeignKey>> inferredForeignKeys,
            List<LinkTableInfo> linkTables, List<DeferredForeignKey> deferredForeignKeys,
            Map<String, Map<String, MergedColumnAnnotations>> tableColumnAnnotations) {
        // Scan for collection fields that imply foreign keys in other tables
        ForeignKeyScanner.scanForInferredForeignKeys(entityClass, inferredForeignKeys, linkTables);
        
        List<TableMapping> tableMappings = PojoMetadata.determineTableMapping(entityClass);
        if (tableMappings.isEmpty()) {
            throw new IllegalArgumentException("Class " + entityClass.getSimpleName() + " must have a @Table annotation");
        }
        
        for (TableMapping mapping : tableMappings) {
            String fullTableName = getFullTableName(mapping, dbContext);
            // Skip if we've already generated this table
            if (generatedTables.contains(fullTableName)) {
                LOG.trace("Skipping already generated table: {}", fullTableName);
                continue;
            }
            LOG.debug("Generating CREATE TABLE for: {}", fullTableName);
            generatedTables.add(fullTableName);
            
            // Get inferred foreign keys for this class
            List<InferredForeignKey> fks = inferredForeignKeys.get(mapping.getType());
            
            // Get merged column annotations for this table (may be null for single-class generation)
            Map<String, MergedColumnAnnotations> mergedAnnotations = null;
            if (tableColumnAnnotations != null) {
                String tableKey = (mapping.schemaName != null ? mapping.schemaName + "." : "") + mapping.tableName;
                mergedAnnotations = tableColumnAnnotations.get(tableKey);
            }
            
            statements.add(generateCreateTableForMapping(mapping, dbContext, fks, deferredForeignKeys, mergedAnnotations));
        }
        
        // Handle @SubClasses annotation
        var subClassesAnnOpt = entityClass.getAnnotation(SubClasses.class);
        if (subClassesAnnOpt.isPresent()) {
            var discAnnOpt = entityClass.getAnnotation(DiscriminatorColumn.class);

            if (discAnnOpt.isPresent()) {
                // Single table inheritance: add discriminator and subclass columns to parent table
                // The parent table was already generated above, we need to modify the last statement
                // to include subclass fields and discriminator column

                // Actually, we need to regenerate with the extra fields
                // Remove the last statement (the parent table we just generated)
                if (!statements.isEmpty()) {
                    String lastStatement = statements.get(statements.size() - 1);
                    TableMapping parentMapping = tableMappings.get(tableMappings.size() - 1);
                    String parentTableName = getFullTableName(parentMapping, dbContext);
                    if (lastStatement.contains(parentTableName)) {
                        statements.remove(statements.size() - 1);
                        generatedTables.remove(parentTableName);

                        // Regenerate with STI info
                        List<FieldModel> stiFields = new ArrayList<>();
                        for (TypeModel subClass : entityClass.getTypeValuesFromAnnotation(subClassesAnnOpt.get(), "value")) {
                            // Collect fields declared only in the subclass
                            for (FieldModel f : PojoMetadata.collectFieldsOfClass(subClass, entityClass)) {
                                if (!isLinkedField(f) && !PojoMetadata.isListOrArray(f.getType())) {
                                    stiFields.add(f);
                                }
                            }
                        }

                        List<InferredForeignKey> fks = inferredForeignKeys.get(parentMapping.getType());
                        Map<String, MergedColumnAnnotations> mergedAnnotations = null;
                        if (tableColumnAnnotations != null) {
                            String tableKey = (parentMapping.schemaName != null ? parentMapping.schemaName + "." : "") + parentMapping.tableName;
                            mergedAnnotations = tableColumnAnnotations.get(tableKey);
                        }

                        String discColumnName = discAnnOpt.get().getStringValue("name").orElse("dtype");
                        statements.add(generateCreateTableForMappingWithSTI(parentMapping, dbContext, fks,
                                deferredForeignKeys, mergedAnnotations, discColumnName, stiFields));
                        generatedTables.add(parentTableName);
                    }
                }
            } else {
                // Table-per-subclass inheritance: generate separate tables for each subclass
                for (TypeModel subClass : entityClass.getTypeValuesFromAnnotation(subClassesAnnOpt.get(), "value")) {
                    generateCreateTableStatementsInternal(subClass, dbContext, generatedTables, statements,
                            inferredForeignKeys, linkTables, deferredForeignKeys, tableColumnAnnotations);
                }
            }
        }
    }
    
    /**
     * Collects merged column annotations from all classes that map to the same table.
     */
    private static void collectMergedColumnAnnotations(TypeModel[] entityClasses, 
            Map<String, Map<String, MergedColumnAnnotations>> tableColumnAnnotations) {
        for (TypeModel entityClass : entityClasses) {
            List<TableMapping> tableMappings = PojoMetadata.determineTableMapping(entityClass);
            for (TableMapping mapping : tableMappings) {
                String tableKey = (mapping.schemaName != null ? mapping.schemaName + "." : "") + mapping.tableName;
                Map<String, MergedColumnAnnotations> columnMap = tableColumnAnnotations
                    .computeIfAbsent(tableKey, k -> new HashMap<>());
                
                // Process fields from this mapping
                for (FieldModel field : mapping.getFields()) {
                    String columnName = PojoMetadata.determineSqlFieldName(field).toLowerCase();
                    MergedColumnAnnotations merged = columnMap.computeIfAbsent(columnName, k -> new MergedColumnAnnotations());
                    AnnotationHelper.ColumnMetadata columnMeta = AnnotationHelper.getColumnMetadata(field);
                    merged.mergeWith(columnMeta);
                }
            }
        }
    }
    
    /**
     * Generates a CREATE TABLE statement for a link table (many-to-many relationship).
     */
    private static String generateCreateLinkTable(LinkTableInfo linkTable, DbContext dbContext, List<DeferredForeignKey> deferredForeignKeys) {
        StringBuilder sb = new StringBuilder();
        
        // Table name
        String tableName = getFullTableName(linkTable.schemaName, linkTable.tableName, dbContext);
        
        sb.append("CREATE TABLE ");
        sb.append(tableName).append(" (\n");
        
        // Owner column
        sb.append("  ").append(dbContext.quoteObjectNames(linkTable.ownerColumn));
        sb.append(" ").append(dbContext.getForeignKeyColumnType());
        sb.append(",\n");
        
        // Foreign column
        sb.append("  ").append(dbContext.quoteObjectNames(linkTable.foreignColumn));
        sb.append(" ").append(dbContext.getForeignKeyColumnType());
        sb.append(",\n");
        
        // Primary key (composite)
        sb.append("  PRIMARY KEY (");
        sb.append(dbContext.quoteObjectNames(linkTable.ownerColumn));
        sb.append(", ");
        sb.append(dbContext.quoteObjectNames(linkTable.foreignColumn));
        sb.append(")\n");
        
        sb.append(")");
        
        // Add engine specification based on DbContext
        String tableSuffix = dbContext.getCreateTableSuffix();
        if (tableSuffix != null && !tableSuffix.isEmpty()) {
            sb.append(tableSuffix);
        }
        
        sb.append(";");
        
        // Defer FK constraints to be added later via ALTER TABLE
        deferredForeignKeys.add(new DeferredForeignKey(
            linkTable.tableName, linkTable.schemaName, linkTable.ownerColumn,
            linkTable.ownerTable, linkTable.ownerIdColumn, linkTable.ownerSchema));
        deferredForeignKeys.add(new DeferredForeignKey(
            linkTable.tableName, linkTable.schemaName, linkTable.foreignColumn,
            linkTable.foreignTable, linkTable.foreignIdColumn, linkTable.foreignSchema));
        
        return sb.toString();
    }
    
    /**
     * Generates an ALTER TABLE statement to add a foreign key constraint.
     */
    private static String generateAlterTableAddForeignKey(DeferredForeignKey dfk, DbContext dbContext) {
        StringBuilder sb = new StringBuilder();
        
        String tableName = getFullTableName(dfk.tableSchema, dfk.tableName, dbContext);
        String refTableName = getFullTableName(dfk.referencedSchema, dfk.referencedTable, dbContext);
        
        sb.append("ALTER TABLE ").append(tableName);
        sb.append(" ADD FOREIGN KEY (").append(dbContext.quoteObjectNames(dfk.columnName)).append(")");
        sb.append(" REFERENCES ").append(refTableName);
        sb.append("(").append(dbContext.quoteObjectNames(dfk.referencedColumn)).append(");");
        
        return sb.toString();
    }
    
    /**
     * Creates tables in the database for the given entity classes.
     * This is a convenience method that generates and executes CREATE TABLE statements.
     * 
     * @param db the data source to execute the statements on
     * @param classes the entity classes to create tables for
     */
    public static void createTables(javax.sql.DataSource db, Class<?>... classes) {
        DB.runInTransaction(db, c -> {
            for (String ddl : generateCreateTableStatements(classes)) {
                org.pojoquery.DB.executeDDL(c, ddl);
            }
        });
    }
    
    private static String generateCreateTableForMapping(TableMapping mapping, DbContext dbContext, 
            List<InferredForeignKey> inferredForeignKeys, List<DeferredForeignKey> deferredForeignKeys,
            Map<String, MergedColumnAnnotations> mergedAnnotations) {
        StringBuilder sb = new StringBuilder();
        
        String tableName = getFullTableName(mapping, dbContext);
        
        // CREATE TABLE
        sb.append("CREATE TABLE ");
        sb.append(tableName).append(" (\n");
        
        List<String> columnDefinitions = new ArrayList<>();
        List<String> primaryKeyColumns = new ArrayList<>();
        Set<String> existingColumnNames = new HashSet<>();
        Set<String> existingFkColumns = new HashSet<>(); // Track FK columns to avoid duplicates
        
        // Determine if we have a composite key from the overall class hierarchy
        List<FieldModel> idFields = PojoMetadata.determineIdFields(mapping.getType());
        boolean isCompositeKey = idFields.size() > 1;
        
        // Check if this is a subclass table (not the root table with @Id fields)
        // In table-per-subclass, the subclass table needs the ID field from parent as FK/PK
        boolean hasIdFieldInThisMapping = false;
        for (FieldModel field : mapping.getFields()) {
            if (AnnotationHelper.isId(field)) {
                hasIdFieldInThisMapping = true;
                break;
            }
        }
        
        // If this mapping doesn't have its own @Id field but the class has inherited @Id,
        // we need to add the ID field as a non-auto-increment primary key (foreign key to parent)
        if (!hasIdFieldInThisMapping && !idFields.isEmpty()) {
            for (FieldModel idField : idFields) {
                String columnName = PojoMetadata.determineSqlFieldName(idField);
                // Add as NOT NULL (not auto-increment - it references the parent table)
                String columnDef = formatColumnDefinition(columnName, idField.getType(), false, dbContext, idField, mergedAnnotations);
                columnDefinitions.add(columnDef);
                primaryKeyColumns.add(dbContext.quoteObjectNames(columnName));
                existingColumnNames.add(columnName.toLowerCase());
            }
        }
        
        for (FieldModel field : mapping.getFields()) {
            // Handle embedded fields
            if (AnnotationHelper.isEmbedded(field)) {
                String prefix = PojoMetadata.determinePrefix(field);
                addEmbeddedColumns(field.getType(), prefix, columnDefinitions, primaryKeyColumns,
                    existingColumnNames, dbContext, isCompositeKey, mergedAnnotations);
                continue;
            }
            
            // Handle linked fields (foreign keys or collections)
            if (isLinkedField(field)) {
                // For single entity references, add a foreign key column
                if (!PojoMetadata.isListOrArray(field.getType())) {
                    String columnName = determineForeignKeyColumnName(field);
                    // Only add if not already defined (e.g., as an @Id field)
                    if (!existingColumnNames.contains(columnName.toLowerCase())) {
                        String columnDef = formatForeignKeyColumnDefinition(columnName, dbContext, field);
                        columnDefinitions.add(columnDef);
                        existingColumnNames.add(columnName.toLowerCase());
                    }
                    
                    // Defer foreign key constraint for single entity references
                    if (!existingFkColumns.contains(columnName.toLowerCase())) {
                        TypeModel linkedType = field.getType();
                        List<TableMapping> linkedMappings = PojoMetadata.determineTableMapping(linkedType);
                        if (!linkedMappings.isEmpty()) {
                            TableMapping linkedMapping = linkedMappings.get(0);
                            List<FieldModel> linkedIdFields = PojoMetadata.determineIdFields(linkedType);
                            if (!linkedIdFields.isEmpty()) {
                                String refColumn = PojoMetadata.determineSqlFieldName(linkedIdFields.get(0));
                                deferredForeignKeys.add(new DeferredForeignKey(
                                    mapping.tableName, mapping.schemaName, columnName,
                                    linkedMapping.tableName, refColumn, linkedMapping.schemaName));
                                existingFkColumns.add(columnName.toLowerCase());
                            }
                        }
                    }
                }
                // Collections are handled via inferred foreign keys in the referenced table
                continue;
            }
            
            String columnName = PojoMetadata.determineSqlFieldName(field);
            boolean isPrimaryKey = AnnotationHelper.isId(field);
            // Only auto-increment if single primary key (not composite) and it's in this mapping
            boolean shouldAutoIncrement = isPrimaryKey && !isCompositeKey;
            String columnDef = formatColumnDefinition(columnName, field.getType(), shouldAutoIncrement, dbContext, field, mergedAnnotations);
            columnDefinitions.add(columnDef);
            existingColumnNames.add(columnName.toLowerCase());

            if (isPrimaryKey) {
                primaryKeyColumns.add(dbContext.quoteObjectNames(columnName));
            }
        }

        // Add inferred foreign key columns from collection fields in other entities
        if (inferredForeignKeys != null) {
            for (InferredForeignKey fk : inferredForeignKeys) {
                // Only add if not already defined in the entity
                if (!existingColumnNames.contains(fk.columnName.toLowerCase())) {
                    String columnDef = formatIdColumnDefinition(fk.columnName, dbContext);
                    columnDefinitions.add(columnDef);
                    existingColumnNames.add(fk.columnName.toLowerCase());
                }
                
                // Defer foreign key constraint if reference information is available
                if (fk.hasReference() && !existingFkColumns.contains(fk.columnName.toLowerCase())) {
                    deferredForeignKeys.add(new DeferredForeignKey(
                        mapping.tableName, mapping.schemaName, fk.columnName,
                        fk.referencedTable, fk.referencedColumn, fk.referencedSchema));
                    existingFkColumns.add(fk.columnName.toLowerCase());
                }
            }
        }
        
        // Add column definitions
        boolean hasMoreConstraints = !primaryKeyColumns.isEmpty();
        for (int i = 0; i < columnDefinitions.size(); i++) {
            sb.append("  ").append(columnDefinitions.get(i));
            if (i < columnDefinitions.size() - 1 || hasMoreConstraints) {
                sb.append(",");
            }
            sb.append("\n");
        }
        
        // Add primary key constraint
        if (!primaryKeyColumns.isEmpty()) {
            sb.append("  PRIMARY KEY (").append(String.join(", ", primaryKeyColumns)).append(")\n");
        }
        
        sb.append(")");
        
        // Add engine specification based on DbContext
        String tableSuffix = dbContext.getCreateTableSuffix();
        if (tableSuffix != null && !tableSuffix.isEmpty()) {
            sb.append(tableSuffix);
        }
        
        sb.append(";");

        return sb.toString();
    }

    /**
     * Generates a CREATE TABLE statement for a mapping with single table inheritance.
     * Includes the discriminator column and fields from all subclasses.
     */
    private static String generateCreateTableForMappingWithSTI(TableMapping mapping, DbContext dbContext,
            List<InferredForeignKey> inferredForeignKeys, List<DeferredForeignKey> deferredForeignKeys,
            Map<String, MergedColumnAnnotations> mergedAnnotations, String discriminatorColumnName,
            List<FieldModel> stiFields) {
        StringBuilder sb = new StringBuilder();

        String tableName = getFullTableName(mapping, dbContext);

        // CREATE TABLE
        sb.append("CREATE TABLE ");
        sb.append(tableName).append(" (\n");

        List<String> columnDefinitions = new ArrayList<>();
        List<String> primaryKeyColumns = new ArrayList<>();
        Set<String> existingColumnNames = new HashSet<>();
        Set<String> existingFkColumns = new HashSet<>();

        // Determine if we have a composite key
        List<FieldModel> idFields = PojoMetadata.determineIdFields(mapping.getType());
        boolean isCompositeKey = idFields.size() > 1;

        // Process fields from the mapping (parent class fields)
        for (FieldModel field : mapping.getFields()) {
            // Handle embedded fields
            var embeddedAnnOpt = field.getAnnotation(Embedded.class);
            if (embeddedAnnOpt.isPresent()) {
                String prefix = embeddedAnnOpt.get().getStringValue("prefix")
                    .filter(p -> !Embedded.DEFAULT.equals(p))
                    .orElse("");
                addEmbeddedColumns(field.getType(), prefix, columnDefinitions, primaryKeyColumns,
                        existingColumnNames, dbContext, isCompositeKey, mergedAnnotations);
                continue;
            }

            // Handle linked fields
            if (isLinkedField(field)) {
                if (!PojoMetadata.isListOrArray(field.getType())) {
                    String columnName = determineForeignKeyColumnName(field);
                    if (!existingColumnNames.contains(columnName.toLowerCase())) {
                        String columnDef = formatForeignKeyColumnDefinition(columnName, dbContext, field);
                        columnDefinitions.add(columnDef);
                        existingColumnNames.add(columnName.toLowerCase());
                    }

                    if (!existingFkColumns.contains(columnName.toLowerCase())) {
                        TypeModel linkedType = field.getType();
                        List<TableMapping> linkedMappings = PojoMetadata.determineTableMapping(linkedType);
                        if (!linkedMappings.isEmpty()) {
                            TableMapping linkedMapping = linkedMappings.get(0);
                            List<FieldModel> linkedIdFields = PojoMetadata.determineIdFields(linkedType);
                            if (!linkedIdFields.isEmpty()) {
                                String refColumn = PojoMetadata.determineSqlFieldName(linkedIdFields.get(0));
                                deferredForeignKeys.add(new DeferredForeignKey(
                                        mapping.tableName, mapping.schemaName, columnName,
                                        linkedMapping.tableName, refColumn, linkedMapping.schemaName));
                                existingFkColumns.add(columnName.toLowerCase());
                            }
                        }
                    }
                }
                continue;
            }

            String columnName = PojoMetadata.determineSqlFieldName(field);
            boolean isPrimaryKey = AnnotationHelper.isId(field);
            boolean shouldAutoIncrement = isPrimaryKey && !isCompositeKey;
            String columnDef = formatColumnDefinition(columnName, field.getType(), shouldAutoIncrement, dbContext, field, mergedAnnotations);
            columnDefinitions.add(columnDef);
            existingColumnNames.add(columnName.toLowerCase());

            if (isPrimaryKey) {
                primaryKeyColumns.add(dbContext.quoteObjectNames(columnName));
            }
        }

        // Add discriminator column (NOT NULL, VARCHAR(255))
        String discColumnDef = dbContext.quoteObjectNames(discriminatorColumnName) + " VARCHAR(255) NOT NULL";
        columnDefinitions.add(discColumnDef);
        existingColumnNames.add(discriminatorColumnName.toLowerCase());

        // Add fields from subclasses (single table inheritance)
        for (FieldModel field : stiFields) {
            String columnName = PojoMetadata.determineSqlFieldName(field);
            if (!existingColumnNames.contains(columnName.toLowerCase())) {
                // Subclass fields are nullable (since not all rows will be of that subclass)
                String columnDef = formatColumnDefinition(columnName, field.getType(), false, dbContext, field, mergedAnnotations);
                columnDefinitions.add(columnDef);
                existingColumnNames.add(columnName.toLowerCase());
            }
        }

        // Add inferred foreign key columns
        if (inferredForeignKeys != null) {
            for (InferredForeignKey fk : inferredForeignKeys) {
                if (!existingColumnNames.contains(fk.columnName.toLowerCase())) {
                    String columnDef = formatIdColumnDefinition(fk.columnName, dbContext);
                    columnDefinitions.add(columnDef);
                    existingColumnNames.add(fk.columnName.toLowerCase());
                }

                if (fk.hasReference() && !existingFkColumns.contains(fk.columnName.toLowerCase())) {
                    deferredForeignKeys.add(new DeferredForeignKey(
                            mapping.tableName, mapping.schemaName, fk.columnName,
                            fk.referencedTable, fk.referencedColumn, fk.referencedSchema));
                    existingFkColumns.add(fk.columnName.toLowerCase());
                }
            }
        }

        // Add column definitions
        boolean hasMoreConstraints = !primaryKeyColumns.isEmpty();
        for (int i = 0; i < columnDefinitions.size(); i++) {
            sb.append("  ").append(columnDefinitions.get(i));
            if (i < columnDefinitions.size() - 1 || hasMoreConstraints) {
                sb.append(",");
            }
            sb.append("\n");
        }

        // Add primary key constraint
        if (!primaryKeyColumns.isEmpty()) {
            sb.append("  PRIMARY KEY (").append(String.join(", ", primaryKeyColumns)).append(")\n");
        }

        sb.append(")");

        // Add engine specification
        String tableSuffix = dbContext.getCreateTableSuffix();
        if (tableSuffix != null && !tableSuffix.isEmpty()) {
            sb.append(tableSuffix);
        }

        sb.append(";");

        return sb.toString();
    }

    private static void addEmbeddedColumns(TypeModel embeddedClass, String prefix,
            List<String> columnDefinitions, List<String> primaryKeyColumns, Set<String> existingColumnNames,
            DbContext dbContext, boolean isCompositeKey, Map<String, MergedColumnAnnotations> mergedAnnotations) {
        // filterFields already handles static, transient, and @Transient
        Collection<FieldModel> fields = PojoMetadata.filterFields(embeddedClass);
        for (FieldModel field : fields) {
            // Recursively handle nested embedded
            if (AnnotationHelper.isEmbedded(field)) {
                String nestedPrefix = prefix + PojoMetadata.determinePrefix(field);
                addEmbeddedColumns(field.getType(), nestedPrefix, columnDefinitions, primaryKeyColumns,
                    existingColumnNames, dbContext, isCompositeKey, mergedAnnotations);
                continue;
            }

            // Handle linked fields (foreign keys) inside embedded
            if (isLinkedField(field)) {
                // For single entity references, add a foreign key column
                if (!PojoMetadata.isListOrArray(field.getType())) {
                    String fkColumnName = determineForeignKeyColumnName(field);
                    String columnName = prefix + fkColumnName;
                    if (!existingColumnNames.contains(columnName.toLowerCase())) {
                        String columnDef = formatForeignKeyColumnDefinition(columnName, dbContext, field);
                        columnDefinitions.add(columnDef);
                        existingColumnNames.add(columnName.toLowerCase());
                    }
                }
                // Collections are handled via inferred foreign keys in the referenced table
                continue;
            }

            String columnName = prefix + PojoMetadata.determineSqlFieldName(field);
            boolean isPrimaryKey = AnnotationHelper.isId(field);
            boolean shouldAutoIncrement = isPrimaryKey && !isCompositeKey;
            String columnDef = formatColumnDefinition(columnName, field.getType(), shouldAutoIncrement, dbContext, field, mergedAnnotations);
            columnDefinitions.add(columnDef);
            existingColumnNames.add(columnName.toLowerCase());

            if (isPrimaryKey) {
                primaryKeyColumns.add(dbContext.quoteObjectNames(columnName));
            }
        }
    }
    
    private static boolean isLinkedField(FieldModel field) {
        TypeModel type = field.getType();
        // Check if it's a collection (list, set, etc.) - reuse PojoMetadata.s logic
        if (PojoMetadata.isListOrArray(type)) {
            return true;
        }
        // Check if the field type has a @Link annotation
        if (field.hasAnnotation(Link.class)) {
            return true;
        }
        // Check if the field type is an entity - reuse PojoMetadata.s logic
        return PojoMetadata.isLinkedClass(type);
    }
    
    private static String determineForeignKeyColumnName(FieldModel field) {
        // First check @Link(linkfield=...) then JPA @JoinColumn(name=...)
        String columnName = AnnotationHelper.getJoinColumnName(field);
        if (columnName != null) {
            return columnName;
        }
        return field.getName() + "_id";
    }
    
    private static String formatIdColumnDefinition(String columnName, DbContext dbContext) {
        StringBuilder sb = new StringBuilder();
        sb.append(dbContext.quoteObjectNames(columnName));
        sb.append(" ");

        sb.append(dbContext.getForeignKeyColumnType());

        return sb.toString();
    }
    
    private static String formatForeignKeyColumnDefinition(String columnName, DbContext dbContext, FieldModel field) {
        StringBuilder sb = new StringBuilder();
        sb.append(dbContext.quoteObjectNames(columnName));
        sb.append(" ");

        sb.append(dbContext.getForeignKeyColumnType());
        
        // Check @Link for nullable and unique constraints
        if (field != null) {
            var linkAnnOpt = field.getAnnotation(Link.class);
            if (linkAnnOpt.isPresent()) {
                var linkAnn = linkAnnOpt.get();
                if (linkAnn.getBooleanAttribute("nullable").map(b -> !b).orElse(false)) {
                    sb.append(" NOT NULL");
                }
                if (linkAnn.getBooleanAttribute("unique").orElse(false)) {
                    sb.append(" UNIQUE");
                }
            }
        }

        return sb.toString();
    }

    private static String formatColumnDefinition(String columnName, TypeModel type, boolean autoIncrement, 
            DbContext dbContext, FieldModel field, Map<String, MergedColumnAnnotations> mergedAnnotations) {
        StringBuilder sb = new StringBuilder();
        sb.append(dbContext.quoteObjectNames(columnName));
        sb.append(" ");
        
        // Get merged annotations for this column (may be null)
        MergedColumnAnnotations merged = mergedAnnotations != null ? mergedAnnotations.get(columnName.toLowerCase()) : null;
        
        // For auto-increment primary keys, some databases use special types (e.g., BIGSERIAL in Postgres)
        if (autoIncrement && !dbContext.getAutoIncrementKeyColumnType().equals("BIGINT")) {
            // Use the auto-increment key column type which includes auto-increment semantics (e.g., BIGSERIAL)
            sb.append(dbContext.getAutoIncrementKeyColumnType());
        } else {
            sb.append(dbContext.mapJavaTypeToSql(field));

            // Add NOT NULL constraint - check both field annotation and merged annotations
            if (!autoIncrement && field != null) {
                boolean isNotNull = false;
                AnnotationHelper.ColumnMetadata columnMeta = AnnotationHelper.getColumnMetadata(field);
                if (columnMeta != null && !columnMeta.nullable) {
                    isNotNull = true;
                }
                if (merged != null && merged.notNull) {
                    isNotNull = true;
                }
                if (isNotNull) {
                    sb.append(" NOT NULL");
                }
            }

            if (autoIncrement) {
                String autoIncrementSyntax = dbContext.getAutoIncrementSyntax();
                if (!autoIncrementSyntax.isEmpty()) {
                    sb.append(" ");
                    sb.append(autoIncrementSyntax);
                }
            }
        }

        // Add UNIQUE constraint - check both field annotation and merged annotations
        if (field != null) {
            boolean isUnique = false;
            AnnotationHelper.ColumnMetadata columnMeta = AnnotationHelper.getColumnMetadata(field);
            if (columnMeta != null && columnMeta.unique) {
                isUnique = true;
            }
            if (merged != null && merged.unique) {
                isUnique = true;
            }
            if (isUnique) {
                sb.append(" UNIQUE");
            }
        }

        return sb.toString();
    }
    
    private static String getFullTableName(TableMapping mapping, DbContext dbContext) {
        return getFullTableName(mapping.schemaName, mapping.tableName, dbContext);
    }
    
    private static String getFullTableName(String schemaName, String tableName, DbContext dbContext) {
        if (schemaName != null && !schemaName.isEmpty()) {
            return dbContext.quoteObjectNames(schemaName) + "." + dbContext.quoteObjectNames(tableName);
        }
        return dbContext.quoteObjectNames(tableName);
    }
    
    // ========== Schema Migration Methods ==========
    
    /**
     * Represents a column definition for schema generation.
     */
    public static class ColumnDefinition {
        public final String name;
        public final String sqlType;
        public final boolean autoIncrement;
        public final boolean isPrimaryKey;
        public final boolean notNull;
        public final boolean unique;
        
        public ColumnDefinition(String name, String sqlType, boolean autoIncrement, boolean isPrimaryKey) {
            this(name, sqlType, autoIncrement, isPrimaryKey, false, false);
        }
        
        public ColumnDefinition(String name, String sqlType, boolean autoIncrement, boolean isPrimaryKey, boolean notNull, boolean unique) {
            this.name = name;
            this.sqlType = sqlType;
            this.autoIncrement = autoIncrement;
            this.isPrimaryKey = isPrimaryKey;
            this.notNull = notNull;
            this.unique = unique;
        }
    }
    
    /**
     * Generates DDL statements (CREATE TABLE or ALTER TABLE) based on the existing schema.
     * If a table doesn't exist, generates CREATE TABLE.
     * If a table exists but has missing columns, generates ALTER TABLE ADD COLUMN.
     * 
     * @param schemaInfo the existing schema information
     * @param entityClasses the entity classes to generate DDL for
     * @return list of DDL statements
     */
    public static List<String> generateMigrationStatements(SchemaInfo schemaInfo, Class<?>... entityClasses) {
        TypeModel[] typeModels = new TypeModel[entityClasses.length];
        for (int i = 0; i < entityClasses.length; i++) {
            typeModels[i] = new ReflectionTypeModel(entityClasses[i]);
        }
        return generateMigrationStatements(schemaInfo, DbContext.getDefault(), typeModels);
    }
    
    /**
     * Generates DDL statements (CREATE TABLE or ALTER TABLE) based on the existing schema.
     * If a table doesn't exist, generates CREATE TABLE.
     * If a table exists but has missing columns, generates ALTER TABLE ADD COLUMN.
     * 
     * @param schemaInfo the existing schema information
     * @param dbContext the database context for dialect-specific generation
     * @param entityClasses the entity classes to generate DDL for
     * @return list of DDL statements
     */
    public static List<String> generateMigrationStatements(SchemaInfo schemaInfo, DbContext dbContext, TypeModel... entityClasses) {
        Set<String> processedTables = new HashSet<>();
        List<String> statements = new ArrayList<>();
        Map<TypeModel, List<InferredForeignKey>> inferredForeignKeys = new HashMap<>();
        List<LinkTableInfo> linkTables = new ArrayList<>();
        List<DeferredForeignKey> deferredForeignKeys = new ArrayList<>();
        
        // First pass: scan all classes for collection fields to build the inferred foreign keys map
        for (TypeModel entityClass : entityClasses) {
            ForeignKeyScanner.scanForInferredForeignKeys(entityClass, inferredForeignKeys, linkTables);
        }
        
        // Second pass: generate DDL statements
        for (TypeModel entityClass : entityClasses) {
            generateMigrationStatements(entityClass, schemaInfo, dbContext, processedTables, statements, inferredForeignKeys, linkTables, deferredForeignKeys);
        }
        
        // Generate link tables (without FK constraints)
        for (LinkTableInfo linkTable : linkTables) {
            String fullTableName = linkTable.schemaName != null && !linkTable.schemaName.isEmpty()
                ? dbContext.quoteObjectNames(linkTable.schemaName) + "." + dbContext.quoteObjectNames(linkTable.tableName)
                : dbContext.quoteObjectNames(linkTable.tableName);
            if (!processedTables.contains(fullTableName)) {
                // Check if link table exists
                SchemaInfo.TableInfo existingTable = schemaInfo.getTable(
                    linkTable.schemaName != null && !linkTable.schemaName.isEmpty() ? linkTable.schemaName : null,
                    linkTable.tableName);
                if (existingTable == null) {
                    processedTables.add(fullTableName);
                    statements.add(generateCreateLinkTable(linkTable, dbContext, deferredForeignKeys));
                }
            }
        }
        
        // Generate ALTER TABLE statements for FK constraints (after all tables are created)
        for (DeferredForeignKey dfk : deferredForeignKeys) {
            statements.add(generateAlterTableAddForeignKey(dfk, dbContext));
        }
        
        return statements;
    }
    
    private static void generateMigrationStatements(TypeModel entityClass, SchemaInfo schemaInfo, DbContext dbContext,
            Set<String> processedTables, List<String> statements, Map<TypeModel, List<InferredForeignKey>> inferredForeignKeys,
            List<LinkTableInfo> linkTables, List<DeferredForeignKey> deferredForeignKeys) {
        // Scan for inferred foreign keys
        ForeignKeyScanner.scanForInferredForeignKeys(entityClass, inferredForeignKeys, linkTables);
        
        List<TableMapping> tableMappings = PojoMetadata.determineTableMapping(entityClass);
        if (tableMappings.isEmpty()) {
            throw new IllegalArgumentException("Class " + entityClass.getSimpleName() + " must have a @Table annotation");
        }
        
        for (TableMapping mapping : tableMappings) {
            String fullTableName = getFullTableName(mapping, dbContext);
            // Skip if we've already processed this table
            if (processedTables.contains(fullTableName)) {
                continue;
            }
            processedTables.add(fullTableName);
            
            // Get inferred foreign keys for this class
            List<InferredForeignKey> fks = inferredForeignKeys.get(mapping.getType());
            
            // Check if table exists
            SchemaInfo.TableInfo existingTable = schemaInfo.getTable(mapping.schemaName, mapping.tableName);
            
            if (existingTable == null) {
                // Table doesn't exist - generate CREATE TABLE (no merged annotations for migration)
                statements.add(generateCreateTableForMapping(mapping, dbContext, fks, deferredForeignKeys, null));
            } else {
                // Table exists - check for missing columns and generate ALTER TABLE
                List<String> alterStatements = generateAlterTableForMapping(mapping, existingTable, dbContext, fks);
                statements.addAll(alterStatements);
            }
        }
        
        // Handle @SubClasses annotation for table-per-subclass inheritance
        var subClassesAnnOpt = entityClass.getAnnotation(SubClasses.class);
        if (subClassesAnnOpt.isPresent()) {
            for (TypeModel subClass : entityClass.getTypeValuesFromAnnotation(subClassesAnnOpt.get(), "value")) {
                generateMigrationStatements(subClass, schemaInfo, dbContext, processedTables, statements, inferredForeignKeys, linkTables, deferredForeignKeys);
            }
        }
    }
    
    /**
     * Generates ALTER TABLE statements to add missing columns to an existing table.
     * Each column gets its own ALTER TABLE statement for maximum database compatibility.
     * 
     * @param mapping the table mapping
     * @param existingTable information about the existing table
     * @param dbContext the database context
     * @param inferredForeignKeys inferred foreign keys to add
     * @return list of ALTER TABLE statements, or empty list if no columns need to be added
     */
    private static List<String> generateAlterTableForMapping(TableMapping mapping, SchemaInfo.TableInfo existingTable, 
            DbContext dbContext, List<InferredForeignKey> inferredForeignKeys) {
        
        List<ColumnDefinition> requiredColumns = getRequiredColumns(mapping, dbContext, inferredForeignKeys);
        List<ColumnDefinition> missingColumns = new ArrayList<>();
        
        for (ColumnDefinition col : requiredColumns) {
            if (!existingTable.hasColumn(col.name)) {
                missingColumns.add(col);
            }
        }
        
        if (missingColumns.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Generate separate ALTER TABLE ADD COLUMN statement for each column
        // This ensures compatibility across all databases (some don't support multiple ADD COLUMN in one statement)
        List<String> statements = new ArrayList<>();
        String tableName = getFullTableName(mapping, dbContext);
        
        for (ColumnDefinition col : missingColumns) {
            StringBuilder sb = new StringBuilder();
            sb.append("ALTER TABLE ");
            sb.append(tableName);
            sb.append(" ADD COLUMN ");
            sb.append(dbContext.quoteObjectNames(col.name));
            sb.append(" ");
            sb.append(col.sqlType);
            // Note: We don't add AUTO_INCREMENT for ALTER TABLE as that requires PRIMARY KEY changes
            
            // Add NOT NULL constraint if specified
            if (col.notNull) {
                sb.append(" NOT NULL");
            }
            
            // Add UNIQUE constraint if specified
            if (col.unique) {
                sb.append(" UNIQUE");
            }
            
            sb.append(";");
            statements.add(sb.toString());
        }
        
        return statements;
    }
    
    /**
     * Gets all required columns for a table mapping.
     */
    private static List<ColumnDefinition> getRequiredColumns(TableMapping mapping, DbContext dbContext, 
            List<InferredForeignKey> inferredForeignKeys) {
        
        List<ColumnDefinition> columns = new ArrayList<>();
        Set<String> existingColumnNames = new HashSet<>();
        
        // Determine if we have a composite key from the overall class hierarchy
        List<FieldModel> idFields = PojoMetadata.determineIdFields(mapping.getType());
        boolean isCompositeKey = idFields.size() > 1;
        
        // Check if this is a subclass table
        boolean hasIdFieldInThisMapping = false;
        for (FieldModel field : mapping.getFields()) {
            if (AnnotationHelper.isId(field)) {
                hasIdFieldInThisMapping = true;
                break;
            }
        }

        // Add ID fields if needed (for subclass tables)
        if (!hasIdFieldInThisMapping && !idFields.isEmpty()) {
            for (FieldModel idField : idFields) {
                String columnName = PojoMetadata.determineSqlFieldName(idField);
                String sqlType = dbContext.mapJavaTypeToSql(idField);
                columns.add(new ColumnDefinition(columnName, sqlType, false, true));
                existingColumnNames.add(columnName.toLowerCase());
            }
        }

        // Process fields
        for (FieldModel field : mapping.getFields()) {
            // Handle embedded fields
            if (AnnotationHelper.isEmbedded(field)) {
                String prefix = PojoMetadata.determinePrefix(field);
                // Remove trailing underscore
                if (prefix.endsWith("_")) {
                    prefix = prefix.substring(0, prefix.length() - 1);
                }
                addEmbeddedColumnsToList(field.getType(), prefix, columns, existingColumnNames, dbContext, isCompositeKey);
                continue;
            }

            // Handle linked fields
            if (isLinkedField(field)) {
                if (!PojoMetadata.isListOrArray(field.getType())) {
                    String columnName = determineForeignKeyColumnName(field);
                    // Only add if not already defined (e.g., as an @Id field)
                    if (!existingColumnNames.contains(columnName.toLowerCase())) {
                        String sqlType = dbContext.getForeignKeyColumnType();
                        // Check @Link for nullable and unique
                        var linkAnnOpt = field.getAnnotation(Link.class);
                        boolean notNull = linkAnnOpt.map(ann -> ann.getBooleanAttribute("nullable").map(b -> !b).orElse(false)).orElse(false);
                        boolean unique = linkAnnOpt.map(ann -> ann.getBooleanAttribute("unique").orElse(false)).orElse(false);
                        columns.add(new ColumnDefinition(columnName, sqlType, false, false, notNull, unique));
                        existingColumnNames.add(columnName.toLowerCase());
                    }
                }
                continue;
            }

            String columnName = PojoMetadata.determineSqlFieldName(field);
            boolean isPrimaryKey = AnnotationHelper.isId(field);
            boolean shouldAutoIncrement = isPrimaryKey && !isCompositeKey;
            String sqlType = dbContext.mapJavaTypeToSql(field);
            AnnotationHelper.ColumnMetadata columnMeta = AnnotationHelper.getColumnMetadata(field);
            boolean notNull = !shouldAutoIncrement && columnMeta != null && !columnMeta.nullable;
            boolean unique = columnMeta != null && columnMeta.unique;

            columns.add(new ColumnDefinition(columnName, sqlType, shouldAutoIncrement, isPrimaryKey, notNull, unique));
            existingColumnNames.add(columnName.toLowerCase());
        }
        
        // Add inferred foreign key columns
        if (inferredForeignKeys != null) {
            for (InferredForeignKey fk : inferredForeignKeys) {
                if (!existingColumnNames.contains(fk.columnName.toLowerCase())) {
                    String sqlType = dbContext.getForeignKeyColumnType();
                    columns.add(new ColumnDefinition(fk.columnName, sqlType, false, false));
                    existingColumnNames.add(fk.columnName.toLowerCase());
                }
            }
        }
        
        return columns;
    }
    
    private static void addEmbeddedColumnsToList(TypeModel embeddedClass, String prefix,
            List<ColumnDefinition> columns, Set<String> existingColumnNames, DbContext dbContext, boolean isCompositeKey) {
        Collection<FieldModel> fields = PojoMetadata.filterFields(embeddedClass);
        for (FieldModel field : fields) {
            if (AnnotationHelper.isEmbedded(field)) {
                // Get prefix from PojoQuery @Embedded if present, otherwise use empty string (JPA @Embedded has no prefix)
                var nestedAnnOpt = field.getAnnotation(Embedded.class);
                String nestedPrefix = prefix + nestedAnnOpt.flatMap(ann -> ann.getStringValue("prefix"))
                    .filter(p -> !Embedded.DEFAULT.equals(p))
                    .orElse("");
                addEmbeddedColumnsToList(field.getType(), nestedPrefix, columns, existingColumnNames, dbContext, isCompositeKey);
                continue;
            }

            String columnName = prefix + PojoMetadata.determineSqlFieldName(field);
            boolean isPrimaryKey = AnnotationHelper.isId(field);
            boolean shouldAutoIncrement = isPrimaryKey && !isCompositeKey;
            String sqlType = dbContext.mapJavaTypeToSql(field);
            AnnotationHelper.ColumnMetadata columnMeta = AnnotationHelper.getColumnMetadata(field);
            boolean notNull = !shouldAutoIncrement && columnMeta != null && !columnMeta.nullable;
            boolean unique = columnMeta != null && columnMeta.unique;

            columns.add(new ColumnDefinition(columnName, sqlType, shouldAutoIncrement, isPrimaryKey, notNull, unique));
            existingColumnNames.add(columnName.toLowerCase());
        }
    }
}
