package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Abstraction over type introspection that works with both runtime Classes
 * and compile-time TypeElements/TypeMirrors.
 *
 * <p>This interface provides the common operations needed for query building
 * without being tied to the Java reflection API. Implementations exist for:
 * <ul>
 *   <li>{@link ReflectionTypeModel} - wraps {@code Class<?>} for runtime use</li>
 *   <li>ElementTypeModel (in processor module) - wraps {@code TypeElement} for annotation processing</li>
 * </ul>
 */
public interface TypeModel {

    /**
     * Returns the fully qualified name of this type.
     * Example: "com.example.User"
     */
    String getQualifiedName();

    /**
     * Returns the simple name of this type.
     * Example: "User"
     */
    String getSimpleName();

    /**
     * Returns the superclass of this type, or null if this type has no superclass
     * (i.e., it is Object, an interface, a primitive type, or void).
     */
    TypeModel getSuperclass();

    /**
     * Returns the fields declared directly in this type (not inherited fields).
     */
    List<FieldModel> getDeclaredFields();

    /**
     * Returns the annotation of the specified type if present on this type.
     *
     * @param annotationType the Class object corresponding to the annotation type
     * @return the annotation if present, null otherwise
     */
    <A extends Annotation> A getAnnotation(Class<A> annotationType);

    /**
     * Returns true if this type has the specified annotation.
     *
     * @param annotationType the annotation type to check for
     * @return true if the annotation is present
     */
    boolean hasAnnotation(Class<? extends Annotation> annotationType);

    /**
     * Returns true if this type represents a primitive type.
     */
    boolean isPrimitive();

    /**
     * Returns true if this type represents an enum type.
     */
    boolean isEnum();

    /**
     * Returns true if this type represents an array type.
     */
    boolean isArray();

    /**
     * If this type is an array type, returns the component type.
     * Returns null if this is not an array type.
     */
    TypeModel getArrayComponentType();

    /**
     * If this type is a parameterized type (like List&lt;User&gt;), returns the
     * first type argument. Returns null if this is not a parameterized type
     * or has no type arguments.
     */
    TypeModel getTypeArgument();

    /**
     * Returns true if this type has a table mapping (i.e., has @Table annotation
     * either directly or on a superclass).
     */
    boolean hasTableMapping();

    /**
     * Returns true if this type represents a type that can be compared for equality
     * with the given type.
     */
    boolean isSameType(TypeModel other);
}
