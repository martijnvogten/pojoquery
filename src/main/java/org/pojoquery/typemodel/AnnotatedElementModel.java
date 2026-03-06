package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.util.List;

public interface AnnotatedElementModel {

    /**
     * Returns the annotation of the specified type if present on this element.
     *
     * @param annotationType the Class object corresponding to the annotation type
     * @return the annotation if present, null otherwise
     */
    AnnotationModel getAnnotation(Class<? extends Annotation> annotationType);

    /**
     * Returns true if this element has the specified annotation.
     *
     * @param annotationType the annotation type to check for
     * @return true if the annotation is present
     */
    boolean hasAnnotation(Class<? extends Annotation> annotationType);

    /**
     * Returns all annotations of the specified type on this element.
     * This handles repeatable annotations by unwrapping container annotations.
     *
     * @param annotationType the annotation type to find
     * @return list of matching annotation models, empty if none found
     */
    List<AnnotationModel> getAnnotationsByType(Class<? extends Annotation> annotationType);

    /**
     * Extracts Class[] values from an annotation attribute and returns them as TypeModels.
     * This is used to read annotation attributes like {@code @SubClasses(value = {A.class, B.class})}.
     *
     * @param annotation the annotation instance
     * @param attributeName the name of the attribute containing Class[] values
     * @return list of TypeModels for the classes referenced in the annotation attribute
     */
    List<TypeModel> getTypeValuesFromAnnotation(Annotation annotation, String attributeName);
}
