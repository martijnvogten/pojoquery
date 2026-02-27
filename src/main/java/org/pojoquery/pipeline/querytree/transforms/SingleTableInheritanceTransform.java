package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;

import static org.pojoquery.pipeline.QueryModel.determineSqlFieldName;

/**
 * Transform 8: @SubClasses + @DiscriminatorColumn → single-table inheritance.
 * All classes in one table with a discriminator column to identify the type.
 */
public class SingleTableInheritanceTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        if (node.type() == null) {
            return node;
        }
        
        SubClasses subClassesAnn = node.type().getAnnotation(SubClasses.class);
        DiscriminatorColumn discAnn = node.type().getAnnotation(DiscriminatorColumn.class);
        
        if (subClassesAnn == null || discAnn == null) {
            return node;
        }
        
        String discColumn = discAnn.name();
        Map<String, TypeModel> discriminatorValues = new HashMap<>();
        List<FieldSelection> newFields = new ArrayList<>(node.fields());
        
        // Add discriminator column
        String discAlias = node.alias() + "." + discColumn;
        newFields.add(new FieldSelection(
            discAlias,
            new SqlExpression("{" + node.alias() + "." + discColumn + "}"),
            null, null
        ));
        
        // Base type uses its simple name as discriminator value
        discriminatorValues.put(node.type().getSimpleName(), node.type());
        
        // Add fields from each subclass (same table, no joins)
        for (Class<?> subClass : subClassesAnn.value()) {
            TypeModel subType = new ReflectionTypeModel(subClass);
            discriminatorValues.put(subType.getSimpleName(), subType);
            
            // Collect only fields declared in subclass (not inherited)
            for (FieldModel f : FieldFilters.fieldsDeclaredIn(subType, node.type())) {
                if (FieldFilters.isSimple(f)) {
                    String colName = determineSqlFieldName(f);
                    String fieldAlias = node.alias() + "." + f.getName();
                    newFields.add(new FieldSelection(
                        fieldAlias,
                        new SqlExpression("{" + node.alias() + "." + colName + "}"),
                        f, null
                    ));
                }
            }
        }
        
        return node.withFields(newFields)
            .withSingleTableInheritance(discAlias, discriminatorValues);
    }
}
