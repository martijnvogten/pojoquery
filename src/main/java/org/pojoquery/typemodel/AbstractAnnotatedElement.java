package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base class for annotated elements (fields and types) that provides
 * immutable annotation overlay functionality.
 * 
 * <p>This base class manages a canonical set of annotations that includes
 * both source annotations (from reflection or annotation processing) and
 * synthetic annotations added by transforms. Synthetic annotations take
 * precedence over source annotations of the same type.
 * 
 * <p>All annotation access is through {@link AnnotationModel}. For runtime
 * access to the underlying element, cast to the concrete implementation.
 * 
 * @param <T> the self-referential type for fluent transform methods
 */
public abstract class AbstractAnnotatedElement<T extends AnnotatedElementModel<T>> implements AnnotatedElementModel<T> {

    private final Map<Class<?>, AnnotationModel> addedAnnotations;

    /**
     * Creates an element with no added annotations.
     */
    protected AbstractAnnotatedElement() {
        this(Map.of());
    }

    /**
     * Creates an element with the specified added annotations.
     *
     * @param addedAnnotations annotations added by transforms (immutable copy is made)
     */
    protected AbstractAnnotatedElement(Map<Class<?>, AnnotationModel> addedAnnotations) {
        this.addedAnnotations = addedAnnotations.isEmpty() ? Map.of() : Map.copyOf(addedAnnotations);
    }

    // ========== Abstract methods ==========

    /**
     * Returns all source annotations from the underlying element.
     * This is the only abstract method subclasses need to implement.
     */
    protected abstract AnnotationModel[] getSourceAnnotations();

    /**
     * Creates a new instance with the specified added annotations.
     * Subclasses implement this to return their concrete type.
     */
    protected abstract T withAnnotations(Map<Class<?>, AnnotationModel> annotations);

    // ========== Helper methods ==========

    /**
     * Finds a source annotation by type.
     */
    private AnnotationModel findSourceAnnotation(Class<? extends Annotation> type) {
        String typeName = type.getName();
        for (AnnotationModel ann : getSourceAnnotations()) {
            if (ann.getType().getQualifiedName().equals(typeName)) {
                return ann;
            }
        }
        return null;
    }

    // ========== Final merged annotation API ==========

    @Override
    public final boolean hasAnnotation(Class<? extends Annotation> type) {
        return addedAnnotations.containsKey(type) || findSourceAnnotation(type) != null;
    }

    @Override
    public final Optional<AnnotationModel> getAnnotation(Class<? extends Annotation> type) {
        AnnotationModel added = addedAnnotations.get(type);
        return added != null ? Optional.of(added) : Optional.ofNullable(findSourceAnnotation(type));
    }

    @Override
    public final AnnotationModel getAnnotationWithNull(Class<? extends Annotation> type) {
        return getAnnotation(type).orElse(null);
    }

    @Override
    public final AnnotationModel[] getAnnotations() {
        Map<String, AnnotationModel> merged = new LinkedHashMap<>();
        
        // Add source annotations first
        for (AnnotationModel src : getSourceAnnotations()) {
            merged.put(src.getType().getQualifiedName(), src);
        }
        
        // Added annotations override source annotations
        for (Map.Entry<Class<?>, AnnotationModel> entry : addedAnnotations.entrySet()) {
            merged.put(entry.getKey().getName(), entry.getValue());
        }
        
        return merged.values().toArray(new AnnotationModel[0]);
    }

    @Override
    public final <R> R getAnnotationAttributeValue(Class<? extends Annotation> annotationType, String attributeName, Class<R> expectedType) {
        return getAnnotation(annotationType)
            .map(am -> am.getValuesMap().get(attributeName))
            .flatMap(value -> Optional.ofNullable(value)
                .filter(expectedType::isInstance)
                .map(expectedType::cast))
            .orElse(null);
    }

    @Override
    public final List<AnnotationModel> getAnnotationsByType(Class<? extends Annotation> type) {
        // If we have an added annotation of this type, it replaces all source annotations
        AnnotationModel added = addedAnnotations.get(type);
        if (added != null) {
            return List.of(added);
        }
        
        // Look for direct matches and container annotations
        List<AnnotationModel> result = new ArrayList<>();
        String targetTypeName = type.getName();
        
        // Check if this annotation type is repeatable
        Repeatable repeatable = type.getAnnotation(Repeatable.class);
        String containerTypeName = repeatable != null ? repeatable.value().getName() : null;
        
        for (AnnotationModel ann : getSourceAnnotations()) {
            String annTypeName = ann.getType().getQualifiedName();
            
            if (annTypeName.equals(targetTypeName)) {
                // Direct match
                result.add(ann);
            } else if (containerTypeName != null && annTypeName.equals(containerTypeName)) {
                // Container annotation - unwrap value()
                result.addAll(ann.getNestedAnnotations("value"));
            }
        }
        
        return result;
    }

    // ========== Immutable transform methods ==========

    @Override
    public final T withAddedAnnotation(Class<? extends Annotation> type, Map<String, Object> values) {
        return withAnnotations(mergeAnnotation(type, values));
    }

    @Override
    public final T withAnnotationAttribute(Class<? extends Annotation> type, String attributeName, Object value) {
        return withAnnotations(mergeAnnotationAttribute(type, attributeName, value));
    }

    @Override
    public final T withAnnotationAttributes(Class<? extends Annotation> type, Map<String, Object> attributes) {
        return withAnnotations(mergeAnnotationAttributes(type, attributes));
    }

    // ========== Helper methods ==========

    /**
     * Returns the map of added annotations.
     */
    protected final Map<Class<?>, AnnotationModel> getAddedAnnotations() {
        return addedAnnotations;
    }

    private Map<Class<?>, AnnotationModel> mergeAnnotation(
            Class<? extends Annotation> type, 
            Map<String, Object> values) {
        Map<Class<?>, AnnotationModel> merged = new HashMap<>(addedAnnotations);
        merged.put(type, new SyntheticAnnotationModel(type, values));
        return merged;
    }

    private Map<Class<?>, AnnotationModel> mergeAnnotationAttribute(
            Class<? extends Annotation> type,
            String attributeName,
            Object value) {
        Map<String, Object> existingValues = getAnnotationValuesMap(type);
        Map<String, Object> newValues = new HashMap<>(existingValues);
        newValues.put(attributeName, value);
        return mergeAnnotation(type, newValues);
    }

    private Map<Class<?>, AnnotationModel> mergeAnnotationAttributes(
            Class<? extends Annotation> type,
            Map<String, Object> attributes) {
        Map<String, Object> existingValues = getAnnotationValuesMap(type);
        Map<String, Object> newValues = new HashMap<>(existingValues);
        newValues.putAll(attributes);
        return mergeAnnotation(type, newValues);
    }

    private Map<String, Object> getAnnotationValuesMap(Class<? extends Annotation> type) {
        return getAnnotation(type)
			// For source annotations, we'd need to extract values via reflection
			// For now, return empty - transforms should provide all needed values
			.map(am -> am.getValuesMap())
            .orElse(Map.of());
    }
}
