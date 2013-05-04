package system.util;

import java.lang.reflect.Method;
import java.util.Map;

public class ClassInfo {

	private Map<String, Method> setters;
	private Map<String, Method> getters;

	public void setSetters(Map<String, Method> setters) {
		this.setters = setters;
	}

	public Method getSetter(String prop) {
		return setters.get(prop.toLowerCase());
	}

	public Method getGetter(String prop) {
		return getters.get(prop.toLowerCase());
	}
	
	public Map<String, Method> getGetters() {
		return getters;
	}

	public void setGetters(Map<String, Method> getters) {
		this.getters = getters;
	}

}