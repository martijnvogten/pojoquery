package org.pojoquery.processor;

import java.util.ArrayList;
import java.util.List;

import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.SqlQuery;

/**
 * A SqlQuery implementation that records all operations instead of building SQL.
 * Used by the annotation processor to capture what CustomizableQueryBuilder does,
 * then generate code based on the recorded operations.
 */
public class RecordingSqlQuery extends SqlQuery<RecordingSqlQuery> {

    public static class RecordedField {
        public final String expression;
        public final String alias;

        public RecordedField(String expression, String alias) {
            this.expression = expression;
            this.alias = alias;
        }

        @Override
        public String toString() {
            return "Field[" + expression + " AS " + alias + "]";
        }
    }

    public static class RecordedJoin {
        public final JoinType joinType;
        public final String schema;
        public final String table;
        public final String alias;
        public final String joinCondition;

        public RecordedJoin(JoinType joinType, String schema, String table, String alias, String joinCondition) {
            this.joinType = joinType;
            this.schema = schema;
            this.table = table;
            this.alias = alias;
            this.joinCondition = joinCondition;
        }

        @Override
        public String toString() {
            return "Join[" + joinType + " " + table + " AS " + alias + " ON " + joinCondition + "]";
        }
    }

    private final List<RecordedField> recordedFields = new ArrayList<>();
    private final List<RecordedJoin> recordedJoins = new ArrayList<>();
    private final List<String> recordedGroupBy = new ArrayList<>();
    private final List<String> recordedOrderBy = new ArrayList<>();
    private String recordedSchema;
    private String recordedTable;

    public RecordingSqlQuery(DbContext context) {
        super(context);
    }

    @Override
    public RecordingSqlQuery addField(SqlExpression expression) {
        recordedFields.add(new RecordedField(expression.getSql(), null));
        return super.addField(expression);
    }

    @Override
    public RecordingSqlQuery addField(SqlExpression expression, String alias) {
        recordedFields.add(new RecordedField(expression.getSql(), alias));
        return super.addField(expression, alias);
    }

    @Override
    public void addJoin(JoinType type, String schemaName, String tableName, String alias, SqlExpression joinCondition) {
        recordedJoins.add(new RecordedJoin(type, schemaName, tableName, alias,
            joinCondition != null ? joinCondition.getSql() : null));
        super.addJoin(type, schemaName, tableName, alias, joinCondition);
    }

    @Override
    public RecordingSqlQuery addGroupBy(String group) {
        recordedGroupBy.add(group);
        return super.addGroupBy(group);
    }

    @Override
    public RecordingSqlQuery addOrderBy(String order) {
        recordedOrderBy.add(order);
        return super.addOrderBy(order);
    }

    @Override
    public void setTable(String schemaName, String tableName) {
        this.recordedSchema = schemaName;
        this.recordedTable = tableName;
        super.setTable(schemaName, tableName);
    }

    // === Getters for recorded data ===

    public List<RecordedField> getRecordedFields() {
        return recordedFields;
    }

    public List<RecordedJoin> getRecordedJoins() {
        return recordedJoins;
    }

    public List<String> getRecordedGroupBy() {
        return recordedGroupBy;
    }

    public List<String> getRecordedOrderBy() {
        return recordedOrderBy;
    }

    public String getRecordedSchema() {
        return recordedSchema;
    }

    public String getRecordedTable() {
        return recordedTable;
    }
}
