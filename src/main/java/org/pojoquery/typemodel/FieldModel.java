package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Abstraction over field introspection that works with both runtime Fields
 * and compile-time VariableElements.
 *
 * <p>This interface provides the common operations needed for query building
 * without being tied to the Java reflection API. Implementations exist for:
 * <ul>
 *   <li>{@link ReflectionFieldModel} - wraps {@code Field} for runtime use</li>
 *   <li>ElementFieldModel (in processor module) - wraps {@code VariableElement} for annotation processing</li>
 * </ul>
 */
public interface FieldModel extends AnnotatedElementModel {

    /**
     * Returns the name of this field.
     */
    String getName();

    /**
     * Returns the type of this field.
     */
    TypeModel getType();

    /**
     * Returns the type that declares this field.
     */
    TypeModel getDeclaringType();

    /**
     * Returns all annotations present on this field.
     *
     * @return an array of annotation models
     */
    AnnotationModel[] getAnnotations();

    /**
     * Returns true if this field has the specified annotation.
     *
     * @param annotationType the annotation type to check for
     * @return true if the annotation is present
     */
    boolean hasAnnotation(Class<? extends Annotation> annotationType);

    /**
     * Returns all annotations of the specified type on this field.
     * This handles repeatable annotations by unwrapping container annotations.
     *
     * @param annotationType the annotation type to find
     * @return list of matching annotation models, empty if none found
     */
    List<AnnotationModel> getAnnotationsByType(Class<? extends Annotation> annotationType);

    /**
     * Returns true if this field is static.
     */
    boolean isStatic();

    /**
     * Returns true if this field has the transient modifier.
     */
    boolean isTransient();
}
