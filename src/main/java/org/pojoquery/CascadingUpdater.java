package org.pojoquery;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.pojoquery.annotations.Cascade;
import org.pojoquery.annotations.CascadeType;
import org.pojoquery.annotations.Link;
import org.pojoquery.internal.MappingException;
import org.pojoquery.pipeline.CustomizableQueryBuilder;
import org.pojoquery.pipeline.QueryBuilder;

/**
 * Provides cascading update functionality for entities with related collections.
 * 
 * <p>When an entity has collection fields annotated with {@link Cascade}, this class
 * automatically handles inserting, updating, and deleting related entities based on
 * the state of the collections.</p>
 * 
 * <h2>How Cascading Works</h2>
 * <p>When {@link #update(Connection, Object)} is called:</p>
 * <ol>
 *   <li>The main entity is updated first</li>
 *   <li>For each {@code @Cascade} annotated collection:
 *     <ul>
 *       <li>Items with null ID are inserted (new items)</li>
 *       <li>Items with existing ID are updated</li>
 *       <li>Items in database but not in collection are deleted (orphan removal)</li>
 *     </ul>
 *   </li>
 * </ol>
 * 
 * <h2>Example</h2>
 * <pre>{@code
 * @Table("orders")
 * public class Order {
 *     @Id Long id;
 *     String orderNumber;
 *     
 *     @Cascade(CascadeType.ALL)
 *     List<LineItem> lineItems;
 * }
 * 
 * // Modify an order
 * Order order = PojoQuery.build(Order.class).findById(connection, orderId);
 * order.lineItems.add(new LineItem("New Product", 5));
 * order.lineItems.get(0).quantity = 10;  // Update existing
 * order.lineItems.remove(1);  // This will be deleted
 * 
 * // Apply all changes in one call
 * CascadingUpdater.update(connection, order);
 * }</pre>
 * 
 * <h2>Foreign Key Convention</h2>
 * <p>By default, the foreign key field in child entities is assumed to be named
 * {@code <parentTable>_id} (e.g., {@code order_id} for a parent table {@code orders}).
 * This can be customized using the {@link Link} annotation.</p>
 * 
 * @see Cascade
 * @see CascadeType
 * @see PojoQuery#update(Connection, Object)
 */
final class CascadingUpdater {
    
    private CascadingUpdater() {
        // Utility class, no instantiation
    }
    
    /**
     * Updates an entity and cascades operations to related collections.
     * 
     * @param context the database context
     * @param connection the database connection
     * @param entity the entity to update
     * @return the number of rows affected for the main entity
     * @throws MappingException if there's a mapping error
     */
    static int update(DbContext context, Connection connection, Object entity) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(entity, "entity must not be null");
        
        // First, update the main entity
        int affectedRows = PojoQuery.update(context, connection, entity);
        
        // Then process cascaded collections
        processCascadedCollections(context, connection, entity, CascadeOperation.MERGE);
        
