package org.pojoquery.pipeline.querytree;

/**
 * Package-private interface for nodes that support indented string formatting.
 * Used by QueryTree for debug output.
 */
interface HasToStringWithIndent {
    String toStringWithIndent(String indent);
}
