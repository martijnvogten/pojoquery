package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A wrapper around FieldModel that transparently applies TypeTransformers.
 * 
 * <p>This class wraps any FieldModel and ensures that:
 * <ul>
 *   <li>Annotation queries (hasAnnotation, getAnnotationAttributeValue) are resolved
 *       through the transformer chain</li>
 *   <li>Type-returning methods (getType, getDeclaringType) return wrapped
 *       instances, so the entire type graph remains transformed</li>
 *   <li>Intrinsic properties (getName, isStatic, isTransient) pass through directly</li>
 * </ul>
 * 
 * <p>The transformation is lazy - the transformer chain is only applied when
 * annotation methods are called, and the result is cached.
 * 
 * @see TransformedTypeModel
 */
public class TransformedFieldModel implements FieldModel {

    private final FieldModel delegate;
    private final List<TypeTransformer> transformers;
    private FieldModel resolved; // lazily cached

    /**
     * Creates a transformed field model.
     *
     * @param delegate the underlying field model
     * @param transformers the list of transformers to apply
     */
    public TransformedFieldModel(FieldModel delegate, List<TypeTransformer> transformers) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.transformers = Objects.requireNonNull(transformers, "transformers must not be null");
    }

    /**
     * Wraps a type model if not null.
     */
    private TypeModel wrapType(TypeModel type) {
        return type == null ? null : new TransformedTypeModel(type, transformers);
    }

    /**
     * Returns the transformed version of this field (with all transformers applied).
     * The result is cached for efficiency.
     */
    private FieldModel resolved() {
        if (resolved == null) {
            FieldModel result = delegate;
            for (TypeTransformer t : transformers) {
                result = t.transformField(result);
            }
            resolved = result;
        }
        return resolved;
    }

    // ========== Type-returning methods - wrap returned types ==========

    @Override
    public TypeModel getType() {
        return wrapType(delegate.getType());
    }

    @Override
    public TypeModel getDeclaringType() {
        return wrapType(delegate.getDeclaringType());
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
    public FieldModel withAddedAnnotation(Class<? extends Annotation> type, Map<String, Object> values) {
        // Apply to delegate, then re-wrap
        return new TransformedFieldModel(delegate.withAddedAnnotation(type, values), transformers);
    }

    @Override
    public FieldModel withAnnotationAttributes(Class<? extends Annotation> type, Map<String, Object> attributes) {
        return new TransformedFieldModel(delegate.withAnnotationAttributes(type, attributes), transformers);
    }

    // ========== Pass-through methods ==========

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public boolean isStatic() {
        return delegate.isStatic();
    }

    @Override
    public boolean isTransient() {
        return delegate.isTransient();
    }

    // ========== Object methods ==========

    @Override
    public String toString() {
        return "TransformedFieldModel[" + delegate.getName() + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TransformedFieldModel other)) return false;
        return delegate.equals(other.delegate) && transformers.equals(other.transformers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate, transformers);
    }

    /**
     * Returns the underlying unwrapped field model.
     */
    public FieldModel getDelegate() {
        return delegate;
    }

    /**
     * Returns the reflection Field if the underlying delegate is a ReflectionFieldModel.
     * This method handles nested TransformedFieldModels by recursively unwrapping.
     *
     * @return the reflection Field if available
     * @throws UnsupportedOperationException if the underlying type is not a ReflectionFieldModel
     */
    public java.lang.reflect.Field getReflectionField() {
        FieldModel current = delegate;
        while (current instanceof TransformedFieldModel tfm) {
            current = tfm.delegate;
        }
        if (current instanceof ReflectionFieldModel rfm) {
            return rfm.getReflectionField();
        }
        throw new UnsupportedOperationException(
            "Cannot get reflection field from " + current.getClass().getSimpleName());
    }

    @Override
    public Optional<AnnotationModel> getAnnotation(Class<? extends Annotation> annotationType) {
        return resolved().getAnnotation(annotationType);
    }
}
