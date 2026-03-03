package org.pojoquery.pipeline.querytree.transforms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;

/**
 * Transform 18: Removes joins not referenced by any field expression.
 * Runs after FromProjectionTransform which may leave unused joins.
 */
public class JoinPruningTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        // Collect all aliases referenced by field expressions
        Set<String> referenced = collectAllReferencedAliases(tree);
        
        TableNode root = (TableNode) tree.root();
        
        // Compute transitive closure (if A is needed and references B, B is needed)
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
        
        // Build map of join alias -> aliases referenced in its condition
        Map<String, Set<String>> joinDependencies = new HashMap<>();
        collectJoinDependencies(root, joinDependencies);
        
        // Fixed-point: if join A is required and its condition references B, B is required
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, Set<String>> entry : joinDependencies.entrySet()) {
                if (required.contains(entry.getKey())) {
                    for (String dep : entry.getValue()) {
                        if (!required.contains(dep)) {
                            required.add(dep);
                            changed = true;
                        }
                    }
                }
            }
        }
        
        return required;
    }
    
    private void collectJoinDependencies(TableNode node, Map<String, Set<String>> deps) {
        for (QueryNode child : node.children()) {
            Set<String> joinDeps = new HashSet<>();
            if (child.joinInfo() != null && child.joinInfo().condition() != null) {
                joinDeps.addAll(ExpressionResolver.extractAliases(child.joinInfo().condition()));
            }
            // Remove self and root from dependencies
            joinDeps.remove(child.alias());
            deps.put(child.alias(), joinDeps);
            
            // Recurse
            if (child instanceof TableNode childTable) {
                collectJoinDependencies(childTable, deps);
            }
        }
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
