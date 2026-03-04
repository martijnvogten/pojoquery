package org.pojoquery.pipeline.querytree.transforms;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.pojoquery.pipeline.querytree.JoinCondition;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;

/**
 * Removes joins not referenced by any field expression.
 * Runs after FromProjectionTransform which may leave unused joins.
 */
public class JoinPruningTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        // Collect all aliases referenced by field expressions
        Set<String> referenced = collectAllReferencedAliases(tree);
        
        TableNode root = (TableNode) tree.root();
        
        // Compute transitive closure: if A is needed and references B in a Custom condition, B is needed
        Set<String> required = computeTransitiveClosure(root, referenced);
        
        // Prune unused joins
        TableNode pruned = pruneUnusedJoins(root, required);
        
        return QueryTree.of(tree.resultType(), pruned)
            .withGroupByClauses(tree.groupBy())
            .withOrderByClauses(tree.orderBy());
    }
    
    private Set<String> collectAllReferencedAliases(QueryTree tree) {
        Set<String> aliases = new HashSet<>();
        
        tree.visitTableNodes(node -> {
            for (var field : node.fields()) {
                aliases.addAll(ExpressionResolver.extractAliases(field.expression()));
            }
        });
        
        // Also check GROUP BY and ORDER BY clauses
        for (String clause : tree.groupBy()) {
            aliases.addAll(ExpressionResolver.extractAliases(clause));
        }
        for (String clause : tree.orderBy()) {
            aliases.addAll(ExpressionResolver.extractAliases(clause));
        }
        
        return aliases;
    }
    
    private Set<String> computeTransitiveClosure(TableNode root, Set<String> initial) {
        Set<String> required = new HashSet<>(initial);
        required.add(root.alias());
        
        // For standard join conditions (ForeignKeyInChild, ForeignKeyInParent, SharedPrimaryKey),
        // the only dependency is the direct parent-child relationship - no extra aliases.
        // Only Custom conditions can reference other aliases, so we only need to handle those.
        
        boolean changed = true;
        while (changed) {
            changed = false;
            changed |= addCustomConditionDeps(root, required);
        }
        
        return required;
    }
    
    /**
     * Recursively finds Custom join conditions and adds their alias dependencies.
     * Returns true if any new aliases were added to the required set.
     */
    private boolean addCustomConditionDeps(TableNode node, Set<String> required) {
        boolean changed = false;
        
        for (QueryNode child : node.children()) {
            if (!required.contains(child.alias())) {
                continue; // Skip if this join isn't required
            }
            
            JoinInfo joinInfo = child.joinInfo();
            if (joinInfo != null) {
                JoinCondition condition = joinInfo.joinCondition();
                
                // Only Custom conditions can reference other aliases
                if (condition instanceof JoinCondition.Custom custom) {
                    Set<String> conditionAliases = ExpressionResolver.extractAliases(custom.condition());
                    for (String dep : conditionAliases) {
                        if (!required.contains(dep)) {
                            required.add(dep);
                            changed = true;
                        }
                    }
                }
                // ForeignKeyInChild, ForeignKeyInParent, SharedPrimaryKey only reference
                // parent and child - no extra alias dependencies
            }
            
            // Recurse into children
            if (child instanceof TableNode childTable) {
                changed |= addCustomConditionDeps(childTable, required);
            }
        }
        
        return changed;
    }
    
    private TableNode pruneUnusedJoins(TableNode node, Set<String> required) {
        List<QueryNode> kept = node.children().stream()
            .filter(c -> required.contains(c.alias()))
            .map(c -> {
                if (c instanceof TableNode childTable) {
                    return pruneUnusedJoins(childTable, required);
                }
                return c;
            })
            .toList();
        
        return node.withChildren(kept);
    }
}
