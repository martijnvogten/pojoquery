package org.pojoquery.util;

/**
 * Utility to format nested record toString() output with proper indentation.
 */
public class RecordIndenter {

	public static String indent(String input) {
		StringBuilder result = new StringBuilder();
		int indentLevel = 0;
		int inlineDepth = 0; // > 0 means we're inside a leaf bracket that stays inline
		boolean inString = false;
		
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			
			if (c == '"' && (i == 0 || input.charAt(i - 1) != '\\')) {
				inString = !inString;
				result.append(c);
			} else if (inString) {
				result.append(c);
			} else if (c == '[') {
				if (inlineDepth > 0 || isLeaf(input, i)) {
					inlineDepth++;
					result.append(c);
				} else {
					result.append("[\n");
					indentLevel++;
					appendIndent(result, indentLevel);
				}
			} else if (c == ']') {
				if (inlineDepth > 0) {
					inlineDepth--;
					result.append(c);
				} else {
					result.append("\n");
					indentLevel--;
					appendIndent(result, indentLevel);
					result.append("]");
				}
			} else if (c == ',' && inlineDepth == 0) {
				result.append(",\n");
				appendIndent(result, indentLevel);
			} else if (c == ' ' && i > 0 && input.charAt(i - 1) == ',' && inlineDepth == 0) {
				// Skip space after comma (we already added newline + indent)
			} else {
				result.append(c);
			}
		}
		
		return result.toString();
	}

	/**
	 * Check if the bracket at position i is a "leaf" - contains no nested brackets.
	 */
	private static boolean isLeaf(String input, int openBracket) {
		int depth = 1;
		boolean inStr = false;
		for (int i = openBracket + 1; i < input.length() && depth > 0; i++) {
			char c = input.charAt(i);
			if (c == '"' && input.charAt(i - 1) != '\\') {
				inStr = !inStr;
			} else if (!inStr) {
				if (c == '[') {
					return false; // has nested bracket, not a leaf
				} else if (c == ']') {
					depth--;
				}
			}
		}
		return true;
	}

	private static void appendIndent(StringBuilder sb, int level) {
		sb.append("  ".repeat(level));
	}

}
