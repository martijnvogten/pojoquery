package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of {@link TypeModel} that wraps a runtime {@link Class}.
 *
 * <p>This implementation is used at runtime for query execution. It provides
 * additional runtime-only methods like {@link #getReflectionClass()} and
 * {@link #isAssignableTo(Class)}.
 * 
 * <p>Extends {@link AbstractAnnotatedElement} to support immutable annotation
 * transforms. Use {@link #withAddedAnnotation} to create a new instance with
 * additional canonical annotations.
 */
public class ReflectionTypeModel extends AbstractAnnotatedElement<TypeModel> implements TypeModel {

    private final Class<?> clazz;

    /**
     * Creates a TypeModel wrapping the given class.
     *
     * @param clazz the class to wrap
     */
    public ReflectionTypeModel(Class<?> clazz) {
        super();
        this.clazz = Objects.requireNonNull(clazz, "clazz must not be null");
    }

    /**
     * Private constructor for creating instances with added annotations.
     */
    protected ReflectionTypeModel(Class<?> clazz, Map<Class<?>, AnnotationModel> addedAnnotations) {
        super(addedAnnotations);
        this.clazz = Objects.requireNonNull(clazz, "clazz must not be null");
    }

    /**
     * Creates a TypeModel for the given class.
     * Convenience factory method.
     */
    public static ReflectionTypeModel of(Class<?> clazz) {
        return new ReflectionTypeModel(clazz);
    }

    // ========== AbstractAnnotatedElement implementation ==========

    @Override
    protected AnnotationModel[] getSourceAnnotations() {
        Annotation[] annotations = clazz.getAnnotations();
        AnnotationModel[] result = new AnnotationModel[annotations.length];
        for (int i = 0; i < annotations.length; i++) {
            result[i] = new ReflectionAnnotationModel(annotations[i]);
        }
        return result;
    }

    // ========== TypeModel interface methods ==========

    @Override
    public String getQualifiedName() {
        return clazz.getName();
    }

    @Override
    public String getSimpleName() {
        return clazz.getSimpleName();
    }

    @Override
    public TypeModel getSuperclass() {
        Class<?> superclass = clazz.getSuperclass();
        return superclass != null ? new ReflectionTypeModel(superclass) : null;
    }

    @Override
    public List<FieldModel> getDeclaredFields() {
        List<FieldModel> result = new ArrayList<>();
        for (Field f : clazz.getDeclaredFields()) {
            result.add(new ReflectionFieldModel(f));
        }
        return result;
    }

    @Override
    public boolean isPrimitive() {
        return clazz.isPrimitive();
    }

    @Override
    public boolean isEnum() {
        return clazz.isEnum();
    }

    @Override
    public boolean isArray() {
        return clazz.isArray();
    }

    @Override
    public TypeModel getArrayComponentType() {
        if (!clazz.isArray()) {
            return null;
        }
        return new ReflectionTypeModel(clazz.getComponentType());
    }

    @Override
    public TypeModel getTypeArgument() {
        // This method is for fields, not types. For types, we can't determine
        // the type argument without additional context (like ParameterizedType).
        // This is mainly used via FieldModel.getType().getTypeArgument()
        return null;
    }

    @Override
    public boolean isMap() {
        return java.util.Map.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean isSameType(TypeModel other) {
        if (!(other instanceof ReflectionTypeModel)) {
            return getQualifiedName().equals(other.getQualifiedName());
        }
        return clazz.equals(((ReflectionTypeModel) other).clazz);
    }

    @Override
    public List<TypeModel> getTypeValuesFromAnnotation(AnnotationModel annotationModel, String attributeName) {
        return annotationModel.getClassValues(attributeName);
    }

    @Override
    protected TypeModel withAnnotations(Map<Class<?>, AnnotationModel> annotations) {
        return new ReflectionTypeModel(clazz, annotations);
    }

    // ========== Runtime-specific methods ==========

    /**
     * Returns the underlying Class object.
     * Use this method when you need runtime reflection capabilities.
     */
    public Class<?> getReflectionClass() {
        return clazz;
    }

    /**
     * Returns true if this type is assignable to the given class.
     * Equivalent to {@code targetClass.isAssignableFrom(thisClass)}.
     */
    public boolean isAssignableTo(Class<?> targetClass) {
        return targetClass.isAssignableFrom(clazz);
    }

    // ========== Object methods ==========

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ReflectionTypeModel other)) return false;
        return clazz.equals(other.clazz) && getAddedAnnotations().equals(other.getAddedAnnotations());
    }

    @Override
    public int hashCode() {
        return Objects.hash(clazz, getAddedAnnotations());
    }

    @Override
    public String toString() {
        return "ReflectionTypeModel[" + clazz.getName() + "]";
    }
}
