/**
 * Immutable tree-based query model for PojoQuery.
 * 
 * <p>This package provides an immutable, tree-structured representation of queries
 * that can be built from POJO definitions and transformed by plugins.</p>
 * 
 * <h2>Core Types</h2>
 * <ul>
 *   <li>{@link QueryTree} - The complete query structure with root, GROUP BY, ORDER BY, WHERE</li>
 *   <li>{@link QueryNode} - Sealed interface for nodes (TableNode, EmbeddedNode, SubqueryNode, LinkedValueNode)</li>
 *   <li>{@link JoinedNode} - A join relationship connecting parent to child node</li>
 *   <li>{@link FieldSelection} - A field in the SELECT clause</li>
 * </ul>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Immutability</strong>: All types are records with defensive copies</li>
 *   <li><strong>Tree structure</strong>: Parent→child relationships via JoinedNode</li>
 *   <li><strong>Transformable</strong>: withXxx() methods return new instances</li>
 * </ul>
 *
 * @see org.pojoquery.pipeline.querytree.QueryTree
 * @see org.pojoquery.pipeline.querytree.QueryNode
 */
package org.pojoquery.pipeline.querytree;
