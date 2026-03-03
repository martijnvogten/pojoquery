package org.pojoquery.pipeline.querytree.transforms;

import org.pojoquery.annotations.From;
import org.pojoquery.annotations.Subquery;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;



/**
 * @Subquery → derived table (subquery) joins.
 * Builds nested QueryTree for the subquery.
 */
public class SubqueryTransform implements QueryTreeTransform {
    
    private final QueryTreePipeline subqueryPipeline;
    
    public SubqueryTransform() {
        this.subqueryPipeline = null; // Will use default
    }
    
    public SubqueryTransform(QueryTreePipeline pipeline) {
        this.subqueryPipeline = pipeline;
    }
    
    @Override
    public QueryTree apply(QueryTree tree) {
        String rootAlias = ((TableNode) tree.root()).alias();
        return tree.transformTableNodes(node -> processNode(node, rootAlias));
    }
    
    private TableNode processNode(TableNode node, String rootAlias) {
        if (node.type() == null) {
            return node;
        }
        
        JoinInfo joinInfo = node.joinInfo();
        if (joinInfo == null) {
            return node;
        }

        FieldModel linkField = joinInfo.linkField();
        Subquery subqAnn = linkField.getAnnotation(Subquery.class);
        if (linkField == null || subqAnn == null || joinInfo.subquery() != null) {
            return node;
        }

        boolean isCollection = FieldFilters.isCollection(linkField.getType());
        TypeModel subqType = isCollection 
            ? FieldFilters.getComponentType(linkField)
            : linkField.getType();
        
        // The subquery type should have @From annotation
        From fromAnn = subqType.getAnnotation(From.class);
        if (fromAnn == null) {
            throw new IllegalArgumentException(
                "@Subquery field '" + linkField.getName() + "' type must have @From annotation"
            );
        }
        
        // Build the subquery's tree using a pipeline
        QueryTreePipeline pipeline = subqueryPipeline != null 
            ? subqueryPipeline 
            : QueryTreePipeline.forSubquery();
        QueryTree subTree = pipeline.build(subqType);
        
        return ((JoinedNode)node).withJoinInfo(joinInfo.withSubQuery(subTree));
    }
    
}
