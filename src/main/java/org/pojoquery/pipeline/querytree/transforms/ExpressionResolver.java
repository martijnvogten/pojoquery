package org.pojoquery.pipeline.querytree.transforms;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.util.CurlyMarkers;

/**
 * Helper methods for resolving alias placeholders in SQL expressions.
 */
public final class ExpressionResolver {
    
    private static final Pattern ALIAS_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_\\.]+)\\}");
    
    private ExpressionResolver() {}
    
    /**
     * Resolves {this.x} placeholders to {alias.x}.
     * 
     * @param expression The expression with placeholders
     * @param thisAlias The alias to substitute for "this"
     * @return The resolved expression
     */
    public static String resolve(String expression, String thisAlias) {
        return resolve(expression, thisAlias, null, null);
    }
    
    /**
     * Resolves placeholders in a join condition or expression.
     * Supports: {this}, {this.field}, {linktable}, {linktable.field}, {alias.field}
     * 
     * @param expression The expression with placeholders
     * @param thisAlias The alias to substitute for "this"
     * @param linkAlias The alias to substitute for simple alias references
     * @param linkTableAlias The alias to substitute for "linktable"
     * @return The resolved expression
     */
    public static String resolve(String expression, String thisAlias, 
                                  String linkAlias, String linkTableAlias) {
        if (expression == null) return null;
        
        return CurlyMarkers.processMarkers(expression, marker -> {
            if ("this".equals(marker)) {
                return "{" + thisAlias + "}";
            }
            if (marker.startsWith("this.")) {
                String rest = marker.substring(5);
                return "{" + thisAlias + "." + rest + "}";
            }
            if ("linktable".equals(marker) && linkTableAlias != null) {
                return "{" + linkTableAlias + "}";
            }
            if (marker.startsWith("linktable.") && linkTableAlias != null) {
                String rest = marker.substring(10);
                return "{" + linkTableAlias + "." + rest + "}";
            }
            if (marker.contains(".") && linkAlias != null) {
                int dot = marker.indexOf('.');
                String markerAlias = marker.substring(0, dot);
                String rest = marker.substring(dot + 1);
                
                // If linkAlias ends with the marker alias, resolve to full linkAlias
                if (linkAlias.equals(markerAlias) || linkAlias.endsWith("." + markerAlias)) {
                    return "{" + linkAlias + "." + rest + "}";
                }
            }
            return "{" + marker + "}";
        });
    }
    
    /**
     * Extracts all aliases referenced in an expression.
     * 
     * @param expression The expression to scan
     * @return Set of referenced aliases (table aliases, not column names)
     */
    public static Set<String> extractAliases(String expression) {
        Set<String> aliases = new HashSet<>();
        if (expression == null) return aliases;
        
        Matcher m = ALIAS_PATTERN.matcher(expression);
        while (m.find()) {
            String ref = m.group(1);
            int lastDot = ref.lastIndexOf('.');
            if (lastDot > 0) {
                aliases.add(ref.substring(0, lastDot));
            } else {
                aliases.add(ref);
            }
        }
        return aliases;
    }
    
    /**
     * Extracts aliases from a SqlExpression.
     */
    public static Set<String> extractAliases(SqlExpression expr) {
        return expr == null ? Set.of() : extractAliases(expr.getSql());
    }
    
    /**
     * Extracts all aliases referenced in field selections.
     */
    public static Set<String> extractAliasesFromFields(List<FieldSelection> fields) {
        Set<String> aliases = new HashSet<>();
        for (FieldSelection f : fields) {
            aliases.addAll(extractAliases(f.expression()));
        }
        return aliases;
    }
}
