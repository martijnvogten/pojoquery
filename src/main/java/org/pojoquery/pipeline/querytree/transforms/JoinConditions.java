package org.pojoquery.pipeline.querytree.transforms;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Link;
import org.pojoquery.pipeline.querytree.JoinCondition;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

import static org.pojoquery.pipeline.PojoMetadata.determineSqlFieldName;
import static org.pojoquery.pipeline.PojoMetadata.determineIdField;

/**
 * Helper methods for building SQL join conditions.
 */
public final class JoinConditions {
    
    private JoinConditions() {}
    
    // ========== Structured JoinCondition factory methods ==========
    
    /**
     * Creates a structured join condition for an entity reference (many-to-one).
     * FK column is in the parent table, pointing to child's ID.
     * 
     * @param field The field creating the join
     * @param targetType The type being joined
     * @return A ForeignKeyInParent condition
     */
    public static JoinCondition.ForeignKeyInParent forEntityReferenceStructured(FieldModel field, TypeModel targetType) {
        String fkColumn = determineFkColumn(field);
        String targetId = determineSqlFieldName(determineIdField(targetType));
        return new JoinCondition.ForeignKeyInParent(fkColumn, targetId);
    }
    
    /**
     * Creates a structured join condition for a one-to-many collection.
     * FK column is in the child table, pointing to parent's ID.
     * 
     * @param parentType The parent type
     * @param parentTableName The parent table name (for FK naming)
     * @param foreignLinkField Custom FK column name in child table, or null for default
     * @return A ForeignKeyInChild condition
     */
    public static JoinCondition.ForeignKeyInChild forCollectionStructured(TypeModel parentType, String parentTableName, String foreignLinkField) {
        String parentId = determineSqlFieldName(determineIdField(parentType));
        String fkColumn = (foreignLinkField != null && !foreignLinkField.isEmpty()) 
            ? foreignLinkField 
            : parentTableName + "_id";
        return new JoinCondition.ForeignKeyInChild(fkColumn, parentId);
    }
    
    /**
     * Creates a structured join condition for inheritance (shared primary key).
     * 
     * @param idField The ID field name
     * @return A SharedPrimaryKey condition
     */
    public static JoinCondition.SharedPrimaryKey forInheritanceStructured(String idField) {
        return new JoinCondition.SharedPrimaryKey(idField, idField);
    }
    
    // ========== Legacy SqlExpression factory methods (for backward compatibility) ==========
    
    /**
     * Creates a join condition for an entity reference (one-to-one).
     * Pattern: {parent.field_id} = {child.id}
     * 
     * @param parentAlias The parent table alias
     * @param childAlias The child (joined) table alias
     * @param field The field creating the join
     * @param targetType The type being joined
     * @return The join condition
     * @deprecated Use {@link #forEntityReferenceStructured} and {@link JoinCondition#toSqlExpression}
     */
    @Deprecated
    public static SqlExpression forEntityReference(String parentAlias, String childAlias, 
                                                    FieldModel field, TypeModel targetType) {
        return forEntityReferenceStructured(field, targetType).toSqlExpression(parentAlias, childAlias);
    }
    
    /**
     * Creates a join condition for a one-to-many collection.
     * Pattern: {parent.id} = {child.parent_id}
     * 
     * @param parentAlias The parent table alias
     * @param childAlias The child (joined) table alias
     * @param parentType The parent type
     * @param parentTableName The parent table name (for FK naming)
     * @return The join condition
     * @deprecated Use {@link #forCollectionStructured} and {@link JoinCondition#toSqlExpression}
     */
    @Deprecated
    public static SqlExpression forCollection(String parentAlias, String childAlias,
                                               TypeModel parentType, String parentTableName) {
        return forCollection(parentAlias, childAlias, parentType, parentTableName, null);
    }
    
    /**
     * Creates a join condition for a one-to-many collection with custom FK column.
     * Pattern: {parent.id} = {child.foreignlinkfield}
     * 
     * @param parentAlias The parent table alias
     * @param childAlias The child (joined) table alias
     * @param parentType The parent type
     * @param parentTableName The parent table name (for default FK naming)
     * @param foreignLinkField Custom FK column name in child table, or null for default
     * @return The join condition
     * @deprecated Use {@link #forCollectionStructured} and {@link JoinCondition#toSqlExpression}
     */
    @Deprecated
    public static SqlExpression forCollection(String parentAlias, String childAlias,
                                               TypeModel parentType, String parentTableName,
                                               String foreignLinkField) {
        return forCollectionStructured(parentType, parentTableName, foreignLinkField)
            .toSqlExpression(parentAlias, childAlias);
    }
    
