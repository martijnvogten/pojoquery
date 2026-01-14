package org.pojoquery.typedquery.chain;

/**
 * Interface for condition chain continuations.
 * Provides access to the continuation object and the condition builder.
 *
 * @param <C> the continuation type returned after adding a condition
 */
public interface ConditionChain<C> {
    /**
     * Returns the continuation object for fluent chaining.
     * @return the continuation
     */
    C getContinuation();

    /**
     * Returns the condition builder for adding expressions.
     * @return the condition builder
     */
    ConditionBuilder getBuilder();
}
