package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.pojoquery.annotations.Embedded;
import org.pojoquery.pipeline.querytree.EmbedInfo;
import org.pojoquery.pipeline.querytree.EmptyTableNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * @Embedded → inline fields with column prefix.
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

        List<QueryNode> newChildren = new ArrayList<>(node.children());

        for (FieldModel f : FieldFilters.embeddedFields(node.type())) {
            if (alreadyJoined(node, f)) {
                continue;
            }
            TypeModel type = f.getType();
            String alias = AliasNaming.childAlias(node.embedInfo() != null ? node.embedInfo().sourceAlias() : node.alias(), rootAlias, f.getName());

            String prefix = determinePrefix(f);

            if (node.embedInfo() != null) {
                // Nested embedding: combine prefixes
                prefix = node.embedInfo().fieldPrefix() + prefix;
            }

            EmbedInfo embedInfo = EmbedInfo.of(f, prefix, node.embedInfo() != null ? node.embedInfo().sourceAlias() : node.alias());

            EmptyTableNode embedNode = EmptyTableNode.ofEmbedded(alias, type, embedInfo);
            newChildren.add(embedNode);
        }

        return node.withChildren(newChildren);
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

    private boolean alreadyJoined(TableNode node, FieldModel field) {
        return node.children().stream()
            .map(QueryNode::embedInfo)
            .filter(Objects::nonNull)
            .anyMatch(ei -> ei.linkField().equals(field));
    }
}
