package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.From;
import org.pojoquery.annotations.Subquery;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.SubqueryNode;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;



/**
 * Transform 16: @Subquery → derived table (subquery) joins.
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
        
        List<JoinedNode> newJoins = new ArrayList<>(node.joins());
        
        for (FieldModel f : FieldFilters.subqueryFields(node.type())) {
            Subquery subqAnn = f.getAnnotation(Subquery.class);
            if (subqAnn == null) continue;
            
            boolean isCollection = FieldFilters.isCollection(f.getType());
            TypeModel subqType = isCollection 
                ? FieldFilters.getComponentType(f)
                : f.getType();
            
            // The subquery type should have @From annotation
            From fromAnn = subqType.getAnnotation(From.class);
            if (fromAnn == null) {
                throw new IllegalArgumentException(
                    "@Subquery field '" + f.getName() + "' type must have @From annotation"
                );
            }
            
            // Build the subquery's tree using a pipeline
            QueryTreePipeline pipeline = subqueryPipeline != null 
                ? subqueryPipeline 
                : QueryTreePipeline.forSubquery();
            QueryTree subTree = pipeline.build(subqType);
            
            String subqAlias = f.getName();
            String joinOn = subqAnn.joinOn();
            
            SqlExpression condition = new SqlExpression(
                "{" + subqAlias + "." + joinOn + "} = {" + node.alias() + "." + joinOn + "}"
            );
            
            // Project fields with simple aliases (just field names for subquery)
            List<FieldSelection> projectedFields = projectFieldsSimple(subqType, subqAlias);
            
            SubqueryNode subqNode = new SubqueryNode(
                subqAlias, subqType, subTree, projectedFields, List.of()
            );
            
            newJoins.add(new JoinedNode(JoinType.LEFT, condition, subqNode, f, isCollection));
        }
        
        return node.withJoins(newJoins);
    }
    
    private List<FieldSelection> projectFieldsSimple(TypeModel type, String alias) {
        List<FieldSelection> fields = new ArrayList<>();
        for (FieldModel f : FieldFilters.simpleFields(type)) {
            String fieldAlias = alias + "." + f.getName();
            fields.add(new FieldSelection(
                fieldAlias,
                new SqlExpression("{" + fieldAlias + "}"),
                f, null
            ));
        }
        return fields;
    }
}
