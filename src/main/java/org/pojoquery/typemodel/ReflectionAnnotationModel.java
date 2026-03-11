package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of {@link AnnotationModel} that wraps a runtime {@link Annotation}.
 *
 * <p>This implementation is used at runtime for annotation inspection.
 */
public class ReflectionAnnotationModel implements AnnotationModel {

    private final Annotation annotation;

    /**
     * Creates an AnnotationModel wrapping the given annotation.
     *
     * @param annotation the annotation to wrap
     */
    public ReflectionAnnotationModel(Annotation annotation) {
        this.annotation = Objects.requireNonNull(annotation, "annotation must not be null");
    }

    /**
     * Creates an AnnotationModel for the given annotation.
     * Convenience factory method.
     */
    public static ReflectionAnnotationModel of(Annotation annotation) {
        return new ReflectionAnnotationModel(annotation);
    }

    @Override
    public TypeModel getType() {
        return new ReflectionTypeModel(annotation.annotationType());
    }

    @Override
    public List<String> getStringValues(String attributeName) {
        Object value = getRawValue(attributeName);
        if (value == null) return Collections.emptyList();
        
        List<String> result = new ArrayList<>();
        if (value.getClass().isArray()) {
            if (value instanceof String[] strings) {
                Collections.addAll(result, strings);
            } else if (value instanceof Class<?>[] classes) {
                for (Class<?> c : classes) {
                    result.add(c.getName());
                }
            }
        } else if (value instanceof String s) {
            result.add(s);
        } else if (value instanceof Class<?> c) {
            result.add(c.getName());
        }
        return result;
    }

    @Override
    public List<String> getEnumValues(String attributeName) {
        return extractValues(attributeName, Enum[].class, Enum.class, Enum::name);
    }

    @Override
    public List<AnnotationModel> getNestedAnnotations(String attributeName) {
        return extractValues(attributeName, Annotation[].class, Annotation.class, ReflectionAnnotationModel::new);
    }

    @Override
    public List<Number> getNumberAttributes(String attributeName) {
        Object value = getRawValue(attributeName);
        if (value == null) return Collections.emptyList();
        
        List<Number> result = new ArrayList<>();
        if (value instanceof int[] ints) {
            for (int i : ints) result.add(i);
        } else if (value instanceof long[] longs) {
            for (long l : longs) result.add(l);
        } else if (value instanceof double[] doubles) {
            for (double d : doubles) result.add(d);
        } else if (value instanceof float[] floats) {
            for (float f : floats) result.add(f);
        } else if (value instanceof short[] shorts) {
            for (short s : shorts) result.add(s);
        } else if (value instanceof byte[] bytes) {
            for (byte b : bytes) result.add(b);
        } else if (value instanceof Number n) {
            result.add(n);
        }
        return result;
    }

    @Override
    public List<Boolean> getBooleanValues(String attributeName) {
        Object value = getRawValue(attributeName);
        if (value == null) return Collections.emptyList();
        
        List<Boolean> result = new ArrayList<>();
        if (value instanceof boolean[] bools) {
            for (boolean b : bools) {
                result.add(b);
            }
        } else if (value instanceof Boolean b) {
            result.add(b);
        }
        return result;
    }

    @Override
    public List<TypeModel> getClassValues(String attributeName) {
        return extractValues(attributeName, Class[].class, Class.class, ReflectionTypeModel::new);
    }

    @SuppressWarnings("unchecked")
    private <T, R> List<R> extractValues(String attributeName,
                                          Class<?> arrayType,
                                          Class<T> singleType,
                                          Function<T, R> mapper) {
        Object value = getRawValue(attributeName);
        if (value == null) return Collections.emptyList();

        List<R> result = new ArrayList<>();
        if (arrayType.isInstance(value)) {
            for (Object item : (Object[]) value) {
                result.add(mapper.apply((T) item));
            }
        } else if (singleType.isInstance(value)) {
            result.add(mapper.apply(singleType.cast(value)));
        }
        return result;
    }

    private Object getRawValue(String attributeName) {
        try {
            Method method = annotation.annotationType().getMethod(attributeName);
            return method.invoke(annotation);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to get annotation value: " + attributeName, e);
        }
    }

    /**
     * Returns the underlying Annotation object.
     */
    public Annotation getAnnotation() {
        return annotation;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ReflectionAnnotationModel)) return false;
        ReflectionAnnotationModel other = (ReflectionAnnotationModel) obj;
        return annotation.equals(other.annotation);
    }

    @Override
    public int hashCode() {
        return annotation.hashCode();
    }

    @Override
    public String toString() {
        return "ReflectionAnnotationModel[@" + getType().getSimpleName() + "]";
    }

    @Override
    public Map<String, Object> getValuesMap() {
        return List.of(annotation.annotationType().getDeclaredMethods()).stream()
            .collect(Collectors.toMap(
                Method::getName,
                m -> {
                    try {
                        return m.invoke(annotation);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException("Failed to get annotation value: " + m.getName(), e);
                    }
                }
            ));
    }
}
