package org.pojoquery.schema;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.pojoquery.DbContext;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.NotNull;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.annotations.Unique;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.CustomizableQueryBuilder;
import org.pojoquery.pipeline.QueryBuilder;

/**
 * Generates CREATE TABLE statements based on entity classes annotated with PojoQuery annotations.
 * 
 * <p>Example usage:</p>
 * <pre>
 * String createTableSql = SchemaGenerator.generateCreateTable(MyEntity.class);
 * </pre>
 */
public class SchemaGenerator {

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
        // First, scan for collection fields that imply foreign keys in other tables
        scanForInferredForeignKeys(entityClass, inferredForeignKeys, linkTables);
        
        List<TableMapping> tableMappings = QueryBuilder.determineTableMapping(entityClass);
        if (tableMappings.isEmpty()) {
            throw new IllegalArgumentException("Class " + entityClass.getName() + " must have a @Table annotation");
        }
        
        for (TableMapping mapping : tableMappings) {
            String fullTableName = getFullTableName(mapping, dbContext);
            // Skip if we've already generated this table
            if (generatedTables.contains(fullTableName)) {
                continue;
            }
            generatedTables.add(fullTableName);
            
            // Get inferred foreign keys for this class
            List<InferredForeignKey> fks = inferredForeignKeys.get(mapping.clazz);
            statements.add(generateCreateTableForMapping(mapping, dbContext, fks, deferredForeignKeys));
        }
        
