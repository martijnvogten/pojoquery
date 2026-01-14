package org.pojoquery.typedquery.chain;

import static org.pojoquery.SqlExpression.sql;

/**
 * Represents a field that can be used in condition expressions.
 * Provides basic comparison operations (eq, ne, isNull, isNotNull).
 *
 * @param <T> the field type
 * @param <C> the continuation type returned after adding a condition
 */
public class ConditionBuilderField<T, C> {
    /** The factory for creating condition chains. */
    protected final ChainFactory<C> chainFactory;
    /** The table alias. */
    protected final String tableAlias;
    /** The column name. */
    protected final String columnName;

    /**
     * Creates a new condition builder field.
     * @param chainFactory the factory for creating condition chains
     * @param tableAlias the table alias
     * @param columnName the column name
     */
    public ConditionBuilderField(ChainFactory<C> chainFactory, String tableAlias, String columnName) {
        this.chainFactory = chainFactory;
        this.tableAlias = tableAlias;
        this.columnName = columnName;
    }

    /**
     * Adds an equality condition.
     * @param other the value to compare against
     * @return the continuation for fluent chaining
     */
    public C eq(T other) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} = ?", other));
        return op.getContinuation();
    }

    /**
     * Adds a not-equal condition.
     * @param other the value to compare against
     * @return the continuation for fluent chaining
     */
    public C ne(T other) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} <> ?", other));
        return op.getContinuation();
    }

    /**
     * Adds an IS NULL condition.
     * @return the continuation for fluent chaining
     */
    public C isNull() {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} IS NULL"));
        return op.getContinuation();
    }

    /**
     * Adds an IS NOT NULL condition.
     * @return the continuation for fluent chaining
     */
    public C isNotNull() {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} IS NOT NULL"));
        return op.getContinuation();
    }
}
