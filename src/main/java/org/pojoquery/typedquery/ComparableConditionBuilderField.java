package org.pojoquery.typedquery;

import static org.pojoquery.SqlExpression.sql;

/**
 * Extension of {@link ConditionBuilderField} for comparable types.
 * Adds comparison operations (gt, lt, ge, le) in addition to the basic operations.
 *
 * @param <T> the field type
 * @param <C> the continuation type returned after adding a condition
 */
public class ComparableConditionBuilderField<T, C> extends ConditionBuilderField<T, C> {

    /**
     * Creates a new comparable condition builder field.
     * @param chainFactory the factory for creating condition chains
     * @param tableAlias the table alias
     * @param columnName the column name
     */
    public ComparableConditionBuilderField(ChainFactory<C> chainFactory, String tableAlias, String columnName) {
        super(chainFactory, tableAlias, columnName);
    }

    /**
     * Adds a greater-than condition.
     * @param other the value to compare against
     * @return the continuation for fluent chaining
     */
    public C gt(T other) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} > ?", other));
        return op.getContinuation();
    }

    /**
     * Adds a greater-than condition comparing to another field.
     * @param other the field to compare against
     * @return the continuation for fluent chaining
     */
    public C gt(ConditionBuilderField<T, ?> other) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} > {" + other.tableAlias + "." + other.columnName + "}"));
        return op.getContinuation();
    }

    /**
     * Adds a greater-than condition comparing to an expression.
     * @param expr the expression to compare against
     * @return the continuation for fluent chaining
     */
    public C gt(FieldExpression<T> expr) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} > " + expr.getSql(), expr.getParameters().toArray()));
        return op.getContinuation();
    }

    /**
     * Adds a less-than condition.
     * @param other the value to compare against
     * @return the continuation for fluent chaining
     */
    public C lt(T other) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} < ?", other));
        return op.getContinuation();
    }

    /**
     * Adds a less-than condition comparing to another field.
     * @param other the field to compare against
     * @return the continuation for fluent chaining
     */
    public C lt(ConditionBuilderField<T, ?> other) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} < {" + other.tableAlias + "." + other.columnName + "}"));
        return op.getContinuation();
    }

    /**
     * Adds a less-than condition comparing to an expression.
     * @param expr the expression to compare against
     * @return the continuation for fluent chaining
     */
    public C lt(FieldExpression<T> expr) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} < " + expr.getSql(), expr.getParameters().toArray()));
        return op.getContinuation();
    }

    /**
     * Adds a greater-than-or-equal condition.
     * @param other the value to compare against
     * @return the continuation for fluent chaining
     */
    public C ge(T other) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} >= ?", other));
        return op.getContinuation();
    }

    /**
     * Adds a greater-than-or-equal condition comparing to another field.
     * @param other the field to compare against
     * @return the continuation for fluent chaining
     */
    public C ge(ConditionBuilderField<T, ?> other) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} >= {" + other.tableAlias + "." + other.columnName + "}"));
        return op.getContinuation();
    }

    /**
     * Adds a greater-than-or-equal condition comparing to an expression.
     * @param expr the expression to compare against
     * @return the continuation for fluent chaining
     */
    public C ge(FieldExpression<T> expr) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} >= " + expr.getSql(), expr.getParameters().toArray()));
        return op.getContinuation();
    }

    /**
     * Adds a less-than-or-equal condition.
     * @param other the value to compare against
     * @return the continuation for fluent chaining
     */
    public C le(T other) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} <= ?", other));
        return op.getContinuation();
    }

    /**
     * Adds a less-than-or-equal condition comparing to another field.
     * @param other the field to compare against
     * @return the continuation for fluent chaining
     */
    public C le(ConditionBuilderField<T, ?> other) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} <= {" + other.tableAlias + "." + other.columnName + "}"));
        return op.getContinuation();
    }

    /**
     * Adds a less-than-or-equal condition comparing to an expression.
     * @param expr the expression to compare against
     * @return the continuation for fluent chaining
     */
    public C le(FieldExpression<T> expr) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} <= " + expr.getSql(), expr.getParameters().toArray()));
        return op.getContinuation();
    }

    public C like(String pattern) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} LIKE ?", pattern));
        return op.getContinuation();
    }

    public C notLike(String pattern) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} NOT LIKE ?", pattern));
        return op.getContinuation();
    }

    public C between(T start, T end) {
        var op = chainFactory.createChain();
        op.getBuilder().add(sql("{" + tableAlias + "." + columnName + "} BETWEEN ? AND ?", start, end));
        return op.getContinuation();
    }
}
