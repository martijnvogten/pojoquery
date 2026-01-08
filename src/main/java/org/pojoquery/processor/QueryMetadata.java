package org.pojoquery.processor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.pojoquery.FieldMapping;
import org.pojoquery.pipeline.Alias;
import org.pojoquery.pipeline.CustomizableQueryBuilder;
import org.pojoquery.pipeline.SimpleFieldMapping;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.pipeline.SqlQuery.SqlField;
import org.pojoquery.pipeline.SqlQuery.SqlJoin;

/**
 * Holds metadata extracted from CustomizableQueryBuilder for code generation.
 *
 * <p>This class captures all the information needed to generate query building
 * and result processing code at compile time.
 */
public class QueryMetadata {

    private final List<AliasMetadata> aliases;
    private final List<FieldMetadata> fields;
    private final List<JoinMetadata> joins;
    private final List<SqlFieldMetadata> sqlFields;
    private final String rootAlias;
    private final String resultClassName;
    private final String schemaName;

    private QueryMetadata(String rootAlias, String schemaName, String resultClassName,
            List<AliasMetadata> aliases, List<FieldMetadata> fields,
            List<JoinMetadata> joins, List<SqlFieldMetadata> sqlFields) {
        this.rootAlias = rootAlias;
        this.schemaName = schemaName;
        this.resultClassName = resultClassName;
        this.aliases = aliases;
        this.fields = fields;
        this.joins = joins;
        this.sqlFields = sqlFields;
    }

    /**
     * Extracts metadata from a CustomizableQueryBuilder instance.
     * Call this at compile time when entity classes are already compiled.
     */
    public static <T> QueryMetadata extract(CustomizableQueryBuilder<?, T> builder) {
        LinkedHashMap<String, Alias> aliases = builder.getAliases();
        Map<String, FieldMapping> fieldMappings = builder.getFieldMappings();
        SqlQuery<?> query = builder.getQuery();

        List<AliasMetadata> aliasList = new ArrayList<>();
        for (Alias alias : aliases.values()) {
            aliasList.add(AliasMetadata.from(alias));
        }

        List<FieldMetadata> fieldList = new ArrayList<>();
        for (Map.Entry<String, FieldMapping> entry : fieldMappings.entrySet()) {
            String fieldAlias = entry.getKey();
            FieldMapping mapping = entry.getValue();

            // Extract field info from SimpleFieldMapping
            if (mapping instanceof SimpleFieldMapping) {
                Field f = ((SimpleFieldMapping) mapping).getField();
                if (f != null) {
                    fieldList.add(FieldMetadata.from(fieldAlias, f));
                }
            }
        }

        // Extract joins from SqlQuery
        List<JoinMetadata> joinList = new ArrayList<>();
        for (SqlJoin join : query.getJoins()) {
            joinList.add(JoinMetadata.from(join));
        }

        // Extract SQL fields from SqlQuery
        List<SqlFieldMetadata> sqlFieldList = new ArrayList<>();
        for (SqlField field : query.getFields()) {
            sqlFieldList.add(SqlFieldMetadata.from(field));
        }

        String rootAlias = query.getTable();
        String schemaName = query.getSchema();
        String resultClassName = builder.getResultClass().getName();

        return new QueryMetadata(rootAlias, schemaName, resultClassName,
            aliasList, fieldList, joinList, sqlFieldList);
    }

    /**
     * Creates QueryMetadata by invoking CustomizableQueryBuilder on a class.
     * The class must already be compiled (use during test-compile or in separate module).
     */
    public static QueryMetadata forClass(Class<?> entityClass) {
        CustomizableQueryBuilder<?, ?> builder =
            org.pojoquery.pipeline.QueryBuilder.from(entityClass);
        return extract(builder);
    }

    public List<AliasMetadata> getAliases() {
        return aliases;
    }

    public List<FieldMetadata> getFields() {
        return fields;
    }

    public List<JoinMetadata> getJoins() {
        return joins;
    }

    public List<SqlFieldMetadata> getSqlFields() {
        return sqlFields;
    }

