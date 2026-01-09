package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;

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
public interface FieldModel {

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
     * Returns the annotation of the specified type if present on this field.
     *
     * @param annotationType the Class object corresponding to the annotation type
     * @return the annotation if present, null otherwise
     */
    <A extends Annotation> A getAnnotation(Class<A> annotationType);

    /**
     * Returns true if this field has the specified annotation.
     *
     * @param annotationType the annotation type to check for
     * @return true if the annotation is present
     */
    boolean hasAnnotation(Class<? extends Annotation> annotationType);

    /**
     * Returns true if this field is static.
     */
    boolean isStatic();

    /**
     * Returns true if this field is transient (has the transient modifier
     * or the @Transient annotation).
     */
    boolean isTransient();
}
