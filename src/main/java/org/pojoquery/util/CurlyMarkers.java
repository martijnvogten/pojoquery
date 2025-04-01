package org.pojoquery.util;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CurlyMarkers {
	public static String processMarkers(String source, Function<String,String> replacer) {
		StringBuilder result = new StringBuilder();
		Matcher m = Pattern.compile("\\{([a-zA-Z0-9_\\.]+)\\}").matcher(source);
		while(m.find()) {
			String identifier = m.group(1);
			m.appendReplacement(result, replacer.apply(identifier));
		}
		m.appendTail(result);
		
		return result.toString();
	}
}
