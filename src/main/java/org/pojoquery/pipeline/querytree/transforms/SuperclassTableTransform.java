package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.QueryModel.determineIdField;
import static org.pojoquery.pipeline.QueryModel.determineSqlFieldName;
import static org.pojoquery.pipeline.QueryModel.determineTableMapping;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;

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
        if (node.type().getAnnotation(DiscriminatorColumn.class) != null) {
            return node;
        }
        
        TableMapping superClassMapping = mappings.get(mappings.size() - 2);
        String parentAlias = AliasNaming.superclassAlias(node.alias(), tree.root().alias(), superClassMapping.tableName);
            
        // Skip if already joined (idempotence)
        if (alreadyJoined(node.joins(), parentAlias)) {
            return node;
        }
        
        // Add superclass tables as INNER JOINs
        List<JoinedNode> newJoins = new ArrayList<>(node.joins());
        String idField = determineSqlFieldName(determineIdField(superClassMapping.type));
        SqlExpression condition = JoinConditions.forInheritance(parentAlias, node.alias(), idField);
        EmptyTableNode parentNode = EmptyTableNode.of(parentAlias, superClassMapping.type).withIsSuperClass(true);
        
        newJoins.add(new JoinedNode(JoinType.INNER, condition, parentNode, null, false));
        
        return node.withJoins(newJoins);
    }
    
    private boolean alreadyJoined(List<JoinedNode> joins, String alias) {
        return joins.stream().anyMatch(j -> j.node() instanceof EmptyTableNode t && t.alias().equals(alias));
    }
}
