package nl.pojoquery;

import java.util.HashMap;
import java.util.Map;

public class TestUtils {

	public static String norm(String str) {
		// replace any whitespace with single space
		return str.replaceAll("\\s+", " ").trim();
	}

	public static <K,V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3) {
		Map<K,V> result = new HashMap<K,V>();
		result.put(k1, v1);
		result.put(k2, v2);
		result.put(k3, v3);
		return result;
	}

	public static <K,V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
		Map<K,V> result = new HashMap<K,V>();
		result.put(k1, v1);
		result.put(k2, v2);
		result.put(k3, v3);
		result.put(k4, v4);
		return result;
	}
	
	public static <K,V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
		Map<K,V> result = new HashMap<K,V>();
		result.put(k1, v1);
		result.put(k2, v2);
		result.put(k3, v3);
		result.put(k4, v4);
		result.put(k5, v5);
		return result;
	}
	
}
