package org.pojoquery.pipeline.querytree.transforms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Select;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.FieldSelectionBase;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;

/**
 * Transform 11: @Select → replace field expression with custom SQL.
 */
public class SelectExpressionTransform implements QueryTreeTransform {
    
    @Override
    public QueryTree apply(QueryTree tree) {
        return tree.transformTableNodes(this::processNode);
    }
    
    private TableNode processNode(TableNode node) {
        if (node.type() == null) {
            return node;
        }
        
        boolean changed = false;
        
        // Override existing field expressions with @Select (only resolved fields)
        List<FieldSelectionBase> newFields = new ArrayList<>();
        for (FieldSelectionBase f : node.fields()) {
            if (f instanceof FieldSelection fs) {
                FieldSelection overridden = overrideWithSelect(fs, node.alias());
                if (overridden != fs) {
                    changed = true;
                }
                newFields.add(overridden);
            } else {
                newFields.add(f);
            }
        }
        
        // Add @Select fields that weren't in the original field list
        Set<String> existingFieldNames = newFields.stream()
            .map(FieldSelectionBase::field)
            .filter(f -> f != null)
            .map(FieldModel::getName)
            .collect(Collectors.toSet());
        
        for (FieldModel f : FieldFilters.selectFields(node.type())) {
            if (!existingFieldNames.contains(f.getName())) {
                String value = f.getAnnotation(Select.class).flatMap(an -> an.getStringValue()).orElseThrow();
                String resolved = ExpressionResolver.resolve(value, node.alias());
                String fieldAlias = node.alias() + "." + f.getName();
                newFields.add(new FieldSelection(fieldAlias, null, new SqlExpression(resolved), f, null));
                changed = true;
            }
        }
        
        return changed ? node.withFields(newFields) : node;
    }
    
    private FieldSelection overrideWithSelect(FieldSelection fs, String alias) {
        if (fs.field() == null) {
            return fs;
        }
        
        var selectAnnOpt = fs.field().getAnnotation(Select.class);
        if (selectAnnOpt.isPresent()) {
            String resolved = ExpressionResolver.resolve(selectAnnOpt.get().getStringValue().orElseThrow(), alias);
            SqlExpression newExpr = new SqlExpression(resolved);
            // Check if already has the correct expression
            if (newExpr.equals(fs.expression())) {
                return fs;
            }
            return new FieldSelection(fs.alias(), fs.columnName(), newExpr, fs.field(), fs.customMapping());
        }
        return fs;
    }
}