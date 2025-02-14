package nl.pojoquery.util;

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

	public static boolean isNullOrEmpty(String tableName) {
		return tableName == null || tableName.isEmpty();
	}

}
