package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

import org.pojoquery.AnnotationHelper;

/**
 * Implementation of {@link FieldModel} that wraps a runtime {@link Field}.
 *
 * <p>This implementation is used at runtime for query execution. It provides
 * additional runtime-only methods like {@link #getReflectionField()}.
 */
public class ReflectionFieldModel implements FieldModel {

    private final Field field;

    /**
     * Creates a FieldModel wrapping the given field.
     *
     * @param field the field to wrap
     */
    public ReflectionFieldModel(Field field) {
        this.field = Objects.requireNonNull(field, "field must not be null");
    }

    /**
     * Creates a FieldModel for the given field.
     * Convenience factory method.
     */
    public static ReflectionFieldModel of(Field field) {
        return new ReflectionFieldModel(field);
    }

    // ========== FieldModel interface methods ==========

    @Override
    public String getName() {
        return field.getName();
    }

    @Override
    public TypeModel getType() {
        return new ReflectionTypeModelWithGenericInfo(field.getType(), field.getGenericType());
    }

    @Override
    public TypeModel getDeclaringType() {
        return new ReflectionTypeModel(field.getDeclaringClass());
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return field.getAnnotation(annotationType);
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return field.getAnnotation(annotationType) != null;
    }

    @Override
    public boolean isStatic() {
        return (field.getModifiers() & Modifier.STATIC) != 0;
    }

    @Override
    public boolean isTransient() {
        if ((field.getModifiers() & Modifier.TRANSIENT) != 0) {
            return true;
        }
        return AnnotationHelper.isTransient(field);
    }

    // ========== Runtime-specific methods ==========

    /**
     * Returns the underlying Field object.
     * Use this method when you need runtime reflection capabilities like
     * getting/setting field values.
     */
    public Field getReflectionField() {
        return field;
    }

    // ========== Object methods ==========

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ReflectionFieldModel)) return false;
        ReflectionFieldModel other = (ReflectionFieldModel) obj;
        return field.equals(other.field);
    }

    @Override
    public int hashCode() {
        return field.hashCode();
    }

    @Override
    public String toString() {
        return "ReflectionFieldModel[" + field.getDeclaringClass().getSimpleName() + "." + field.getName() + "]";
    }

    // ========== Inner class for type with generic info ==========

    /**
     * Extended ReflectionTypeModel that also carries generic type information
     * from a field declaration. This allows getTypeArgument() to work correctly.
     */
    private static class ReflectionTypeModelWithGenericInfo extends ReflectionTypeModel {
        private final Type genericType;

        ReflectionTypeModelWithGenericInfo(Class<?> clazz, Type genericType) {
            super(clazz);
            this.genericType = genericType;
        }

        @Override
        public TypeModel getTypeArgument() {
            if (genericType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericType;
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    return new ReflectionTypeModel((Class<?>) typeArgs[0]);
                }
            }
            return super.getTypeArgument();
        }
    }
}
