package org.pojoquery.typedquery;

/**
 * Represents a field that can be used in ORDER BY clauses.
 * Provides fluent methods for specifying sort direction.
 *
 * @param <Q> the query type returned for fluent chaining
 */
public class OrderByField<Q> {
    private final String tableAlias;
    private final String columnName;
    private final OrderByTarget target;
    private final Q query;

    /**
     * Creates a new order by field.
     * @param target the target to receive the ORDER BY clause
     * @param query the query to return for fluent chaining
     * @param tableAlias the table alias
     * @param columnName the column name
     */
    public OrderByField(OrderByTarget target, Q query, String tableAlias, String columnName) {
        this.target = target;
        this.query = query;
        this.tableAlias = tableAlias;
        this.columnName = columnName;
    }

    /**
     * Adds an ascending ORDER BY clause for this field.
     * @return the query for further chaining
     */
    public Q asc() {
        target.orderBy("{" + tableAlias + "." + columnName + "}", true);
        return query;
    }

    /**
     * Adds a descending ORDER BY clause for this field.
     * @return the query for further chaining
     */
    public Q desc() {
        target.orderBy("{" + tableAlias + "." + columnName + "}", false);
        return query;
    }
}
