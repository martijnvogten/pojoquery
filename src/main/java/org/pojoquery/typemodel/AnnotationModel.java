package org.pojoquery.typemodel;

import java.util.List;
import java.util.Map;

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

	Map<String,Object> getValuesMap();
}
