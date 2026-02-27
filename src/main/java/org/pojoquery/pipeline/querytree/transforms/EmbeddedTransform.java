package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.pipeline.querytree.EmbeddedNode;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

import static org.pojoquery.pipeline.QueryModel.determineSqlFieldName;

/**
 * Transform 9: @Embedded → inline fields with column prefix.
 * Creates EmbeddedNode for result mapping, fields come from parent table with prefix.
 */
public class EmbeddedTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        String rootAlias = ((TableNode) tree.root()).alias();
        return tree.transformTableNodes(node -> processNode(node, rootAlias));
    }
    
    private TableNode processNode(TableNode node, String rootAlias) {
        if (node.type() == null) {
            return node;
        }
        
        List<JoinedNode> newJoins = new ArrayList<>(node.joins());
        
        for (FieldModel f : FieldFilters.embeddedFields(node.type())) {
            String prefix = determinePrefix(f);
            TypeModel embeddedType = f.getType();
            String embedAlias = AliasNaming.childAlias(node.alias(), rootAlias, f.getName());
            
            // Collect fields from embedded type with prefix
            List<FieldSelection> embeddedFields = new ArrayList<>();
            for (FieldModel ef : FieldFilters.simpleFields(embeddedType)) {
                String colName = prefix + determineSqlFieldName(ef);
                String fieldAlias = embedAlias + "." + ef.getName();
                embeddedFields.add(new FieldSelection(
                    fieldAlias,
                    new SqlExpression("{" + node.alias() + "." + colName + "}"),
                    ef, null
                ));
            }
            
            // Create EmbeddedNode for result mapping
            EmbeddedNode embedNode = new EmbeddedNode(
                embedAlias, embeddedType, prefix, embeddedFields, List.of()
            );
            
            // Add as a pseudo-join (null join type indicates embedded)
            newJoins.add(new JoinedNode(null, null, embedNode, f, false));
        }
        
        return node.withJoins(newJoins);
    }
    
    private String determinePrefix(FieldModel f) {
        Embedded embeddedAnn = f.getAnnotation(Embedded.class);
        if (embeddedAnn != null) {
            String prefix = embeddedAnn.prefix();
            if (prefix.equals(Embedded.DEFAULT)) {
                return f.getName() + "_";
            }
            return prefix;
        }
        // JPA @Embedded without PojoQuery annotation - no prefix
        return "";
    }
}