    /**
     * Creates a join condition for inheritance (parent-child on shared ID).
     * Pattern: {parent.id} = {child.id}
     * 
     * @param parentAlias The parent table alias
     * @param childAlias The child table alias
     * @param idField The ID field name
     * @return The join condition
     * @deprecated Use {@link #forInheritanceStructured} and {@link JoinCondition#toSqlExpression}
     */
    @Deprecated
    public static SqlExpression forInheritance(String parentAlias, String childAlias, String idField) {
        return forInheritanceStructured(idField).toSqlExpression(parentAlias, childAlias);
    }
    
    /**
     * Creates a join condition for @SubClasses expansion (subclass LEFT JOINed to parent).
     * Pattern: {subclass.id} = {parent.id}
     * 
     * @param parentAlias The parent (base class) table alias
     * @param subclassAlias The subclass table alias
     * @param idField The ID field name
     * @return The join condition
     * @deprecated Use {@link #forInheritanceStructured} and {@link JoinCondition#toSqlExpression}
     */
    @Deprecated
    public static SqlExpression forSubclass(String parentAlias, String subclassAlias, String idField) {
        // Note: for subclass, the join is subclass.id = parent.id 
        // which is the reverse direction in the SQL, but same columns
        return new SqlExpression(
            "{" + subclassAlias + "." + idField + "} = {" + parentAlias + "." + idField + "}"
        );
    }
    
    /**
     * Creates a join condition for superclass tables (superclass INNER JOINed to child).
     * Pattern: {superclass.id} = {child.id}
     * 
     * @param childAlias The child (subclass) table alias  
     * @param superclassAlias The superclass table alias
     * @param idField The ID field name
     * @return The join condition
     * @deprecated Use {@link #forInheritanceStructured} and {@link JoinCondition#toSqlExpression}
     */
    @Deprecated
    public static SqlExpression forSuperclass(String childAlias, String superclassAlias, String idField) {
        return new SqlExpression(
            "{" + superclassAlias + "." + idField + "} = {" + childAlias + "." + idField + "}"
        );
    }
    
    /**
     * Creates the first join condition for a many-to-many (parent to link table).
     * Pattern: {parent.id} = {linkTable.parent_id}
     * 
     * @param parentAlias The parent table alias
     * @param linkTableAlias The link table alias
     * @param parentIdField The parent's ID field name
     * @param linkField The FK column in the link table pointing to parent
     * @return The join condition
     * @deprecated JoinTableInfo now holds column names directly
     */
    @Deprecated
    public static SqlExpression forLinkTableParent(String parentAlias, String linkTableAlias,
                                                    String parentIdField, String linkField) {
        return new SqlExpression(
            "{" + parentAlias + "." + parentIdField + "} = {" + linkTableAlias + "." + linkField + "}"
        );
    }
    
    /**
     * Creates the second join condition for a many-to-many (link table to target).
     * Pattern: {linkTable.target_id} = {target.id}
     * 
     * @param linkTableAlias The link table alias
     * @param targetAlias The target table alias
     * @param foreignLinkField The FK column in the link table pointing to target
     * @param targetIdField The target's ID field name
     * @return The join condition
     * @deprecated JoinTableInfo now holds column names directly
     */
    @Deprecated
    public static SqlExpression forLinkTableTarget(String linkTableAlias, String targetAlias,
                                                    String foreignLinkField, String targetIdField) {
        return new SqlExpression(
            "{" + linkTableAlias + "." + foreignLinkField + "} = {" + targetAlias + "." + targetIdField + "}"
        );
    }
    
    /**
     * Determines the FK column name for a field.
     * Checks @Link(linkfield) first, then @FieldName, then defaults to fieldName_id.
     */
    public static String determineFkColumn(FieldModel field) {
        // Check for @Link(linkfield) first - this explicitly specifies the FK column name
        var linkfieldValue = field.getAnnotation(Link.class)
            .flatMap(ann -> ann.getStringValue("linkfield"))
            .filter(s -> !s.isEmpty());
        if (linkfieldValue.isPresent()) {
            return linkfieldValue.get();
        }
        
        // Check for @FieldName - this specifies the FK column name
        return field.getAnnotation(FieldName.class)
            .flatMap(ann -> ann.getStringValue())
            .orElse(field.getName() + "_id");
    }
}
