package org.pojoquery.util;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for processing curly brace markers in SQL strings.
 * Markers follow the pattern {@code {alias.column}} or {@code {alias}}.
 */
public class CurlyMarkers {
    
    private static final Pattern MARKER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_\\.]+)\\}");
    
    /**
     * Processes markers in the form {identifier} and replaces them using the provided function.
     * @param source the source string containing markers
     * @param replacer the function to transform each identifier
     * @return the processed string with markers replaced
     */
    public static String processMarkers(String source, Function<String,String> replacer) {
        StringBuilder result = new StringBuilder();
        Matcher m = MARKER_PATTERN.matcher(source);
        while(m.find()) {
            String identifier = m.group(1);
            m.appendReplacement(result, replacer.apply(identifier));
        }
        m.appendTail(result);

        return result.toString();
    }
    
    /**
     * Extracts all table/node aliases referenced in an expression.
     * For {@code {alias.column}} returns "alias", for {@code {alias}} returns "alias".
     * 
     * @param expression The expression to scan (may be null)
     * @return Set of referenced aliases
     */
    public static Set<String> extractAliases(String expression) {
        Set<String> aliases = new HashSet<>();
        if (expression == null) return aliases;
        
        Matcher m = MARKER_PATTERN.matcher(expression);
        while (m.find()) {
            String ref = m.group(1);
            int lastDot = ref.lastIndexOf('.');
            aliases.add(lastDot > 0 ? ref.substring(0, lastDot) : ref);
        }
        return aliases;
    }
    
    /**
     * Extracts the column name for a specific alias from an expression.
     * If expression contains {@code {myAlias.myColumn}}, calling with "myAlias" returns "myColumn".
     * 
     * @param expression The expression to scan
     * @param alias The alias to find the column for
     * @return The column name, or null if not found
     */
    public static String extractColumnForAlias(String expression, String alias) {
        if (expression == null || alias == null) return null;
        
        String prefix = "{" + alias + ".";
        int start = expression.indexOf(prefix);
        if (start >= 0) {
            int end = expression.indexOf("}", start);
            if (end > start + prefix.length()) {
                return expression.substring(start + prefix.length(), end);
            }
        }
        return null;
    }
    
    /**
     * Extracts the column name from a simple {@code {alias.column}} expression.
     * Returns the part after the last dot, before the closing brace.
     * 
     * @param expression The expression (e.g., "{article.title}")
     * @return The column name (e.g., "title"), or the expression itself if no match
     */
    public static String extractColumnName(String expression) {
        if (expression == null) return null;
        
        Matcher m = MARKER_PATTERN.matcher(expression);
        if (m.find()) {
            String ref = m.group(1);
            int lastDot = ref.lastIndexOf('.');
            return lastDot > 0 ? ref.substring(lastDot + 1) : ref;
        }
        return expression;
    }
    
    /**
     * Extracts the alias from a simple {@code {alias.column}} expression.
     * Returns the part before the last dot.
     * 
     * @param expression The expression (e.g., "{article.title}")
     * @return The alias (e.g., "article"), or the expression itself if no match
     */
    public static String extractAlias(String expression) {
        if (expression == null) return null;
        
        Matcher m = MARKER_PATTERN.matcher(expression);
        if (m.find()) {
            String ref = m.group(1);
            int lastDot = ref.lastIndexOf('.');
            return lastDot > 0 ? ref.substring(0, lastDot) : ref;
        }
        return expression;
    }
}
