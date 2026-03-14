package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.PojoMetadata.determineIdField;
import static org.pojoquery.pipeline.PojoMetadata.determineSqlFieldName;
import static org.pojoquery.pipeline.PojoMetadata.determineTableMapping;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.JoinCondition;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableInfo;

/**
 * Handles class hierarchies where parent classes have @Table.
 * Each @Table in hierarchy → INNER JOIN on shared ID.
 */
public class SuperclassTableTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformNodes(n -> n instanceof EmptyTableNode, (EmptyTableNode tn) -> this.processNode(tn, tree));
    }
    
    private QueryNode processNode(EmptyTableNode node, QueryTree tree) {
        List<TableMapping> mappings = determineTableMapping(node.type());
        if (mappings.size() <= 1) {
            return node; // No superclass tables
        }

        // Skip single-table inheritance - handled by SingleTableInheritanceTransform
        if (node.type().hasAnnotation(DiscriminatorColumn.class)) {
            return node;
        }
        
        TableMapping superClassMapping = mappings.get(mappings.size() - 2);
        String parentAlias = AliasNaming.superclassAlias(node.alias(), tree.root().alias(), superClassMapping.tableName);
            
        // Skip if already joined (idempotence)
        if (alreadyJoined(node.children(), parentAlias)) {
            return node;
        }
        
        // Determine join type: INNER for root nodes, LEFT for already-joined nodes
        // (to preserve the optional semantics of the parent join)
        boolean isRoot = node.joinInfo() == null;
        
        List<QueryNode> newChildren = new ArrayList<>(node.children());
        String idField = determineSqlFieldName(determineIdField(superClassMapping.type));
        JoinCondition.SharedPrimaryKey condition = new JoinCondition.SharedPrimaryKey(idField, idField);
        JoinInfo superJoinInfo = isRoot 
            ? JoinInfo.innerJoinSuperClass(TableInfo.of(superClassMapping.schemaName, superClassMapping.tableName), condition)
            : JoinInfo.leftJoinSuperClass(TableInfo.of(superClassMapping.schemaName, superClassMapping.tableName), condition);
        EmptyTableNode parentNode = EmptyTableNode.ofJoined(parentAlias, superClassMapping.type, superJoinInfo).withIsSuperClass(true);
        
        newChildren.add(parentNode);
        
        return node.withChildren(newChildren);
    }
    
    private boolean alreadyJoined(List<QueryNode> children, String alias) {
        return children.stream().anyMatch(c -> c instanceof EmptyTableNode t && t.alias().equals(alias));
    }
}
