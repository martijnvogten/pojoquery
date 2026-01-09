package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.pojoquery.AnnotationHelper;

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
    public boolean hasTableMapping() {
        return AnnotationHelper.hasTableAnnotation(clazz);
    }

    @Override
    public boolean isSameType(TypeModel other) {
        if (!(other instanceof ReflectionTypeModel)) {
            return getQualifiedName().equals(other.getQualifiedName());
        }
        return clazz.equals(((ReflectionTypeModel) other).clazz);
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

    // ========== Static utility methods ==========

    /**
     * Filters out static and transient fields from a class.
     * Static import friendly version of the filtering logic.
     */
    public static List<FieldModel> filterFields(Class<?> clz) {
        List<FieldModel> result = new ArrayList<>();
        for (Field f : clz.getDeclaredFields()) {
            if ((f.getModifiers() & Modifier.STATIC) > 0) {
                continue;
            }
            if ((f.getModifiers() & Modifier.TRANSIENT) > 0) {
                continue;
            }
            if (AnnotationHelper.isTransient(f)) {
                continue;
            }
            result.add(new ReflectionFieldModel(f));
        }
        return result;
    }

    /**
     * Collects fields from a class up to (but not including) a stop class.
     */
    public static List<FieldModel> collectFieldsOfClass(Class<?> clz, Class<?> stopAtSuperClass) {
        List<FieldModel> result = new ArrayList<>();
        while (clz != null && !clz.equals(stopAtSuperClass)) {
            result.addAll(0, filterFields(clz));
            clz = clz.getSuperclass();
        }
        return result;
    }

    /**
     * Collects all fields from a class hierarchy.
     */
    public static List<FieldModel> collectFieldsOfClass(Class<?> type) {
        return collectFieldsOfClass(type, null);
    }
}
