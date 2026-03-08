package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.pojoquery.internal.MappingException;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.typemodel.TypeModel;

/**
 * Detects cycles in the entity relationship hierarchy.
 * 
 * <p>PojoQuery requires cycle-free type hierarchies to prevent infinite query expansion.
 * A cycle occurs when an entity type appears multiple times in a single path from 
 * the root to a leaf node.</p>
 * 
 * <p>Example of a cycle:</p>
 * <pre>
 * class Person {
 *     Address address;  // Person → Address
 * }
 * class Address {
 *     Person owner;     // Address → Person (cycle!)
 * }
 * </pre>
 * 
 * <p>To break cycles, move associations into separate query-specific subclasses:</p>
 * <pre>
 * // Base classes without cyclic references
 * &#64;Table("person")
 * class Person {
 *     &#64;Id Long id;
 *     String name;
 * }
 * 
 * &#64;Table("address")
 * class Address {
 *     &#64;Id Long id;
 *     String street;
 * }
 * 
 * // Query-specific subclass: Person with their addresses
 * class PersonWithAddresses extends Person {
 *     List&lt;AddressRecord&gt; addresses;  // Uses AddressRecord, not AddressWithOwner
 * }
 * 
 * // Simple address projection without back-reference
 * class AddressRecord extends Address {
 *     // No owner field - breaks the cycle
 * }
 * 
 * // Alternatively, for address-centric queries:
 * class AddressWithOwner extends Address {
 *     PersonRecord owner;  // Uses PersonRecord, not PersonWithAddresses
 * }
 * 
 * class PersonRecord extends Person {
 *     // No addresses field - breaks the cycle
 * }
 * </pre>
 * 
 * <p>This transform checks all nodes in the query tree to detect cycles based on
 * type equality.</p>
 */
public class CycleDetectionTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        if (tree.root() == null) {
            return tree;
        }
        checkForCycles(tree.root(), new ArrayList<>());
        return tree;
    }
    
    private void checkForCycles(QueryNode node, List<TypeModel> ancestorTypes) {
        TypeModel nodeType = node.type();
        
        if (nodeType != null) {
            // Check if this type already appears in the ancestor path
            for (TypeModel ancestorType : ancestorTypes) {
                if (nodeType.equals(ancestorType)) {
                    // Build the cycle path for the error message
                    List<TypeModel> cyclePath = new ArrayList<>(ancestorTypes);
                    cyclePath.add(nodeType);
                    throw new MappingException(buildCycleMessage(cyclePath));
                }
            }
            
            // Add this type to the path for checking children
            List<TypeModel> newPath = new ArrayList<>(ancestorTypes);
            newPath.add(nodeType);
            
            for (QueryNode child : node.children()) {
                checkForCycles(child, newPath);
            }
        } else {
            // For nodes without type, just recurse into children
            for (QueryNode child : node.children()) {
                checkForCycles(child, ancestorTypes);
            }
        }
    }
    
    private String buildCycleMessage(List<TypeModel> cyclePath) {
        String pathStr = cyclePath.stream()
            .map(TypeModel::getSimpleName)
            .collect(Collectors.joining(" → "));
        
        return "Cycle detected in entity hierarchy: " + pathStr + 
            ". PojoQuery requires cycle-free type hierarchies to prevent infinite query expansion.";
    }
}
