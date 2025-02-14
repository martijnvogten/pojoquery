package nl.pojoquery.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class Types {

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
}
