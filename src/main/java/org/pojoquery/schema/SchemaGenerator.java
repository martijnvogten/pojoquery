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
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
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
        return generateCreateTableStatements(entityClass, DbContext.DEFAULT);
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
        generateCreateTableStatements(entityClass, dbContext, generatedTables, statements, inferredForeignKeys);
        return statements;
    }
    
    private static void generateCreateTableStatements(Class<?> entityClass, DbContext dbContext, 
            Set<String> generatedTables, List<String> statements, Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys) {
        // First, scan for collection fields that imply foreign keys in other tables
        scanForInferredForeignKeys(entityClass, inferredForeignKeys);
        
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
            statements.add(generateCreateTableForMapping(mapping, dbContext, fks));
        }
        
        // Handle @SubClasses annotation for table-per-subclass inheritance
        SubClasses subClassesAnn = entityClass.getAnnotation(SubClasses.class);
        if (subClassesAnn != null) {
            for (Class<?> subClass : subClassesAnn.value()) {
                generateCreateTableStatements(subClass, dbContext, generatedTables, statements, inferredForeignKeys);
            }
        }
    }
    
    /**
     * Scans an entity class for fields that imply foreign keys:
     * 1. Collection fields (one-to-many) - FK goes in the referenced entity's table
     * 2. Single entity references with @Link(linkfield=...) - FK goes in the declaring class's table
     */
    private static void scanForInferredForeignKeys(Class<?> entityClass, Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys) {
        // Scan collection fields for one-to-many relationships
        scanCollectionFieldsForInferredForeignKeys(entityClass, inferredForeignKeys);
        
        // Scan single entity reference fields with @Link(linkfield=...)
        scanSingleEntityFieldsForInferredForeignKeys(entityClass, inferredForeignKeys);
    }
    
    /**
     * Scans collection fields that imply foreign keys in the referenced entity.
     */
    private static void scanCollectionFieldsForInferredForeignKeys(Class<?> entityClass, Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys) {
        Collection<Field> fields = QueryBuilder.filterFields(entityClass);
        for (Field field : fields) {
            if (CustomizableQueryBuilder.isListOrArray(field.getType())) {
                // Check if this is a many-to-many via linktable
                Link linkAnn = field.getAnnotation(Link.class);
                if (linkAnn != null && !Link.NONE.equals(linkAnn.linktable())) {
                    // Many-to-many relationship, handled via link table
                    continue;
                }
                
                // Get the component type of the collection
                Class<?> componentType = getCollectionComponentType(field);
                if (componentType != null && CustomizableQueryBuilder.isLinkedClass(componentType)) {
                    // Determine the foreign key column name
                    String fkColumnName = determineForeignKeyColumnNameForCollection(entityClass, field);
                    
                    // Find the root table class for the component type (handles inheritance)
                    // The FK should be added to the class that defines the root @Table
                    Class<?> rootTableClass = findRootTableClass(componentType);
                    if (rootTableClass != null) {
                        inferredForeignKeys.computeIfAbsent(rootTableClass, k -> new ArrayList<>())
                                .add(new InferredForeignKey(fkColumnName, entityClass));
                    }
                }
            }
        }
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
                
                // Find the table class where this FK should be placed
                // For subclasses, this might be the parent table
                Class<?> tableClass = findTableClass(entityClass);
                if (tableClass != null) {
                    inferredForeignKeys.computeIfAbsent(tableClass, k -> new ArrayList<>())
                            .add(new InferredForeignKey(fkColumnName, field.getType()));
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
     * Represents an inferred foreign key column.
     */
    private static class InferredForeignKey {
        final String columnName;
        final Class<?> referencedClass;
        
        InferredForeignKey(String columnName, Class<?> referencedClass) {
            this.columnName = columnName;
            this.referencedClass = referencedClass;
        }
    }
    
    /**
     * Generates a list of CREATE TABLE statements for multiple entity classes using default DbContext.
     * 
     * @param entityClasses the entity classes
     * @return list of CREATE TABLE statements
     */
    public static List<String> generateCreateTableStatements(Class<?>... entityClasses) {
        return generateCreateTableStatements(DbContext.DEFAULT, entityClasses);
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
        
        // First pass: scan all classes for collection fields to build the inferred foreign keys map
        for (Class<?> entityClass : entityClasses) {
            scanForInferredForeignKeys(entityClass, inferredForeignKeys);
        }
        
        // Second pass: generate CREATE TABLE statements
        for (Class<?> entityClass : entityClasses) {
            generateCreateTableStatements(entityClass, dbContext, generatedTables, statements, inferredForeignKeys);
        }
        return statements;
    }
    
    private static String generateCreateTableForMapping(TableMapping mapping, DbContext dbContext, List<InferredForeignKey> inferredForeignKeys) {
        StringBuilder sb = new StringBuilder();
        
        String tableName = getFullTableName(mapping, dbContext);
        
        // CREATE TABLE
        sb.append("CREATE TABLE ");
        sb.append(tableName).append(" (\n");
        
        List<String> columnDefinitions = new ArrayList<>();
        List<String> primaryKeyColumns = new ArrayList<>();
        Set<String> existingColumnNames = new HashSet<>();
        
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
                String columnDef = formatColumnDefinition(columnName, idField.getType(), false, dbContext);
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
                    String columnDef = formatColumnDefinition(columnName, Long.class, false, dbContext);
                    columnDefinitions.add(columnDef);
                    existingColumnNames.add(columnName.toLowerCase());
                }
                // Collections are handled via inferred foreign keys in the referenced table
                continue;
            }
            
            String columnName = QueryBuilder.determineSqlFieldName(field);
            boolean isPrimaryKey = field.getAnnotation(Id.class) != null;
            // Only auto-increment if single primary key (not composite) and it's in this mapping
            boolean shouldAutoIncrement = isPrimaryKey && !isCompositeKey;
            String columnDef = formatColumnDefinition(columnName, field.getType(), shouldAutoIncrement, dbContext);
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
                    String columnDef = formatColumnDefinition(fk.columnName, Long.class, false, dbContext);
                    columnDefinitions.add(columnDef);
                    existingColumnNames.add(fk.columnName.toLowerCase());
                }
            }
        }
        
        // Add column definitions
        for (int i = 0; i < columnDefinitions.size(); i++) {
            sb.append("  ").append(columnDefinitions.get(i));
            if (i < columnDefinitions.size() - 1 || !primaryKeyColumns.isEmpty()) {
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
        String tableSuffix = dbContext.getTableSuffix();
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
            String columnDef = formatColumnDefinition(columnName, field.getType(), shouldAutoIncrement, dbContext);
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
    
    private static String formatColumnDefinition(String columnName, Class<?> type, boolean autoIncrement, DbContext dbContext) {
        StringBuilder sb = new StringBuilder();
        sb.append(dbContext.quoteObjectNames(columnName));
        sb.append(" ");
        sb.append(dbContext.mapJavaTypeToSql(type));
        
        if (autoIncrement) {
            sb.append(dbContext.getAutoIncrementSyntax());
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
        
        public ColumnDefinition(String name, String sqlType, boolean autoIncrement, boolean isPrimaryKey) {
            this.name = name;
            this.sqlType = sqlType;
            this.autoIncrement = autoIncrement;
            this.isPrimaryKey = isPrimaryKey;
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
        return generateMigrationStatements(schemaInfo, DbContext.DEFAULT, entityClasses);
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
        
        // First pass: scan all classes for collection fields to build the inferred foreign keys map
        for (Class<?> entityClass : entityClasses) {
            scanForInferredForeignKeys(entityClass, inferredForeignKeys);
        }
        
        // Second pass: generate DDL statements
        for (Class<?> entityClass : entityClasses) {
            generateMigrationStatements(entityClass, schemaInfo, dbContext, processedTables, statements, inferredForeignKeys);
        }
        return statements;
    }
    
    private static void generateMigrationStatements(Class<?> entityClass, SchemaInfo schemaInfo, DbContext dbContext,
            Set<String> processedTables, List<String> statements, Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys) {
        // Scan for inferred foreign keys
        scanForInferredForeignKeys(entityClass, inferredForeignKeys);
        
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
                statements.add(generateCreateTableForMapping(mapping, dbContext, fks));
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
                generateMigrationStatements(subClass, schemaInfo, dbContext, processedTables, statements, inferredForeignKeys);
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
                String sqlType = dbContext.mapJavaTypeToSql(idField.getType());
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
                    String sqlType = dbContext.mapJavaTypeToSql(Long.class);
                    columns.add(new ColumnDefinition(columnName, sqlType, false, false));
                    existingColumnNames.add(columnName.toLowerCase());
                }
                continue;
            }
            
            String columnName = QueryBuilder.determineSqlFieldName(field);
            boolean isPrimaryKey = field.getAnnotation(Id.class) != null;
            boolean shouldAutoIncrement = isPrimaryKey && !isCompositeKey;
            String sqlType = dbContext.mapJavaTypeToSql(field.getType());
            
            columns.add(new ColumnDefinition(columnName, sqlType, shouldAutoIncrement, isPrimaryKey));
            existingColumnNames.add(columnName.toLowerCase());
        }
        
        // Add inferred foreign key columns
        if (inferredForeignKeys != null) {
            for (InferredForeignKey fk : inferredForeignKeys) {
                if (!existingColumnNames.contains(fk.columnName.toLowerCase())) {
                    String sqlType = dbContext.mapJavaTypeToSql(Long.class);
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
            String sqlType = dbContext.mapJavaTypeToSql(field.getType());
            
            columns.add(new ColumnDefinition(columnName, sqlType, shouldAutoIncrement, isPrimaryKey));
            existingColumnNames.add(columnName.toLowerCase());
        }
    }
}
