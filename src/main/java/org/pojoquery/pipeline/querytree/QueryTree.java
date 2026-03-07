package org.pojoquery.pipeline.querytree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.pojoquery.SqlExpression;
import org.pojoquery.typemodel.ReflectionTypeModel;
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
 * @param resultType The top-level Java type that will be returned
 * @param root The root node of the query (main table or subquery)
 * @param groupBy GROUP BY clauses (may contain {alias.field} placeholders)
 * @param orderBy ORDER BY clauses (may contain {alias.field} placeholders)
 * @param wheres WHERE conditions to apply
 */
public record QueryTree(
    TypeModel resultType,
    QueryNode root,
    List<String> groupBy,
    List<String> orderBy,
    List<SqlExpression> wheres
) {
    
    public QueryTree {
        Objects.requireNonNull(resultType, "resultType");
        groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
        orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
        wheres = wheres == null ? List.of() : List.copyOf(wheres);
    }

    /**
     * Creates a simple QueryTree with just the result type and no root node.
     */
    public static QueryTree of(TypeModel resultType) {
        return new QueryTree(resultType, null, List.of(), List.of(), List.of());
    }
    
    /**
     * Creates a simple QueryTree with just the result type and no root node.
     */
    public static QueryTree of(Class<?> resultType) {
        return new QueryTree(ReflectionTypeModel.of(resultType), null, List.of(), List.of(), List.of());
    }
    
    /**
     * Creates a simple QueryTree with the result Type and a root node.
     */
    public static QueryTree of(TypeModel resultType, QueryNode root) {
        return new QueryTree(resultType, root, List.of(), List.of(), List.of());
    }
    
    /**
     * Returns a new QueryTree with an additional WHERE condition.
     */
    public QueryTree withWhere(SqlExpression where) {
        return new QueryTree(resultType, root, groupBy, orderBy,
            Stream.concat(wheres.stream(), Stream.of(where)).toList());
    }
    
    /**
     * Returns a new QueryTree with an additional ORDER BY clause.
     */
    public QueryTree withOrderBy(String clause) {
        return new QueryTree(resultType, root, groupBy,
            Stream.concat(orderBy.stream(), Stream.of(clause)).toList(), wheres);
    }
    
    /**
     * Returns a new QueryTree with an additional GROUP BY clause.
     */
    public QueryTree withGroupBy(String clause) {
        return new QueryTree(resultType, root,
            Stream.concat(groupBy.stream(), Stream.of(clause)).toList(), orderBy, wheres);
    }
    
    /**
     * Returns a new QueryTree with replaced GROUP BY clauses.
     */
    public QueryTree withGroupByClauses(List<String> clauses) {
        return new QueryTree(resultType, root, clauses, orderBy, wheres);
    }
    
    /**
     * Returns a new QueryTree with replaced ORDER BY clauses.
     */
    public QueryTree withOrderByClauses(List<String> clauses) {
        return new QueryTree(resultType, root, groupBy, clauses, wheres);
    }
    
    /**
     * Returns a new QueryTree with a different root node.
     */
    public QueryTree withRoot(QueryNode newRoot) {
        return new QueryTree(resultType, newRoot, groupBy, orderBy, wheres);
    }
    
    // --- Transformation helpers ---

    public <T extends QueryNode> QueryTree transformNodes(Predicate<QueryNode> predicate, Function<T,QueryNode> transform) {
        QueryNode newRoot = transformNodesRecursive(root, predicate, transform);
        return new QueryTree(resultType, newRoot, groupBy, orderBy, wheres);
    }
    
    @SuppressWarnings("unchecked")
    private static <T extends QueryNode> QueryNode transformNodesRecursive(QueryNode node, Predicate<QueryNode> predicate, Function<T,QueryNode> transform) {
        if (node == null) {
            return null;
        }
        
        // Apply transform if predicate matches
        QueryNode transformed = predicate.test(node) ? transform.apply((T) node) : node;
        
        List<QueryNode> transformedChildren = transformed.children().stream()
            .map(child -> transformNodesRecursive(child, predicate, transform))
            .toList();
        return transformed.withChildren(transformedChildren);
    }
    
    public Stream<QueryNode> findNodes(Predicate<QueryNode> predicate) {
        return findNodesRecursive(root, predicate);
    }
    
    private static Stream<QueryNode> findNodesRecursive(QueryNode node, Predicate<QueryNode> predicate) {
        if (node == null) {
            return Stream.empty();
        }
        
        Stream<QueryNode> self = predicate.test(node) ? Stream.of(node) : Stream.empty();
        Stream<QueryNode> childStream = node.children().stream()
            .flatMap(child -> findNodesRecursive(child, predicate));
        
        return Stream.concat(self, childStream);
    }
    
    /**
     * Finds all child nodes in the tree matching the predicate.
     * This searches for joined nodes (nodes with non-null joinInfo).
     */
    public Stream<QueryNode> findJoinedNodes(Predicate<QueryNode> predicate) {
        return findJoinedNodesRecursive(root, predicate);
    }
    
    private static Stream<QueryNode> findJoinedNodesRecursive(QueryNode node, Predicate<QueryNode> predicate) {
        if (node == null) {
            return Stream.empty();
        }
        
        Stream<QueryNode> matching = node.children().stream()
            .filter(child -> child.joinInfo() != null)
            .filter(predicate);
        Stream<QueryNode> fromChildren = node.children().stream()
            .flatMap(child -> findJoinedNodesRecursive(child, predicate));
        
        return Stream.concat(matching, fromChildren);
    }
    
    /**
     * Transforms all TableNodes in the tree, top-down.
     * Parents are transformed before children.
     * 
     * @param transform Function to apply to each TableNode
     * @return A new QueryTree with all TableNodes transformed, or this if nothing changed
     */
    public QueryTree transformTableNodes(UnaryOperator<TableNode> transform) {
        QueryNode newRoot = transformNode(root, transform);
        if (newRoot == root) {
            return this;
        }
        return new QueryTree(resultType, newRoot, groupBy, orderBy, wheres);
    }
    
    private static QueryNode transformNode(QueryNode node, UnaryOperator<TableNode> transform) {
        // Apply transform if this is a TableNode
        QueryNode result = node instanceof TableNode table ? transform.apply(table) : node;
        
        // Recurse into children
        List<QueryNode> oldChildren = result.children();
        List<QueryNode> transformedChildren = new ArrayList<>(oldChildren.size());
        boolean childrenChanged = false;
        for (QueryNode child : oldChildren) {
            QueryNode transformed = transformNode(child, transform);
            transformedChildren.add(transformed);
            if (transformed != child) {
                childrenChanged = true;
            }
        }
        
        // Only create a new node if something changed
        if (result == node && !childrenChanged) {
            return node;
        }
        if (childrenChanged) {
            return result.withChildren(transformedChildren);
        }
        return result;
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
        }
        for (QueryNode child : node.children()) {
            visitNode(child, visitor);
        }
    }
    
    /**
     * Returns all aliases in the tree.
     */
    public List<String> allAliases() {
        return collectFromTableNodes(TableNode::alias);
    }

    // --- Parent and hierarchy utilities ---

    /**
     * Returns a map from child alias to parent node.
     * The root node's alias is not included as a key (it has no parent).
     */
    public Map<String, QueryNode> parentNodes() {
        Map<String, QueryNode> result = new HashMap<>();
        collectParentsRecursive(root, result);
        return result;
    }

    private static void collectParentsRecursive(QueryNode parent, Map<String, QueryNode> result) {
        if (parent == null) return;
        for (QueryNode child : parent.children()) {
            result.put(child.alias(), parent);
            collectParentsRecursive(child, result);
        }
    }

    /**
     * Returns all nodes in processing order (depth-first, parent before children).
     * This matches the order used by CustomizableQueryBuilder.processRowInternal.
     */
    public List<QueryNode> allNodes() {
        List<QueryNode> result = new ArrayList<>();
        collectNodesRecursive(root, result);
        return result;
    }

    private static void collectNodesRecursive(QueryNode node, List<QueryNode> result) {
        if (node == null) return;
        result.add(node);
        for (QueryNode child : node.children()) {
            collectNodesRecursive(child, result);
        }
    }

    /**
     * Returns a map from alias to node for quick lookup.
     */
    public Map<String, QueryNode> nodesByAlias() {
        Map<String, QueryNode> result = new LinkedHashMap<>();
        collectNodesByAliasRecursive(root, result);
        return result;
    }

    private static void collectNodesByAliasRecursive(QueryNode node, Map<String, QueryNode> result) {
        if (node == null) return;
        result.put(node.alias(), node);
        for (QueryNode child : node.children()) {
            collectNodesByAliasRecursive(child, result);
        }
    }

    /**
     * Returns the root alias, or null if the tree has no root.
     */
    public String rootAlias() {
        return root != null ? root.alias() : null;
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
        if (node instanceof HasToStringWithIndent h) {
            return h.toStringWithIndent(indent);
        }
        return indent + node.getClass().getSimpleName() + "{}\n";
    }
}
