package org.pojoquery.typemodel;

import java.lang.reflect.Field;

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
 * 
 * <p>FieldModel is immutable. Transform methods like {@link #withAddedAnnotation}
 * return new instances with the specified modifications.
 */
public interface FieldModel extends AnnotatedElementModel<FieldModel> {

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
     * Returns true if this field is static.
     */
    boolean isStatic();

    /**
     * Returns true if this field has the transient modifier.
     */
    boolean isTransient();

    /**
     * Returns the runtime Field if this is a ReflectionFieldModel, or null otherwise.
     * Do not use this method compile time (e.g. from annotation processing).
     */
    Field getReflectionField();
}
