package org.pojoquery.typemodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Implementation of {@link AnnotationModel} that wraps a compile-time {@link AnnotationMirror}.
 *
 * <p>This implementation is used during annotation processing for compile-time inspection.
 */
public class ElementAnnotationModel implements AnnotationModel {

    private final AnnotationMirror annotationMirror;
    private final Elements elements;
    private final Types types;

    /**
     * Creates an AnnotationModel wrapping the given AnnotationMirror.
     *
     * @param annotationMirror the annotation mirror to wrap
     * @param elements the Elements utility from the processing environment
     * @param types the Types utility from the processing environment
     */
    public ElementAnnotationModel(AnnotationMirror annotationMirror, Elements elements, Types types) {
        this.annotationMirror = Objects.requireNonNull(annotationMirror, "annotationMirror must not be null");
        this.elements = Objects.requireNonNull(elements, "elements must not be null");
        this.types = Objects.requireNonNull(types, "types must not be null");
    }

    @Override
    public TypeModel getType() {
        return new ElementTypeModel(annotationMirror.getAnnotationType(), elements, types);
    }

    @Override
    public List<String> getStringValues(String attributeName) {
        AnnotationValue av = getRawValue(attributeName);
        if (av == null) return Collections.emptyList();
        
        List<String> result = new ArrayList<>();
        Object value = av.getValue();
        
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof AnnotationValue av2) {
                    Object v = av2.getValue();
                    if (v instanceof String s) {
                        result.add(s);
                    } else if (v instanceof TypeMirror tm) {
                        result.add(tm.toString());
                    }
                }
            }
        } else if (value instanceof String s) {
            result.add(s);
        } else if (value instanceof TypeMirror tm) {
            result.add(tm.toString());
        }
        return result;
    }

    @Override
    public List<String> getEnumValues(String attributeName) {
        return extractValues(attributeName, VariableElement.class, ve -> ve.getSimpleName().toString());
    }

    private <T, R> List<R> extractValues(String attributeName,
                                          Class<T> valueType,
                                          Function<T, R> mapper) {
        AnnotationValue av = getRawValue(attributeName);
        if (av == null) return Collections.emptyList();

        List<R> result = new ArrayList<>();
        Object value = av.getValue();

        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof AnnotationValue av2) {
                    Object v = av2.getValue();
                    if (valueType.isInstance(v)) {
                        result.add(mapper.apply(valueType.cast(v)));
                    }
                }
            }
        } else if (valueType.isInstance(value)) {
            result.add(mapper.apply(valueType.cast(value)));
        }
        return result;
    }

    private AnnotationValue getRawValue(String attributeName) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> values = 
            elements.getElementValuesWithDefaults(annotationMirror);
        
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals(attributeName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Returns the underlying AnnotationMirror.
     */
    public AnnotationMirror getAnnotationMirror() {
        return annotationMirror;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ElementAnnotationModel)) return false;
        ElementAnnotationModel other = (ElementAnnotationModel) obj;
        return annotationMirror.equals(other.annotationMirror);
    }

    @Override
    public int hashCode() {
        return annotationMirror.hashCode();
    }

    @Override
    public String toString() {
        return "ElementAnnotationModel[@" + getType().getSimpleName() + "]";
    }

    @Override
    public Map<String, Object> getValuesMap() {
        Map<String, Object> result = new java.util.HashMap<>();
        Map<? extends ExecutableElement, ? extends AnnotationValue> values = 
            elements.getElementValuesWithDefaults(annotationMirror);
        
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
            String name = entry.getKey().getSimpleName().toString();
            Object value = entry.getValue().getValue();
            result.put(name, value);
        }
        return Collections.unmodifiableMap(result);
    }
}
