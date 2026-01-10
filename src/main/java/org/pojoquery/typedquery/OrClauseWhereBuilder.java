package org.pojoquery.typedquery;

/**
 * Base class for fluent where clause building within OR groups (begin/end blocks).
 * Supports patterns like: begin().where().lastName.eq("Smith").or().firstName.eq("John").end()
 *
 * @param <E> The entity type
 * @param <Q> The query type (for returning after end())
 * @param <W> The where builder type (self-referential for fluent chains)
 */
public abstract class OrClauseWhereBuilder<E, Q extends TypedQuery<E, Q>, W extends OrClauseWhereBuilder<E, Q, W>>
        extends AbstractConditionBuilder<E, W, OrClauseWhereBuilder<E, Q, W>.ChainResult> {

    // Prefixed with underscore to avoid clashes with entity field names
    protected final OrClauseBuilder<E, Q> _orClauseBuilder;

    protected OrClauseWhereBuilder(OrClauseBuilder<E, Q> orClauseBuilder) {
        this._orClauseBuilder = orClauseBuilder;
    }

    @Override
    protected String getDefaultNextOperator() {
        return "OR"; // default to OR for OR groups
    }

    @Override
    protected ChainResult createChainResult() {
        return new ChainResult();
    }

    /**
     * Finishes the OR clause and returns to the parent query.
     */
    protected Q finishOrClause() {
        Condition<E> condition = buildCondition();
        if (condition == null) {
            return _orClauseBuilder.getParentQuery();
        }

        // Add to the parent builder and end
        _orClauseBuilder.addCondition(condition);
        return _orClauseBuilder.endInternal();
    }

    /**
     * Chain result that allows .or() to continue, or .end() to finish the OR group.
     */
    public class ChainResult {
        
        /**
         * Continue with another OR condition using fluent field access.
         */
        public W or() {
            _nextOperator = "OR";
            return self();
        }

        /**
         * Continue with an AND condition using fluent field access.
         * Note: AND within an OR group creates (A OR (B AND C)) semantics.
         */
        public W and() {
            _nextOperator = "AND";
            return self();
        }

        /**
         * Ends the OR group and returns to the parent query.
         * Combines all conditions with the specified operators.
         */
        public Q end() {
            return finishOrClause();
        }
    }
}
