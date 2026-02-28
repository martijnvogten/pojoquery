package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link TypeModel} that wraps a runtime {@link Class}.
 *
 * <p>This implementation is used at runtime for query execution. It provides
 * additional runtime-only methods like {@link #getReflectionClass()} and
 * {@link #isAssignableTo(Class)}.
 */
public class ReflectionTypeModel implements TypeModel {

    private final Class<?> clazz;

    /**
     * Creates a TypeModel wrapping the given class.
     *
     * @param clazz the class to wrap
     */
    public ReflectionTypeModel(Class<?> clazz) {
        this.clazz = Objects.requireNonNull(clazz, "clazz must not be null");
    }

    /**
     * Creates a TypeModel for the given class.
     * Convenience factory method.
     */
    public static ReflectionTypeModel of(Class<?> clazz) {
        return new ReflectionTypeModel(clazz);
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
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return clazz.getAnnotation(annotationType);
    }

    @Override
    public <A extends Annotation> A getDeclaredAnnotation(Class<A> annotationType) {
        return clazz.getDeclaredAnnotation(annotationType);
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return clazz.getAnnotation(annotationType) != null;
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
    public List<TypeModel> getTypeValuesFromAnnotation(Annotation annotation, String attributeName) {
        try {
            java.lang.reflect.Method method = annotation.annotationType().getMethod(attributeName);
            Class<?>[] classes = (Class<?>[]) method.invoke(annotation);
            List<TypeModel> result = new ArrayList<>();
            for (Class<?> c : classes) {
                result.add(new ReflectionTypeModel(c));
            }
            return result;
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            throw new RuntimeException("Failed to extract " + attributeName + " from annotation " + annotation, e);
        }
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
        if (!(obj instanceof ReflectionTypeModel)) return false;
        ReflectionTypeModel other = (ReflectionTypeModel) obj;
        return clazz.equals(other.clazz);
    }

    @Override
    public int hashCode() {
        return clazz.hashCode();
    }

    @Override
    public String toString() {
        return "ReflectionTypeModel[" + clazz.getName() + "]";
    }
}
