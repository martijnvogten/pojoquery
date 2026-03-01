package org.pojoquery.pipeline.querytree.transforms;

import static org.pojoquery.pipeline.QueryModel.determineIdField;
import static org.pojoquery.pipeline.QueryModel.determineSqlFieldName;
import static org.pojoquery.pipeline.QueryModel.determineTableMapping;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Expands @SubClasses (table-per-subclass) to add subclass table nodes.
 * 
 * <p>For table-per-subclass inheritance (no @DiscriminatorColumn), this creates
 * TableNodes for each subclass with their declared simple fields. This is part of
 * the recursive structure expansion - subclass tables then get processed for their
 * own entity refs, collections, and subclasses.</p>
 * 
 * <p>For single-table inheritance (@DiscriminatorColumn present), this transform
 * does nothing - that's handled by SingleTableInheritanceTransform.</p>
 */
public class SubclassExpansionTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformNodes(n -> n instanceof EmptyTableNode, this::processNode);
    }
    
    private QueryNode processNode(EmptyTableNode node) {
        // Only expand if @SubClasses is declared directly on this type, not inherited
        SubClasses subClassesAnn = node.type().getDeclaredAnnotation(SubClasses.class);
        if (subClassesAnn == null) {
            return node;
        }
        
        // Skip single-table inheritance - handled by SingleTableInheritanceTransform
        if (node.type().getAnnotation(DiscriminatorColumn.class) != null) {
            return node;
        }

        // Don't add subclasses to superclass joins
        if (node.isSuperClass()) {
            return node;
        }
        
        List<JoinedNode> newJoins = new ArrayList<>(node.joins());
        String idField = determineSqlFieldName(determineIdField(node.type()));
        
        for (Class<?> subClass : subClassesAnn.value()) {
            TypeModel subType = ReflectionTypeModel.of(subClass);
            List<TableMapping> subTableMappings = determineTableMapping(subType);
            TableMapping subTableMapping = subTableMappings.get(subTableMappings.size() - 1); // Get most specific @Table mapping
            
            String subAlias = AliasNaming.subclassAlias(node.alias(), subTableMapping.tableName);
            
            // Skip if already joined (from previous iteration)
            if (alreadyJoined(newJoins, subAlias)) {
                continue;
            }
            
            // LEFT JOIN subclass_table ON subclass.id = parent.id
            SqlExpression condition = JoinConditions.forSubclass(node.alias(), subAlias, idField);
            EmptyTableNode subNode = EmptyTableNode.of(subAlias, subType);
            
            newJoins.add(new JoinedNode(JoinType.LEFT, condition, subNode, null, false));
        }
        
        return node.withJoins(newJoins);
    }
    
    private boolean alreadyJoined(List<JoinedNode> joins, String alias) {
        return joins.stream()
            .anyMatch(j -> j.node() instanceof EmptyTableNode t && t.alias().equals(alias));
    }
}