        // Handle @SubClasses annotation for table-per-subclass inheritance
        SubClasses subClassesAnn = entityClass.getAnnotation(SubClasses.class);
        if (subClassesAnn != null) {
            for (Class<?> subClass : subClassesAnn.value()) {
                generateCreateTableStatements(subClass, dbContext, generatedTables, statements, inferredForeignKeys, linkTables, deferredForeignKeys);
            }
        }
    }
    
    /**
     * Scans an entity class for fields that imply foreign keys:
     * 1. Collection fields (one-to-many) - FK goes in the referenced entity's table
     * 2. Single entity references with @Link(linkfield=...) - FK goes in the declaring class's table
     * 3. Collection fields with linktable - generates link table with both FKs
     */
    private static void scanForInferredForeignKeys(Class<?> entityClass, Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys,
            List<LinkTableInfo> linkTables) {
        // Scan collection fields for one-to-many relationships and many-to-many link tables
        scanCollectionFieldsForInferredForeignKeys(entityClass, inferredForeignKeys, linkTables);
        
        // Scan single entity reference fields with @Link(linkfield=...)
        scanSingleEntityFieldsForInferredForeignKeys(entityClass, inferredForeignKeys);
    }
    
    /**
     * Scans collection fields that imply foreign keys in the referenced entity,
     * or link tables for many-to-many relationships.
     */
    private static void scanCollectionFieldsForInferredForeignKeys(Class<?> entityClass, 
            Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys, List<LinkTableInfo> linkTables) {
        Collection<Field> fields = QueryBuilder.filterFields(entityClass);
        TableMapping ownerMapping = QueryBuilder.determineTableMapping(entityClass).get(0);
        List<Field> ownerIdFields = QueryBuilder.determineIdFields(entityClass);
        String ownerIdColumn = ownerIdFields.isEmpty() ? "id" : QueryBuilder.determineSqlFieldName(ownerIdFields.get(0));
        
        for (Field field : fields) {
            if (CustomizableQueryBuilder.isListOrArray(field.getType())) {
                // Check if this is a many-to-many via linktable
                Link linkAnn = field.getAnnotation(Link.class);
                if (linkAnn != null && !Link.NONE.equals(linkAnn.linktable())) {
                    // Many-to-many relationship - generate link table
                    Class<?> componentType = getCollectionComponentType(field);
                    if (componentType != null && CustomizableQueryBuilder.isLinkedClass(componentType)) {
                        LinkTableInfo linkTableInfo = createLinkTableInfo(entityClass, ownerMapping, ownerIdColumn, field, linkAnn, componentType);
                        if (linkTableInfo != null) {
                            linkTables.add(linkTableInfo);
                        }
                    }
                    continue;
                }
                
                // One-to-many - Get the component type of the collection
                Class<?> componentType = getCollectionComponentType(field);
                if (componentType != null && CustomizableQueryBuilder.isLinkedClass(componentType)) {
                    // Determine the foreign key column name
                    String fkColumnName = determineForeignKeyColumnNameForCollection(entityClass, field);
                    
                    // Find the root table class for the component type (handles inheritance)
                    // The FK should be added to the class that defines the root @Table
                    Class<?> rootTableClass = findRootTableClass(componentType);
                    if (rootTableClass != null) {
                        // Create FK with reference to the owning table
                        InferredForeignKey fk = new InferredForeignKey(fkColumnName, ownerMapping.tableName, ownerIdColumn, ownerMapping.schemaName);
                        inferredForeignKeys.computeIfAbsent(rootTableClass, k -> new ArrayList<>()).add(fk);
                    }
                }
            }
        }
    }
    
    /**
     * Creates a LinkTableInfo for a many-to-many relationship.
     */
    private static LinkTableInfo createLinkTableInfo(Class<?> entityClass, TableMapping ownerMapping, String ownerIdColumn,
            Field field, Link linkAnn, Class<?> componentType) {
        // Get foreign table info
        List<TableMapping> foreignMappings = QueryBuilder.determineTableMapping(componentType);
        if (foreignMappings.isEmpty()) {
            return null;
        }
        TableMapping foreignMapping = foreignMappings.get(0);
        List<Field> foreignIdFields = QueryBuilder.determineIdFields(componentType);
        String foreignIdColumn = foreignIdFields.isEmpty() ? "id" : QueryBuilder.determineSqlFieldName(foreignIdFields.get(0));
        
        // Determine link table columns
        String ownerColumn = !Link.NONE.equals(linkAnn.linkfield()) 
            ? linkAnn.linkfield() 
            : ownerMapping.tableName + "_id";
        String foreignColumn = !Link.NONE.equals(linkAnn.foreignlinkfield())
            ? linkAnn.foreignlinkfield()
            : foreignMapping.tableName + "_id";
        
        // Handle self-referencing relationships (same table on both sides)
        // If columns would clash, prefix the foreign column with the field name
        if (ownerColumn.equalsIgnoreCase(foreignColumn)) {
            // Use field name to disambiguate (e.g., "friends" -> "friends_id")
            foreignColumn = field.getName() + "_id";
        }
        
        return new LinkTableInfo(
            linkAnn.linktable(),
            linkAnn.linkschema().isEmpty() ? null : linkAnn.linkschema(),
            ownerColumn, ownerMapping.tableName, ownerIdColumn, ownerMapping.schemaName,
            foreignColumn, foreignMapping.tableName, foreignIdColumn, foreignMapping.schemaName
        );
    }
    
    /**
     * Finds the class that has the root @Table annotation for the given class.
     * Walks up the inheritance hierarchy to find the first class with @Table.
     */
    private static Class<?> findRootTableClass(Class<?> clazz) {
        Class<?> rootTableClass = null;
        Class<?> current = clazz;
        while (current != null) {
            if (current.getAnnotation(Table.class) != null) {
                rootTableClass = current;
            }
            current = current.getSuperclass();
        }
        return rootTableClass;
    }
    
    /**
     * Scans single entity reference fields with @Link(linkfield=...) that imply foreign keys
     * in the declaring class's table (or parent table for subclasses).
     */
    private static void scanSingleEntityFieldsForInferredForeignKeys(Class<?> entityClass, Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys) {
        Collection<Field> fields = QueryBuilder.filterFields(entityClass);
        for (Field field : fields) {
            // Skip collections
            if (CustomizableQueryBuilder.isListOrArray(field.getType())) {
                continue;
            }
            
            // Check for single entity reference with @Link annotation
            Link linkAnn = field.getAnnotation(Link.class);
            if (linkAnn != null && !Link.NONE.equals(linkAnn.linkfield())) {
                // This field has an explicit linkfield - the FK column goes in the declaring class's table
                String fkColumnName = linkAnn.linkfield();
                
                // Get reference table info from the linked type
                Class<?> linkedType = field.getType();
                List<TableMapping> linkedMappings = QueryBuilder.determineTableMapping(linkedType);
                String refTable = null;
                String refColumn = null;
                String refSchema = null;
                if (!linkedMappings.isEmpty()) {
                    TableMapping linkedMapping = linkedMappings.get(0);
                    refTable = linkedMapping.tableName;
                    refSchema = linkedMapping.schemaName;
                    List<Field> linkedIdFields = QueryBuilder.determineIdFields(linkedType);
                    refColumn = linkedIdFields.isEmpty() ? "id" : QueryBuilder.determineSqlFieldName(linkedIdFields.get(0));
                }
                
                // Find the table class where this FK should be placed
                // For subclasses, this might be the parent table
                Class<?> tableClass = findTableClass(entityClass);
                if (tableClass != null) {
                    InferredForeignKey fk = new InferredForeignKey(fkColumnName, refTable, refColumn, refSchema);
                    inferredForeignKeys.computeIfAbsent(tableClass, k -> new ArrayList<>()).add(fk);
                }
            }
        }
    }
    
    /**
     * Finds the class that defines the @Table annotation for the given class.
     * For subclasses without their own @Table, returns the parent class with @Table.
     */
    private static Class<?> findTableClass(Class<?> clazz) {
        List<TableMapping> mappings = QueryBuilder.determineTableMapping(clazz);
        if (!mappings.isEmpty()) {
            return mappings.get(0).clazz;
        }
        return null;
    }
    
    /**
     * Gets the component type of a collection or array field.
     */
    private static Class<?> getCollectionComponentType(Field field) {
        Class<?> type = field.getType();
        if (type.isArray()) {
            return type.getComponentType();
        }
        // For generic collections, try to extract the type parameter
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            Type[] typeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
            if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                return (Class<?>) typeArgs[0];
            }
        }
        return null;
    }
    
    /**
     * Determines the foreign key column name for a collection field.
     * Uses the owning class's table name + "_id" by default, unless @Link specifies foreignlinkfield.
     */
    private static String determineForeignKeyColumnNameForCollection(Class<?> owningClass, Field field) {
        Link linkAnn = field.getAnnotation(Link.class);
        if (linkAnn != null && !Link.NONE.equals(linkAnn.foreignlinkfield())) {
            return linkAnn.foreignlinkfield();
        }
        // Default: owning table name + "_id"
        TableMapping ownerMapping = QueryBuilder.determineTableMapping(owningClass).get(0);
        return ownerMapping.tableName + "_id";
    }
    
    /**
     * Represents an inferred foreign key column with its reference information.
     */
    private static class InferredForeignKey {
        final String columnName;
        final String referencedTable;
        final String referencedColumn;
        final String referencedSchema;
        
        InferredForeignKey(String columnName) {
            this(columnName, null, null, null);
        }
        
        InferredForeignKey(String columnName, String referencedTable, String referencedColumn, String referencedSchema) {
            this.columnName = columnName;
            this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn;
            this.referencedSchema = referencedSchema;
        }
        
        boolean hasReference() {
            return referencedTable != null && referencedColumn != null;
        }
    }
    
    /**
     * Represents a deferred foreign key constraint to be added via ALTER TABLE.
     */
    private static class DeferredForeignKey {
        final String tableName;
        final String tableSchema;
        final String columnName;
        final String referencedTable;
        final String referencedColumn;
        final String referencedSchema;
        
        DeferredForeignKey(String tableName, String tableSchema, String columnName, 
                          String referencedTable, String referencedColumn, String referencedSchema) {
            this.tableName = tableName;
            this.tableSchema = tableSchema;
            this.columnName = columnName;
            this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn;
            this.referencedSchema = referencedSchema;
        }
    }
    
    /**
     * Represents a link table for many-to-many relationships.
     */
    private static class LinkTableInfo {
        final String tableName;
        final String schemaName;
        final String ownerColumn;
        final String ownerTable;
        final String ownerIdColumn;
        final String ownerSchema;
        final String foreignColumn;
        final String foreignTable;
        final String foreignIdColumn;
        final String foreignSchema;
        
        LinkTableInfo(String tableName, String schemaName,
                      String ownerColumn, String ownerTable, String ownerIdColumn, String ownerSchema,
                      String foreignColumn, String foreignTable, String foreignIdColumn, String foreignSchema) {
            this.tableName = tableName;
            this.schemaName = schemaName;
            this.ownerColumn = ownerColumn;
            this.ownerTable = ownerTable;
            this.ownerIdColumn = ownerIdColumn;
            this.ownerSchema = ownerSchema;
            this.foreignColumn = foreignColumn;
            this.foreignTable = foreignTable;
            this.foreignIdColumn = foreignIdColumn;
            this.foreignSchema = foreignSchema;
        }
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
        
        // First pass: scan all classes for collection fields to build the inferred foreign keys map
        for (Class<?> entityClass : entityClasses) {
            scanForInferredForeignKeys(entityClass, inferredForeignKeys, linkTables);
        }
        
        // Second pass: generate CREATE TABLE statements (without FK constraints)
        for (Class<?> entityClass : entityClasses) {
            generateCreateTableStatements(entityClass, dbContext, generatedTables, statements, inferredForeignKeys, linkTables, deferredForeignKeys);
        }
        
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
    
    /**
     * Generates a CREATE TABLE statement for a link table (many-to-many relationship).
     */
    private static String generateCreateLinkTable(LinkTableInfo linkTable, DbContext dbContext, List<DeferredForeignKey> deferredForeignKeys) {
        StringBuilder sb = new StringBuilder();
        
        // Table name
        String tableName = linkTable.schemaName != null && !linkTable.schemaName.isEmpty()
            ? dbContext.quoteObjectNames(linkTable.schemaName) + "." + dbContext.quoteObjectNames(linkTable.tableName)
            : dbContext.quoteObjectNames(linkTable.tableName);
        
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
        
        String tableName = dfk.tableSchema != null && !dfk.tableSchema.isEmpty()
            ? dbContext.quoteObjectNames(dfk.tableSchema) + "." + dbContext.quoteObjectNames(dfk.tableName)
            : dbContext.quoteObjectNames(dfk.tableName);
        
        String refTableName = dfk.referencedSchema != null && !dfk.referencedSchema.isEmpty()
            ? dbContext.quoteObjectNames(dfk.referencedSchema) + "." + dbContext.quoteObjectNames(dfk.referencedTable)
            : dbContext.quoteObjectNames(dfk.referencedTable);
        
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
        for (String ddl : generateCreateTableStatements(classes)) {
            org.pojoquery.DB.executeDDL(db, ddl);
        }
    }
    
    private static String generateCreateTableForMapping(TableMapping mapping, DbContext dbContext, List<InferredForeignKey> inferredForeignKeys, List<DeferredForeignKey> deferredForeignKeys) {
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
                String columnDef = formatColumnDefinition(columnName, idField.getType(), false, dbContext, idField);
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
                addEmbeddedColumns(field.getType(), prefix, columnDefinitions, primaryKeyColumns, existingColumnNames, dbContext, isCompositeKey);
                continue;
            }
            
            // Handle linked fields (foreign keys or collections)
            if (isLinkedField(field)) {
                // For single entity references, add a foreign key column
                if (!CustomizableQueryBuilder.isListOrArray(field.getType())) {
                    String columnName = determineForeignKeyColumnName(field);
                    // Only add if not already defined (e.g., as an @Id field)
                    if (!existingColumnNames.contains(columnName.toLowerCase())) {
                        String columnDef = formatIdColumnDefinition(columnName, dbContext);
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
            String columnDef = formatColumnDefinition(columnName, field.getType(), shouldAutoIncrement, dbContext, field);
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
            List<String> columnDefinitions, List<String> primaryKeyColumns, Set<String> existingColumnNames, DbContext dbContext, boolean isCompositeKey) {
        // Use QueryBuilder's filterFields which already handles static, transient, and @Transient
        Collection<Field> fields = QueryBuilder.filterFields(embeddedClass);
        for (Field field : fields) {
            // Recursively handle nested embedded
            if (field.getAnnotation(Embedded.class) != null) {
                Embedded nested = field.getAnnotation(Embedded.class);
                String nestedPrefix = prefix + (Embedded.DEFAULT.equals(nested.prefix()) ? "" : nested.prefix());
                addEmbeddedColumns(field.getType(), nestedPrefix, columnDefinitions, primaryKeyColumns, existingColumnNames, dbContext, isCompositeKey);
                continue;
            }
            
            String columnName = prefix + QueryBuilder.determineSqlFieldName(field);
            boolean isPrimaryKey = field.getAnnotation(Id.class) != null;
            boolean shouldAutoIncrement = isPrimaryKey && !isCompositeKey;
            String columnDef = formatColumnDefinition(columnName, field.getType(), shouldAutoIncrement, dbContext, field);
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

    private static String formatColumnDefinition(String columnName, Class<?> type, boolean autoIncrement, DbContext dbContext, Field field) {
        StringBuilder sb = new StringBuilder();
        sb.append(dbContext.quoteObjectNames(columnName));
        sb.append(" ");
        
        // For auto-increment primary keys, some databases use special types (e.g., BIGSERIAL in Postgres)
        if (autoIncrement && !dbContext.getAutoIncrementKeyColumnType().equals("BIGINT")) {
            // Use the auto-increment key column type which includes auto-increment semantics (e.g., BIGSERIAL)
            sb.append(dbContext.getAutoIncrementKeyColumnType());
        } else {
            sb.append(dbContext.mapJavaTypeToSql(field));
            
            // Add NOT NULL constraint if @NotNull is present (and not already implied by auto-increment)
            if (!autoIncrement && field != null && field.getAnnotation(NotNull.class) != null) {
                sb.append(" NOT NULL");
            }
            
            if (autoIncrement) {
                String autoIncrementSyntax = dbContext.getAutoIncrementSyntax();
                if (!autoIncrementSyntax.isEmpty()) {
                    sb.append(" ");
                    sb.append(autoIncrementSyntax);
                }
            }
        }
        
        // Add UNIQUE constraint if @Unique is present
        if (field != null && field.getAnnotation(Unique.class) != null) {
            sb.append(" UNIQUE");
        }
        
        return sb.toString();
    }
    
    private static String getFullTableName(TableMapping mapping, DbContext dbContext) {
        if (mapping.schemaName != null && !mapping.schemaName.isEmpty()) {
            return dbContext.quoteObjectNames(mapping.schemaName) + "." + dbContext.quoteObjectNames(mapping.tableName);
        }
        return dbContext.quoteObjectNames(mapping.tableName);
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
            scanForInferredForeignKeys(entityClass, inferredForeignKeys, linkTables);
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
        scanForInferredForeignKeys(entityClass, inferredForeignKeys, linkTables);
        
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
                // Table doesn't exist - generate CREATE TABLE
                statements.add(generateCreateTableForMapping(mapping, dbContext, fks, deferredForeignKeys));
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
                        columns.add(new ColumnDefinition(columnName, sqlType, false, false));
                        existingColumnNames.add(columnName.toLowerCase());
                    }
                }
                continue;
            }
            
            String columnName = QueryBuilder.determineSqlFieldName(field);
            boolean isPrimaryKey = field.getAnnotation(Id.class) != null;
            boolean shouldAutoIncrement = isPrimaryKey && !isCompositeKey;
            String sqlType = dbContext.mapJavaTypeToSql(field);
            boolean notNull = !shouldAutoIncrement && field.getAnnotation(NotNull.class) != null;
            boolean unique = field.getAnnotation(Unique.class) != null;
            
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
            boolean notNull = !shouldAutoIncrement && field.getAnnotation(NotNull.class) != null;
            boolean unique = field.getAnnotation(Unique.class) != null;
            
            columns.add(new ColumnDefinition(columnName, sqlType, shouldAutoIncrement, isPrimaryKey, notNull, unique));
            existingColumnNames.add(columnName.toLowerCase());
        }
    }
}
