package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.pojoquery.annotations.Column;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Lob;
import org.pojoquery.annotations.Table;
import org.pojoquery.annotations.Transient;

/**
 * Utility class that maps jakarta.persistence annotations to PojoQuery canonical annotations.
 * 
 * <p>This allows code using Jakarta Persistence annotations to work with PojoQuery
 * without modification. The transform adds synthetic PojoQuery annotations based on
 * detected jakarta.persistence annotations.
 * 
 * <p>Supported mappings:
 * <ul>
 *   <li>jakarta.persistence.Table → @Table</li>
 *   <li>jakarta.persistence.Id → @Id</li>
 *   <li>jakarta.persistence.Column → @Column</li>
 *   <li>jakarta.persistence.Transient → @Transient</li>
 *   <li>jakarta.persistence.Embedded → @Embedded</li>
 *   <li>jakarta.persistence.Lob → @Lob</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * FieldModel field = ...;
 * FieldModel transformed = JakartaAnnotations.transformField(field);
 * // transformed now has @Id if original had @jakarta.persistence.Id
 * </pre>
 * 
 * @see JakartaAnnotationsTransform for pipeline integration
 */
public final class JakartaAnnotations implements TypeTransformer {

    // Jakarta annotation classes (loaded via reflection to avoid hard dependency)
    private static final Class<? extends Annotation> JAKARTA_TABLE;
    private static final Class<? extends Annotation> JAKARTA_ID;
    private static final Class<? extends Annotation> JAKARTA_COLUMN;
    private static final Class<? extends Annotation> JAKARTA_JOIN_COLUMN;
    private static final Class<? extends Annotation> JAKARTA_TRANSIENT;
    private static final Class<? extends Annotation> JAKARTA_EMBEDDED;
    private static final Class<? extends Annotation> JAKARTA_LOB;

    private static final boolean JAKARTA_AVAILABLE;

    static {
        JAKARTA_TABLE = tryLoadAnnotationClass("jakarta.persistence.Table");
        JAKARTA_ID = tryLoadAnnotationClass("jakarta.persistence.Id");
        JAKARTA_COLUMN = tryLoadAnnotationClass("jakarta.persistence.Column");
        JAKARTA_JOIN_COLUMN = tryLoadAnnotationClass("jakarta.persistence.JoinColumn");
        JAKARTA_TRANSIENT = tryLoadAnnotationClass("jakarta.persistence.Transient");
        JAKARTA_EMBEDDED = tryLoadAnnotationClass("jakarta.persistence.Embedded");
        JAKARTA_LOB = tryLoadAnnotationClass("jakarta.persistence.Lob");

        JAKARTA_AVAILABLE = JAKARTA_TABLE != null;
    }

    /**
     * Returns true if jakarta.persistence annotations are available on the classpath.
     */
    public static boolean isAvailable() {
        return JAKARTA_AVAILABLE;
    }

    // ========== TypeTransformer implementation ==========

    /**
     * Transforms a TypeModel by adding PojoQuery annotations based on jakarta.persistence annotations.
     * 
     * @param type the type model to transform
     * @return a new TypeModel with canonical annotations added, or the original if no jakarta annotations present
     */
    @Override
    public TypeModel transformType(TypeModel type) {
        if (!JAKARTA_AVAILABLE || type == null) {
            return type;
        }

        if (type.hasAnnotation(JAKARTA_TABLE) && !type.hasAnnotation(Table.class)) {
            type = type.withAddedAnnotation(Table.class, extractTableAttributes(type));
        }

        return type;
    }

    private static Map<String, Object> extractTableAttributes(TypeModel type) {
        java.util.Map<String, Object> attrs = new java.util.HashMap<>();
        
        // JPA uses "name", PojoQuery @Table uses "value"
        Optional.ofNullable(type.getAnnotationAttributeValue(JAKARTA_TABLE, "name", String.class))
            .filter(name -> !name.isEmpty())
            .ifPresent(name -> attrs.put("value", name));

        Optional.ofNullable(type.getAnnotationAttributeValue(JAKARTA_TABLE, "schema", String.class))
            .filter(schema -> !schema.isEmpty())
            .ifPresent(schema -> attrs.put("schema", schema));

        return attrs;
    }

