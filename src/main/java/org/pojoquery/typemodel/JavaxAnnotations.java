package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

import org.pojoquery.annotations.Column;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Lob;
import org.pojoquery.annotations.Table;
import org.pojoquery.annotations.Transient;

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
 * FieldModel field = ...;
 * FieldModel transformed = JavaxAnnotations.transformField(field);
 * // transformed now has @Id if original had @javax.persistence.Id
 * </pre>
 */
public final class JavaxAnnotations {

    // Javax annotation classes (loaded via reflection to avoid hard dependency)
    private static final Class<? extends Annotation> JAVAX_TABLE;
    private static final Class<? extends Annotation> JAVAX_ID;
    private static final Class<? extends Annotation> JAVAX_COLUMN;
    private static final Class<? extends Annotation> JAVAX_JOIN_COLUMN;
    private static final Class<? extends Annotation> JAVAX_TRANSIENT;
    private static final Class<? extends Annotation> JAVAX_EMBEDDED;
    private static final Class<? extends Annotation> JAVAX_LOB;

    private static final boolean JAVAX_AVAILABLE;

    static {
        JAVAX_TABLE = tryLoadAnnotationClass("javax.persistence.Table");
        JAVAX_ID = tryLoadAnnotationClass("javax.persistence.Id");
        JAVAX_COLUMN = tryLoadAnnotationClass("javax.persistence.Column");
        JAVAX_JOIN_COLUMN = tryLoadAnnotationClass("javax.persistence.JoinColumn");
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
     * Transforms a FieldModel by adding PojoQuery annotations based on javax.persistence annotations.
     * 
     * @param fs the field model to transform
     * @return a new FieldModel with canonical annotations added, or the original if no javax annotations present
     */
    public static FieldModel transformField(FieldModel fs) {
        if (!JAVAX_AVAILABLE || fs == null) {
            return fs;
        }

        fs = mapAnnotation(fs, JAVAX_ID, Id.class);
        fs = mapAnnotation(fs, JAVAX_TRANSIENT, Transient.class);
        fs = mapAnnotation(fs, JAVAX_EMBEDDED, Embedded.class);
        fs = mapAnnotation(fs, JAVAX_LOB, Lob.class);
        if (fs.hasAnnotation(JAVAX_JOIN_COLUMN) && !fs.getAnnotationAttributeValue(JAVAX_JOIN_COLUMN, "name", String.class).isEmpty()) {
            fs = mapAnnotation(fs, JAVAX_JOIN_COLUMN, FieldName.class, ann -> {
                return Map.of("value", ann.getStringValue("name").orElse(""));
            });
        }
        if (fs.hasAnnotation(JAVAX_COLUMN) && !fs.getAnnotationAttributeValue(JAVAX_COLUMN, "name", String.class).isEmpty()) {
            fs = mapAnnotation(fs, JAVAX_COLUMN, FieldName.class, ann -> {
                return Map.of("value", ann.getStringValue("name").orElse(""));
            });
        }
        fs = mapColumn(fs);

        return fs;
    }

    private static <T extends AnnotatedElementModel<T>> T mapAnnotation(T f, Class<? extends Annotation> source, Class<? extends Annotation> target) {
        if (f.hasAnnotation(source) && !f.hasAnnotation(target)) {
            return (T) f.withAddedAnnotation(target, Collections.emptyMap());
        }
        return f;
    }

    private static <T extends AnnotatedElementModel<T>> T mapAnnotation(T fs, Class<? extends Annotation> source, Class<? extends Annotation> target, Function<AnnotationModel, Map<String, Object>> attributeMapper) {
        if (fs.hasAnnotation(source) && !fs.hasAnnotation(target)) {
            Map<String, Object> attributes = fs.getAnnotation(source)
                .map(attributeMapper)
                .orElse(Collections.emptyMap());
            return (T) fs.withAddedAnnotation(target, attributes);
        }
        return fs;
    }

    private static FieldModel mapColumn(FieldModel fs) {
        if (fs.hasAnnotation(Column.class)) {
            return fs;
        }
        return fs.getAnnotation(JAVAX_COLUMN)
            .map(col -> fs.withAddedAnnotation(Column.class, extractColumnAttributes(col)))
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

        attrs.put("length", columnAnnotation.getNumberAttribute("length").intValue());
        attrs.put("precision", columnAnnotation.getNumberAttribute("precision").intValue());
        attrs.put("scale", columnAnnotation.getNumberAttribute("scale").intValue());
        
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
