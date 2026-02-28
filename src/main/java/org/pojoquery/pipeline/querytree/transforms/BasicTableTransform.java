package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

import static org.pojoquery.pipeline.QueryModel.determineSqlFieldName;
import static org.pojoquery.pipeline.QueryModel.determineIdFields;

/**
 * Transform 1: Creates root TableNode from @Table, collects @Id fields.
 * Only handles primitive/wrapper/common types - no relationships.
 */
public class BasicTableTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        TypeModel type = tree.resultType();
        AnnotationHelper.TableInfo tableInfo = AnnotationHelper.getTableInfo(type);
        
        if (tableInfo == null) {
            throw new IllegalArgumentException("Missing @Table annotation on " + type.getQualifiedName());
        }
        
        String rootAlias = tableInfo.name;
        List<FieldSelection> fields = new ArrayList<>();
        List<String> idFields = new ArrayList<>();
        
        // FieldFilters.simpleFields uses determineTableMapping to constrain to THIS table only
        for (FieldModel f : FieldFilters.simpleFields(type)) {
            String colName = determineSqlFieldName(f);
            String alias = rootAlias + "." + f.getName();
            SqlExpression expr = new SqlExpression("{" + rootAlias + "." + colName + "}");
            fields.add(new FieldSelection(alias, expr, f, null));
        }
        
        for (FieldModel f : determineIdFields(type)) {
            idFields.add(determineSqlFieldName(f));
        }
        
        String schemaName = (tableInfo.schema == null || tableInfo.schema.isEmpty()) ? null : tableInfo.schema;
        TableNode root = TableNode.simple(rootAlias, type, schemaName, tableInfo.name, 
            fields, List.of(), idFields);
        
        return QueryTree.of(root, type);
    }
}
