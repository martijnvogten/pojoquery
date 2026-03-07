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
import org.pojoquery.pipeline.querytree.transforms.JavaxAnnotationsTransform;

/**
 * Utility class that maps javax.persistence annotations to PojoQuery canonical annotations.
 * 
 * <p>This allows code using legacy JPA annotations to work with PojoQuery
 * without modification. The transform adds synthetic PojoQuery annotations based on
 * detected javax.persistence annotations.
 * 
 * <p>Supported mappings:
 * <ul>
 *   <li>javax.persistence.Table → @Table</li>
 *   <li>javax.persistence.Id → @Id</li>
 *   <li>javax.persistence.Column → @Column</li>
 *   <li>javax.persistence.Transient → @Transient</li>
 *   <li>javax.persistence.Embedded → @Embedded</li>
 *   <li>javax.persistence.Lob → @Lob</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * FieldSelection fs = ...;
 * FieldSelection transformed = JavaxAnnotations.transformFieldSelection(fs);
 * // transformed now has @Id if original had @javax.persistence.Id
 * </pre>
 * 
 * @see JavaxAnnotationsTransform for pipeline integration
 */
public final class JavaxAnnotations {

    // Javax annotation classes (loaded via reflection to avoid hard dependency)
    private static final Class<? extends Annotation> JAVAX_TABLE;
    private static final Class<? extends Annotation> JAVAX_ID;
    private static final Class<? extends Annotation> JAVAX_COLUMN;
    private static final Class<? extends Annotation> JAVAX_TRANSIENT;
    private static final Class<? extends Annotation> JAVAX_EMBEDDED;
    private static final Class<? extends Annotation> JAVAX_LOB;

    private static final boolean JAVAX_AVAILABLE;

    static {
        JAVAX_TABLE = tryLoadAnnotationClass("javax.persistence.Table");
        JAVAX_ID = tryLoadAnnotationClass("javax.persistence.Id");
        JAVAX_COLUMN = tryLoadAnnotationClass("javax.persistence.Column");
        JAVAX_TRANSIENT = tryLoadAnnotationClass("javax.persistence.Transient");
        JAVAX_EMBEDDED = tryLoadAnnotationClass("javax.persistence.Embedded");
        JAVAX_LOB = tryLoadAnnotationClass("javax.persistence.Lob");

        JAVAX_AVAILABLE = JAVAX_TABLE != null;
    }

    private JavaxAnnotations() {
        // Utility class
    }

    /**
     * Returns true if javax.persistence annotations are available on the classpath.
     */
    public static boolean isAvailable() {
        return JAVAX_AVAILABLE;
    }

    /**
     * Transforms a TypeModel by adding PojoQuery annotations based on javax.persistence annotations.
     * 
     * @param type the type model to transform
     * @return a new TypeModel with canonical annotations added, or the original if no javax annotations present
     */
    public static TypeModel transformType(TypeModel type) {
        if (!JAVAX_AVAILABLE) {
            return type;
        }

		if (type.hasAnnotation(Table.class)) {
			return type;
		}

        return type.getAnnotation(JAVAX_TABLE)
            .flatMap(an -> an.getStringValue("name"))
            .map(name -> type.withAddedAnnotation(Table.class, Map.of("value", name)))
            .orElse(type);
    }

    /**
     * Transforms a FieldSelection by adding PojoQuery annotations based on javax.persistence annotations.
     * 
     * @param fieldSelection the field selection to transform
     * @return a new FieldSelection with canonical annotations added, or the original if no javax annotations present
     */
    public static FieldSelection transformFieldSelection(FieldSelection fs) {
        if (!JAVAX_AVAILABLE || fs == null || fs.field() == null) {
            return fs;
        }

        fs = mapAnnotation(fs, JAVAX_ID, Id.class);
        fs = mapAnnotation(fs, JAVAX_TRANSIENT, Transient.class);
        fs = mapAnnotation(fs, JAVAX_EMBEDDED, Embedded.class);
        fs = mapAnnotation(fs, JAVAX_LOB, Lob.class);
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
        return fs.field().getAnnotation(JAVAX_COLUMN)
            .map(col -> fs.withFieldAnnotation(Column.class, extractColumnAttributes(col)))
            .orElse(fs);
    }

    /**
     * Extracts column attributes from a javax.persistence.Column annotation.
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
