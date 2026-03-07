package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Map;

import org.pojoquery.annotations.Column;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Lob;
import org.pojoquery.annotations.Table;
import org.pojoquery.annotations.Transient;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.transforms.JakartaAnnotationsTransform;

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
 * FieldSelection fs = ...;
 * FieldSelection transformed = JakartaAnnotations.transformFieldSelection(fs);
 * // transformed now has @Id if original had @jakarta.persistence.Id
 * </pre>
 * 
 * @see JakartaAnnotationsTransform for pipeline integration
 */
public final class JakartaAnnotations {

    // Jakarta annotation classes (loaded via reflection to avoid hard dependency)
    private static final Class<? extends Annotation> JAKARTA_TABLE;
    private static final Class<? extends Annotation> JAKARTA_ID;
    private static final Class<? extends Annotation> JAKARTA_COLUMN;
    private static final Class<? extends Annotation> JAKARTA_TRANSIENT;
    private static final Class<? extends Annotation> JAKARTA_EMBEDDED;
    private static final Class<? extends Annotation> JAKARTA_LOB;

    private static final boolean JAKARTA_AVAILABLE;

    static {
        JAKARTA_TABLE = tryLoadAnnotationClass("jakarta.persistence.Table");
        JAKARTA_ID = tryLoadAnnotationClass("jakarta.persistence.Id");
        JAKARTA_COLUMN = tryLoadAnnotationClass("jakarta.persistence.Column");
        JAKARTA_TRANSIENT = tryLoadAnnotationClass("jakarta.persistence.Transient");
        JAKARTA_EMBEDDED = tryLoadAnnotationClass("jakarta.persistence.Embedded");
        JAKARTA_LOB = tryLoadAnnotationClass("jakarta.persistence.Lob");

        JAKARTA_AVAILABLE = JAKARTA_TABLE != null;
    }

    private JakartaAnnotations() {
        // Utility class
    }

    /**
     * Returns true if jakarta.persistence annotations are available on the classpath.
     */
    public static boolean isAvailable() {
        return JAKARTA_AVAILABLE;
    }

    /**
     * Transforms a TypeModel by adding PojoQuery annotations based on jakarta.persistence annotations.
     * 
     * @param type the type model to transform
     * @return a new TypeModel with canonical annotations added, or the original if no jakarta annotations present
     */
    public static TypeModel transformType(TypeModel type) {
        if (!JAKARTA_AVAILABLE || type == null) {
            return type;
        }

        return type.getAnnotation(JAKARTA_TABLE)
            .filter(an -> !type.hasAnnotation(Table.class))
            .map(an -> type.withAddedAnnotation(Table.class, 
                an.getStringValue("name")
                    .filter(name -> !name.isEmpty())
                    .<Map<String, Object>>map(name -> Map.of("value", name))
                    .orElse(Collections.emptyMap())))
            .orElse(type);
    }

    /**
     * Transforms a FieldSelection by adding PojoQuery annotations based on jakarta.persistence annotations.
     * 
     * @param fs the field selection to transform
     * @return a new FieldSelection with canonical annotations added, or the original if no jakarta annotations present
     */
    public static FieldSelection transformFieldSelection(FieldSelection fs) {
        if (!JAKARTA_AVAILABLE || fs == null || fs.field() == null) {
            return fs;
        }

        fs = mapAnnotation(fs, JAKARTA_ID, Id.class);
        fs = mapAnnotation(fs, JAKARTA_TRANSIENT, Transient.class);
        fs = mapAnnotation(fs, JAKARTA_EMBEDDED, Embedded.class);
        fs = mapAnnotation(fs, JAKARTA_LOB, Lob.class);
        fs = mapColumn(fs);

        return fs;
    }

    private static FieldSelection mapAnnotation(FieldSelection fs, Class<? extends Annotation> source, Class<? extends Annotation> target) {
        if (fs.field().hasAnnotation(source) && !fs.field().hasAnnotation(target)) {
            return fs.withFieldAnnotation(target, Collections.emptyMap());
        }
        return fs;
    }

    private static FieldSelection mapColumn(FieldSelection fs) {
        if (fs.field().hasAnnotation(Column.class)) {
            return fs;
        }
        return fs.field().getAnnotation(JAKARTA_COLUMN)
            .map(col -> fs.withFieldAnnotation(Column.class, extractColumnAttributes(col)))
            .orElse(fs);
    }

    /**
     * Extracts column attributes from a jakarta.persistence.Column annotation.
     */
    private static Map<String, Object> extractColumnAttributes(AnnotationModel columnAnnotation) {
        java.util.Map<String, Object> attrs = new java.util.HashMap<>();
        
        columnAnnotation.getStringValue("name")
            .filter(name -> !name.isEmpty())
            .ifPresent(name -> attrs.put("name", name));

        columnAnnotation.getNumberAttribute("length")
            .filter(length -> length.intValue() != 255) // 255 is default
            .ifPresent(length -> attrs.put("length", length.intValue()));

        columnAnnotation.getNumberAttribute("precision")
            .filter(precision -> precision.intValue() != 0)
            .ifPresent(precision -> attrs.put("precision", precision.intValue()));

        columnAnnotation.getNumberAttribute("scale")
            .filter(scale -> scale.intValue() != 0)
            .ifPresent(scale -> attrs.put("scale", scale.intValue()));

        columnAnnotation.getBooleanAttribute("nullable")
            .filter(nullable -> !nullable) // true is default
            .ifPresent(nullable -> attrs.put("nullable", nullable));

        columnAnnotation.getBooleanAttribute("unique")
            .filter(unique -> unique) // false is default
            .ifPresent(unique -> attrs.put("unique", unique));

        return attrs;
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
