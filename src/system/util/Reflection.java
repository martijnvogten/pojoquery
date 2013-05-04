package system.util;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class Reflection {

	private static Map<Class<?>, ClassInfo> cache = new HashMap<Class<?>, ClassInfo>();

	public static void copyProperties(Object source, Object target) {
		Method[] sourceMethods = source.getClass().getMethods();
		Method[] targetMethods = target.getClass().getMethods();
		for(Method m : sourceMethods) {
			Class<?> returnType = m.getReturnType();
			if (returnType == null) {
				continue;
			}
			String propertyName = propertyNameForGetter(m.getName());
			if(propertyName == null) {
				continue;
			}
			// Is there a setter in target?
			String setterName = setterNameForProperty(propertyName);
			for(Method tm : targetMethods) {
				if (!tm.getName().equals(setterName)) {
					continue;
				}
				if (tm.getParameterTypes().length != 1) {
					continue;
				}
				if (tm.getParameterTypes()[0].equals(m.getReturnType())) {
					try {
						tm.invoke(target, m.invoke(source));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
	}

	private static String setterNameForProperty(String propertyName) {
		return "set" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
	}

	private static String propertyNameForGetter(String name) {
		if ("getClass".equals(name) || "toString".equals(name) || "hashCode".equals(name)) {
			return null;
		}
		if (name.length() > 3 && name.startsWith("get")) {
			return name.substring(3, 4).toLowerCase() + name.substring(4);
		}
		if (name.length() > 2 && name.startsWith("is")) {
			return name.substring(2, 3).toLowerCase() + name.substring(3);
		}
		return null;
	}

	private static String propertyNameForSetter(String name) {
		if (name.length() > 3 && name.startsWith("set")) {
			return name.substring(3, 4).toLowerCase() + name.substring(4);
		}
		return null;
	}
	
	

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void setProperties(Map<String, ?> parameterMap, Object target) {
		try {
			ClassInfo classInfo = getClassInfo(target.getClass());
			for(String prop : parameterMap.keySet()) {
				Method m = classInfo.getSetter(prop); 
				if (m == null) {
					continue;
				}
				Object value = parameterMap.get(prop);
				if (value == null) {
					m.invoke(target, new Object[] {null});
				} else { 
					Class<?> paramType = m.getParameterTypes()[0];
					if (value instanceof String && paramType.equals(Long.class)) {
						if (((String)value).length() == 0) {
							m.invoke(target, 0L);
						} else {
							m.invoke(target, Long.parseLong((String) value));
						}
					}else if (value instanceof Integer && paramType.equals(Long.class)) {
						m.invoke(target, ((Integer)value).longValue());
					} else if (value instanceof Integer && paramType.equals(Boolean.class)) {
						m.invoke(target, value != null && value.equals(1) ? Boolean.TRUE : Boolean.FALSE);
					} else if (value instanceof String && paramType.equals(Integer.class)) {
						m.invoke(target, Integer.valueOf((String)value));
					} else {
						if (paramType.isEnum()) {
							m.invoke(target, Enum.valueOf((Class<? extends Enum>) paramType, (String)value));
						} else if (m.getParameterTypes()[0].isAssignableFrom(value.getClass())) {
							m.invoke(target, value);
						}
					}
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static ClassInfo getClassInfo(Class<?> targetClass) {
		if (!cache.containsKey(targetClass)) {
			cache.put(targetClass, buildClassInfo(targetClass));
		}
		ClassInfo classInfo = cache.get(targetClass);
		return classInfo;
	}

	private static ClassInfo buildClassInfo(Class<?> targetClass) {
		ClassInfo result = new ClassInfo();
		Map<String,Method> setters = new HashMap<String,Method>();
		Map<String,Method> getters = new HashMap<String,Method>();
		Map<String,Method> allMethods = new HashMap<String,Method>();
		for(Method m : targetClass.getMethods()) {
			allMethods.put(m.getName(), m);
		}
		for(Method m : targetClass.getMethods()) {
			String prop = propertyNameForSetter(m.getName());
			if (prop == null || m.getParameterTypes().length != 1) {
				continue;
			}
			// Found a setter
			setters.put(prop.toLowerCase(), m);
			// Find a corresponding getter
			for(String methodName : allMethods.keySet()) {
				if (prop.equalsIgnoreCase(propertyNameForGetter(methodName))) {
					getters.put(prop.toLowerCase(), allMethods.get(methodName));
					break;
				}
			}
		}
		result.setSetters(setters);
		result.setGetters(getters);
		return result;
	}

	public static Map<String,Object> getProperties(Object source) {
		ClassInfo classInfo = getClassInfo(source.getClass());
		Map<String, Object> result = new HashMap<String,Object>();
		for(Method getter : classInfo.getGetters().values()) {
			String prop = propertyNameForGetter(getter.getName());
			try {
				result.put(prop, getter.invoke(source));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return result;
	}

	public static void setProperty(String field, Object value, Object target) {
		Map<String, Object> props = new HashMap<String,Object>();
		props.put(field, value);
		setProperties(props, target);
	}

}
