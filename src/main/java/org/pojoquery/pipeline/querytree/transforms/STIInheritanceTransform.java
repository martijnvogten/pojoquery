package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.PojoMetadata.determineTableMapping;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.querytree.EmbedInfo;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.typemodel.TypeModel;

/**
 * Handles Single Table Inheritance (STI) where all classes share one table with a discriminator column.
 * 
 * <p>This transform recursively expands the class hierarchy:</p>
 * <ul>
 *   <li>All superclasses become EmbeddedNode (same table, same sourceAlias)</li>
 *   <li>All subclasses from @SubClasses become EmbeddedNode (same table, same sourceAlias)</li>
 *   <li>No JOIN is added - all share the root's table</li>
 * </ul>
 * 
 * <p>Nodes are created as EmptyTableNode with EmbedInfo - BasicTableTransform
 * later converts them to EmbeddedNode with fields.</p>
 * 
 * <p>This transform only runs for classes with @DiscriminatorColumn.</p>
 */
public class STIInheritanceTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformNodes(n -> n instanceof EmptyTableNode, this::processNode);
    }
    
    private QueryNode processNode(EmptyTableNode node) {
        // Only process if has @DiscriminatorColumn (STI marker)
        if (!node.type().hasAnnotation(DiscriminatorColumn.class)) {
            return node;
        }
        
        // Skip if this is already a super/sub class node
        if (node.isSuperClass() || node.isSubClass()) {
            return node;
        }
        
        List<QueryNode> newChildren = new ArrayList<>(node.children());
        
        // The source alias is THIS node's alias (all classes share one table)
        String sourceAlias = node.alias();
        TypeModel rootType = node.type();
        
        // 1. Add ALL superclasses as EmbeddedNode (same table, same sourceAlias)
        addSuperclassEmbeds(node, newChildren, sourceAlias, rootType);
        
        // 2. Add ALL subclasses as EmbeddedNode (same table, same sourceAlias)
        addSubclassEmbeds(node.type(), newChildren, sourceAlias, rootType, node.alias());
        
        return node.withChildren(newChildren);
    }
    
    /**
     * Walks up the class hierarchy and adds each superclass as an embedded node.
     * All share the same sourceAlias since they're in the same table.
     */
    private void addSuperclassEmbeds(EmptyTableNode node, List<QueryNode> children, String sourceAlias, TypeModel rootType) {
        List<TableMapping> mappings = determineTableMapping(node.type());
        
        // Walk up the hierarchy (skip most-derived)
        for (int i = mappings.size() - 2; i >= 0; i--) {
            TableMapping superMapping = mappings.get(i);
            String superAlias = node.alias() + ".super." + superMapping.type.getSimpleName();
            
            if (alreadyJoined(children, superAlias)) continue;
            
            // EmbeddedNode: no join, shares sourceAlias with root
            EmbedInfo embedInfo = new EmbedInfo(null, "", sourceAlias, rootType);
            EmptyTableNode superNode = EmptyTableNode.ofEmbedded(superAlias, superMapping.type, embedInfo)
                .withIsSuperClass(true);
            children.add(superNode);
        }
    }
    
    /**
     * Walks down the class hierarchy via @SubClasses and adds each as an embedded node.
     * Recursively handles @SubClasses on subclasses.
     */
    private void addSubclassEmbeds(TypeModel type, List<QueryNode> children, String sourceAlias, TypeModel rootType, String rootAlias) {
        var subClassesAnn = type.getAnnotation(SubClasses.class);
        if (subClassesAnn.isEmpty()) return;
        
        List<TypeModel> subClasses = type.getTypeValuesFromAnnotation(subClassesAnn.get(), "value");
        for (TypeModel subType : subClasses) {
            String subAlias = rootAlias + ".sub." + subType.getSimpleName();
            
            if (alreadyJoined(children, subAlias)) continue;
            
            // EmbeddedNode: no join, shares sourceAlias with root
            EmbedInfo embedInfo = new EmbedInfo(null, "", sourceAlias, rootType);
            EmptyTableNode subNode = EmptyTableNode.ofEmbedded(subAlias, subType, embedInfo)
                .withIsSubClass(true);
            children.add(subNode);
            
            // Recursive: subclass may have its own @SubClasses (still STI if parent is STI)
            addSubclassEmbeds(subType, children, sourceAlias, rootType, rootAlias);
        }
    }
    
    private boolean alreadyJoined(List<QueryNode> children, String alias) {
        return children.stream()
            .anyMatch(c -> c instanceof EmptyTableNode t && t.alias().equals(alias));
    }
}
