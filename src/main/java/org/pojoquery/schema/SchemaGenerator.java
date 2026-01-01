package org.pojoquery.schema;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.annotations.Column;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.CustomizableQueryBuilder;
import org.pojoquery.pipeline.QueryBuilder;
import org.pojoquery.schema.ForeignKeyInfo.DeferredForeignKey;
import org.pojoquery.schema.ForeignKeyInfo.InferredForeignKey;
import org.pojoquery.schema.ForeignKeyInfo.LinkTableInfo;
import org.pojoquery.schema.ForeignKeyInfo.MergedColumnAnnotations;
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
        Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys = new HashMap<>();
        List<LinkTableInfo> linkTables = new ArrayList<>();
        List<DeferredForeignKey> deferredForeignKeys = new ArrayList<>();
        generateCreateTableStatements(entityClass, dbContext, generatedTables, statements, inferredForeignKeys, linkTables, deferredForeignKeys);
        
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
        for (DeferredForeignKey dfk : deferredForeignKeys) {
            statements.add(generateAlterTableAddForeignKey(dfk, dbContext));
        }
        
        return statements;
    }
    
    private static void generateCreateTableStatements(Class<?> entityClass, DbContext dbContext, 
            Set<String> generatedTables, List<String> statements, Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys,
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
        Set<String> generatedTables = new HashSet<>();
        List<String> statements = new ArrayList<>();
        Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys = new HashMap<>();
        List<LinkTableInfo> linkTables = new ArrayList<>();
        List<DeferredForeignKey> deferredForeignKeys = new ArrayList<>();
        
        // Collect merged column annotations for tables that have multiple class mappings
        Map<String, Map<String, MergedColumnAnnotations>> tableColumnAnnotations = new HashMap<>();
        collectMergedColumnAnnotations(entityClasses, tableColumnAnnotations);
        
        // First pass: scan all classes for collection fields to build the inferred foreign keys map
        for (Class<?> entityClass : entityClasses) {
            ForeignKeyScanner.scanForInferredForeignKeys(entityClass, inferredForeignKeys, linkTables);
        }
        
        // Second pass: generate CREATE TABLE statements (without FK constraints)
        for (Class<?> entityClass : entityClasses) {
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
        for (DeferredForeignKey dfk : deferredForeignKeys) {
            statements.add(generateAlterTableAddForeignKey(dfk, dbContext));
        }
        
        return statements;
    }
    
    /**
     * Internal method that generates CREATE TABLE statements for a single entity class.
     */
    private static void generateCreateTableStatementsInternal(Class<?> entityClass, DbContext dbContext, 
            Set<String> generatedTables, List<String> statements, Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys,
            List<LinkTableInfo> linkTables, List<DeferredForeignKey> deferredForeignKeys,
            Map<String, Map<String, MergedColumnAnnotations>> tableColumnAnnotations) {
        // Scan for collection fields that imply foreign keys in other tables
        ForeignKeyScanner.scanForInferredForeignKeys(entityClass, inferredForeignKeys, linkTables);
        
        List<TableMapping> tableMappings = QueryBuilder.determineTableMapping(entityClass);
        if (tableMappings.isEmpty()) {
            throw new IllegalArgumentException("Class " + entityClass.getName() + " must have a @Table annotation");
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
            List<InferredForeignKey> fks = inferredForeignKeys.get(mapping.clazz);
            
            // Get merged column annotations for this table (may be null for single-class generation)
            Map<String, MergedColumnAnnotations> mergedAnnotations = null;
            if (tableColumnAnnotations != null) {
                String tableKey = (mapping.schemaName != null ? mapping.schemaName + "." : "") + mapping.tableName;
                mergedAnnotations = tableColumnAnnotations.get(tableKey);
            }
            
            statements.add(generateCreateTableForMapping(mapping, dbContext, fks, deferredForeignKeys, mergedAnnotations));
        }
        
        // Handle @SubClasses annotation for table-per-subclass inheritance
        SubClasses subClassesAnn = entityClass.getAnnotation(SubClasses.class);
        if (subClassesAnn != null) {
            for (Class<?> subClass : subClassesAnn.value()) {
                generateCreateTableStatementsInternal(subClass, dbContext, generatedTables, statements, 
                    inferredForeignKeys, linkTables, deferredForeignKeys, tableColumnAnnotations);
            }
        }
    }
    
    /**
     * Collects merged column annotations from all classes that map to the same table.
     */
    private static void collectMergedColumnAnnotations(Class<?>[] entityClasses, 
            Map<String, Map<String, MergedColumnAnnotations>> tableColumnAnnotations) {
        for (Class<?> entityClass : entityClasses) {
            List<TableMapping> tableMappings = QueryBuilder.determineTableMapping(entityClass);
            for (TableMapping mapping : tableMappings) {
                String tableKey = (mapping.schemaName != null ? mapping.schemaName + "." : "") + mapping.tableName;
                Map<String, MergedColumnAnnotations> columnMap = tableColumnAnnotations
                    .computeIfAbsent(tableKey, k -> new HashMap<>());
                
                // Process fields from this mapping
                for (Field field : mapping.fields) {
                    String columnName = QueryBuilder.determineSqlFieldName(field).toLowerCase();
                    MergedColumnAnnotations merged = columnMap.computeIfAbsent(columnName, k -> new MergedColumnAnnotations());
                    Column columnAnn = field.getAnnotation(Column.class);
                    merged.mergeWith(columnAnn);
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
        List<Field> idFields = QueryBuilder.determineIdFields(mapping.clazz);
        boolean isCompositeKey = idFields.size() > 1;
        
        // Check if this is a subclass table (not the root table with @Id fields)
        // In table-per-subclass, the subclass table needs the ID field from parent as FK/PK
        boolean hasIdFieldInThisMapping = false;
        for (Field field : mapping.fields) {
            if (field.getAnnotation(Id.class) != null) {
                hasIdFieldInThisMapping = true;
                break;
            }
        }
        
        // If this mapping doesn't have its own @Id field but the class has inherited @Id,
        // we need to add the ID field as a non-auto-increment primary key (foreign key to parent)
        if (!hasIdFieldInThisMapping && !idFields.isEmpty()) {
            for (Field idField : idFields) {
                String columnName = QueryBuilder.determineSqlFieldName(idField);
                // Add as NOT NULL (not auto-increment - it references the parent table)
                String columnDef = formatColumnDefinition(columnName, idField.getType(), false, dbContext, idField, mergedAnnotations);
                columnDefinitions.add(columnDef);
                primaryKeyColumns.add(dbContext.quoteObjectNames(columnName));
                existingColumnNames.add(columnName.toLowerCase());
            }
        }
        
        for (Field field : mapping.fields) {
            // Handle embedded fields
            if (field.getAnnotation(Embedded.class) != null) {
                Embedded embedded = field.getAnnotation(Embedded.class);
                String prefix = Embedded.DEFAULT.equals(embedded.prefix()) ? "" : embedded.prefix();
                addEmbeddedColumns(field.getType(), prefix, columnDefinitions, primaryKeyColumns, 
                    existingColumnNames, dbContext, isCompositeKey, mergedAnnotations);
                continue;
            }
            
            // Handle linked fields (foreign keys or collections)
            if (isLinkedField(field)) {
                // For single entity references, add a foreign key column
                if (!CustomizableQueryBuilder.isListOrArray(field.getType())) {
                    String columnName = determineForeignKeyColumnName(field);
                    // Only add if not already defined (e.g., as an @Id field)
                    if (!existingColumnNames.contains(columnName.toLowerCase())) {
                        String columnDef = formatForeignKeyColumnDefinition(columnName, dbContext, field);
                        columnDefinitions.add(columnDef);
                        existingColumnNames.add(columnName.toLowerCase());
                    }
                    
                    // Defer foreign key constraint for single entity references
                    if (!existingFkColumns.contains(columnName.toLowerCase())) {
                        Class<?> linkedType = field.getType();
                        List<TableMapping> linkedMappings = QueryBuilder.determineTableMapping(linkedType);
                        if (!linkedMappings.isEmpty()) {
                            TableMapping linkedMapping = linkedMappings.get(0);
                            List<Field> linkedIdFields = QueryBuilder.determineIdFields(linkedType);
                            if (!linkedIdFields.isEmpty()) {
                                String refColumn = QueryBuilder.determineSqlFieldName(linkedIdFields.get(0));
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
            
            String columnName = QueryBuilder.determineSqlFieldName(field);
            boolean isPrimaryKey = field.getAnnotation(Id.class) != null;
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
    
    private static void addEmbeddedColumns(Class<?> embeddedClass, String prefix, 
            List<String> columnDefinitions, List<String> primaryKeyColumns, Set<String> existingColumnNames, 
            DbContext dbContext, boolean isCompositeKey, Map<String, MergedColumnAnnotations> mergedAnnotations) {
        // Use QueryBuilder's filterFields which already handles static, transient, and @Transient
        Collection<Field> fields = QueryBuilder.filterFields(embeddedClass);
        for (Field field : fields) {
            // Recursively handle nested embedded
            if (field.getAnnotation(Embedded.class) != null) {
                Embedded nested = field.getAnnotation(Embedded.class);
                String nestedPrefix = prefix + (Embedded.DEFAULT.equals(nested.prefix()) ? "" : nested.prefix());
                addEmbeddedColumns(field.getType(), nestedPrefix, columnDefinitions, primaryKeyColumns, 
                    existingColumnNames, dbContext, isCompositeKey, mergedAnnotations);
                continue;
            }
            
            String columnName = prefix + QueryBuilder.determineSqlFieldName(field);
            boolean isPrimaryKey = field.getAnnotation(Id.class) != null;
            boolean shouldAutoIncrement = isPrimaryKey && !isCompositeKey;
            String columnDef = formatColumnDefinition(columnName, field.getType(), shouldAutoIncrement, dbContext, field, mergedAnnotations);
            columnDefinitions.add(columnDef);
            existingColumnNames.add(columnName.toLowerCase());
            
            if (isPrimaryKey) {
                primaryKeyColumns.add(dbContext.quoteObjectNames(columnName));
            }
        }
    }
    
    private static boolean isLinkedField(Field field) {
        Class<?> type = field.getType();
        // Check if it's a collection (list, set, etc.) - reuse QueryBuilder's logic
        if (CustomizableQueryBuilder.isListOrArray(type)) {
            return true;
        }
        // Check if the field type has a @Link annotation
        if (field.getAnnotation(Link.class) != null) {
            return true;
        }
        // Check if the field type is an entity - reuse QueryBuilder's logic
        return CustomizableQueryBuilder.isLinkedClass(type);
    }
    
    private static String determineForeignKeyColumnName(Field field) {
        Link linkAnn = field.getAnnotation(Link.class);
        if (linkAnn != null && !Link.NONE.equals(linkAnn.linkfield())) {
            return linkAnn.linkfield();
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
    
    private static String formatForeignKeyColumnDefinition(String columnName, DbContext dbContext, Field field) {
        StringBuilder sb = new StringBuilder();
        sb.append(dbContext.quoteObjectNames(columnName));
        sb.append(" ");

        sb.append(dbContext.getForeignKeyColumnType());
        
        // Check @Link for nullable and unique constraints
        if (field != null) {
            Link linkAnn = field.getAnnotation(Link.class);
            if (linkAnn != null) {
                if (!linkAnn.nullable()) {
                    sb.append(" NOT NULL");
                }
                if (linkAnn.unique()) {
                    sb.append(" UNIQUE");
                }
            }
        }

        return sb.toString();
    }

    private static String formatColumnDefinition(String columnName, Class<?> type, boolean autoIncrement, 
            DbContext dbContext, Field field, Map<String, MergedColumnAnnotations> mergedAnnotations) {
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
                Column columnAnn = field.getAnnotation(Column.class);
                if (columnAnn != null && !columnAnn.nullable()) {
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
            Column columnAnn = field.getAnnotation(Column.class);
            if (columnAnn != null && columnAnn.unique()) {
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
        return generateMigrationStatements(schemaInfo, DbContext.getDefault(), entityClasses);
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
    public static List<String> generateMigrationStatements(SchemaInfo schemaInfo, DbContext dbContext, Class<?>... entityClasses) {
        Set<String> processedTables = new HashSet<>();
        List<String> statements = new ArrayList<>();
        Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys = new HashMap<>();
        List<LinkTableInfo> linkTables = new ArrayList<>();
        List<DeferredForeignKey> deferredForeignKeys = new ArrayList<>();
        
        // First pass: scan all classes for collection fields to build the inferred foreign keys map
        for (Class<?> entityClass : entityClasses) {
            ForeignKeyScanner.scanForInferredForeignKeys(entityClass, inferredForeignKeys, linkTables);
        }
        
        // Second pass: generate DDL statements
        for (Class<?> entityClass : entityClasses) {
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
    
    private static void generateMigrationStatements(Class<?> entityClass, SchemaInfo schemaInfo, DbContext dbContext,
            Set<String> processedTables, List<String> statements, Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys,
            List<LinkTableInfo> linkTables, List<DeferredForeignKey> deferredForeignKeys) {
        // Scan for inferred foreign keys
        ForeignKeyScanner.scanForInferredForeignKeys(entityClass, inferredForeignKeys, linkTables);
        
        List<TableMapping> tableMappings = QueryBuilder.determineTableMapping(entityClass);
        if (tableMappings.isEmpty()) {
            throw new IllegalArgumentException("Class " + entityClass.getName() + " must have a @Table annotation");
        }
        
        for (TableMapping mapping : tableMappings) {
            String fullTableName = getFullTableName(mapping, dbContext);
            // Skip if we've already processed this table
            if (processedTables.contains(fullTableName)) {
                continue;
            }
            processedTables.add(fullTableName);
            
            // Get inferred foreign keys for this class
            List<InferredForeignKey> fks = inferredForeignKeys.get(mapping.clazz);
            
            // Check if table exists
            SchemaInfo.TableInfo existingTable = schemaInfo.getTable(mapping.schemaName, mapping.tableName);
            
            if (existingTable == null) {
                // Table doesn't exist - generate CREATE TABLE (no merged annotations for migration)
                statements.add(generateCreateTableForMapping(mapping, dbContext, fks, deferredForeignKeys, null));
            } else {
                // Table exists - check for missing columns and generate ALTER TABLE
                String alterStatement = generateAlterTableForMapping(mapping, existingTable, dbContext, fks);
                if (alterStatement != null) {
                    statements.add(alterStatement);
                }
            }
        }
        
        // Handle @SubClasses annotation for table-per-subclass inheritance
        SubClasses subClassesAnn = entityClass.getAnnotation(SubClasses.class);
        if (subClassesAnn != null) {
            for (Class<?> subClass : subClassesAnn.value()) {
                generateMigrationStatements(subClass, schemaInfo, dbContext, processedTables, statements, inferredForeignKeys, linkTables, deferredForeignKeys);
            }
        }
    }
    
    /**
     * Generates an ALTER TABLE statement to add missing columns to an existing table.
     * 
     * @param mapping the table mapping
     * @param existingTable information about the existing table
     * @param dbContext the database context
     * @param inferredForeignKeys inferred foreign keys to add
     * @return ALTER TABLE statement, or null if no columns need to be added
     */
    private static String generateAlterTableForMapping(TableMapping mapping, SchemaInfo.TableInfo existingTable, 
            DbContext dbContext, List<InferredForeignKey> inferredForeignKeys) {
        
        List<ColumnDefinition> requiredColumns = getRequiredColumns(mapping, dbContext, inferredForeignKeys);
        List<ColumnDefinition> missingColumns = new ArrayList<>();
        
        for (ColumnDefinition col : requiredColumns) {
            if (!existingTable.hasColumn(col.name)) {
                missingColumns.add(col);
            }
        }
        
        if (missingColumns.isEmpty()) {
            return null;
        }
        
        // Generate ALTER TABLE ADD COLUMN statement(s)
        StringBuilder sb = new StringBuilder();
        String tableName = getFullTableName(mapping, dbContext);
        
        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append("\n");
        
        for (int i = 0; i < missingColumns.size(); i++) {
            ColumnDefinition col = missingColumns.get(i);
            sb.append("  ADD COLUMN ");
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
            
            if (i < missingColumns.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        
        sb.append(";");
        
        return sb.toString();
    }
    
    /**
     * Gets all required columns for a table mapping.
     */
    private static List<ColumnDefinition> getRequiredColumns(TableMapping mapping, DbContext dbContext, 
            List<InferredForeignKey> inferredForeignKeys) {
        
        List<ColumnDefinition> columns = new ArrayList<>();
        Set<String> existingColumnNames = new HashSet<>();
        
        // Determine if we have a composite key from the overall class hierarchy
        List<Field> idFields = QueryBuilder.determineIdFields(mapping.clazz);
        boolean isCompositeKey = idFields.size() > 1;
        
        // Check if this is a subclass table
        boolean hasIdFieldInThisMapping = false;
        for (Field field : mapping.fields) {
            if (field.getAnnotation(Id.class) != null) {
                hasIdFieldInThisMapping = true;
                break;
            }
        }
        
        // Add ID fields if needed (for subclass tables)
        if (!hasIdFieldInThisMapping && !idFields.isEmpty()) {
            for (Field idField : idFields) {
                String columnName = QueryBuilder.determineSqlFieldName(idField);
                String sqlType = dbContext.mapJavaTypeToSql(idField);
                columns.add(new ColumnDefinition(columnName, sqlType, false, true));
                existingColumnNames.add(columnName.toLowerCase());
            }
        }
        
        // Process fields
        for (Field field : mapping.fields) {
            // Handle embedded fields
            if (field.getAnnotation(Embedded.class) != null) {
                Embedded embedded = field.getAnnotation(Embedded.class);
                String prefix = Embedded.DEFAULT.equals(embedded.prefix()) ? "" : embedded.prefix();
                addEmbeddedColumnsToList(field.getType(), prefix, columns, existingColumnNames, dbContext, isCompositeKey);
                continue;
            }
            
            // Handle linked fields
            if (isLinkedField(field)) {
                if (!CustomizableQueryBuilder.isListOrArray(field.getType())) {
                    String columnName = determineForeignKeyColumnName(field);
                    // Only add if not already defined (e.g., as an @Id field)
                    if (!existingColumnNames.contains(columnName.toLowerCase())) {
                        String sqlType = dbContext.getForeignKeyColumnType();
                        // Check @Link for nullable and unique
                        Link linkAnn = field.getAnnotation(Link.class);
                        boolean notNull = linkAnn != null && !linkAnn.nullable();
                        boolean unique = linkAnn != null && linkAnn.unique();
                        columns.add(new ColumnDefinition(columnName, sqlType, false, false, notNull, unique));
                        existingColumnNames.add(columnName.toLowerCase());
                    }
                }
                continue;
            }
            
            String columnName = QueryBuilder.determineSqlFieldName(field);
            boolean isPrimaryKey = field.getAnnotation(Id.class) != null;
            boolean shouldAutoIncrement = isPrimaryKey && !isCompositeKey;
            String sqlType = dbContext.mapJavaTypeToSql(field);
            Column columnAnn = field.getAnnotation(Column.class);
            boolean notNull = !shouldAutoIncrement && columnAnn != null && !columnAnn.nullable();
            boolean unique = columnAnn != null && columnAnn.unique();
            
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
    
    private static void addEmbeddedColumnsToList(Class<?> embeddedClass, String prefix, 
            List<ColumnDefinition> columns, Set<String> existingColumnNames, DbContext dbContext, boolean isCompositeKey) {
        Collection<Field> fields = QueryBuilder.filterFields(embeddedClass);
        for (Field field : fields) {
            if (field.getAnnotation(Embedded.class) != null) {
                Embedded nested = field.getAnnotation(Embedded.class);
                String nestedPrefix = prefix + (Embedded.DEFAULT.equals(nested.prefix()) ? "" : nested.prefix());
                addEmbeddedColumnsToList(field.getType(), nestedPrefix, columns, existingColumnNames, dbContext, isCompositeKey);
                continue;
            }
            
            String columnName = prefix + QueryBuilder.determineSqlFieldName(field);
            boolean isPrimaryKey = field.getAnnotation(Id.class) != null;
            boolean shouldAutoIncrement = isPrimaryKey && !isCompositeKey;
            String sqlType = dbContext.mapJavaTypeToSql(field);
            Column columnAnn = field.getAnnotation(Column.class);
            boolean notNull = !shouldAutoIncrement && columnAnn != null && !columnAnn.nullable();
            boolean unique = columnAnn != null && columnAnn.unique();
            
            columns.add(new ColumnDefinition(columnName, sqlType, shouldAutoIncrement, isPrimaryKey, notNull, unique));
            existingColumnNames.add(columnName.toLowerCase());
        }
    }
}
