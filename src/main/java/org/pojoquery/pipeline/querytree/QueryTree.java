package org.pojoquery.pipeline.querytree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.pojoquery.SqlExpression;
import org.pojoquery.typemodel.TypeModel;

/**
 * Immutable representation of a complete query structure.
 * 
 * <p>A QueryTree captures all the information needed to generate a SQL SELECT statement
 * and map the results back to Java objects. It forms a tree structure where the root
 * represents the main table, and child nodes represent joined tables, embedded objects,
 * or subqueries.</p>
 * 
 * <p>QueryTree is immutable and can be transformed by plugins/transformers to add
 * fields, joins, or modify the structure in a controlled way.</p>
 *
 * @param root The root node of the query (main table or subquery)
 * @param resultType The top-level Java type that will be returned
 * @param groupBy GROUP BY clauses (may contain {alias.field} placeholders)
 * @param orderBy ORDER BY clauses (may contain {alias.field} placeholders)
 * @param wheres WHERE conditions to apply
 */
public record QueryTree(
    QueryNode root,
    TypeModel resultType,
    List<String> groupBy,
    List<String> orderBy,
    List<SqlExpression> wheres
) {
    
    public QueryTree {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(resultType, "resultType");
        groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
        orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
        wheres = wheres == null ? List.of() : List.copyOf(wheres);
    }
    
    /**
     * Creates a simple QueryTree with just a root node.
     */
    public static QueryTree of(QueryNode root, TypeModel resultType) {
        return new QueryTree(root, resultType, List.of(), List.of(), List.of());
    }
    
    /**
     * Returns a new QueryTree with an additional WHERE condition.
     */
    public QueryTree withWhere(SqlExpression where) {
        return new QueryTree(root, resultType, groupBy, orderBy,
            Stream.concat(wheres.stream(), Stream.of(where)).toList());
    }
    
    /**
     * Returns a new QueryTree with an additional ORDER BY clause.
     */
    public QueryTree withOrderBy(String clause) {
        return new QueryTree(root, resultType, groupBy,
            Stream.concat(orderBy.stream(), Stream.of(clause)).toList(), wheres);
    }
    
    /**
     * Returns a new QueryTree with an additional GROUP BY clause.
     */
    public QueryTree withGroupBy(String clause) {
        return new QueryTree(root, resultType,
            Stream.concat(groupBy.stream(), Stream.of(clause)).toList(), orderBy, wheres);
    }
    
    /**
     * Returns a new QueryTree with replaced GROUP BY clauses.
     */
    public QueryTree withGroupByClauses(List<String> clauses) {
        return new QueryTree(root, resultType, clauses, orderBy, wheres);
    }
    
    /**
     * Returns a new QueryTree with replaced ORDER BY clauses.
     */
    public QueryTree withOrderByClauses(List<String> clauses) {
        return new QueryTree(root, resultType, groupBy, clauses, wheres);
    }
    
    /**
     * Returns a new QueryTree with a different root node.
     */
    public QueryTree withRoot(QueryNode newRoot) {
        return new QueryTree(newRoot, resultType, groupBy, orderBy, wheres);
    }
    
    // --- Transformation helpers ---
    
    /**
     * Transforms all TableNodes in the tree, top-down.
     * Parents are transformed before children, so newly added joins also get transformed.
     * 
     * @param transform Function to apply to each TableNode
     * @return A new QueryTree with all TableNodes transformed
     */
    public QueryTree transformTableNodes(UnaryOperator<TableNode> transform) {
        QueryNode newRoot = transformNode(root, transform);
        return new QueryTree(newRoot, resultType, groupBy, orderBy, wheres);
    }
    
    private static QueryNode transformNode(QueryNode node, UnaryOperator<TableNode> transform) {
        if (node instanceof TableNode table) {
            // First apply transform to this node (may add new joins)
            TableNode transformed = transform.apply(table);
            
            // Then recurse into all children (including newly added ones)
            List<JoinedNode> transformedJoins = transformed.joins().stream()
                .map(j -> transformJoin(j, transform))
                .toList();
            
            return transformed.withJoins(transformedJoins);
            
        } else if (node instanceof SubqueryNode subq) {
            // Recurse into subquery's tree
            QueryTree transformedSubTree = subq.subquery().transformTableNodes(transform);
            return new SubqueryNode(subq.alias(), subq.type(), transformedSubTree, 
                subq.fields(), subq.joins());
            
        } else if (node instanceof EmbeddedNode emb) {
            // Recurse into embedded joins
            List<JoinedNode> transformedJoins = emb.joins().stream()
                .map(j -> transformJoin(j, transform))
                .toList();
            return new EmbeddedNode(emb.alias(), emb.type(), emb.fieldPrefix(),
                emb.fields(), transformedJoins);
        }
        return node;
    }
    
    private static JoinedNode transformJoin(JoinedNode join, UnaryOperator<TableNode> transform) {
        QueryNode transformedNode = transformNode(join.node(), transform);
        return join.withNode(transformedNode);
    }
    
    /**
     * Collects values from all TableNodes (e.g., for gathering all aliases).
     * 
     * @param collector Function to extract a value from each TableNode
     * @return List of collected values
     */
    public <T> List<T> collectFromTableNodes(Function<TableNode, T> collector) {
        List<T> results = new ArrayList<>();
        visitTableNodes(node -> results.add(collector.apply(node)));
        return results;
    }
    
    /**
     * Visits all TableNodes without modifying them.
     * 
     * @param visitor Consumer to call for each TableNode
     */
    public void visitTableNodes(Consumer<TableNode> visitor) {
        visitNode(root, visitor);
    }
    
    private static void visitNode(QueryNode node, Consumer<TableNode> visitor) {
        if (node instanceof TableNode table) {
            visitor.accept(table);
            for (JoinedNode join : table.joins()) {
                visitNode(join.node(), visitor);
            }
        } else if (node instanceof SubqueryNode subq) {
            subq.subquery().visitTableNodes(visitor);
        } else if (node instanceof EmbeddedNode emb) {
            for (JoinedNode join : emb.joins()) {
                visitNode(join.node(), visitor);
            }
        }
    }
    
    /**
     * Returns all aliases in the tree.
     */
    public List<String> allAliases() {
        return collectFromTableNodes(TableNode::alias);
    }

    @Override
    public String toString() {
        return toStringWithIndent("");
    }

    String toStringWithIndent(String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("QueryTree {\n");
        sb.append(indent).append("  resultType: ").append(resultType.getQualifiedName()).append("\n");
        if (!groupBy.isEmpty()) {
            sb.append(indent).append("  groupBy: ").append(groupBy).append("\n");
        }
        if (!orderBy.isEmpty()) {
            sb.append(indent).append("  orderBy: ").append(orderBy).append("\n");
        }
        if (!wheres.isEmpty()) {
            sb.append(indent).append("  wheres: ").append(wheres.size()).append(" condition(s)\n");
        }
        sb.append(indent).append("  root:\n");
        sb.append(toStringNode(root, indent + "    "));
        sb.append(indent).append("}");
        return sb.toString();
    }

    static String toStringNode(QueryNode node, String indent) {
        if (node instanceof TableNode t) {
            return t.toStringWithIndent(indent);
        } else if (node instanceof EmbeddedNode e) {
            return e.toStringWithIndent(indent);
        } else if (node instanceof SubqueryNode s) {
            return s.toStringWithIndent(indent);
        } else if (node instanceof LinkedValueNode l) {
            return l.toStringWithIndent(indent);
        }
        return indent + node.getClass().getSimpleName() + "{}\n";
    }
}
