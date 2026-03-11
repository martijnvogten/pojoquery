package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.util.List;
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
     * Returns the annotation of the specified type if present on this element.
     * Returns both source annotations and synthetic annotations added via transforms.
     * Synthetic annotations take precedence over source annotations of the same type.
     *
     * @param annotationType the Class object corresponding to the annotation type
     * @return the annotation model if present, empty otherwise
     */
    Optional<AnnotationModel> getAnnotation(Class<? extends Annotation> annotationType);
    
    @Deprecated
    AnnotationModel getAnnotationWithNull(Class<? extends Annotation> annotationType);

    /**
     * Returns true if this element has the specified annotation.
     *
     * @param annotationType the annotation type to check for
     * @return true if the annotation is present
     */
    boolean hasAnnotation(Class<? extends Annotation> annotationType);

    /**
     * Returns all annotations present on this element.
     *
     * @return an array of annotation models
     */
    AnnotationModel[] getAnnotations();

    /**
     * Returns all annotations of the specified type on this element.
     * This handles repeatable annotations by unwrapping container annotations.
     *
     * @param annotationType the annotation type to find
     * @return list of matching annotation models, empty if none found
     */
    List<AnnotationModel> getAnnotationsByType(Class<? extends Annotation> annotationType);

    /**
     * Extracts Class[] values from an annotation attribute and returns them as TypeModels.
     * This is used to read annotation attributes like {@code @SubClasses(value = {A.class, B.class})}.
     *
     * @param annotationModel the annotation model
     * @param attributeName the name of the attribute containing Class[] values
     * @return list of TypeModels for the classes referenced in the annotation attribute
     */
    List<TypeModel> getTypeValuesFromAnnotation(AnnotationModel annotationModel, String attributeName);

    <R> R getAnnotationAttributeValue(Class<? extends Annotation> annotationType, String attributeName, Class<R> expectedType);

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
     * Returns a new instance with the specified attribute added to an annotation.
     * If the annotation doesn't exist, it is created with just this attribute.
     * If it exists, the attribute is added/updated while preserving other attributes.
     *
     * @param type the annotation type
     * @param attributeName the attribute to set
     * @param value the attribute value
     * @return a new instance with the attribute set
     */
    T withAnnotationAttribute(Class<? extends Annotation> type, String attributeName, Object value);

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
