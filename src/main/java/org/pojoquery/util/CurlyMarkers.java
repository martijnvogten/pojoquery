package org.pojoquery.util;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for processing curly brace markers in SQL strings.
 */
public class CurlyMarkers {
    /**
     * Processes markers in the form {identifier} and replaces them using the provided function.
     * @param source the source string containing markers
     * @param replacer the function to transform each identifier
     * @return the processed string with markers replaced
     */
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