    @Override
    public FieldModel transformField(FieldModel fs) {
        if (!JAKARTA_AVAILABLE || fs == null) {
            return fs;
        }

        fs = mapAnnotation(fs, JAKARTA_ID, Id.class);
        fs = mapAnnotation(fs, JAKARTA_TRANSIENT, Transient.class);
        fs = mapAnnotation(fs, JAKARTA_EMBEDDED, Embedded.class);
        fs = mapAnnotation(fs, JAKARTA_LOB, Lob.class);
        if (fs.hasAnnotation(JAKARTA_JOIN_COLUMN) && !fs.hasAnnotation(FieldName.class) &&
                !fs.getAnnotationAttributeValue(JAKARTA_JOIN_COLUMN, "name", String.class).isEmpty()) {
            fs = fs.withAddedAnnotation(FieldName.class, Map.of("value", fs.getAnnotationAttributeValue(JAKARTA_JOIN_COLUMN, "name", String.class)));
        }
        if (fs.hasAnnotation(JAKARTA_COLUMN) && !fs.hasAnnotation(FieldName.class) &&
                !fs.getAnnotationAttributeValue(JAKARTA_COLUMN, "name", String.class).isEmpty()) {
            fs = fs.withAddedAnnotation(FieldName.class, Map.of("value", fs.getAnnotationAttributeValue(JAKARTA_COLUMN, "name", String.class)));
        }
        if (fs.hasAnnotation(JAKARTA_COLUMN) && !fs.hasAnnotation(Column.class)) {
            fs = fs.withAddedAnnotation(Column.class, extractColumnAttributes(fs));
        }
        return fs;
    }

    private static <T extends AnnotatedElementModel<T>> T mapAnnotation(T f, Class<? extends Annotation> source, Class<? extends Annotation> target) {
        if (f.hasAnnotation(source) && !f.hasAnnotation(target)) {
            return (T) f.withAddedAnnotation(target, Collections.emptyMap());
        }
        return f;
    }

    /**
     * Extracts column attributes from a jakarta.persistence.Column annotation.
     */
    private static Map<String, Object> extractColumnAttributes(FieldModel fieldModel) {
        java.util.Map<String, Object> attrs = new java.util.HashMap<>();
        
        getColumnAttribute(fieldModel, "name", String.class)
            .filter(name -> !name.isEmpty())
            .ifPresent(name -> attrs.put("name", name));

        getColumnAttribute(fieldModel, "length", Integer.class)
            .ifPresent(length -> attrs.put("length", length.intValue()));
        getColumnAttribute(fieldModel, "precision", Integer.class)
            .ifPresent(precision -> attrs.put("precision", precision.intValue()));
        getColumnAttribute(fieldModel, "scale", Integer.class)
            .ifPresent(scale -> attrs.put("scale", scale.intValue()));

        getColumnAttribute(fieldModel, "nullable", Boolean.class)
            .filter(nullable -> !nullable) // true is default
            .ifPresent(nullable -> attrs.put("nullable", nullable));

        getColumnAttribute(fieldModel, "unique", Boolean.class)
            .filter(unique -> unique) // false is default
            .ifPresent(unique -> attrs.put("unique", unique));

        return attrs;
    }

    private static <T> Optional<T> getColumnAttribute(FieldModel fieldModel, String attributeName, Class<T> expectedType) {
        return Optional.ofNullable(fieldModel.getAnnotationAttributeValue(JAKARTA_COLUMN, attributeName, expectedType));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> tryLoadAnnotationClass(String className) {
        try {
            return (Class<? extends Annotation>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
