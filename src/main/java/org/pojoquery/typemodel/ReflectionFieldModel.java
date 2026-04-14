package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of {@link FieldModel} that wraps a runtime {@link Field}.
 *
 * <p>This implementation is used at runtime for query execution. It provides
 * additional runtime-only methods like {@link #getReflectionField()}.
 * 
 * <p>Extends {@link AbstractAnnotatedElement} to support immutable annotation
 * transforms. Use {@link #withAddedAnnotation} to create a new instance with
 * additional canonical annotations.
 */
public class ReflectionFieldModel extends AbstractAnnotatedElement<FieldModel> implements FieldModel {

    private final Field field;

    /**
     * Creates a FieldModel wrapping the given field.
     *
     * @param field the field to wrap
     */
    public ReflectionFieldModel(Field field) {
        super();
        this.field = Objects.requireNonNull(field, "field must not be null");
    }

    /**
     * Private constructor for creating instances with added annotations.
     */
    private ReflectionFieldModel(Field field, Map<Class<?>, AnnotationModel> addedAnnotations) {
        super(addedAnnotations);
        this.field = Objects.requireNonNull(field, "field must not be null");
    }

    /**
     * Creates a FieldModel for the given field.
     * Convenience factory method.
     */
    public static ReflectionFieldModel of(Field field) {
        return new ReflectionFieldModel(field);
    }

    // ========== AbstractAnnotatedElement implementation ==========

    @Override
    protected AnnotationModel[] getSourceAnnotations() {
        Annotation[] annotations = field.getAnnotations();
        AnnotationModel[] result = new AnnotationModel[annotations.length];
        for (int i = 0; i < annotations.length; i++) {
            result[i] = new ReflectionAnnotationModel(annotations[i]);
        }
        return result;
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
    public boolean isStatic() {
        return (field.getModifiers() & Modifier.STATIC) != 0;
    }

    @Override
    public boolean isTransient() {
        return (field.getModifiers() & Modifier.TRANSIENT) != 0;
    }

    @Override
    protected FieldModel withAnnotations(Map<Class<?>, AnnotationModel> annotations) {
        return new ReflectionFieldModel(field, annotations);
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
        if (!(obj instanceof ReflectionFieldModel other)) return false;
        return field.equals(other.field) && getAddedAnnotations().equals(other.getAddedAnnotations());
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, getAddedAnnotations());
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
            if (genericType instanceof ParameterizedType pt) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    return new ReflectionTypeModel((Class<?>) typeArgs[0]);
                }
            }
            return super.getTypeArgument();
        }
    }
}
