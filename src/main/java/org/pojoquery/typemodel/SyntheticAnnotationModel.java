package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A synthetic {@link AnnotationModel} backed by a Map of attribute values.
 * 
 * <p>This allows creating annotation models programmatically without actual
 * Java annotations, enabling annotation transforms to add canonical annotations
 * to fields and types.
 * 
 * <p>Example usage:
 * <pre>
 * var linkAnnotation = SyntheticAnnotationModel.of(Link.class, Map.of(
 *     "linktable", "user_group",
 *     "linkfield", "user_id"
 * ));
 * </pre>
 */
public final class SyntheticAnnotationModel implements AnnotationModel {

    private final Class<? extends Annotation> annotationType;
    private final Map<String, Object> values;

    /**
     * Creates a synthetic annotation model.
     *
     * @param annotationType the canonical annotation type
     * @param values the attribute name-value pairs
     */
    public SyntheticAnnotationModel(Class<? extends Annotation> annotationType, Map<String, Object> values) {
        this.annotationType = Objects.requireNonNull(annotationType, "annotationType must not be null");
        this.values = values != null ? Map.copyOf(values) : Map.of();
    }

    @Override
    public TypeModel getType() {
        return new ReflectionTypeModel(annotationType);
    }

    @Override
    public List<String> getStringValues(String attributeName) {
        Object value = values.get(attributeName);
        if (value == null) return Collections.emptyList();

        if (value instanceof String s) {
            return List.of(s);
        } else if (value instanceof String[] arr) {
            return List.of(arr);
        } else if (value instanceof Class<?> c) {
            return List.of(c.getName());
        } else if (value instanceof Class<?>[] classes) {
            List<String> result = new ArrayList<>();
            for (Class<?> c : classes) {
                result.add(c.getName());
            }
            return result;
        }
        return Collections.emptyList();
    }

    @Override
    public List<String> getEnumValues(String attributeName) {
        Object value = values.get(attributeName);
        if (value == null) return Collections.emptyList();

        if (value instanceof Enum<?> e) {
            return List.of(e.name());
        } else if (value instanceof Enum<?>[] enums) {
            List<String> result = new ArrayList<>();
            for (Enum<?> e : enums) {
                result.add(e.name());
            }
            return result;
        } else if (value instanceof String s) {
            // Allow passing enum names as strings
            return List.of(s);
        } else if (value instanceof String[] arr) {
            return List.of(arr);
        }
        return Collections.emptyList();
    }

    /**
     * Returns all attribute values as an immutable map.
     */
    public Map<String, Object> getValuesMap() {
        return values;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SyntheticAnnotationModel other)) return false;
        return annotationType.equals(other.annotationType) && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotationType, values);
    }

    @Override
    public String toString() {
        return "SyntheticAnnotationModel[@" + annotationType.getSimpleName() + ", values=" + values + "]";
    }
}