        return affectedRows;
    }
    
    /**
     * Inserts an entity and cascades insert operations to related collections.
     * 
     * <p>Uses the default {@link DbContext}.</p>
     * 
     * @param <PK> the type of the primary key
     * @param connection the database connection
     * @param entity the entity to insert
     * @return the generated primary key
     * @throws MappingException if there's a mapping error
     */
    static <PK> PK insert(Connection connection, Object entity) {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(entity, "entity must not be null");
        return insert(DbContext.getDefault(), connection, entity);
    }
    
    /**
     * Inserts an entity and cascades insert operations to related collections.
     * 
     * @param <PK> the type of the primary key
     * @param context the database context
     * @param connection the database connection
     * @param entity the entity to insert
     * @return the generated primary key
     * @throws MappingException if there's a mapping error
     */
    static <PK> PK insert(DbContext context, Connection connection, Object entity) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(entity, "entity must not be null");
        
        // First, insert the main entity
        PK pk = PojoQuery.insert(context, connection, entity);
        
        // Then process cascaded collections
        processCascadedCollections(context, connection, entity, CascadeOperation.PERSIST);
        
        return pk;
    }

    /**
     * Deletes an entity and cascades delete operations to related collections.
     * 
     * @param context the database context
     * @param connection the database connection
     * @param entity the entity to delete
     * @throws MappingException if there's a mapping error
     */
    static void delete(DbContext context, Connection connection, Object entity) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(entity, "entity must not be null");
        
        // First, delete cascaded collections (must be done before parent)
        processCascadedCollections(context, connection, entity, CascadeOperation.REMOVE);
        
        // Then delete the main entity
        PojoQuery.delete(context, connection, entity);
    }
    
    private enum CascadeOperation {
        PERSIST, MERGE, REMOVE
    }
    
    private static void processCascadedCollections(DbContext context, Connection connection, 
            Object entity, CascadeOperation operation) {
        
        Class<?> entityClass = entity.getClass();
        Object parentId = getEntityId(entity);
        
        if (parentId == null && operation != CascadeOperation.PERSIST) {
            throw new MappingException("Cannot cascade operations for entity without ID: " + entityClass.getName());
        }
        
        for (Field field : QueryBuilder.collectFieldsOfClass(entityClass)) {
            Cascade cascadeAnn = field.getAnnotation(Cascade.class);
            if (cascadeAnn == null) {
                continue;
            }
            
            Set<CascadeType> cascadeTypes = new HashSet<>(Arrays.asList(cascadeAnn.value()));
            boolean shouldCascade = cascadeTypes.contains(CascadeType.ALL) || 
                    (operation == CascadeOperation.PERSIST && cascadeTypes.contains(CascadeType.PERSIST)) ||
                    (operation == CascadeOperation.MERGE && cascadeTypes.contains(CascadeType.MERGE)) ||
                    (operation == CascadeOperation.REMOVE && cascadeTypes.contains(CascadeType.REMOVE));
            
            if (!shouldCascade) {
                continue;
            }
            
            if (!Collection.class.isAssignableFrom(field.getType())) {
                // For now, only support collections. Single entity references could be added later.
                continue;
            }
            
            Class<?> componentType = getCollectionComponentType(field);
            if (componentType == null || !CustomizableQueryBuilder.isLinkedClass(componentType)) {
                continue;
            }
            
            field.setAccessible(true);
            Collection<?> collection;
            try {
                collection = (Collection<?>) field.get(entity);
            } catch (IllegalAccessException e) {
                throw new MappingException("Cannot access field " + field.getName(), e);
            }
            
            switch (operation) {
                case PERSIST:
                    cascadeInsert(context, connection, collection, entity, entityClass, componentType, field);
                    break;
                case MERGE:
                    cascadeMerge(context, connection, collection, entity, entityClass, componentType, field);
                    break;
                case REMOVE:
                    cascadeDelete(context, connection, parentId, entityClass, componentType, field);
                    break;
            }
        }
    }
    
    private static void cascadeInsert(DbContext context, Connection connection, 
            Collection<?> collection, Object parentEntity, Class<?> parentClass,
            Class<?> componentType, Field field) {
        
        if (collection == null || collection.isEmpty()) {
            return;
        }
        
        for (Object item : collection) {
            if (item == null) continue;
            
            // Set the foreign key to parent
            setForeignKey(item, parentEntity, parentClass, field);
            
            // Insert the child
            PojoQuery.insertInternal(context, connection, item.getClass(), item);
        }
    }
    
    private static void cascadeMerge(DbContext context, Connection connection, 
            Collection<?> collection, Object parentEntity, Class<?> parentClass,
            Class<?> componentType, Field field) {
        
        Object parentId = getEntityId(parentEntity);
        ForeignKeyInfo fkInfo = findForeignKeyInfo(componentType, parentClass, field);
        
        // Get existing IDs from database
        Set<Object> existingIds = getExistingChildIds(context, connection, componentType, 
                fkInfo.sqlColumnName, parentId);
        Set<Object> processedIds = new HashSet<>();
        
        // Process items in the collection
        if (collection != null) {
            for (Object item : collection) {
                if (item == null) continue;
                
                Object itemId = getEntityId(item);
                
                // Set the foreign key to parent
                setForeignKey(item, parentEntity, parentClass, field);
                
                if (itemId == null || isZeroId(itemId)) {
                    // New item - insert
                    PojoQuery.insert(context, connection, item);
                } else {
                    // Existing item - update
                    PojoQuery.update(context, connection, item);
                    processedIds.add(itemId);
                }
            }
        }
        
        // Delete orphaned items (in DB but not in collection)
        for (Object existingId : existingIds) {
            if (!processedIds.contains(existingId)) {
                deleteChildById(context, connection, componentType, existingId);
            }
        }
    }
    
    private static void cascadeDelete(DbContext context, Connection connection, 
            Object parentId, Class<?> parentClass, Class<?> componentType, Field field) {
        
        ForeignKeyInfo fkInfo = findForeignKeyInfo(componentType, parentClass, field);
        
        // Delete all children with this parent ID
        deleteChildrenByParentId(context, connection, componentType, fkInfo.sqlColumnName, parentId);
    }
    
    private static Object getEntityId(Object entity) {
        List<Field> idFields = QueryBuilder.determineIdFields(entity.getClass());
        if (idFields.isEmpty()) {
            return null;
        }
        
        Field idField = idFields.get(0);
        idField.setAccessible(true);
        try {
            return idField.get(entity);
        } catch (IllegalAccessException e) {
            throw new MappingException("Cannot access ID field", e);
        }
    }
    
    private static boolean isZeroId(Object id) {
        if (id instanceof Number) {
            return ((Number) id).longValue() == 0L;
        }
        return false;
    }
    
    private static Class<?> getCollectionComponentType(Field field) {
        java.lang.reflect.Type genericType = field.getGenericType();
        if (genericType instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) genericType;
            java.lang.reflect.Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                return (Class<?>) typeArgs[0];
            }
        }
        return null;
    }
    
    /**
     * Information about a foreign key relationship from child to parent.
     */
    private static class ForeignKeyInfo {
        final Field field;           // The field on the child class
        final String sqlColumnName;  // The SQL column name (e.g., "order_id")
        final boolean isEntityRef;   // True if field is an entity reference, false if direct ID
        
        ForeignKeyInfo(Field field, String sqlColumnName, boolean isEntityRef) {
            this.field = field;
            this.sqlColumnName = sqlColumnName;
            this.isEntityRef = isEntityRef;
        }
    }
    
    private static ForeignKeyInfo findForeignKeyInfo(Class<?> childClass, Class<?> parentClass, Field collectionField) {
        // Check for @Link annotation on the collection field
        Link linkAnn = collectionField.getAnnotation(Link.class);
        if (linkAnn != null && !Link.NONE.equals(linkAnn.foreignlinkfield())) {
            String fkFieldName = linkAnn.foreignlinkfield();
            Field fkField = findField(childClass, fkFieldName);
            if (fkField != null) {
                return new ForeignKeyInfo(fkField, fkFieldName, false);
            }
        }
        
        // Look for entity reference field that points to parent class
        for (Field f : QueryBuilder.collectFieldsOfClass(childClass)) {
            if (f.getType().isAssignableFrom(parentClass) || parentClass.isAssignableFrom(f.getType())) {
                // This is an entity reference to the parent
                String sqlName = CustomizableQueryBuilder.determineSqlFieldName(f);
                return new ForeignKeyInfo(f, sqlName, true);
            }
        }
        
        // Look for direct ID field with convention: parentTableName_id
        String tableName = AnnotationHelper.getTableName(parentClass);
        if (tableName == null) {
            tableName = parentClass.getSimpleName().toLowerCase();
        }
        String expectedFieldName = tableName + "_id";
        
        Field fkField = findField(childClass, expectedFieldName);
        if (fkField != null) {
            return new ForeignKeyInfo(fkField, expectedFieldName, false);
        }
        
        // Also try with @FieldName annotation
        for (Field f : QueryBuilder.collectFieldsOfClass(childClass)) {
            String sqlName = CustomizableQueryBuilder.determineSqlFieldName(f);
            if (expectedFieldName.equals(sqlName) || (tableName + "_id").equals(sqlName)) {
                return new ForeignKeyInfo(f, sqlName, Number.class.isAssignableFrom(f.getType()) || f.getType().isPrimitive());
            }
        }
        
        // Fallback: return info with default convention (field may not exist on child class)
        return new ForeignKeyInfo(null, expectedFieldName, false);
    }
    
    private static void setForeignKey(Object item, Object parentEntity, Class<?> parentClass, Field collectionField) {
        Object parentId = getEntityId(parentEntity);
        if (parentId == null) {
            return;
        }
        
        Class<?> itemClass = item.getClass();
        ForeignKeyInfo fkInfo = findForeignKeyInfo(itemClass, parentClass, collectionField);
        
        if (fkInfo != null) {
            fkInfo.field.setAccessible(true);
            try {
                if (fkInfo.isEntityRef) {
                    // Set the parent entity reference
                    fkInfo.field.set(item, parentEntity);
                } else {
                    // Set the parent ID directly
                    fkInfo.field.set(item, parentId);
                }
            } catch (IllegalAccessException e) {
                throw new MappingException("Cannot set foreign key field " + fkInfo.field.getName(), e);
            }
        }
    }
    
    private static Field findField(Class<?> clazz, String fieldName) {
        for (Field f : QueryBuilder.collectFieldsOfClass(clazz)) {
            if (f.getName().equals(fieldName)) {
                return f;
            }
        }
        return null;
    }
    
    private static Set<Object> getExistingChildIds(DbContext context, Connection connection, 
            Class<?> componentType, String foreignKeyField, Object parentId) {
        
        Set<Object> ids = new HashSet<>();
        
        String tableName = AnnotationHelper.getTableName(componentType);
        Field idField = QueryBuilder.determineIdField(componentType);
        String idFieldName = CustomizableQueryBuilder.determineSqlFieldName(idField);
        
        // Build query: SELECT id FROM child_table WHERE parent_id = ?
        String sql = "SELECT " + context.quoteObjectNames(idFieldName) + 
                     " FROM " + context.quoteObjectNames(tableName) + 
                     " WHERE " + context.quoteObjectNames(foreignKeyField) + " = ?";
        
        List<java.util.Map<String, Object>> results = DB.queryRows(connection, new SqlExpression(sql, Arrays.asList(parentId)));
        for (java.util.Map<String, Object> row : results) {
            Object idValue = row.get(idFieldName);
            if (idValue != null) {
                ids.add(idValue);
            }
        }
        
        return ids;
    }
    
    private static void deleteChildById(DbContext context, Connection connection, 
            Class<?> componentType, Object id) {
        
        String tableName = AnnotationHelper.getTableName(componentType);
        Field idField = QueryBuilder.determineIdField(componentType);
        String idFieldName = CustomizableQueryBuilder.determineSqlFieldName(idField);
        
        String sql = "DELETE FROM " + context.quoteObjectNames(tableName) + 
                     " WHERE " + context.quoteObjectNames(idFieldName) + " = ?";
        
        DB.update(connection, new SqlExpression(sql, Arrays.asList(id)));
    }
    
    private static void deleteChildrenByParentId(DbContext context, Connection connection, 
            Class<?> componentType, String foreignKeyField, Object parentId) {
        
        String tableName = AnnotationHelper.getTableName(componentType);
        
        String sql = "DELETE FROM " + context.quoteObjectNames(tableName) + 
                     " WHERE " + context.quoteObjectNames(foreignKeyField) + " = ?";
        
        DB.update(connection, new SqlExpression(sql, Arrays.asList(parentId)));
    }
}
