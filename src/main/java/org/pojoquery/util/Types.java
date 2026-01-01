package org.pojoquery.util;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class Types {

	/**
	 * Gets the component type of a generic type (e.g., the String in List&lt;String&gt;).
	 */
	public static Class<?> getComponentType(Type genericType) {
		if (genericType instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) genericType;
			for(Type p : pt.getActualTypeArguments()) {
				if (p instanceof Class) {
					Class<?> cls = (Class<?>) p;
					return cls;
				}
			}
		}
		return null;
	}
	
	/**
	 * Gets the component type of a collection or array field.
	 * For arrays, returns the array component type.
	 * For generic collections, extracts the type parameter.
	 */
	public static Class<?> getCollectionComponentType(Field field) {
		Class<?> type = field.getType();
		if (type.isArray()) {
			return type.getComponentType();
		}
		return getComponentType(field.getGenericType());
	}
}
