package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;

import static org.pojoquery.pipeline.QueryModel.determineIdField;
import static org.pojoquery.pipeline.QueryModel.determineIdFields;
import static org.pojoquery.pipeline.QueryModel.determineSqlFieldName;

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
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        if (node.type() == null) {
            return node;
        }
        
        // Only expand if @SubClasses is declared directly on this type, not inherited
        // This prevents BedRoom from expanding sibling subclasses via inherited annotation from Room
        SubClasses subClassesAnn = node.type().getDeclaredAnnotation(SubClasses.class);
        if (subClassesAnn == null) {
            return node;
        }
        
        // Skip single-table inheritance - handled by SingleTableInheritanceTransform
        if (node.type().getAnnotation(DiscriminatorColumn.class) != null) {
            return node;
        }
        
        List<JoinedNode> newJoins = new ArrayList<>(node.joins());
        String idField = node.idFieldNames().isEmpty() ? "id" : node.idFieldNames().get(0);
        
        for (Class<?> subClass : subClassesAnn.value()) {
            TypeModel subType = new ReflectionTypeModel(subClass);
            AnnotationHelper.TableInfo subTable = AnnotationHelper.getTableInfo(subType);
            if (subTable == null) {
                continue;
            }
            
            String subAlias = AliasNaming.subclassAlias(node.alias(), subTable.name);
            
            // Skip if already joined (from previous iteration)
            if (alreadyJoined(newJoins, subAlias)) {
                continue;
            }
            
            // LEFT JOIN subclass_table ON subclass.id = parent.id
            SqlExpression condition = JoinConditions.forSubclass(node.alias(), subAlias, idField);
            
            // Create node with all simple fields declared in the subclass
            List<FieldSelection> subFields = new ArrayList<>();
            List<String> subIdFields = new ArrayList<>();
            
            // Add ID field (required for null-checking to determine actual type)
            FieldModel idFieldModel = determineIdField(subType);
            subFields.add(TableNodeFactory.fieldSelection(subAlias, idFieldModel));
            
            // Add all simple fields declared in subclass (not inherited from parent)
            for (FieldModel f : FieldFilters.fieldsDeclaredIn(subType, node.type())) {
                if (FieldFilters.isSimple(f) && !f.getName().equals(idFieldModel.getName())) {
                    String colName = determineSqlFieldName(f);
                    String alias = subAlias + "." + f.getName();
                    SqlExpression expr = new SqlExpression("{" + subAlias + "." + colName + "}");
                    subFields.add(new FieldSelection(alias, expr, f, null));
                }
            }
            
            // Collect ID fields for the subclass
            for (FieldModel f : determineIdFields(subType)) {
                subIdFields.add(determineSqlFieldName(f));
            }
            
            String schemaName = (subTable.schema == null || subTable.schema.isEmpty()) ? null : subTable.schema;
            // Set type to null - subclass expansion nodes don't need further structural expansion
            // (superclass fields come from parent, not from joining superclass table again)
            TableNode subNode = TableNode.simple(
                subAlias, null, schemaName, subTable.name,
                subFields, List.of(), subIdFields.isEmpty() ? List.of(idField) : subIdFields
            );
            
            newJoins.add(new JoinedNode(JoinType.LEFT, condition, subNode, null, false));
        }
        
        return node.withJoins(newJoins);
    }
    
    private boolean alreadyJoined(List<JoinedNode> joins, String alias) {
        return joins.stream()
            .anyMatch(j -> j.node() instanceof TableNode t && t.alias().equals(alias));
    }
}
