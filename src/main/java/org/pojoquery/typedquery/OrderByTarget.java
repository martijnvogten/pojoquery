package org.pojoquery.typedquery;

/**
 * Interface for objects that can receive ORDER BY clauses.
 */
public interface OrderByTarget {
    /**
     * Adds an ORDER BY clause for the given field expression.
     * @param fieldExpression the field expression (e.g., "{a.name}")
     * @param ascending true for ASC, false for DESC
     */
    void orderBy(String fieldExpression, boolean ascending);
}
