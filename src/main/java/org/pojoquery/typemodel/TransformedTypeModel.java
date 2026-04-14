package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A wrapper around TypeModel that transparently applies TypeTransformers.
 * 
 * <p>This class wraps any TypeModel and ensures that:
 * <ul>
 *   <li>Annotation queries (hasAnnotation, getAnnotationAttributeValue) are resolved
 *       through the transformer chain</li>
 *   <li>Navigation methods (getSuperclass, getDeclaredFields, etc.) return wrapped
 *       instances, so the entire type graph remains transformed</li>
 *   <li>Intrinsic properties (getQualifiedName, isPrimitive, etc.) pass through directly</li>
 * </ul>
 * 
 * <p>The transformation is lazy - the transformer chain is only applied when
 * annotation methods are called, and the result is cached.
 * 
 * @see TransformedFieldModel
 */
public class TransformedTypeModel implements TypeModel {

    private final TypeModel delegate;
    private final List<TypeTransformer> transformers;
    private TypeModel resolved; // lazily cached

    /**
     * Creates a transformed type model.
     *
     * @param delegate the underlying type model
     * @param transformers the list of transformers to apply
     */
    public TransformedTypeModel(TypeModel delegate, List<TypeTransformer> transformers) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.transformers = Objects.requireNonNull(transformers, "transformers must not be null");
    }

    /**
     * Wraps a type model if not null.
     */
    private TypeModel wrap(TypeModel type) {
        return type == null ? null : new TransformedTypeModel(type, transformers);
    }

    /**
     * Returns the transformed version of this type (with all transformers applied).
     * The result is cached for efficiency.
     */
    private TypeModel resolved() {
        if (resolved == null) {
            TypeModel result = delegate;
            for (TypeTransformer t : transformers) {
                result = t.transformType(result);
            }
            resolved = result;
        }
        return resolved;
    }

    // ========== Navigation methods - wrap returned types ==========

    @Override
    public TypeModel getSuperclass() {
        return wrap(delegate.getSuperclass());
    }

    @Override
    public List<FieldModel> getDeclaredFields() {
        return delegate.getDeclaredFields().stream()
            .<FieldModel>map(f -> new TransformedFieldModel(f, transformers))
            .toList();
    }

    @Override
    public TypeModel getArrayComponentType() {
        return wrap(delegate.getArrayComponentType());
    }

    @Override
    public TypeModel getTypeArgument() {
        return wrap(delegate.getTypeArgument());
    }

    // ========== Annotation methods - delegate to resolved ==========

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return resolved().hasAnnotation(annotationType);
    }

    @Override
    public <R> R getAnnotationAttributeValue(Class<? extends Annotation> annotationType, 
                                              String attributeName, Class<R> expectedType) {
        return resolved().getAnnotationAttributeValue(annotationType, attributeName, expectedType);
    }

    @Override
    public TypeModel withAddedAnnotation(Class<? extends Annotation> type, Map<String, Object> values) {
        // Apply to delegate, then re-wrap
        return new TransformedTypeModel(delegate.withAddedAnnotation(type, values), transformers);
    }

    @Override
    public TypeModel withAnnotationAttributes(Class<? extends Annotation> type, Map<String, Object> attributes) {
        return new TransformedTypeModel(delegate.withAnnotationAttributes(type, attributes), transformers);
    }

    // ========== Pass-through methods ==========

    @Override
    public String getQualifiedName() {
        return delegate.getQualifiedName();
    }

    @Override
    public String getSimpleName() {
        return delegate.getSimpleName();
    }

    @Override
    public boolean isPrimitive() {
        return delegate.isPrimitive();
    }

    @Override
    public boolean isEnum() {
        return delegate.isEnum();
    }

    @Override
    public boolean isArray() {
        return delegate.isArray();
    }

    @Override
    public boolean isMap() {
        return delegate.isMap();
    }

    @Override
    public boolean isSameType(TypeModel other) {
        // Unwrap if comparing with another TransformedTypeModel
        TypeModel otherDelegate = other instanceof TransformedTypeModel ttm ? ttm.delegate : other;
        return delegate.isSameType(otherDelegate);
    }

    @Override
    public boolean isSameType(Class<?> other) {
        return delegate.isSameType(other);
    }

    // ========== Object methods ==========

    @Override
    public String toString() {
        return "TransformedTypeModel[" + delegate.getQualifiedName() + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TransformedTypeModel other)) return false;
        return delegate.equals(other.delegate) && transformers.equals(other.transformers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate, transformers);
    }

    /**
     * Returns the underlying unwrapped type model.
     */
    public TypeModel getDelegate() {
        return delegate;
    }

    /**
     * Returns the reflection class if the underlying delegate is a ReflectionTypeModel.
     * This method handles nested TransformedTypeModels by recursively unwrapping.
     *
     * @return the reflection class if available
     * @throws UnsupportedOperationException if the underlying type is not a ReflectionTypeModel
     */
    public Class<?> getReflectionClass() {
        TypeModel current = delegate;
        while (current instanceof TransformedTypeModel ttm) {
            current = ttm.delegate;
        }
        if (current instanceof ReflectionTypeModel rtm) {
            return rtm.getReflectionClass();
        }
        throw new UnsupportedOperationException(
            "Cannot get reflection class from " + current.getClass().getSimpleName());
    }

    @Override
    public Optional<AnnotationModel> getAnnotation(Class<? extends Annotation> annotationType) {
        return resolved().getAnnotation(annotationType);
    }
}
