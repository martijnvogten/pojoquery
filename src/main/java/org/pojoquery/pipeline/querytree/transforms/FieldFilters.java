package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.PojoMetadata.collectFieldsOfClass;
import static org.pojoquery.pipeline.PojoMetadata.determineTableMapping;
import static org.pojoquery.pipeline.PojoMetadata.isListOrArray;

import java.lang.annotation.Annotation;
import java.util.List;

import org.pojoquery.annotations.Aggregate;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Other;
import org.pojoquery.annotations.Select;
import org.pojoquery.annotations.Subquery;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.PojoMetadata;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Helper methods for filtering fields by type and annotations.
 * 
 * <p>Methods constrain fields to the most specific table by default for
 * table-per-subclass inheritance. This ensures that fields like entity
 * references only appear on the table where the FK column actually lives.</p>
 */
public final class FieldFilters {
    
    private FieldFilters() {}
    
    /**
     * Collects all non-static, non-transient fields from a type (full inheritance hierarchy).
     */
    public static List<FieldModel> allFields(TypeModel type) {
        return collectFieldsOfClass(type);
    }
    
    /**
     * Returns fields belonging to the most specific table for this type.
     * For table-per-subclass inheritance, this excludes fields from superclass tables.
     */
    public static List<FieldModel> tableFields(TypeModel type) {
        List<TableMapping> mappings = determineTableMapping(type);
        if (mappings.isEmpty()) {
            return allFields(type);
        }
        // Most specific table is the last one
        return mappings.get(mappings.size() - 1).getFields();
    }
    
    /**
     * Collects fields declared in type up to (but not including) stopAt.
     */
    public static List<FieldModel> fieldsDeclaredIn(TypeModel type, TypeModel stopAt) {
        return collectFieldsOfClass(type, stopAt);
    }
    
    /**
     * Returns fields with a specific annotation.
     */
    public static List<FieldModel> withAnnotation(TypeModel type, Class<? extends Annotation> ann) {
        return tableFields(type).stream()
            .filter(f -> f.hasAnnotation(ann))
            .toList();
    }
    
    /**
     * Returns fields that are simple (primitive, wrapper, or common type).
     * Constrained to the most specific table.
     */
    public static List<FieldModel> simpleFields(TypeModel type) {
        return tableFields(type).stream()
            .filter(FieldFilters::isSimple)
            .toList();
    }
    
    /**
     * Returns fields that reference other entities (have @Table on their type).
     * Constrained to the most specific table.
     */
    public static List<FieldModel> entityReferences(TypeModel type) {
        return tableFields(type).stream()
            .filter(f -> isEntityReference(f) && !PojoMetadata.isTransient(f) && !f.getType().isEnum())
            .toList();
    }
    
    /**
     * Returns collection fields (List, Set, array) of entities without @Link.
     * Constrained to the most specific table.
     */
    public static List<FieldModel> simpleCollections(TypeModel type) {
        return tableFields(type).stream()
            .filter(f -> isCollection(f.getType()) 
                && !hasAnnotation(f, Link.class)
                && hasTableOnComponent(f)
                && !PojoMetadata.isTransient(f))
            .toList();
    }
    
    /**
     * Returns fields with @Link annotation that have a linktable (many-to-many).
     */
    public static List<FieldModel> linkTableFields(TypeModel type) {
        return tableFields(type).stream()
            .filter(f -> 
                f.getAnnotation(Link.class)
                .filter(ann -> ann.hasAttribute("linktable") && !ann.hasAttribute("fetchColumn"))
                .isPresent()
            )
            .toList();
    }
    
    /**
     * Returns fields with @Link(fetchColumn) - value collections.
     * Constrained to the most specific table.
     */
    public static List<FieldModel> valueCollectionFields(TypeModel type) {
        return tableFields(type).stream()
            .filter(f -> 
                f.getAnnotation(Link.class)
                .filter(link -> link.hasAttribute("fetchColumn"))
                .isPresent()
            )
            .toList();
    }
    
    /**
     * Returns fields with @Embedded annotation.
     * Constrained to the most specific table.
     */
    public static List<FieldModel> embeddedFields(TypeModel type) {
        return tableFields(type).stream()
            .filter(FieldFilters::isEmbedded)
            .toList();
    }
    
    /**
     * Returns fields with @Select annotation.
     */
    public static List<FieldModel> selectFields(TypeModel type) {
        return withAnnotation(type, Select.class);
    }
    
    /**
     * Returns fields with @Aggregate annotation.
     */
    public static List<FieldModel> aggregateFields(TypeModel type) {
        return withAnnotation(type, Aggregate.class);
    }
    
    /**
     * Returns fields with @Other annotation.
     */
    public static List<FieldModel> otherFields(TypeModel type) {
        return withAnnotation(type, Other.class);
    }
    
    /**
     * Returns fields with @Subquery annotation.
     */
    public static List<FieldModel> subqueryFields(TypeModel type) {
        return withAnnotation(type, Subquery.class);
    }
    
    // ----- Type checks -----
    
    public static boolean isSimple(FieldModel field) {
        if ( PojoMetadata.isTransient(field)) return false;
        TypeModel type = field.getType();
        return type.isPrimitive() || isWrapper(type) || isCommonType(type) || type.isEnum();
    }
    
    public static boolean isEntityReference(FieldModel field) {
        TypeModel type = field.getType();
        return !isSimple(field)
            && !isCollection(type) 
            && !isEmbedded(field);
    }
    
    public static boolean isCollection(TypeModel type) {
        return isListOrArray(type);
    }
    
    public static boolean isEmbedded(FieldModel field) {
        return field.hasAnnotation(Embedded.class);
    }
    
    public static boolean hasAnnotation(FieldModel field, Class<? extends Annotation> ann) {
        return field.hasAnnotation(ann);
    }
    
    public static TypeModel getComponentType(FieldModel field) {
        TypeModel type = field.getType();
        if (type.isArray()) {
            return type.getArrayComponentType();
        }
        return type.getTypeArgument();
    }
    
    private static boolean hasTableOnComponent(FieldModel field) {
        TypeModel component = getComponentType(field);
        return component != null && !component.isPrimitive() && !determineTableMapping(component).isEmpty();
    }
    
    private static boolean isWrapper(TypeModel type) {
        String name = type.getQualifiedName();
        return name.equals("java.lang.Long") ||
               name.equals("java.lang.Integer") ||
               name.equals("java.lang.String") ||
               name.equals("java.lang.Boolean") ||
               name.equals("java.lang.Double") ||
               name.equals("java.lang.Float") ||
               name.equals("java.lang.Short") ||
               name.equals("java.lang.Byte") ||
               name.equals("java.lang.Character");
    }
    
    private static boolean isCommonType(TypeModel type) {
        String name = type.getQualifiedName();
        return name.equals("java.util.Date") ||
               name.equals("java.sql.Date") ||
               name.equals("java.sql.Timestamp") ||
               name.equals("java.time.LocalDate") ||
               name.equals("java.time.LocalDateTime") ||
               name.equals("java.time.LocalTime") ||
               name.equals("java.time.Instant") ||
               name.equals("java.time.ZonedDateTime") ||
               name.equals("java.time.OffsetDateTime") ||
               name.equals("java.math.BigDecimal") ||
               name.equals("java.math.BigInteger") ||
               name.equals("java.util.UUID") ||
               name.equals("[B"); // byte[]
    }
}
