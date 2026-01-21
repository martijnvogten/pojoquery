package org.pojoquery.typedquery;

/**
 * Builder for inline sub-clause groups within a WhereChainBuilder.
 * 
 * <p>This enables patterns like:
 * <pre>{@code
 * query.where()
 *     .begin()
 *         .firstName.eq("John")
 *         .and()
 *         .lastName.eq("Smith")
 *     .end()
 *     .or()
 *     .begin()
 *         .firstName.eq("Jane")
 *         .and()
 *         .lastName.eq("Doe")
 *     .end()
 *     .list(connection);
 * }</pre>
 * 
 * <p>Which produces: {@code (firstName='John' AND lastName='Smith') OR (firstName='Jane' AND lastName='Doe')}
 *
 * @param <E> The entity type
 * @param <Q> The query type
 * @param <W> The parent where builder type (to return to after end())
 * @param <I> The inline builder type (self-referential for fluent chains)
 */
public abstract class InlineSubClauseBuilder<E, Q extends TypedQuery<E, Q>, 
        W extends WhereChainBuilder<E, Q, W, ?>, 
        I extends InlineSubClauseBuilder<E, Q, W, I>>
        extends AbstractConditionBuilder<E, I, InlineSubClauseBuilder<E, Q, W, I>.ChainResult> {

    /** The parent where chain builder to return to after end(). */
    protected final W _parentBuilder;

    protected InlineSubClauseBuilder(W parentBuilder) {
        this._parentBuilder = parentBuilder;
    }

    @Override
    protected String getDefaultNextOperator() {
        return "AND"; // default to AND within a group
    }

    @Override
    protected ChainResult createChainResult() {
        return new ChainResult();
    }

    /**
     * Finishes the sub-clause and adds the grouped condition to the parent builder.
     * Returns the parent's ChainResult so chaining can continue with .or() or .and().
     */
    protected WhereChainBuilder<E, Q, W, ?>.ChainResult finishAndReturnToParent() {
        Condition<E> condition = buildCondition();
        if (condition != null) {
            // Add the condition to the parent - parentheses will be added when combining with and()/or()
            _parentBuilder.addCondition(condition);
        }
        return _parentBuilder.createChainResult();
    }

    /**
     * Chain result for inline sub-clauses.
     */
    public class ChainResult {
        
        /**
         * Continue with another OR condition within this group.
         */
        public I or() {
            _nextOperator = "OR";
            return self();
        }

        /**
         * Continue with another AND condition within this group.
         */
        public I and() {
            _nextOperator = "AND";
            return self();
        }

        /**
         * Ends the sub-clause group and returns to the parent where builder's ChainResult.
         * This allows chaining like: .end().or().begin()...
         */
        public WhereChainBuilder<E, Q, W, ?>.ChainResult end() {
            return finishAndReturnToParent();
        }
    }
}
