package org.pojoquery.schema;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.annotations.Link;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.CustomizableQueryBuilder;
import org.pojoquery.pipeline.QueryBuilder;
import org.pojoquery.schema.ForeignKeyInfo.InferredForeignKey;
import org.pojoquery.schema.ForeignKeyInfo.LinkTableInfo;
import org.pojoquery.util.Types;

/**
 * Scans entity classes to infer foreign key relationships based on field annotations.
 * This includes one-to-many relationships (FK in child table), many-to-many link tables,
 * and explicit @Link annotations.
 */
class ForeignKeyScanner {
    
    /**
     * Scans an entity class for fields that imply foreign keys:
     * 1. Collection fields (one-to-many) - FK goes in the referenced entity's table
     * 2. Single entity references with @Link(linkfield=...) - FK goes in the declaring class's table
     * 3. Collection fields with linktable - generates link table with both FKs
     */
    static void scanForInferredForeignKeys(Class<?> entityClass, 
            Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys,
            List<LinkTableInfo> linkTables) {
        // Scan collection fields for one-to-many relationships and many-to-many link tables
        scanCollectionFields(entityClass, inferredForeignKeys, linkTables);
        
        // Scan single entity reference fields with @Link(linkfield=...)
        scanSingleEntityFields(entityClass, inferredForeignKeys);
    }
    
    /**
     * Scans collection fields that imply foreign keys in the referenced entity,
     * or link tables for many-to-many relationships.
     */
    private static void scanCollectionFields(Class<?> entityClass, 
            Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys, 
            List<LinkTableInfo> linkTables) {
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
                    Class<?> componentType = Types.getCollectionComponentType(field);
                    if (componentType != null && CustomizableQueryBuilder.isLinkedClass(componentType)) {
                        LinkTableInfo linkTableInfo = createLinkTableInfo(ownerMapping, ownerIdColumn, field, linkAnn, componentType);
                        if (linkTableInfo != null) {
                            linkTables.add(linkTableInfo);
                        }
                    }
                    continue;
                }
                
                // One-to-many - Get the component type of the collection
                Class<?> componentType = Types.getCollectionComponentType(field);
                if (componentType != null && CustomizableQueryBuilder.isLinkedClass(componentType)) {
                    // Check if the component type already has a back-reference field to the owner
                    // If so, skip inferring a FK - the existing field will create the FK column
                    if (hasBackReferenceField(componentType, entityClass)) {
                        continue;
                    }
                    
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
     * Checks if the target class has a field that references back to the owner class or any of its superclasses.
     * This is used to detect existing back-references so we don't infer duplicate FK columns.
     * 
     * @param targetClass the class to check for back-reference fields (e.g., LineItem)
     * @param ownerClass the owner class that has a collection of targetClass (e.g., CustomerOrderWithLineItems)
     * @return true if targetClass has a field referencing ownerClass or one of its superclasses
     */
    private static boolean hasBackReferenceField(Class<?> targetClass, Class<?> ownerClass) {
        Collection<Field> targetFields = QueryBuilder.filterFields(targetClass);
        
        // Build a set of all classes in the owner's hierarchy that share the same table
        List<TableMapping> ownerMappings = QueryBuilder.determineTableMapping(ownerClass);
        if (ownerMappings.isEmpty()) {
            return false;
        }
        String ownerTableName = ownerMappings.get(0).tableName;
        
        for (Field field : targetFields) {
            // Skip collections - we're looking for single entity references
            if (CustomizableQueryBuilder.isListOrArray(field.getType())) {
                continue;
            }
            
            Class<?> fieldType = field.getType();
            if (CustomizableQueryBuilder.isLinkedClass(fieldType)) {
                // Check if this field's type maps to the same table as the owner
                List<TableMapping> fieldTypeMappings = QueryBuilder.determineTableMapping(fieldType);
                if (!fieldTypeMappings.isEmpty()) {
                    String fieldTypeTableName = fieldTypeMappings.get(0).tableName;
                    if (ownerTableName.equals(fieldTypeTableName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Creates a LinkTableInfo for a many-to-many relationship.
     */
    private static LinkTableInfo createLinkTableInfo(TableMapping ownerMapping, String ownerIdColumn,
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
     * Scans single entity reference fields with @Link(linkfield=...) that imply foreign keys
     * in the declaring class's table (or parent table for subclasses).
     */
    private static void scanSingleEntityFields(Class<?> entityClass, 
            Map<Class<?>, List<InferredForeignKey>> inferredForeignKeys) {
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
     * Finds the class that has the root @Table annotation for the given class.
     * Walks up the inheritance hierarchy to find the first class with @Table.
     */
    static Class<?> findRootTableClass(Class<?> clazz) {
        Class<?> rootTableClass = null;
        Class<?> current = clazz;
        while (current != null) {
            if (AnnotationHelper.hasTableAnnotation(current)) {
                rootTableClass = current;
            }
            current = current.getSuperclass();
        }
        return rootTableClass;
    }
    
    /**
     * Finds the class that defines the @Table annotation for the given class.
     * For subclasses without their own @Table, returns the parent class with @Table.
     */
    static Class<?> findTableClass(Class<?> clazz) {
        List<TableMapping> mappings = QueryBuilder.determineTableMapping(clazz);
        if (!mappings.isEmpty()) {
            return mappings.get(0).getReflectionClass();
        }
        return null;
    }
    
    /**
     * Determines the foreign key column name for a collection field.
     * Uses the owning class's table name + "_id" by default, unless @Link specifies foreignlinkfield.
     */
    static String determineForeignKeyColumnNameForCollection(Class<?> owningClass, Field field) {
        Link linkAnn = field.getAnnotation(Link.class);
        if (linkAnn != null && !Link.NONE.equals(linkAnn.foreignlinkfield())) {
            return linkAnn.foreignlinkfield();
        }
        // Default: owning table name + "_id"
        TableMapping ownerMapping = QueryBuilder.determineTableMapping(owningClass).get(0);
        return ownerMapping.tableName + "_id";
    }
}
