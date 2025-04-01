package org.pojoquery.util;

public class Strings {

	public static String implode(String glue, Iterable<String> pieces) {
		StringBuilder result = new StringBuilder();
		for(String piece : pieces) {
			if (result.length() > 0) {
				result.append(glue);
			}
			result.append(piece);
		}
		return result.toString();
	}

	public static boolean isNullOrEmpty(String str) {
		return str == null || str.isEmpty();
	}

}
