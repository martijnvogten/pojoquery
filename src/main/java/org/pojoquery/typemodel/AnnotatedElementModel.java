package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for elements that can have annotations.
 * 
 * <p>All annotation access is through {@link AnnotationModel}, which provides a uniform
 * abstraction over source annotations (from reflection or annotation processing) and
 * synthetic annotations added by transforms.
 * 
 * <p>For runtime access to the underlying element (Field, Class, etc.), cast to the
 * concrete implementation (e.g., {@code ReflectionFieldModel}) and use the accessor
 * methods like {@code getReflectionField()}.
 * 
 * @param <T> the self-referential type for fluent transform methods
 */
public interface AnnotatedElementModel<T extends AnnotatedElementModel<T>> {

    /**
     * Returns true if this element has the specified annotation.
     *
     * @param annotationType the annotation type to check for
     * @return true if the annotation is present
     */
    boolean hasAnnotation(Class<? extends Annotation> annotationType);

    <R> R getAnnotationAttributeValue(Class<? extends Annotation> annotationType, String attributeName, Class<R> expectedType);

    Optional<AnnotationModel> getAnnotation(Class<? extends Annotation> annotationType);
    
    // ========== Immutable transform methods ==========

    /**
     * Returns a new instance with the specified annotation added or replaced.
     * If an annotation of this type already exists, it is replaced.
     * 
     * <p>This method supports the canonical annotation pattern where transforms
     * map various annotation sources (JPA, Jakarta, custom) to canonical
     * PojoQuery annotations.
     *
     * @param type the annotation type to add
     * @param values the annotation attribute values
     * @return a new instance with the annotation added
     */
    T withAddedAnnotation(Class<? extends Annotation> type, Map<String, Object> values);

    /**
     * Returns a new instance with the specified attributes merged into an annotation.
     * If the annotation doesn't exist, it is created with these attributes.
     * If it exists, attributes are merged (new values override existing).
     *
     * @param type the annotation type
     * @param attributes the attributes to merge
     * @return a new instance with the attributes merged
     */
    T withAnnotationAttributes(Class<? extends Annotation> type, Map<String, Object> attributes);
}
