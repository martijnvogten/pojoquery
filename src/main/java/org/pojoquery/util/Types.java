package org.pojoquery.util;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;

public class Types {

	/**
	 * Determines if the first type is the same as, or is a supertype of, the second type.
	 * This is equivalent to {@code superType.isAssignableFrom(subType)} for Class objects.
	 *
	 * @param superType the type to check as potential supertype
	 * @param subType the type to check as potential subtype
	 * @return true if superType is assignable from subType
	 */
	public static boolean isAssignableFrom(TypeModel superType, TypeModel subType) {
		if (superType == null || subType == null) {
			return false;
		}
		
		// Fast path for ReflectionTypeModel - use Class.isAssignableFrom
		if (superType instanceof ReflectionTypeModel && subType instanceof ReflectionTypeModel) {
			Class<?> superClass = ((ReflectionTypeModel) superType).getReflectionClass();
			Class<?> subClass = ((ReflectionTypeModel) subType).getReflectionClass();
			return superClass.isAssignableFrom(subClass);
		}
		
		// General path: walk the type hierarchy using qualified names
		if (superType.isSameType(subType)) {
			return true;
		}
		
		// Check superclass hierarchy
		TypeModel currentSuper = subType.getSuperclass();
		while (currentSuper != null) {
			if (superType.isSameType(currentSuper)) {
				return true;
			}
			currentSuper = currentSuper.getSuperclass();
		}
		
		return false;
	}

	/**
	 * Gets the component type of a collection or array field.
	 * For arrays, returns the array component type.
	 * For generic collections, extracts the type parameter.
	 */
	public static TypeModel getCollectionComponentType(FieldModel field) {
		TypeModel type = field.getType();
		if (type.isArray()) {
			return type.getArrayComponentType();
		}
		return field.getType().getTypeArgument();
	}

	/**
	 * Finds an annotation mirror by annotation simple name on the given element.
	 * Useful in annotation processors where you can't directly access annotation classes.
	 */
	public static AnnotationMirror getAnnotationMirror(Element element, String annotationName) {
		for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
			DeclaredType annotationType = mirror.getAnnotationType();
			TypeElement typeElement = (TypeElement) annotationType.asElement();
			if (typeElement.getSimpleName().toString().equals(annotationName)) {
				return mirror;
			}
		}
		return null;
	}

	/**
	 * Gets a specific value from an annotation mirror by key name.
	 */
	public static AnnotationValue getAnnotationValue(AnnotationMirror mirror, String key) {
		for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry 
				: mirror.getElementValues().entrySet()) {
			if (entry.getKey().getSimpleName().toString().equals(key)) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * Gets array values from an annotation attribute on an element.
	 * Use this for annotation attributes declared as arrays (e.g., {@code Class<?>[] value()}).
	 * 
	 * @param element the element that has the annotation
	 * @param annotation the annotation instance (used to determine the annotation type)
	 * @param attributeName the attribute name
	 * @return list of AnnotationValue objects, or empty list if not found or not an array
	 */
	@SuppressWarnings("unchecked")
	public static List<Object> getAnnotationMirrorValues(Element element, Annotation annotation, String attributeName) {
		AnnotationMirror mirror = getAnnotationMirror(element, annotation.annotationType().getSimpleName());
		if (mirror != null) {
			AnnotationValue value = getAnnotationValue(mirror, attributeName);
			if (value != null) {
				if (value.getValue() instanceof java.util.List) {
					return (List<Object>) value.getValue();
				} else {
					return List.of(value.getValue());
				}
			}
		}
		return java.util.List.of();
	}
}

