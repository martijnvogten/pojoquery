package org.pojoquery.typedquery;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.SqlExpression;

/**
 * A SQL expression that supports chained conditions back to query fields.
 * This is used by SQL functions (concat, lower, etc.) to enable fluent chaining
 * like {@code q.concat(q.firstName, " ", q.lastName).eq("John Doe").and().id.gt(1L)}.
 *
 * @param <T> the result type of the expression
 * @param <C> the condition chain type (query-specific)
 */
public class ChainableExpression<T, C extends ConditionChain<C>> {
    private final String sql;
    private final List<Object> parameters;
    private final ChainFactory<C> chainFactory;

    public ChainableExpression(String sql, List<Object> parameters, ChainFactory<C> chainFactory) {
        this.sql = sql;
        this.parameters = parameters;
        this.chainFactory = chainFactory;
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    // === Comparison methods that return the chain for continued fluent building ===

    /**
     * Creates an equality condition comparing this expression to a value.
     * @param value the value to compare against
     * @return the condition chain for continued building
     */
    public C eq(T value) {
        ConditionChain<C> chain = chainFactory.createChain();
        List<Object> params = new ArrayList<>(parameters);
        params.add(value);
        chain.getBuilder().add(SqlExpression.sql(sql + " = ?", params.toArray()));
        return chain.getContinuation();
    }

    /**
     * Creates an equality condition comparing this expression to another field.
     * @param other the field to compare against
     * @return the condition chain for continued building
     */
    public C eq(ConditionBuilderField<T, ?> other) {
        ConditionChain<C> chain = chainFactory.createChain();
        chain.getBuilder().add(SqlExpression.sql(sql + " = {" + other.tableAlias + "." + other.columnName + "}", parameters.toArray()));
        return chain.getContinuation();
    }

    /**
     * Creates an equality condition comparing this expression to another expression.
     * @param other the expression to compare against
     * @return the condition chain for continued building
     */
    public C eq(ChainableExpression<T, ?> other) {
        ConditionChain<C> chain = chainFactory.createChain();
        List<Object> params = new ArrayList<>(parameters);
        params.addAll(other.getParameters());
        chain.getBuilder().add(SqlExpression.sql(sql + " = " + other.getSql(), params.toArray()));
        return chain.getContinuation();
    }

    /**
     * Creates a not-equal condition comparing this expression to a value.
     * @param value the value to compare against
     * @return the condition chain for continued building
     */
    public C ne(T value) {
        ConditionChain<C> chain = chainFactory.createChain();
        List<Object> params = new ArrayList<>(parameters);
        params.add(value);
        chain.getBuilder().add(SqlExpression.sql(sql + " <> ?", params.toArray()));
        return chain.getContinuation();
    }

    /**
     * Creates a not-equal condition comparing this expression to another field.
     * @param other the field to compare against
     * @return the condition chain for continued building
     */
    public C ne(ConditionBuilderField<T, ?> other) {
        ConditionChain<C> chain = chainFactory.createChain();
        chain.getBuilder().add(SqlExpression.sql(sql + " <> {" + other.tableAlias + "." + other.columnName + "}", parameters.toArray()));
        return chain.getContinuation();
    }

    /**
     * Creates an IS NULL condition.
     * @return the condition chain for continued building
     */
    public C isNull() {
        ConditionChain<C> chain = chainFactory.createChain();
        chain.getBuilder().add(SqlExpression.sql(sql + " IS NULL", parameters.toArray()));
        return chain.getContinuation();
    }

    /**
     * Creates an IS NOT NULL condition.
     * @return the condition chain for continued building
     */
    public C isNotNull() {
        ConditionChain<C> chain = chainFactory.createChain();
        chain.getBuilder().add(SqlExpression.sql(sql + " IS NOT NULL", parameters.toArray()));
        return chain.getContinuation();
    }

    /**
     * Creates a LIKE condition.
     * @param pattern the pattern to match (use % for wildcards)
     * @return the condition chain for continued building
     */
    public C like(String pattern) {
        ConditionChain<C> chain = chainFactory.createChain();
        List<Object> params = new ArrayList<>(parameters);
        params.add(pattern);
        chain.getBuilder().add(SqlExpression.sql(sql + " LIKE ?", params.toArray()));
        return chain.getContinuation();
    }

    /**
     * Creates a NOT LIKE condition.
     * @param pattern the pattern to match (use % for wildcards)
     * @return the condition chain for continued building
     */
    public C notLike(String pattern) {
        ConditionChain<C> chain = chainFactory.createChain();
        List<Object> params = new ArrayList<>(parameters);
        params.add(pattern);
        chain.getBuilder().add(SqlExpression.sql(sql + " NOT LIKE ?", params.toArray()));
        return chain.getContinuation();
    }

    /**
     * Creates an IN condition.
     * @param values the values to check against
     * @return the condition chain for continued building
     */
    @SuppressWarnings("unchecked")
    public C in(T... values) {
        ConditionChain<C> chain = chainFactory.createChain();
        List<Object> params = new ArrayList<>(parameters);
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) placeholders.append(", ");
            placeholders.append("?");
            params.add(values[i]);
        }
        chain.getBuilder().add(SqlExpression.sql(sql + " IN (" + placeholders + ")", params.toArray()));
        return chain.getContinuation();
    }

    /**
     * Creates a NOT IN condition.
     * @param values the values to check against
     * @return the condition chain for continued building
     */
    @SuppressWarnings("unchecked")
    public C notIn(T... values) {
        ConditionChain<C> chain = chainFactory.createChain();
        List<Object> params = new ArrayList<>(parameters);
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) placeholders.append(", ");
            placeholders.append("?");
            params.add(values[i]);
        }
        chain.getBuilder().add(SqlExpression.sql(sql + " NOT IN (" + placeholders + ")", params.toArray()));
        return chain.getContinuation();
    }

    // === Helper for building SQL parts ===

    /**
     * Appends a part (field, expression, or literal) to a SQL builder.
     */
    public static void appendPart(StringBuilder sql, List<Object> parameters, Object part) {
        if (part instanceof ConditionBuilderField<?, ?> field) {
            sql.append("{").append(field.tableAlias).append(".").append(field.columnName).append("}");
        } else if (part instanceof ChainableExpression<?, ?> expr) {
            sql.append(expr.getSql());
            parameters.addAll(expr.getParameters());
        } else if (part instanceof FieldExpression<?> expr) {
            sql.append(expr.getSql());
            parameters.addAll(expr.getParameters());
        } else {
            sql.append("?");
            parameters.add(part);
        }
    }
}