    public String getRootAlias() {
        return rootAlias;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getResultClassName() {
        return resultClassName;
    }

    /**
     * Finds the alias metadata for a given alias name.
     */
    public AliasMetadata getAlias(String aliasName) {
        for (AliasMetadata alias : aliases) {
            if (alias.getAlias().equals(aliasName)) {
                return alias;
            }
        }
        return null;
    }

    /**
     * Gets all fields belonging to a specific alias.
     */
    public List<FieldMetadata> getFieldsForAlias(String aliasName) {
        List<FieldMetadata> result = new ArrayList<>();
        String prefix = aliasName + ".";
        for (FieldMetadata field : fields) {
            if (field.getFieldAlias().startsWith(prefix)) {
                result.add(field);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "QueryMetadata[rootAlias=" + rootAlias + ", resultClass=" + resultClassName +
            ", aliases=" + aliases.size() + ", fields=" + fields.size() + "]";
    }

    // === Nested metadata classes ===

    /**
     * Metadata for an alias (entity or embedded object in the query).
     */
    public static class AliasMetadata {
        private final String alias;
        private final String resultClassName;
        private final String parentAlias;
        private final String linkFieldName;
        private final String linkFieldDeclaringClass;
        private final List<String> idFieldNames;
        private final boolean isEmbedded;
        private final boolean isLinkedValue;
        private final boolean isSubClass;
        private final List<String> subClassAliases;

        private AliasMetadata(String alias, String resultClassName, String parentAlias,
                String linkFieldName, String linkFieldDeclaringClass, List<String> idFieldNames,
                boolean isEmbedded, boolean isLinkedValue, boolean isSubClass,
                List<String> subClassAliases) {
            this.alias = alias;
            this.resultClassName = resultClassName;
            this.parentAlias = parentAlias;
            this.linkFieldName = linkFieldName;
            this.linkFieldDeclaringClass = linkFieldDeclaringClass;
            this.idFieldNames = idFieldNames;
            this.isEmbedded = isEmbedded;
            this.isLinkedValue = isLinkedValue;
            this.isSubClass = isSubClass;
            this.subClassAliases = subClassAliases;
        }

        static AliasMetadata from(Alias alias) {
            List<String> idFieldNames = new ArrayList<>();
            if (alias.getIdFields() != null) {
                for (Field f : alias.getIdFields()) {
                    idFieldNames.add(f.getName());
                }
            }

            String linkFieldName = null;
            String linkFieldDeclaringClass = null;
            if (alias.getLinkField() != null) {
                linkFieldName = alias.getLinkField().getName();
                linkFieldDeclaringClass = alias.getLinkField().getDeclaringClass().getName();
            }

            List<String> subClassAliases = alias.getSubClassAliases();
            if (subClassAliases == null) {
                subClassAliases = List.of();
            }

            return new AliasMetadata(
                alias.getAlias(),
                alias.getResultClass().getName(),
                alias.getParentAlias(),
                linkFieldName,
                linkFieldDeclaringClass,
                idFieldNames,
                alias.getIsEmbedded(),
                alias.isLinkedValue(),
                alias.getIsASubClass(),
                subClassAliases
            );
        }

        public String getAlias() { return alias; }
        public String getResultClassName() { return resultClassName; }
        public String getParentAlias() { return parentAlias; }
        public String getLinkFieldName() { return linkFieldName; }
        public String getLinkFieldDeclaringClass() { return linkFieldDeclaringClass; }
        public List<String> getIdFieldNames() { return idFieldNames; }
        public boolean isEmbedded() { return isEmbedded; }
        public boolean isLinkedValue() { return isLinkedValue; }
        public boolean isSubClass() { return isSubClass; }
        public List<String> getSubClassAliases() { return subClassAliases; }
        public boolean isPrimaryAlias() { return parentAlias == null; }

        @Override
        public String toString() {
            return "AliasMetadata[" + alias + " -> " + resultClassName +
                (parentAlias != null ? ", parent=" + parentAlias : "") + "]";
        }
    }

    /**
     * Metadata for a field mapping.
     */
    public static class FieldMetadata {
        private final String fieldAlias;
        private final String fieldName;
        private final String fieldTypeName;
        private final String declaringClassName;
        private final boolean isEnum;
        private final boolean isPrimitive;

        private FieldMetadata(String fieldAlias, String fieldName, String fieldTypeName,
                String declaringClassName, boolean isEnum, boolean isPrimitive) {
            this.fieldAlias = fieldAlias;
            this.fieldName = fieldName;
            this.fieldTypeName = fieldTypeName;
            this.declaringClassName = declaringClassName;
            this.isEnum = isEnum;
            this.isPrimitive = isPrimitive;
        }

        static FieldMetadata from(String fieldAlias, Field f) {
            return new FieldMetadata(
                fieldAlias,
                f.getName(),
                f.getType().getName(),
                f.getDeclaringClass().getName(),
                f.getType().isEnum(),
                f.getType().isPrimitive()
            );
        }

        public String getFieldAlias() { return fieldAlias; }
        public String getFieldName() { return fieldName; }
        public String getFieldTypeName() { return fieldTypeName; }
        public String getDeclaringClassName() { return declaringClassName; }
        public boolean isEnum() { return isEnum; }
        public boolean isPrimitive() { return isPrimitive; }

        /**
         * Returns the alias portion (everything before the last dot).
         */
        public String getAliasPrefix() {
            int lastDot = fieldAlias.lastIndexOf('.');
            return lastDot > 0 ? fieldAlias.substring(0, lastDot) : "";
        }

        @Override
        public String toString() {
            return "FieldMetadata[" + fieldAlias + " -> " + fieldName + " : " + fieldTypeName + "]";
        }
    }

    /**
     * Metadata for a SQL join.
     */
    public static class JoinMetadata {
        private final String joinType;
        private final String schemaName;
        private final String tableName;
        private final String alias;
        private final String joinConditionSql;

        private JoinMetadata(String joinType, String schemaName, String tableName,
                String alias, String joinConditionSql) {
            this.joinType = joinType;
            this.schemaName = schemaName;
            this.tableName = tableName;
            this.alias = alias;
            this.joinConditionSql = joinConditionSql;
        }

        static JoinMetadata from(SqlJoin join) {
            return new JoinMetadata(
                join.joinType.name(),
                join.schema,
                join.table,
                join.alias,
                join.joinCondition != null ? join.joinCondition.getSql() : null
            );
        }

        public String getJoinType() { return joinType; }
        public String getSchemaName() { return schemaName; }
        public String getTableName() { return tableName; }
        public String getAlias() { return alias; }
        public String getJoinConditionSql() { return joinConditionSql; }

        @Override
        public String toString() {
            return "JoinMetadata[" + joinType + " " + tableName + " AS " + alias + "]";
        }
    }

    /**
     * Metadata for a SQL field (SELECT expression).
     */
    public static class SqlFieldMetadata {
        private final String expressionSql;
        private final String alias;

        private SqlFieldMetadata(String expressionSql, String alias) {
            this.expressionSql = expressionSql;
            this.alias = alias;
        }

        static SqlFieldMetadata from(SqlField field) {
            return new SqlFieldMetadata(
                field.expression.getSql(),
                field.alias
            );
        }

        public String getExpressionSql() { return expressionSql; }
        public String getAlias() { return alias; }

        @Override
        public String toString() {
            return "SqlFieldMetadata[" + expressionSql + " AS " + alias + "]";
        }
    }
}
