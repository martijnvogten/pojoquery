package org.pojoquery.typedquery;

/**
 * Base class for fluent where clause building within sub-clause groups (begin/end blocks).
 * Supports patterns like: begin().where().lastName.eq("Smith").or().firstName.eq("John").end()
 *
 * @param <E> The entity type
 * @param <Q> The query type (for returning after end())
 * @param <W> The where builder type (self-referential for fluent chains)
 */
public abstract class SubClauseWhereBuilder<E, Q extends TypedQuery<E, Q>, W extends SubClauseWhereBuilder<E, Q, W>>
        extends AbstractConditionBuilder<E, W, SubClauseWhereBuilder<E, Q, W>.ChainResult> {

    // Prefixed with underscore to avoid clashes with entity field names
    protected final SubClauseBuilder<E, Q> _subClauseBuilder;

    protected SubClauseWhereBuilder(SubClauseBuilder<E, Q> subClauseBuilder) {
        this._subClauseBuilder = subClauseBuilder;
    }

    @Override
    protected String getDefaultNextOperator() {
        return "OR"; // default to OR for sub-clause groups
    }

    @Override
    protected ChainResult createChainResult() {
        return new ChainResult();
    }

    /**
     * Finishes the sub-clause and returns to the parent query.
     */
    protected Q finishSubClause() {
        Condition<E> condition = buildCondition();
        if (condition == null) {
            return _subClauseBuilder.getParentQuery();
        }

        // Add to the parent builder and end
        _subClauseBuilder.addCondition(condition);
        return _subClauseBuilder.endInternal();
    }

    /**
     * Chain result that allows .or() to continue, or .end() to finish the sub-clause group.
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
         * Note: AND within a sub-clause group creates (A OR (B AND C)) semantics.
         */
        public W and() {
            _nextOperator = "AND";
            return self();
        }

        /**
         * Ends the sub-clause group and returns to the parent query.
         * Combines all conditions with the specified operators.
         */
        public Q end() {
            return finishSubClause();
        }
    }
}
