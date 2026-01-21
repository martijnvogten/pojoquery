package org.pojoquery.typedquery;

/**
 * Factory interface for creating condition chains.
 *
 * @param <C> the continuation type
 */
public interface ChainFactory<C> {
    /**
     * Creates a new condition chain.
     * @return the new condition chain
     */
    ConditionChain<C> createChain();
}
