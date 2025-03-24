package nl.pojoquery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TestUtils {

	/** normalize SQL into a nicely readable format */
	public static String norm(String str) {
		return str.replaceAll("\\s+", " ").trim()
				.replaceAll("(SELECT|,)", "$1\n")
				.replaceAll(" (WHERE|FROM|HAVING|GROUP BY|ORDER BY)", "\n$1")
				.replaceAll(" (LEFT JOIN|INNER JOIN)", "\n $1");
	}
	
	public static List<Map<String, Object>> resultSet(String[] columns, Object... values) {
		ArrayList<Map<String, Object>> result = new ArrayList<Map<String,Object>>();
		if (values.length % columns.length != 0) {
			throw new IllegalArgumentException("values count does not match column count");
		}
		Iterator<Object> iter = Arrays.asList(values).iterator();
		while(iter.hasNext()) {
			Map<String,Object> row = new HashMap<String,Object>();
			for(String col : columns) {
				row.put(col, iter.next());
			}
			result.add(row);
		}
		return result;
	}
	
	
}
