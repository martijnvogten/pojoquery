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

    /**
     * Factory method for creating a synthetic annotation.
     */
    public static SyntheticAnnotationModel of(Class<? extends Annotation> type, Map<String, Object> values) {
        return new SyntheticAnnotationModel(type, values);
    }

    /**
     * Factory method for creating a marker annotation (no attributes).
     */
    public static SyntheticAnnotationModel marker(Class<? extends Annotation> type) {
        return new SyntheticAnnotationModel(type, Map.of());
    }

    /**
     * Returns the annotation type this synthetic annotation represents.
     */
    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
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

    @Override
    public List<AnnotationModel> getAnnotationValues(String attributeName) {
        Object value = values.get(attributeName);
        if (value == null) return Collections.emptyList();

        if (value instanceof AnnotationModel am) {
            return List.of(am);
        } else if (value instanceof AnnotationModel[] arr) {
            return List.of(arr);
        }
        return Collections.emptyList();
    }

    @Override
    public List<Number> getNumberAttributes(String attributeName) {
        Object value = values.get(attributeName);
        if (value == null) return Collections.emptyList();

        if (value instanceof Number n) {
            return List.of(n);
        } else if (value instanceof int[] arr) {
            List<Number> result = new ArrayList<>();
            for (int i : arr) result.add(i);
            return result;
        } else if (value instanceof long[] arr) {
            List<Number> result = new ArrayList<>();
            for (long l : arr) result.add(l);
            return result;
        } else if (value instanceof double[] arr) {
            List<Number> result = new ArrayList<>();
            for (double d : arr) result.add(d);
            return result;
        }
        return Collections.emptyList();
    }

    @Override
    public List<Boolean> getBooleanValues(String attributeName) {
        Object value = values.get(attributeName);
        if (value == null) return Collections.emptyList();

        if (value instanceof Boolean b) {
            return List.of(b);
        } else if (value instanceof boolean[] arr) {
            List<Boolean> result = new ArrayList<>();
            for (boolean b : arr) result.add(b);
            return result;
        }
        return Collections.emptyList();
    }

    @Override
    public List<TypeModel> getClassValues(String attributeName) {
        Object value = values.get(attributeName);
        if (value == null) return Collections.emptyList();

        if (value instanceof Class<?> c) {
            return List.of(new ReflectionTypeModel(c));
        } else if (value instanceof Class<?>[] classes) {
            List<TypeModel> result = new ArrayList<>();
            for (Class<?> c : classes) {
                result.add(new ReflectionTypeModel(c));
            }
            return result;
        } else if (value instanceof TypeModel tm) {
            return List.of(tm);
        } else if (value instanceof TypeModel[] tms) {
            return List.of(tms);
        }
        return Collections.emptyList();
    }

    /**
     * Returns all attribute values as an immutable map.
     */
    public Map<String, Object> getValuesMap() {
        return values;
    }

    /**
     * Returns the raw value for an attribute, or null if not present.
     */
    public Object getRawValue(String attributeName) {
        return values.get(attributeName);
    }

    /**
     * Returns a new SyntheticAnnotationModel with the specified attribute added/updated.
     */
    public SyntheticAnnotationModel withAttribute(String name, Object value) {
        var newValues = new java.util.HashMap<>(values);
        newValues.put(name, value);
        return new SyntheticAnnotationModel(annotationType, newValues);
    }

    /**
     * Returns a new SyntheticAnnotationModel with the specified attributes merged.
     */
    public SyntheticAnnotationModel withAttributes(Map<String, Object> additionalValues) {
        var newValues = new java.util.HashMap<>(values);
        newValues.putAll(additionalValues);
        return new SyntheticAnnotationModel(annotationType, newValues);
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
