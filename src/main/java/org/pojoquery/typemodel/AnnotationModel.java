package org.pojoquery.typemodel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstraction over annotation introspection that works with both runtime Annotations
 * and compile-time AnnotationMirrors.
 *
 * <p>This interface provides uniform access to annotation type information and values
 * regardless of whether we're at compile time or runtime. Implementations exist for:
 * <ul>
 *   <li>{@link ReflectionAnnotationModel} - wraps {@code Annotation} for runtime use</li>
 *   <li>{@link ElementAnnotationModel} - wraps {@code AnnotationMirror} for annotation processing</li>
 * </ul>
 * 
 * <p>All value accessors return {@code List} to uniformly handle both single values
 * and array attributes. Single values are returned as single-element lists.
 */
public interface AnnotationModel {

    /**
     * Returns the type of this annotation.
     */
    TypeModel getType();

    /**
     * Returns string values for the specified attribute.
     * Class attributes are converted to fully qualified class names.
     *
     * @param attributeName the annotation attribute name
     * @return list of string values, empty if attribute not present
     */
    List<String> getStringValues(String attributeName);

    /**
     * Returns enum constant names for the specified attribute.
     *
     * @param attributeName the annotation attribute name
     * @return list of enum constant names, empty if attribute not present
     */
    List<String> getEnumValues(String attributeName);

    /**
     * Returns nested annotation values for the specified attribute.
     *
     * @param attributeName the annotation attribute name
     * @return list of annotation models, empty if attribute not present
     */
    List<AnnotationModel> getNestedAnnotations(String attributeName);

    /**
     * Returns numeric values for the specified attribute.
     * Handles int, long, double, float, short, byte attributes.
     *
     * @param attributeName the annotation attribute name
     * @return list of numbers, empty if attribute not present
     */
    List<Number> getNumberAttributes(String attributeName);

    /**
     * Returns boolean values for the specified attribute.
     *
     * @param attributeName the annotation attribute name
     * @return list of boolean values, empty if attribute not present
     */
    List<Boolean> getBooleanValues(String attributeName);

    /**
     * Returns class/type values for the specified attribute.
     *
     * @param attributeName the annotation attribute name
     * @return list of type models, empty if attribute not present
     */
    List<TypeModel> getClassValues(String attributeName);

    // Convenience methods for "value" attribute

    default Optional<String> getStringValue(String attributeName) {
        return getStringValues(attributeName).stream().findFirst();
    }

    default Optional<String> getStringValue() {
        return getStringValues().stream().findFirst();
    }

    default List<String> getStringValues() {
        return getStringValues("value").stream().filter(s -> !s.isEmpty()).toList();
    }

	default <E extends Enum<E>> E getEnumValue(Class<E> enumType, String attributeName) {
		String stringValue = getEnumValues(attributeName).stream().findFirst().orElse(null);
		if (stringValue == null) {
			return null;
		}
		return Enum.valueOf(enumType, stringValue);
	}

    default List<String> getEnumValues() {
        return getEnumValues("value");
    }

    default List<AnnotationModel> getAnnotationValues() {
        return getNestedAnnotations("value");
    }

    default Optional<Number> getNumberAttribute(String attr) {
        return getNumberAttributes(attr).stream().findFirst();
    }

    default Optional<Boolean> getBooleanAttribute(String attr) {
        return getBooleanValues(attr).stream().findFirst();
    }

    default List<Number> getNumberAttributes() {
        return getNumberAttributes("value");
    }

    default List<Boolean> getBooleanAttributes() {
        return getBooleanValues("value");
    }

    default List<TypeModel> getClassAttributes() {
        return getClassValues("value");
    }

    default boolean hasAttribute(String attributeName) {
        return getValuesMap().containsKey(attributeName);
    }

	Map<String,Object> getValuesMap();
}
