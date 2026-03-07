package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.pipeline.querytree.EmbedInfo;
import org.pojoquery.pipeline.querytree.EmbeddedNode;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.TypeModel;

/**
 * All classes in one table with a discriminator column to identify the type.
 */
public class SingleTableInheritanceTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        // Skip nodes without a type (e.g., EmptyTableNode placeholders)
        if (node.type() == null) {
            return node;
        }

        var subClassesAnnOpt = node.type().getAnnotation(SubClasses.class);
        var discAnnOpt = node.type().getAnnotation(DiscriminatorColumn.class);
        
        if (subClassesAnnOpt.isEmpty() || discAnnOpt.isEmpty()) {
            return node;
        }

        String discColumn = discAnnOpt.get().getStringValue("name").orElse("dtype");

        if (((JoinedNode)node).discriminatorValues() != null) {
            return node;
        }

        Map<String, TypeModel> discriminatorValues = new HashMap<>();
        List<FieldSelection> newFields = new ArrayList<>(node.fields());
        
        // Add discriminator column
        String discAlias = node.alias() + "." + discColumn;
        newFields.add(new FieldSelection(
            discAlias,
            discColumn,
            new SqlExpression("{" + node.alias() + "." + discColumn + "}"),
            null, null
        ));
        
        // Base type uses its simple name as discriminator value
        discriminatorValues.put(node.type().getSimpleName(), node.type());
        
        List<QueryNode> newChildren = new ArrayList<>(node.children());
        // Add fields from each subclass (same table, no joins)
        List<TypeModel> subClasses = node.type().getTypeValuesFromAnnotation(subClassesAnnOpt.get(), "value");
        for (TypeModel subType : subClasses) {
            if (discriminatorValues.containsKey(subType.getSimpleName())) {
                continue;
            }
            if (alreadyJoined(node.children(), subType)) {
                continue;
            }
            discriminatorValues.put(subType.getSimpleName(), subType);
            
            String sourceAlias = node.embedInfo() != null ? node.embedInfo().sourceAlias() : node.alias();
            EmptyTableNode subClassNode = EmptyTableNode.ofEmbedded(sourceAlias, subType, new EmbedInfo(null, "", sourceAlias, node.type()));

            newChildren.add(subClassNode);
            // // Collect only fields declared in subclass (not inherited)
            // for (FieldModel f : FieldFilters.fieldsDeclaredIn(subType, node.type())) {
            //     if (FieldFilters.isSimple(f)) {
            //         String colName = determineSqlFieldName(f);
            //         String fieldAlias = node.alias() + "." + f.getName();
            //         newFields.add(new FieldSelection(
            //             fieldAlias,
            //             new SqlExpression("{" + node.alias() + "." + colName + "}"),
            //             f, null
            //         ));
            //     }
            // }
        }
        
        return ((JoinedNode)node.withFields(newFields).withChildren(newChildren))
            .withSingleTableInheritance(discAlias, discriminatorValues);
    }

    private boolean alreadyJoined(List<QueryNode> children, TypeModel type) {
        return children.stream()
            .filter(c -> c instanceof EmbeddedNode)
            .map(c -> (EmbeddedNode) c)
            .anyMatch(en -> en.type().equals(type));
    }
}
