package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.PojoMetadata.determineIdField;
import static org.pojoquery.pipeline.PojoMetadata.determineSqlFieldName;
import static org.pojoquery.pipeline.PojoMetadata.determineTableMapping;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.JoinCondition;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.typemodel.TypeModel;

/**
 * Handles Table Per Subclass (TPS) inheritance where each class has its own table.
 * 
 * <p>This transform recursively expands the class hierarchy:</p>
 * <ul>
 *   <li>Superclasses become INNER JOIN (when root) or LEFT JOIN (when already joined)</li>
 *   <li>Subclasses from @SubClasses become LEFT JOIN</li>
 *   <li>Each node gets its own table, own alias, own sourceAlias</li>
 * </ul>
 * 
 * <p>Nodes are created as EmptyTableNode with JoinInfo - BasicTableTransform
 * later converts them to JoinedNode with fields.</p>
 * 
 * <p>This transform is skipped for classes with @DiscriminatorColumn (that's STI).</p>
 */
public class TPSInheritanceTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformNodes(n -> n instanceof EmptyTableNode, this::processNode);
    }
    
    private QueryNode processNode(EmptyTableNode node) {
        // Skip embedded nodes (they don't have their own table to inherit from)
        if (node.embedInfo() != null) {
            return node;
        }
        
        // Skip if has @DiscriminatorColumn (that's STI, handled by STIInheritanceTransform)
        if (node.type().hasAnnotation(DiscriminatorColumn.class)) {
            return node;
        }
        
        // Skip if this is already a super/sub class node (they don't need further expansion here)
        if (node.isSuperClass() || node.isSubClass()) {
            return node;
        }
        
        // Check if this type has TPS inheritance (multiple table mappings or @SubClasses)
        List<TableMapping> mappings = determineTableMapping(node.type());
        boolean hasSuperclasses = mappings.size() > 1;
        boolean hasSubclasses = node.type().hasAnnotation(SubClasses.class);
        
        if (!hasSuperclasses && !hasSubclasses) {
            return node; // No TPS inheritance to process
        }
        
        List<QueryNode> newChildren = new ArrayList<>(node.children());
        String idField = determineSqlFieldName(determineIdField(node.type()));
        String rootAlias = node.alias();
        
        // 1. Add ALL superclass tables (recursive up the hierarchy)
        addSuperclassTables(node, newChildren, idField, rootAlias);
        
        // 2. Add ALL subclass tables (from @SubClasses, recursive down)
        addSubclassTables(node.type(), newChildren, idField, rootAlias);
        
        // Set superType so this node only gets its own table's fields
        EmptyTableNode result = node.withChildren(newChildren);
        if (hasSuperclasses) {
            TypeModel superType = mappings.get(mappings.size() - 2).type;
            result = result.withSuperType(superType);
        }
        
        return result;
    }
    
    /**
     * Walks up the class hierarchy and adds each superclass with @Table as a joined node.
     */
    private void addSuperclassTables(EmptyTableNode node, List<QueryNode> children, String idField, String rootAlias) {
        List<TableMapping> mappings = determineTableMapping(node.type());
        boolean isRoot = node.joinInfo() == null;
        
        // Walk up the hierarchy (skip the most-derived class at index size-1, that's the node itself)
        for (int i = mappings.size() - 2; i >= 0; i--) {
            TableMapping superMapping = mappings.get(i);
            // Superclass alias is always rootAlias.tableName (e.g., "bedroom.room")
            String parentAlias = rootAlias + "." + superMapping.tableName;
            
            if (alreadyJoined(children, parentAlias)) continue;
            
            // Each superclass gets its own JoinedNode (own table, own sourceAlias)
            JoinCondition.SharedPrimaryKey condition = new JoinCondition.SharedPrimaryKey(idField, idField);
            
            // Superclass of root uses INNER JOIN, superclass of already-joined uses LEFT JOIN
            JoinInfo joinInfo = isRoot 
                ? JoinInfo.innerJoinSuperClass(TableInfo.of(superMapping.schemaName, superMapping.tableName), condition)
                : JoinInfo.leftJoinSuperClass(TableInfo.of(superMapping.schemaName, superMapping.tableName), condition);
            
            // superType = parent of this table's type (to stop field scanning there)
            TypeModel superType = i > 0 ? mappings.get(i - 1).type : superMapping.type.getSuperclass();
            
            EmptyTableNode superNode = EmptyTableNode.ofJoined(parentAlias, superMapping.type, joinInfo)
                .withIsSuperClass(true)
                .withSuperType(superType);
            children.add(superNode);
        }
    }
    
    /**
     * Walks down the class hierarchy via @SubClasses and adds each as a joined node.
     * Recursively handles @SubClasses on subclasses.
     */
    private void addSubclassTables(TypeModel type, List<QueryNode> children, String idField, String rootAlias) {
        var subClassesAnn = type.getAnnotation(SubClasses.class);
        if (subClassesAnn.isEmpty()) return;
        
        List<TypeModel> subClasses = type.getTypeValuesFromAnnotation(subClassesAnn.get(), "value");
        for (TypeModel subType : subClasses) {
            // Skip if subType uses STI (has @DiscriminatorColumn)
            if (subType.hasAnnotation(DiscriminatorColumn.class)) continue;
            
            List<TableMapping> subMappings = determineTableMapping(subType);
            if (subMappings.isEmpty()) continue;
            
            TableMapping subMapping = subMappings.get(subMappings.size() - 1);
            String subAlias = rootAlias + "." + subMapping.tableName;
            
            if (alreadyJoined(children, subAlias)) continue;
            
            // LEFT JOIN subclass (may not exist for every row)
            JoinCondition.SharedPrimaryKey condition = new JoinCondition.SharedPrimaryKey(idField, idField);
            
            // superType = parent table's type (to get only this table's fields)
            TypeModel superType = subMappings.size() > 1 ? subMappings.get(subMappings.size() - 2).type : subType.getSuperclass();
            
            EmptyTableNode subNode = EmptyTableNode.ofJoined(subAlias, subType,
                JoinInfo.leftJoinSubClass(TableInfo.of(subMapping.schemaName, subMapping.tableName), condition))
                .withIsSubClass(true)
                .withSuperType(superType);
            
            children.add(subNode);
            
            // Recursive: subclass may have its own @SubClasses
            addSubclassTables(subType, children, idField, rootAlias);
        }
    }
    
    private boolean alreadyJoined(List<QueryNode> children, String alias) {
        return children.stream()
            .anyMatch(c -> c instanceof EmptyTableNode t && t.alias().equals(alias));
    }
}
