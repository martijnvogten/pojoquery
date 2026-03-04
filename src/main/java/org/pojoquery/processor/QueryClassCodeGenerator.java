package org.pojoquery.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.querytree.EmbeddedNode;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.JoinTableInfo;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;
import org.pojoquery.util.CurlyMarkers;

/**
 * Generates fluent query builder source code from a QueryTree.
 * 
 * <p>This class is decoupled from annotation processing and works with any TypeModel
 * implementation, enabling runtime testing of code generation with ReflectionTypeModel.</p>
 * 
 * <p>Usage:</p>
 * <pre>
 * QueryTree tree = QueryTreeBuilder.from(MyEntity.class);
 * QueryClassCodeGenerator generator = new QueryClassCodeGenerator();
 * StringWriter output = new StringWriter();
 * generator.generate(tree, "com.example", "MyEntity", "MyEntityQuery", output);
 * String generatedCode = output.toString();
 * </pre>
 */
public class QueryClassCodeGenerator {

    /**
     * Configuration for code generation.
     */
    public static class GeneratorConfig {
        public final String packageName;
        public final String entityName;
        public final String queryClassName;
        
        public GeneratorConfig(String packageName, String entityName, String queryClassName) {
            this.packageName = packageName;
            this.entityName = entityName;
            this.queryClassName = queryClassName;
        }
    }

    /**
     * Represents information about a field for code generation.
     */
    private static class FieldInfo {
        final String typeName;
        final boolean isComparable;

        FieldInfo(String typeName, boolean isComparable) {
            this.typeName = typeName;
            this.isComparable = isComparable;
        }
    }

    /**
     * Internal representation of a SQL field for code generation.
     */
    public static class GenField {
        public final String alias;       // e.g., "article.title"
        public final String expression;  // e.g., "{article.article_title}"
        
        public GenField(String alias, String expression) {
            this.alias = alias;
            this.expression = expression;
        }
    }

    /**
     * Internal representation of a SQL join for code generation.
     */
    public static class GenJoin {
        public final JoinType joinType;
        public final String schema;
        public final String table;
        public final String alias;
        public final String joinCondition;

        public GenJoin(JoinType joinType, String schema, String table, String alias, String joinCondition) {
            this.joinType = joinType;
            this.schema = schema;
            this.table = table;
            this.alias = alias;
            this.joinCondition = joinCondition;
        }
    }

    /**
     * Extracted metadata from QueryTree needed for code generation.
     */
    public static class QueryMetadata {
        public final String schemaName;
        public final String tableName;
        public final List<GenField> fields;
        public final List<GenJoin> joins;
        public final Map<String, QueryNode> nodesByAlias;
        public final Map<String, QueryNode> parentNodes;
        public final TypeModel resultType;
        
        public QueryMetadata(String schemaName, String tableName, List<GenField> fields,
                List<GenJoin> joins, Map<String, QueryNode> nodesByAlias, 
                Map<String, QueryNode> parentNodes, TypeModel resultType) {
            this.schemaName = schemaName;
            this.tableName = tableName;
            this.fields = fields;
            this.joins = joins;
            this.nodesByAlias = nodesByAlias;
            this.parentNodes = parentNodes;
            this.resultType = resultType;
        }
    }

    private static final Set<String> COMPARABLE_TYPES = Set.of(
        "int", "Integer", "java.lang.Integer",
        "long", "Long", "java.lang.Long",
        "short", "Short", "java.lang.Short",
        "byte", "Byte", "java.lang.Byte",
        "double", "Double", "java.lang.Double",
        "float", "Float", "java.lang.Float",
        "char", "Character", "java.lang.Character",
        "String", "java.lang.String",
        "java.time.LocalDate", "java.time.LocalDateTime", "java.time.LocalTime",
        "java.time.Instant", "java.time.ZonedDateTime", "java.time.OffsetDateTime",
        "java.util.Date", "java.sql.Date", "java.sql.Timestamp",
        "java.math.BigDecimal", "java.math.BigInteger",
        "java.util.UUID"
    );

    /**
     * Extracts metadata from a QueryTree that is needed for code generation.
     */
    public QueryMetadata extractMetadata(QueryTree tree) {
        QueryNode root = tree.root();
        if (!(root instanceof TableNode rootTable)) {
            throw new IllegalArgumentException("QueryTree root must be a TableNode");
        }
        
        TableInfo tableInfo = rootTable.tableInfo();
        String schemaName = tableInfo != null ? tableInfo.schemaName() : null;
        String tableName = tableInfo != null ? tableInfo.tableName() : root.alias();
        
        List<GenField> fields = new ArrayList<>();
        List<GenJoin> joins = new ArrayList<>();
        
        // Collect fields from root
        for (FieldSelection fs : rootTable.fields()) {
            fields.add(new GenField(fs.alias(), fs.expression().getSql()));
        }
        
        // Recursively collect joins and fields from children
        collectJoinsAndFields(rootTable, fields, joins);
        
        return new QueryMetadata(
            schemaName, tableName, fields, joins,
            tree.nodesByAlias(), tree.parentNodes(), tree.resultType()
        );
    }

    private void collectJoinsAndFields(TableNode parent, List<GenField> fields, List<GenJoin> joins) {
        for (QueryNode child : parent.children()) {
            JoinInfo joinInfo = child.joinInfo();

            if (child instanceof TableNode childTable) {
                if (joinInfo != null) {
                    // Handle many-to-many joins
                    if (joinInfo.joinTableInfo() != null) {
                        JoinTableInfo jti = joinInfo.joinTableInfo();
                        // Add join to the junction table
                        SqlExpression parentCond = jti.parentCondition(parent.alias());
                        joins.add(new GenJoin(JoinType.LEFT, 
                            jti.joinTable().schemaName(), 
                            jti.joinTable().tableName(),
                            jti.joinTableAlias(),
                            parentCond != null ? parentCond.getSql() : null));
                        // Add join to the target table
                        SqlExpression targetCond = jti.targetCondition(child.alias());
                        joins.add(new GenJoin(JoinType.LEFT,
                            joinInfo.childTable().schemaName(),
                            joinInfo.childTable().tableName(),
                            child.alias(),
                            targetCond != null ? targetCond.getSql() : null));
                    } else {
                        // Regular join
                        SqlExpression condition = joinInfo.toSqlCondition(parent.alias(), child.alias());
                        TableInfo childTableInfo = joinInfo.childTable();
                        if (childTableInfo != null) {
                            joins.add(new GenJoin(joinInfo.joinType(),
                                childTableInfo.schemaName(),
                                childTableInfo.tableName(),
                                child.alias(),
                                condition != null ? condition.getSql() : null));
                        }
                    }
                }
                
                // Add fields from the joined table
                for (FieldSelection fs : childTable.fields()) {
                    fields.add(new GenField(fs.alias(), fs.expression().getSql()));
                }
                
                // Recurse
                collectJoinsAndFields(childTable, fields, joins);
            }
        }
    }

    /**
     * Generates the complete query class source code.
     * 
     * @param tree The QueryTree describing the entity structure
     * @param packageName The package name for the generated class
     * @param entityName The simple name of the entity class
     * @param queryClassName The name for the generated query class
     * @param out The writer to output the generated code
     */
    public void generate(QueryTree tree, String packageName, String entityName, 
            String queryClassName, Writer out) throws IOException {
        
        QueryMetadata metadata = extractMetadata(tree);
        GeneratorConfig config = new GeneratorConfig(packageName, entityName, queryClassName);
        
        generate(metadata, config, out);
    }

    /**
     * Generates the complete query class source code from pre-extracted metadata.
     */
    public void generate(QueryMetadata metadata, GeneratorConfig config, Writer out) throws IOException {
        String queryClassName = config.queryClassName;
        String entityName = config.entityName;
        String packageName = config.packageName;
        String tableName = metadata.tableName;
        String schemaName = metadata.schemaName;
        
        String chainClassName = queryClassName + "StaticConditionChain";

        // Package declaration
        if (!packageName.isEmpty()) {
            out.write("package " + packageName + ";\n\n");
        }

        // Imports
        writeImports(out);

        // Class Javadoc
        out.write("/**\n");
        out.write(" * Generated fluent query builder for {@link " + entityName + "}.\n");
        out.write(" * <p>Usage example:\n");
        out.write(" * <pre>\n");
        out.write(" * " + queryClassName + " q = new " + queryClassName + "();\n");
        out.write(" * q.title.eq(\"John\").and().title.isNotNull();\n");
        out.write(" * </pre>\n");
        out.write(" */\n");
        out.write("@SuppressWarnings(\"all\")\n");

        // Determine the primary key type
        QueryNode rootNode = metadata.nodesByAlias.get(tableName);
        String pkType = determinePkType(rootNode);

        out.write("public class " + queryClassName + " extends TypedQuery<" + entityName + ", " + pkType + ", " + queryClassName + "> {\n\n");

        // Group fields by alias
        Map<String, List<GenField>> fieldsByAlias = groupFieldsByAlias(metadata.fields);

        // Generate static condition builder fields for main entity
        List<GenField> mainFields = fieldsByAlias.getOrDefault(tableName, List.of());
        out.write("    // Static condition builder fields for main entity\n");
        for (GenField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            String columnName = extractColumnNameFromExpression(field.expression);
            FieldInfo fieldInfo = getFieldInfo(rootNode, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.write("    public final " + builderClass + "<" + fieldInfo.typeName + ", " + chainClassName + "> " + fieldName + " =\n");
            out.write("            new " + builderClass + "<>(() -> new " + chainClassName + "(), \"" + tableName + "\", \"" + columnName + "\");\n");
        }
        out.write("\n");

        // Build tree structure for nested relationships
        RelationNode root = buildRelationTree(tableName, fieldsByAlias.keySet());
        
        // Generate nested static field classes for relationships
        for (RelationNode child : root.children.values()) {
            generateNestedStaticFields(out, queryClassName, chainClassName, child, fieldsByAlias, metadata.nodesByAlias);
        }

        // Generate the StaticConditionChain inner class
        generateStaticConditionChainClass(out, queryClassName, chainClassName, tableName, mainFields, rootNode, fieldsByAlias, metadata.nodesByAlias, "    ");
        out.write("\n");

        // initializeQuery() method
        generateInitializeQuery(out, schemaName, tableName, metadata.fields, metadata.joins);
        out.write("\n");

        // Constructor
        out.write("    public " + queryClassName + "() {\n");
        out.write("        initializeQuery();\n");
        out.write("    }\n\n");

        // Collected conditions and helper methods
        generateConditionHelpers(out, queryClassName, entityName, pkType);

        // SQL function methods
        generateSqlFunctionMethods(out, chainClassName);
        out.write("\n");

        // OrderBy and GroupBy builders
        String orderByBuilderClass = queryClassName + "OrderByBuilder";
        String groupByBuilderClass = queryClassName + "GroupByBuilder";
        generateOrderByGroupByMethods(out, queryClassName, orderByBuilderClass, groupByBuilderClass);

        // processRows method
        generateProcessRows(out, entityName, tableName, fieldsByAlias, metadata.nodesByAlias, metadata.parentNodes);
        out.write("\n");

        // processRowStreaming method
        generateProcessRowStreaming(out, entityName, tableName, fieldsByAlias, metadata.nodesByAlias, metadata.parentNodes);
        out.write("\n");

        // getPrimaryKeyFromRow method
        generateGetPrimaryKeyFromRow(out, tableName, metadata.nodesByAlias, fieldsByAlias);
        out.write("\n");

        // getIdFieldName method
        generateGetIdFieldName(out, tableName, metadata.nodesByAlias, fieldsByAlias);
        out.write("\n");

        // buildIdCondition method
        generateBuildIdCondition(out, tableName, metadata.nodesByAlias, fieldsByAlias);
        out.write("\n");

        // getEntityClass method
        generateGetEntityClass(out, entityName);
        out.write("\n");

        // Inner classes
        generateGroupByBuilderClass(out, queryClassName, groupByBuilderClass, tableName, mainFields, "    ");
        out.write("\n");

        generateOrderByBuilderClass(out, queryClassName, orderByBuilderClass, tableName, mainFields, fieldsByAlias, metadata.nodesByAlias, "    ");
        out.write("\n");

        generateDelegateClass(out, entityName, queryClassName, pkType, groupByBuilderClass, orderByBuilderClass, "    ");
        out.write("\n");

        generateGroupByFieldClass(out, queryClassName, groupByBuilderClass, orderByBuilderClass, "    ");
        out.write("\n");

        // WhereBuilder inner class
        String whereBuilderClass = queryClassName + "WhereBuilder";
        generateWhereBuilderClass(out, queryClassName, whereBuilderClass, tableName, mainFields, rootNode, fieldsByAlias, metadata.nodesByAlias, "    ");
        out.write("\n");

        out.write("}\n");
    }

    // === Helper methods ===

    private void writeImports(Writer out) throws IOException {
        out.write("import java.lang.reflect.Field;\n");
        out.write("import java.sql.Connection;\n");
        out.write("import java.util.ArrayList;\n");
        out.write("import java.util.HashMap;\n");
        out.write("import java.util.List;\n");
        out.write("import java.util.Map;\n");
        out.write("import java.util.Optional;\n");
        out.write("import java.util.function.Supplier;\n\n");
        out.write("import org.pojoquery.DB;\n");
        out.write("import org.pojoquery.DbContext;\n");
        out.write("import org.pojoquery.FieldMapping;\n");
        out.write("import org.pojoquery.SqlExpression;\n");
        out.write("import org.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;\n");
        out.write("import org.pojoquery.pipeline.SqlQuery;\n");
        out.write("import org.pojoquery.util.FieldHelper;\n\n");
        out.write("import org.pojoquery.typedquery.ChainFactory;\n");
        out.write("import org.pojoquery.typedquery.ChainableExpression;\n");
        out.write("import org.pojoquery.typedquery.ComparableConditionBuilderField;\n");
        out.write("import org.pojoquery.typedquery.ConditionBuilder;\n");
        out.write("import org.pojoquery.typedquery.ConditionBuilderField;\n");
        out.write("import org.pojoquery.typedquery.ConditionBuilderImpl;\n");
        out.write("import org.pojoquery.typedquery.ConditionChain;\n");
        out.write("import org.pojoquery.typedquery.OrderByField;\n");
        out.write("import org.pojoquery.typedquery.OrderByTarget;\n");
        out.write("import org.pojoquery.typedquery.TypedQuery;\n\n");
        out.write("import static org.pojoquery.SqlExpression.sql;\n\n");
    }

    private String determinePkType(QueryNode rootNode) {
        if (rootNode instanceof JoinedNode jn) {
            List<String> idFields = jn.idFieldNames();
            if (idFields != null && !idFields.isEmpty()) {
                String idFieldName = idFields.get(0);
                TypeModel type = jn.type();
                FieldModel field = findFieldByName(type, idFieldName);
                if (field != null) {
                    return getBoxedType(field.getType());
                }
            }
        }
        return "Object";
    }

    private Map<String, List<GenField>> groupFieldsByAlias(List<GenField> fields) {
        Map<String, List<GenField>> result = new LinkedHashMap<>();
        for (GenField field : fields) {
            String alias = extractAliasFromFieldAlias(field.alias);
            result.computeIfAbsent(alias, k -> new ArrayList<>()).add(field);
        }
        return result;
    }

    private String extractAliasFromFieldAlias(String fieldAlias) {
        int lastDot = fieldAlias.lastIndexOf('.');
        return lastDot > 0 ? fieldAlias.substring(0, lastDot) : fieldAlias;
    }

    private String extractFieldNameFromAlias(String fieldAlias) {
        int lastDot = fieldAlias.lastIndexOf('.');
        return lastDot > 0 ? fieldAlias.substring(lastDot + 1) : fieldAlias;
    }

    private String extractColumnNameFromExpression(String expression) {
        return CurlyMarkers.extractColumnName(expression);
    }

    private FieldModel findFieldByName(TypeModel type, String fieldName) {
        while (type != null) {
            for (FieldModel field : type.getDeclaredFields()) {
                if (field.getName().equals(fieldName)) {
                    return field;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private FieldInfo getFieldInfo(QueryNode node, String fieldName) {
        if (node == null) return new FieldInfo("Object", false);
        TypeModel type = node.type();
        FieldModel field = findFieldByName(type, fieldName);
        if (field != null) {
            TypeModel fieldType = field.getType();
            return new FieldInfo(getBoxedType(fieldType), isComparableType(fieldType));
        }
        return new FieldInfo("Object", false);
    }

    private boolean isComparableType(TypeModel type) {
        if (type == null) return false;
        String name = type.getQualifiedName();
        return COMPARABLE_TYPES.contains(name) || type.isEnum();
    }

    private String getBoxedType(TypeModel type) {
        String name = type.getQualifiedName();
        if (name.equals("int")) return "Integer";
        if (name.equals("long")) return "Long";
        if (name.equals("boolean")) return "Boolean";
        if (name.equals("double")) return "Double";
        if (name.equals("float")) return "Float";
        if (name.equals("short")) return "Short";
        if (name.equals("byte")) return "Byte";
        if (name.equals("char")) return "Character";
        if (name.startsWith("java.lang.")) {
            return type.getSimpleName();
        }
        return name;
    }

    private String escapeJava(String s) {
        if (s == null) return null;
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String sanitizeVarName(String alias) {
        return alias.replace(".", "_").replace("-", "_");
    }

    private List<String> getIdFieldNames(QueryNode node) {
        if (node instanceof JoinedNode jn) {
            return jn.idFieldNames() != null ? jn.idFieldNames() : List.of();
        }
        return List.of();
    }

    private boolean isEmbedded(QueryNode node) {
        return node instanceof EmbeddedNode;
    }

    private FieldModel getLinkField(QueryNode node) {
        JoinInfo joinInfo = node.joinInfo();
        return joinInfo != null ? joinInfo.linkField() : null;
    }

    private String getParentAlias(QueryNode node, Map<String, QueryNode> parentNodes) {
        QueryNode parent = parentNodes.get(node.alias());
        return parent != null ? parent.alias() : null;
    }

    // === RelationNode for tree building ===

    private static class RelationNode {
        final String name;
        final String fullAlias;
        final Map<String, RelationNode> children = new LinkedHashMap<>();

        RelationNode(String name, String fullAlias) {
            this.name = name;
            this.fullAlias = fullAlias;
        }
    }

    private RelationNode buildRelationTree(String tableName, Set<String> aliases) {
        RelationNode root = new RelationNode(tableName, tableName);
        
        for (String alias : aliases) {
            if (alias.equals(tableName)) continue;
            
            String[] parts = alias.split("\\.");
            RelationNode current = root;
            StringBuilder pathBuilder = new StringBuilder();
            
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) pathBuilder.append(".");
                pathBuilder.append(parts[i]);
                String currentPath = pathBuilder.toString();
                
                if (!current.children.containsKey(parts[i])) {
                    current.children.put(parts[i], new RelationNode(parts[i], currentPath));
                }
                current = current.children.get(parts[i]);
            }
        }
        
        return root;
    }

    // === Code generation methods ===

    private void generateNestedStaticFields(Writer out, String queryClassName, String chainClassName,
            RelationNode node, Map<String, List<GenField>> fieldsByAlias, Map<String, QueryNode> nodesByAlias) throws IOException {
        
        String relationName = node.name;
        String aliasName = node.fullAlias;
        QueryNode relNode = nodesByAlias.get(aliasName);
        List<GenField> relationFields = fieldsByAlias.getOrDefault(aliasName, List.of());
        
        out.write("    /** Static condition builder fields for the {@code " + relationName + "} relationship */\n");
        out.write("    public final " + capitalize(relationName) + "Fields " + relationName + " = new " + capitalize(relationName) + "Fields();\n\n");
        out.write("    public class " + capitalize(relationName) + "Fields {\n");
        
        for (GenField field : relationFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            String columnName = extractColumnNameFromExpression(field.expression);
            FieldInfo fieldInfo = getFieldInfo(relNode, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.write("        public final " + builderClass + "<" + fieldInfo.typeName + ", " + chainClassName + "> " + fieldName + " =\n");
            out.write("                new " + builderClass + "<>(() -> new " + chainClassName + "(), \"" + aliasName + "\", \"" + columnName + "\");\n");
        }
        
        for (RelationNode child : node.children.values()) {
            out.write("\n");
            generateNestedStaticFieldsIndented(out, queryClassName, chainClassName, child, fieldsByAlias, nodesByAlias, "        ");
        }
        
        out.write("    }\n\n");
    }

    private void generateNestedStaticFieldsIndented(Writer out, String queryClassName, String chainClassName,
            RelationNode node, Map<String, List<GenField>> fieldsByAlias, Map<String, QueryNode> nodesByAlias, String indent) throws IOException {
        
        String relationName = node.name;
        String aliasName = node.fullAlias;
        QueryNode relNode = nodesByAlias.get(aliasName);
        List<GenField> relationFields = fieldsByAlias.getOrDefault(aliasName, List.of());
        
        out.write(indent + "/** Static condition builder fields for the {@code " + relationName + "} relationship */\n");
        out.write(indent + "public final " + capitalize(relationName) + "Fields " + relationName + " = new " + capitalize(relationName) + "Fields();\n\n");
        out.write(indent + "public class " + capitalize(relationName) + "Fields {\n");
        
        for (GenField field : relationFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            String columnName = extractColumnNameFromExpression(field.expression);
            FieldInfo fieldInfo = getFieldInfo(relNode, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.write(indent + "    public final " + builderClass + "<" + fieldInfo.typeName + ", " + chainClassName + "> " + fieldName + " =\n");
            out.write(indent + "            new " + builderClass + "<>(() -> new " + chainClassName + "(), \"" + aliasName + "\", \"" + columnName + "\");\n");
        }
        
        for (RelationNode child : node.children.values()) {
            out.write("\n");
            generateNestedStaticFieldsIndented(out, queryClassName, chainClassName, child, fieldsByAlias, nodesByAlias, indent + "    ");
        }
        
        out.write(indent + "}\n");
    }

    private void generateStaticConditionChainClass(Writer out, String queryClassName,
            String chainClassName, String tableName, List<GenField> mainFields, QueryNode mainNode,
            Map<String, List<GenField>> fieldsByAlias, Map<String, QueryNode> nodesByAlias, String indent) throws IOException {

        out.write(indent + "/**\n");
        out.write(indent + " * Condition chain for building static conditions.\n");
        out.write(indent + " * Implements Supplier&lt;SqlExpression&gt; to be used in and()/or() methods.\n");
        out.write(indent + " */\n");
        out.write(indent + "public class " + chainClassName + "\n");
        out.write(indent + "        implements ConditionChain<" + chainClassName + ">, Supplier<SqlExpression> {\n\n");

        // Inner StaticConditionFields class with main entity fields
        out.write(indent + "    public class StaticConditionFields {\n");
        for (GenField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            String columnName = extractColumnNameFromExpression(field.expression);
            FieldInfo fieldInfo = getFieldInfo(mainNode, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.write(indent + "        public final " + builderClass + "<" + fieldInfo.typeName + ", " + chainClassName + "> " + fieldName + " =\n");
            out.write(indent + "                new " + builderClass + "<>(() -> " + chainClassName + ".this, \"" + tableName + "\", \"" + columnName + "\");\n");
        }
        
        // Add nested relationship field classes
        RelationNode root = buildRelationTree(tableName, fieldsByAlias.keySet());
        for (RelationNode child : root.children.values()) {
            out.write("\n");
            generateNestedStaticConditionFields(out, chainClassName, child, fieldsByAlias, nodesByAlias, indent + "        ");
        }
        
        out.write(indent + "    }\n\n");

        out.write(indent + "    ConditionBuilder builder = new ConditionBuilderImpl();\n\n");

        out.write(indent + "    @Override\n");
        out.write(indent + "    public ConditionBuilder getBuilder() {\n");
        out.write(indent + "        return builder;\n");
        out.write(indent + "    }\n\n");

        out.write(indent + "    public StaticConditionFields and() {\n");
        out.write(indent + "        builder.add(sql(\" AND \"));\n");
        out.write(indent + "        return new StaticConditionFields();\n");
        out.write(indent + "    }\n\n");

        out.write(indent + "    public StaticConditionFields or() {\n");
        out.write(indent + "        builder.add(sql(\" OR \"));\n");
        out.write(indent + "        return new StaticConditionFields();\n");
        out.write(indent + "    }\n\n");

        out.write(indent + "    public " + chainClassName + " and(Supplier<SqlExpression> expr) {\n");
        out.write(indent + "        builder.add(sql(\" AND \")).startClause().add(expr.get()).endClause();\n");
        out.write(indent + "        return this;\n");
        out.write(indent + "    }\n\n");

        out.write(indent + "    public " + chainClassName + " or(Supplier<SqlExpression> expr) {\n");
        out.write(indent + "        builder.add(sql(\" OR \")).startClause().add(expr.get()).endClause();\n");
        out.write(indent + "        return this;\n");
        out.write(indent + "    }\n\n");

        out.write(indent + "    @Override\n");
        out.write(indent + "    public " + chainClassName + " getContinuation() {\n");
        out.write(indent + "        return this;\n");
        out.write(indent + "    }\n\n");

        out.write(indent + "    @Override\n");
        out.write(indent + "    public SqlExpression get() {\n");
        out.write(indent + "        return SqlExpression.implode(\"\", ((ConditionBuilderImpl) builder).getExpressions());\n");
        out.write(indent + "    }\n");
        out.write(indent + "}\n");
    }

    private void generateNestedStaticConditionFields(Writer out, String chainClassName,
            RelationNode node, Map<String, List<GenField>> fieldsByAlias, Map<String, QueryNode> nodesByAlias, String indent) throws IOException {
        
        String relationName = node.name;
        String aliasName = node.fullAlias;
        QueryNode relNode = nodesByAlias.get(aliasName);
        List<GenField> relationFields = fieldsByAlias.getOrDefault(aliasName, List.of());
        
        String innerClassName = capitalize(relationName) + "ConditionFields";
        out.write(indent + "/** Condition fields for the {@code " + relationName + "} relationship */\n");
        out.write(indent + "public final " + innerClassName + " " + relationName + " = new " + innerClassName + "();\n\n");
        out.write(indent + "public class " + innerClassName + " {\n");
        
        for (GenField field : relationFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            String columnName = extractColumnNameFromExpression(field.expression);
            FieldInfo fieldInfo = getFieldInfo(relNode, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.write(indent + "    public final " + builderClass + "<" + fieldInfo.typeName + ", " + chainClassName + "> " + fieldName + " =\n");
            out.write(indent + "            new " + builderClass + "<>(() -> " + chainClassName + ".this, \"" + aliasName + "\", \"" + columnName + "\");\n");
        }
        
        for (RelationNode child : node.children.values()) {
            out.write("\n");
            generateNestedStaticConditionFields(out, chainClassName, child, fieldsByAlias, nodesByAlias, indent + "    ");
        }
        
        out.write(indent + "}\n");
    }

    private void generateInitializeQuery(Writer out, String schemaName, String tableName,
            List<GenField> fields, List<GenJoin> joins) throws IOException {

        out.write("    @Override\n");
        out.write("    protected void initializeQuery() {\n");
        String tableSchemaArg = schemaName == null ? "null" : "\"" + escapeJava(schemaName) + "\"";
        out.write("        query.setTable(" + tableSchemaArg + ", \"" + escapeJava(tableName) + "\");\n");
        
        for (GenJoin join : joins) {
            String joinSchemaArg = join.schema == null ? "null" : "\"" + escapeJava(join.schema) + "\"";
            String conditionArg = join.joinCondition == null ? "null" :
                "SqlExpression.sql(\"" + escapeJava(join.joinCondition) + "\")";
            out.write("        query.addJoin(org.pojoquery.pipeline.SqlQuery.JoinType." + join.joinType.name() +
                ", " + joinSchemaArg + ", \"" + escapeJava(join.table) +
                "\", \"" + escapeJava(join.alias) + "\", " + conditionArg + ");\n");
        }
        
        for (GenField field : fields) {
            out.write("        query.addField(sql(\"" + escapeJava(field.expression) + "\"), \"" + escapeJava(field.alias) + "\");\n");
        }
        out.write("    }\n");
    }

    private void generateConditionHelpers(Writer out, String queryClassName, String entityName, String pkType) throws IOException {
        String whereBuilderClass = queryClassName + "WhereBuilder";
        
        out.write("    /** Collected WHERE conditions - lives on query for direct access from builders. */\n");
        out.write("    protected final List<SqlExpression> collectedConditions = new java.util.ArrayList<>();\n\n");
        out.write("    /** Applies any pending where conditions to the query. */\n");
        out.write("    protected void applyPendingConditions() {\n");
        out.write("        if (!collectedConditions.isEmpty()) {\n");
        out.write("            SqlExpression whereExpr = SqlExpression.implode(\"\", collectedConditions);\n");
        out.write("            collectedConditions.clear();\n");
        out.write("            query.addWhere(whereExpr);\n");
        out.write("        }\n");
        out.write("    }\n\n");

        out.write("    @Override\n");
        out.write("    public List<" + entityName + "> list(Connection connection) {\n");
        out.write("        applyPendingConditions();\n");
        out.write("        return super.list(connection);\n");
        out.write("    }\n\n");

        out.write("    @Override\n");
        out.write("    public List<" + pkType + "> listIds(Connection connection) {\n");
        out.write("        applyPendingConditions();\n");
        out.write("        return super.listIds(connection);\n");
        out.write("    }\n\n");

        out.write("    public " + whereBuilderClass + " where() {\n");
        out.write("        if (!collectedConditions.isEmpty()) {\n");
        out.write("            collectedConditions.add(sql(\" AND \"));\n");
        out.write("        }\n");
        out.write("        return new " + whereBuilderClass + "(this);\n");
        out.write("    }\n\n");

        String terminatorClass = whereBuilderClass + "." + whereBuilderClass + "ConditionTerminator";
        out.write("    /**\n");
        out.write("     * Adds a where condition from a static condition chain and returns a terminator for continued chaining.\n");
        out.write("     * <p>Example: {@code q.where(q.concat(q.author.name, \" \", q.author.email).eq(\"James Brown\")).and().author.isNotNull()}\n");
        out.write("     */\n");
        out.write("    public " + terminatorClass + " where(Supplier<SqlExpression> condition) {\n");
        out.write("        if (!collectedConditions.isEmpty()) {\n");
        out.write("            collectedConditions.add(sql(\" AND \"));\n");
        out.write("        }\n");
        out.write("        " + whereBuilderClass + " whereBuilder = new " + whereBuilderClass + "(this);\n");
        out.write("        whereBuilder.builder.add(condition.get());\n");
        out.write("        return whereBuilder.getContinuation();\n");
        out.write("    }\n\n");
    }

    private void generateSqlFunctionMethods(Writer out, String chainClassName) throws IOException {
        String chainFactory = "() -> new " + chainClassName + "()";
        
        out.write("    // === SQL function methods with chainable return types ===\n\n");
        
        out.write("    /**\n");
        out.write("     * Creates a CONCAT expression from the given parts.\n");
        out.write("     * Parts can be ConditionBuilderField instances or literal values.\n");
        out.write("     * <p>Example: {@code q.where(q.concat(q.firstName, \" \", q.lastName).eq(\"John Doe\").and().id.gt(1L))}\n");
        out.write("     */\n");
        out.write("    public ChainableExpression<String, " + chainClassName + "> concat(Object... parts) {\n");
        out.write("        return buildConcat(" + chainFactory + ", parts);\n");
        out.write("    }\n\n");
        
        out.write("    /**\n");
        out.write("     * Creates a LOWER expression.\n");
        out.write("     * <p>Example: {@code q.where(q.lower(q.email).eq(\"john@example.com\"))}\n");
        out.write("     */\n");
        out.write("    public ChainableExpression<String, " + chainClassName + "> lower(Object part) {\n");
        out.write("        return buildSingleArgFunction(\"LOWER\", " + chainFactory + ", part);\n");
        out.write("    }\n\n");
        
        out.write("    /**\n");
        out.write("     * Creates an UPPER expression.\n");
        out.write("     */\n");
        out.write("    public ChainableExpression<String, " + chainClassName + "> upper(Object part) {\n");
        out.write("        return buildSingleArgFunction(\"UPPER\", " + chainFactory + ", part);\n");
        out.write("    }\n\n");
        
        out.write("    /**\n");
        out.write("     * Creates a TRIM expression.\n");
        out.write("     */\n");
        out.write("    public ChainableExpression<String, " + chainClassName + "> trim(Object part) {\n");
        out.write("        return buildSingleArgFunction(\"TRIM\", " + chainFactory + ", part);\n");
        out.write("    }\n\n");
        
        out.write("    /**\n");
        out.write("     * Creates a LENGTH expression.\n");
        out.write("     */\n");
        out.write("    public ChainableExpression<Number, " + chainClassName + "> length(Object part) {\n");
        out.write("        return buildSingleArgFunction(\"LENGTH\", " + chainFactory + ", part);\n");
        out.write("    }\n\n");
        
        out.write("    /**\n");
        out.write("     * Creates a COALESCE expression.\n");
        out.write("     */\n");
        out.write("    public <V> ChainableExpression<V, " + chainClassName + "> coalesce(Object... parts) {\n");
        out.write("        return buildMultiArgFunction(\"COALESCE\", " + chainFactory + ", parts);\n");
        out.write("    }\n\n");
        
        out.write("    /**\n");
        out.write("     * Creates an ABS expression.\n");
        out.write("     */\n");
        out.write("    public <V extends Number> ChainableExpression<V, " + chainClassName + "> abs(Object part) {\n");
        out.write("        return buildSingleArgFunction(\"ABS\", " + chainFactory + ", part);\n");
        out.write("    }\n\n");
        
        out.write("    /**\n");
        out.write("     * Creates a SUBSTRING expression.\n");
        out.write("     */\n");
        out.write("    public ChainableExpression<String, " + chainClassName + "> substring(Object part, int start, int len) {\n");
        out.write("        return buildSubstring(" + chainFactory + ", part, start, len);\n");
        out.write("    }\n");
    }

    private void generateOrderByGroupByMethods(Writer out, String queryClassName, 
            String orderByBuilderClass, String groupByBuilderClass) throws IOException {
        
        out.write("    public " + orderByBuilderClass + " orderBy() {\n");
        out.write("        return new " + orderByBuilderClass + "();\n");
        out.write("    }\n\n");

        out.write("    public " + groupByBuilderClass + " groupBy() {\n");
        out.write("        return new " + groupByBuilderClass + "();\n");
        out.write("    }\n\n");

        out.write("    public " + queryClassName + " groupBy(String fieldExpression) {\n");
        out.write("        query.addGroupBy(fieldExpression);\n");
        out.write("        return this;\n");
        out.write("    }\n\n");

        out.write("    public " + queryClassName + " orderBy(String fieldExpression, boolean ascending) {\n");
        out.write("        query.addOrderBy(fieldExpression + (ascending ? \" ASC\" : \" DESC\"));\n");
        out.write("        return this;\n");
        out.write("    }\n\n");
    }

    private void generateProcessRows(Writer out, String entityName, String tableName,
            Map<String, List<GenField>> fieldsByAlias, Map<String, QueryNode> nodesByAlias,
            Map<String, QueryNode> parentNodes) throws IOException {

        out.write("    @Override\n");
        out.write("    @SuppressWarnings(\"unchecked\")\n");
        out.write("    protected List<" + entityName + "> processRows(List<Map<String, Object>> rows) throws NoSuchFieldException, IllegalAccessException {\n\n");

        // Generate field mapping lookups
        for (Map.Entry<String, QueryNode> entry : nodesByAlias.entrySet()) {
            String aliasName = entry.getKey();
            QueryNode node = entry.getValue();
            if (isEmbedded(node)) continue;

            String className = node.type().getSimpleName();
            String varPrefix = "fm" + capitalize(sanitizeVarName(aliasName));

            List<GenField> aliasFields = fieldsByAlias.get(aliasName);
            if (aliasFields != null) {
                out.write("        // " + className + " field mappings\n");
                for (GenField field : aliasFields) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    String varName = varPrefix + capitalize(fieldName);
                    out.write("        FieldMapping " + varName + " = dbContext.getFieldMapping(FieldHelper.getField(" +
                        className + ".class, \"" + fieldName + "\"));\n");
                }
                out.write("\n");
            }

            // Link field for relationships
            FieldModel linkField = getLinkField(node);
            String parentAlias = getParentAlias(node, parentNodes);
            if (linkField != null && parentAlias != null) {
                QueryNode parentNode = nodesByAlias.get(parentAlias);
                if (parentNode != null) {
                    String parentClassName = parentNode.type().getSimpleName();
                    String linkFieldName = linkField.getName();
                    String linkFieldVar = "f" + capitalize(sanitizeVarName(parentAlias)) + capitalize(linkFieldName);
                    out.write("        // Link field: " + parentAlias + "." + linkFieldName + "\n");
                    out.write("        Field " + linkFieldVar + " = FieldHelper.getField(" + parentClassName + ".class, \"" + linkFieldName + "\");\n");
                    out.write("        " + linkFieldVar + ".setAccessible(true);\n\n");
                }
            }
        }

        // Entity deduplication maps
        out.write("        // Entity deduplication maps\n");
        out.write("        List<" + entityName + "> result = new ArrayList<>();\n");
        for (Map.Entry<String, QueryNode> entry : nodesByAlias.entrySet()) {
            QueryNode node = entry.getValue();
            if (isEmbedded(node)) continue;
            String varName = sanitizeVarName(entry.getKey()) + "ById";
            String className = node.type().getSimpleName();
            out.write("        Map<Object, " + className + "> " + varName + " = new HashMap<>();\n");
        }
        out.write("\n");

        // Row processing loop
        out.write("        for (Map<String, Object> row : rows) {\n");

        // Process root entity
        QueryNode rootNode = nodesByAlias.get(tableName);
        List<String> rootIdFieldNames = getIdFieldNames(rootNode);
        if (rootNode != null && !rootIdFieldNames.isEmpty()) {
            String rootIdField = rootIdFieldNames.get(0);
            String rootVarPrefix = "fm" + capitalize(sanitizeVarName(tableName));

            out.write("            // Process root entity: " + entityName + "\n");
            out.write("            Object " + sanitizeVarName(tableName) + "Id = row.get(\"" + tableName + "." + rootIdField + "\");\n");
            out.write("            if (" + sanitizeVarName(tableName) + "Id == null) continue;\n\n");
            out.write("            " + entityName + " " + sanitizeVarName(tableName) + " = " + sanitizeVarName(tableName) + "ById.get(" + sanitizeVarName(tableName) + "Id);\n");
            out.write("            if (" + sanitizeVarName(tableName) + " == null) {\n");
            out.write("                " + sanitizeVarName(tableName) + " = new " + entityName + "();\n");

            List<GenField> rootFields = fieldsByAlias.get(tableName);
            if (rootFields != null) {
                for (GenField field : rootFields) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    String fmVar = rootVarPrefix + capitalize(fieldName);
                    out.write("                " + fmVar + ".apply(" + sanitizeVarName(tableName) + ", row.get(\"" + field.alias + "\"));\n");
                }
            }

            out.write("                " + sanitizeVarName(tableName) + "ById.put(" + sanitizeVarName(tableName) + "Id, " + sanitizeVarName(tableName) + ");\n");
            out.write("                result.add(" + sanitizeVarName(tableName) + ");\n");
            out.write("            }\n");
        }

        // Process related nodes
        for (Map.Entry<String, QueryNode> entry : nodesByAlias.entrySet()) {
            String aliasName = entry.getKey();
            QueryNode node = entry.getValue();

            if (aliasName.equals(tableName) || isEmbedded(node)) continue;
            String parentAlias = getParentAlias(node, parentNodes);
            FieldModel linkField = getLinkField(node);
            if (parentAlias == null || linkField == null) continue;
            List<String> aliasIdFields = getIdFieldNames(node);
            if (aliasIdFields.isEmpty()) continue;

            String className = node.type().getSimpleName();
            String aliasIdField = aliasIdFields.get(0);

            String aliasVarPrefix = "fm" + capitalize(sanitizeVarName(aliasName));
            String entityVar = sanitizeVarName(aliasName);
            String byIdVar = sanitizeVarName(aliasName) + "ById";
            String parentVar = sanitizeVarName(parentAlias);
            String linkFieldName = linkField.getName();
            String linkFieldVar = "f" + capitalize(sanitizeVarName(parentAlias)) + capitalize(linkFieldName);

            out.write("\n            // Process relationship: " + aliasName + " (" + className + ")\n");
            out.write("            Object " + entityVar + "Id = row.get(\"" + aliasName + "." + aliasIdField + "\");\n");
            out.write("            if (" + entityVar + "Id != null) {\n");
            out.write("                " + className + " " + entityVar + " = " + byIdVar + ".get(" + entityVar + "Id);\n");
            out.write("                if (" + entityVar + " == null) {\n");
            out.write("                    " + entityVar + " = new " + className + "();\n");

            List<GenField> aliasFields = fieldsByAlias.get(aliasName);
            if (aliasFields != null) {
                for (GenField field : aliasFields) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    String fmVar = aliasVarPrefix + capitalize(fieldName);
                    out.write("                    " + fmVar + ".apply(" + entityVar + ", row.get(\"" + field.alias + "\"));\n");
                }
            }

            out.write("                    " + byIdVar + ".put(" + entityVar + "Id, " + entityVar + ");\n");
            out.write("                }\n\n");
            out.write("                // Link to parent\n");
            
            QueryNode parentNode = nodesByAlias.get(parentAlias);
            if (parentAlias.equals(tableName)) {
                out.write("                FieldHelper.putValueIntoField(" + parentVar + ", " + linkFieldVar + ", " + entityVar + ");\n");
            } else if (parentNode != null) {
                List<String> parentIdFields = getIdFieldNames(parentNode);
                if (!parentIdFields.isEmpty()) {
                    String parentIdField = parentIdFields.get(0);
                    String parentByIdVar = sanitizeVarName(parentAlias) + "ById";
                    String parentIdVarName = entityVar + "_parentId";
                    String parentVarName = entityVar + "_parent";
                    out.write("                Object " + parentIdVarName + " = row.get(\"" + parentAlias + "." + parentIdField + "\");\n");
                    out.write("                " + parentNode.type().getSimpleName() + " " + parentVarName + " = " + parentByIdVar + ".get(" + parentIdVarName + ");\n");
                    out.write("                if (" + parentVarName + " != null) {\n");
                    out.write("                    FieldHelper.putValueIntoField(" + parentVarName + ", " + linkFieldVar + ", " + entityVar + ");\n");
                    out.write("                }\n");
                }
            }
            out.write("            }\n");
        }

        out.write("        }\n\n");
        out.write("        return result;\n");
        out.write("    }\n");
    }

    private void generateProcessRowStreaming(Writer out, String entityName, String tableName,
            Map<String, List<GenField>> fieldsByAlias, Map<String, QueryNode> nodesByAlias,
            Map<String, QueryNode> parentNodes) throws IOException {

        out.write("    @Override\n");
        out.write("    @SuppressWarnings(\"unchecked\")\n");
        out.write("    protected " + entityName + " processRowStreaming(Map<String, Object> row, Map<Object, Object> entityCache) throws NoSuchFieldException, IllegalAccessException {\n\n");

        // Generate field mapping lookups
        for (Map.Entry<String, QueryNode> entry : nodesByAlias.entrySet()) {
            String aliasName = entry.getKey();
            QueryNode node = entry.getValue();
            if (isEmbedded(node)) continue;

            String className = node.type().getSimpleName();
            String varPrefix = "fm" + capitalize(sanitizeVarName(aliasName));

            List<GenField> aliasFields = fieldsByAlias.get(aliasName);
            if (aliasFields != null) {
                out.write("        // " + className + " field mappings\n");
                for (GenField field : aliasFields) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    String varName = varPrefix + capitalize(fieldName);
                    out.write("        FieldMapping " + varName + " = dbContext.getFieldMapping(FieldHelper.getField(" +
                        className + ".class, \"" + fieldName + "\"));\n");
                }
                out.write("\n");
            }

            // Link field for relationships
            FieldModel linkField = getLinkField(node);
            String parentAlias = getParentAlias(node, parentNodes);
            if (linkField != null && parentAlias != null) {
                QueryNode parentNode = nodesByAlias.get(parentAlias);
                if (parentNode != null) {
                    String parentClassName = parentNode.type().getSimpleName();
                    String linkFieldName = linkField.getName();
                    String linkFieldVar = "f" + capitalize(sanitizeVarName(parentAlias)) + capitalize(linkFieldName);
                    out.write("        // Link field: " + parentAlias + "." + linkFieldName + "\n");
                    out.write("        Field " + linkFieldVar + " = FieldHelper.getField(" + parentClassName + ".class, \"" + linkFieldName + "\");\n");
                    out.write("        " + linkFieldVar + ".setAccessible(true);\n\n");
                }
            }
        }

        out.write("        " + entityName + " rootEntity = null;\n\n");

        // Process root entity
        QueryNode rootNode = nodesByAlias.get(tableName);
        List<String> rootIdFieldNames = getIdFieldNames(rootNode);
        if (rootNode != null && !rootIdFieldNames.isEmpty()) {
            String rootIdField = rootIdFieldNames.get(0);
            String rootVarPrefix = "fm" + capitalize(sanitizeVarName(tableName));

            out.write("        // Process root entity: " + entityName + "\n");
            out.write("        Object " + sanitizeVarName(tableName) + "Id = row.get(\"" + tableName + "." + rootIdField + "\");\n");
            out.write("        if (" + sanitizeVarName(tableName) + "Id != null) {\n");
            out.write("            " + entityName + " " + sanitizeVarName(tableName) + " = (" + entityName + ") entityCache.get(" + sanitizeVarName(tableName) + "Id);\n");
            out.write("            if (" + sanitizeVarName(tableName) + " == null) {\n");
            out.write("                " + sanitizeVarName(tableName) + " = new " + entityName + "();\n");

            List<GenField> rootFields = fieldsByAlias.get(tableName);
            if (rootFields != null) {
                for (GenField field : rootFields) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    String fmVar = rootVarPrefix + capitalize(fieldName);
                    out.write("                " + fmVar + ".apply(" + sanitizeVarName(tableName) + ", row.get(\"" + field.alias + "\"));\n");
                }
            }

            out.write("                entityCache.put(" + sanitizeVarName(tableName) + "Id, " + sanitizeVarName(tableName) + ");\n");
            out.write("            }\n");
            out.write("            rootEntity = " + sanitizeVarName(tableName) + ";\n");
        }

        // Process related nodes
        for (Map.Entry<String, QueryNode> entry : nodesByAlias.entrySet()) {
            String aliasName = entry.getKey();
            QueryNode node = entry.getValue();

            if (aliasName.equals(tableName) || isEmbedded(node)) continue;
            String parentAlias = getParentAlias(node, parentNodes);
            FieldModel linkField = getLinkField(node);
            if (parentAlias == null || linkField == null) continue;
            List<String> aliasIdFields = getIdFieldNames(node);
            if (aliasIdFields.isEmpty()) continue;

            String className = node.type().getSimpleName();
            String aliasIdField = aliasIdFields.get(0);

            String aliasVarPrefix = "fm" + capitalize(sanitizeVarName(aliasName));
            String entityVar = sanitizeVarName(aliasName);
            String parentVar = sanitizeVarName(parentAlias);
            String linkFieldName = linkField.getName();
            String linkFieldVar = "f" + capitalize(sanitizeVarName(parentAlias)) + capitalize(linkFieldName);

            out.write("\n            // Process relationship: " + aliasName + " (" + className + ")\n");
            out.write("            Object " + entityVar + "Id = row.get(\"" + aliasName + "." + aliasIdField + "\");\n");
            out.write("            if (" + entityVar + "Id != null) {\n");
            out.write("                " + className + " " + entityVar + " = (" + className + ") entityCache.get(" + entityVar + "Id);\n");
            out.write("                if (" + entityVar + " == null) {\n");
            out.write("                    " + entityVar + " = new " + className + "();\n");

            List<GenField> aliasFields = fieldsByAlias.get(aliasName);
            if (aliasFields != null) {
                for (GenField field : aliasFields) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    String fmVar = aliasVarPrefix + capitalize(fieldName);
                    out.write("                    " + fmVar + ".apply(" + entityVar + ", row.get(\"" + field.alias + "\"));\n");
                }
            }

            out.write("                    entityCache.put(" + entityVar + "Id, " + entityVar + ");\n");
            out.write("                }\n\n");
            out.write("                // Link to parent\n");
            
            QueryNode parentNode = nodesByAlias.get(parentAlias);
            if (parentAlias.equals(tableName)) {
                out.write("                FieldHelper.putValueIntoField(" + parentVar + ", " + linkFieldVar + ", " + entityVar + ");\n");
            } else if (parentNode != null) {
                List<String> parentIdFields = getIdFieldNames(parentNode);
                if (!parentIdFields.isEmpty()) {
                    String parentIdField = parentIdFields.get(0);
                    String parentIdVarName = entityVar + "_parentId";
                    String parentVarName = entityVar + "_parent";
                    out.write("                Object " + parentIdVarName + " = row.get(\"" + parentAlias + "." + parentIdField + "\");\n");
                    out.write("                " + parentNode.type().getSimpleName() + " " + parentVarName + " = (" + parentNode.type().getSimpleName() + ") entityCache.get(" + parentIdVarName + ");\n");
                    out.write("                if (" + parentVarName + " != null) {\n");
                    out.write("                    FieldHelper.putValueIntoField(" + parentVarName + ", " + linkFieldVar + ", " + entityVar + ");\n");
                    out.write("                }\n");
                }
            }
            out.write("            }\n");
        }

        // Close root entity null check
        if (rootNode != null && !rootIdFieldNames.isEmpty()) {
            out.write("        }\n");
        }

        out.write("\n        return rootEntity;\n");
        out.write("    }\n");
    }

    private void generateGetPrimaryKeyFromRow(Writer out, String tableName,
            Map<String, QueryNode> nodesByAlias, Map<String, List<GenField>> fieldsByAlias) throws IOException {

        QueryNode rootNode = nodesByAlias.get(tableName);
        List<String> idFieldNames = getIdFieldNames(rootNode);
        String idColumnName = !idFieldNames.isEmpty() ? idFieldNames.get(0) : "id";

        out.write("    @Override\n");
        out.write("    protected Object getPrimaryKeyFromRow(Map<String, Object> row) {\n");
        out.write("        return row.get(\"" + tableName + "." + idColumnName + "\");\n");
        out.write("    }\n");
    }

    private void generateGetIdFieldName(Writer out, String tableName,
            Map<String, QueryNode> nodesByAlias, Map<String, List<GenField>> fieldsByAlias) throws IOException {

        QueryNode rootNode = nodesByAlias.get(tableName);
        List<String> idFieldNames = getIdFieldNames(rootNode);
        String idColumnName = !idFieldNames.isEmpty() ? idFieldNames.get(0) : "id";

        out.write("    @Override\n");
        out.write("    protected String getIdFieldName() {\n");
        out.write("        return \"" + tableName + "." + idColumnName + "\";\n");
        out.write("    }\n");
    }

    private void generateBuildIdCondition(Writer out, String tableName,
            Map<String, QueryNode> nodesByAlias, Map<String, List<GenField>> fieldsByAlias) throws IOException {

        QueryNode rootNode = nodesByAlias.get(tableName);
        List<String> idFieldNames = getIdFieldNames(rootNode);
        String idColumnName = !idFieldNames.isEmpty() ? idFieldNames.get(0) : "id";

        out.write("    @Override\n");
        out.write("    protected SqlExpression buildIdCondition(Object id) {\n");
        out.write("        return SqlExpression.sql(\"{" + tableName + "." + idColumnName + "} = ?\", id);\n");
        out.write("    }\n");
    }

    private void generateGetEntityClass(Writer out, String entityName) throws IOException {
        out.write("    @Override\n");
        out.write("    protected Class<" + entityName + "> getEntityClass() {\n");
        out.write("        return " + entityName + ".class;\n");
        out.write("    }\n");
    }

    private void generateGroupByBuilderClass(Writer out, String queryClassName,
            String groupByBuilderClass, String tableName, List<GenField> mainFields, String indent) throws IOException {

        out.write(indent + "public class " + groupByBuilderClass + " {\n\n");

        for (GenField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            out.write(indent + "    public final " + queryClassName + "GroupByField " + fieldName + ";\n");
        }
        out.write("\n");

        out.write(indent + "    public " + groupByBuilderClass + "() {\n");
        for (GenField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            String columnName = extractColumnNameFromExpression(field.expression);
            out.write(indent + "        this." + fieldName + " = new " + queryClassName + "GroupByField(\"" + tableName + "\", \"" + columnName + "\");\n");
        }
        out.write(indent + "    }\n");

        out.write(indent + "}\n");
    }

    private void generateOrderByBuilderClass(Writer out, String queryClassName,
            String orderByBuilderClass, String tableName, List<GenField> mainFields,
            Map<String, List<GenField>> fieldsByAlias, Map<String, QueryNode> nodesByAlias, String indent) throws IOException {

        out.write(indent + "public class " + orderByBuilderClass + " implements OrderByTarget {\n\n");

        for (GenField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            out.write(indent + "    public final OrderByField<" + queryClassName + "> " + fieldName + ";\n");
        }
        out.write("\n");

        // Nested relationship fields
        RelationNode root = buildRelationTree(tableName, fieldsByAlias.keySet());
        for (RelationNode child : root.children.values()) {
            generateNestedOrderByFields(out, queryClassName, child, fieldsByAlias, nodesByAlias, indent + "    ");
        }

        out.write(indent + "    public " + orderByBuilderClass + "() {\n");
        for (GenField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            String columnName = extractColumnNameFromExpression(field.expression);
            out.write(indent + "        this." + fieldName + " = new OrderByField<>(this, " + queryClassName + ".this, \"" + tableName + "\", \"" + columnName + "\");\n");
        }
        out.write(indent + "    }\n\n");

        out.write(indent + "    @Override\n");
        out.write(indent + "    public void orderBy(String fieldExpression, boolean ascending) {\n");
        out.write(indent + "        query.addOrderBy(fieldExpression + (ascending ? \" ASC\" : \" DESC\"));\n");
        out.write(indent + "    }\n");

        out.write(indent + "}\n");
    }

    private void generateNestedOrderByFields(Writer out, String queryClassName,
            RelationNode node, Map<String, List<GenField>> fieldsByAlias, Map<String, QueryNode> nodesByAlias, String indent) throws IOException {
        
        String relationName = node.name;
        String aliasName = node.fullAlias;
        List<GenField> relationFields = fieldsByAlias.getOrDefault(aliasName, List.of());
        
        String innerClassName = capitalize(relationName) + "OrderByFields";
        out.write(indent + "/** OrderBy fields for the {@code " + relationName + "} relationship */\n");
        out.write(indent + "public final " + innerClassName + " " + relationName + " = new " + innerClassName + "();\n\n");
        out.write(indent + "public class " + innerClassName + " {\n");
        
        for (GenField field : relationFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            String columnName = extractColumnNameFromExpression(field.expression);
            out.write(indent + "    public final OrderByField<" + queryClassName + "> " + fieldName + " =\n");
            out.write(indent + "            new OrderByField<>(" + queryClassName + "OrderByBuilder.this, " + queryClassName + ".this, \"" + aliasName + "\", \"" + columnName + "\");\n");
        }
        
        for (RelationNode child : node.children.values()) {
            out.write("\n");
            generateNestedOrderByFields(out, queryClassName, child, fieldsByAlias, nodesByAlias, indent + "    ");
        }
        
        out.write(indent + "}\n\n");
    }

    private void generateDelegateClass(Writer out, String entityName, String queryClassName,
            String pkType, String groupByBuilderClass, String orderByBuilderClass, String indent) throws IOException {

        out.write(indent + "/**\n");
        out.write(indent + " * Delegate class for callback pattern - allows groupBy().field.list() syntax.\n");
        out.write(indent + " */\n");
        out.write(indent + "private class " + queryClassName + "Delegate {\n");
        out.write(indent + "    protected void callback() {}\n\n");
        out.write(indent + "    public List<" + entityName + "> list(Connection connection) {\n");
        out.write(indent + "        callback();\n");
        out.write(indent + "        return " + queryClassName + ".this.list(connection);\n");
        out.write(indent + "    }\n\n");
        out.write(indent + "    public List<" + pkType + "> listIds(Connection connection) {\n");
        out.write(indent + "        callback();\n");
        out.write(indent + "        return " + queryClassName + ".this.listIds(connection);\n");
        out.write(indent + "    }\n\n");
        out.write(indent + "    public Optional<" + entityName + "> first(Connection connection) {\n");
        out.write(indent + "        callback();\n");
        out.write(indent + "        return " + queryClassName + ".this.first(connection);\n");
        out.write(indent + "    }\n\n");
        out.write(indent + "    public Optional<" + entityName + "> findById(Connection connection, " + pkType + " id) {\n");
        out.write(indent + "        callback();\n");
        out.write(indent + "        return " + queryClassName + ".this.findById(connection, id);\n");
        out.write(indent + "    }\n\n");
        out.write(indent + "    public void stream(Connection connection, java.util.function.Consumer<" + entityName + "> consumer) {\n");
        out.write(indent + "        callback();\n");
        out.write(indent + "        " + queryClassName + ".this.stream(connection, consumer);\n");
        out.write(indent + "    }\n\n");
        out.write(indent + "    public " + groupByBuilderClass + " groupBy() {\n");
        out.write(indent + "        callback();\n");
        out.write(indent + "        return " + queryClassName + ".this.groupBy();\n");
        out.write(indent + "    }\n\n");
        out.write(indent + "    public " + orderByBuilderClass + " orderBy() {\n");
        out.write(indent + "        callback();\n");
        out.write(indent + "        return " + queryClassName + ".this.orderBy();\n");
        out.write(indent + "    }\n");
        out.write(indent + "}\n");
    }

    private void generateGroupByFieldClass(Writer out, String queryClassName,
            String groupByBuilderClass, String orderByBuilderClass, String indent) throws IOException {

        out.write(indent + "public class " + queryClassName + "GroupByField extends " + queryClassName + "Delegate {\n");
        out.write(indent + "    private String tableAlias;\n");
        out.write(indent + "    private String columnName;\n\n");
        out.write(indent + "    public " + queryClassName + "GroupByField(String tableAlias, String columnName) {\n");
        out.write(indent + "        this.tableAlias = tableAlias;\n");
        out.write(indent + "        this.columnName = columnName;\n");
        out.write(indent + "    }\n\n");
        out.write(indent + "    @Override\n");
        out.write(indent + "    protected void callback() {\n");
        out.write(indent + "        query.addGroupBy(\"{\" + tableAlias + \".\" + columnName + \"}\");\n");
        out.write(indent + "    }\n");
        out.write(indent + "}\n");
    }

    private void generateWhereBuilderClass(Writer out, String queryClassName,
            String whereBuilderClass, String tableName, List<GenField> mainFields, QueryNode mainNode,
            Map<String, List<GenField>> fieldsByAlias, Map<String, QueryNode> nodesByAlias, String indent) throws IOException {

        String terminatorClass = whereBuilderClass + "ConditionTerminator";

        out.write(indent + "/**\n");
        out.write(indent + " * Where clause builder for fluent condition chaining.\n");
        out.write(indent + " */\n");
        out.write(indent + "public class " + whereBuilderClass + " implements ConditionChain<" + whereBuilderClass + "." + terminatorClass + "> {\n\n");

        // Fields for condition building on main entity
        for (GenField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            String columnName = extractColumnNameFromExpression(field.expression);
            FieldInfo fieldInfo = getFieldInfo(mainNode, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.write(indent + "    public final " + builderClass + "<" + fieldInfo.typeName + ", " + terminatorClass + "> " + fieldName + " =\n");
            out.write(indent + "            new " + builderClass + "<>(this::getContinuation, \"" + tableName + "\", \"" + columnName + "\");\n");
        }
        out.write("\n");

        // Nested relationship fields
        RelationNode root = buildRelationTree(tableName, fieldsByAlias.keySet());
        for (RelationNode child : root.children.values()) {
            generateNestedWhereFields(out, queryClassName, whereBuilderClass, terminatorClass, child, fieldsByAlias, nodesByAlias, indent + "    ");
        }

        out.write(indent + "    ConditionBuilder builder = new WhereBuilderImpl();\n\n");

        out.write(indent + "    protected " + whereBuilderClass + "(" + queryClassName + " query) {\n");
        out.write(indent + "    }\n\n");

        // Inner BuilderImpl class
        out.write(indent + "    public class WhereBuilderImpl implements ConditionBuilder {\n");
        out.write(indent + "        public ConditionBuilder startClause() {\n");
        out.write(indent + "            " + queryClassName + ".this.collectedConditions.add(sql(\" (\"));\n");
        out.write(indent + "            return this;\n");
        out.write(indent + "        }\n\n");
        out.write(indent + "        public ConditionBuilder endClause() {\n");
        out.write(indent + "            " + queryClassName + ".this.collectedConditions.add(sql(\") \"));\n");
        out.write(indent + "            return this;\n");
        out.write(indent + "        }\n\n");
        out.write(indent + "        @Override\n");
        out.write(indent + "        public ConditionBuilder add(SqlExpression expr) {\n");
        out.write(indent + "            " + queryClassName + ".this.collectedConditions.add(expr);\n");
        out.write(indent + "            return this;\n");
        out.write(indent + "        }\n");
        out.write(indent + "    }\n\n");

        // ConditionTerminator inner class
        out.write(indent + "    public class " + terminatorClass + " extends " + queryClassName + "Delegate {\n\n");
        
        // Fields for condition building - copy from main entity
        for (GenField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            String columnName = extractColumnNameFromExpression(field.expression);
            FieldInfo fieldInfo = getFieldInfo(mainNode, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.write(indent + "        public final " + builderClass + "<" + fieldInfo.typeName + ", " + terminatorClass + "> " + fieldName + " =\n");
            out.write(indent + "                new " + builderClass + "<>(() -> this, \"" + tableName + "\", \"" + columnName + "\");\n");
        }
        out.write("\n");

        // Nested relationship fields for terminator
        for (RelationNode child : root.children.values()) {
            generateNestedTerminatorFields(out, queryClassName, terminatorClass, child, fieldsByAlias, nodesByAlias, indent + "        ");
        }

        out.write(indent + "        public " + terminatorClass + " and() {\n");
        out.write(indent + "            builder.add(sql(\" AND \"));\n");
        out.write(indent + "            return this;\n");
        out.write(indent + "        }\n\n");

        out.write(indent + "        public " + terminatorClass + " or() {\n");
        out.write(indent + "            builder.add(sql(\" OR \"));\n");
        out.write(indent + "            return this;\n");
        out.write(indent + "        }\n\n");

        out.write(indent + "        public " + terminatorClass + " and(Supplier<SqlExpression> expr) {\n");
        out.write(indent + "            builder.add(sql(\" AND \")).startClause().add(expr.get()).endClause();\n");
        out.write(indent + "            return this;\n");
        out.write(indent + "        }\n\n");

        out.write(indent + "        public " + terminatorClass + " or(Supplier<SqlExpression> expr) {\n");
        out.write(indent + "            builder.add(sql(\" OR \")).startClause().add(expr.get()).endClause();\n");
        out.write(indent + "            return this;\n");
        out.write(indent + "        }\n");
        out.write(indent + "    }\n\n");

        // getContinuation and getBuilder methods
        out.write(indent + "    @Override\n");
        out.write(indent + "    public ConditionBuilder getBuilder() {\n");
        out.write(indent + "        return builder;\n");
        out.write(indent + "    }\n\n");

        out.write(indent + "    @Override\n");
        out.write(indent + "    public " + terminatorClass + " getContinuation() {\n");
        out.write(indent + "        return new " + terminatorClass + "();\n");
        out.write(indent + "    }\n");

        out.write(indent + "}\n");
    }

    private void generateNestedWhereFields(Writer out, String queryClassName, String whereBuilderClass,
            String terminatorClass, RelationNode node, Map<String, List<GenField>> fieldsByAlias,
            Map<String, QueryNode> nodesByAlias, String indent) throws IOException {

        String relationName = node.name;
        String aliasName = node.fullAlias;
        QueryNode relNode = nodesByAlias.get(aliasName);
        List<GenField> relationFields = fieldsByAlias.getOrDefault(aliasName, List.of());

        String innerClassName = capitalize(relationName) + "WhereFields";
        out.write(indent + "/** Where fields for the {@code " + relationName + "} relationship */\n");
        out.write(indent + "public final " + innerClassName + " " + relationName + " = new " + innerClassName + "();\n\n");
        out.write(indent + "public class " + innerClassName + " {\n");

        for (GenField field : relationFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            String columnName = extractColumnNameFromExpression(field.expression);
            FieldInfo fieldInfo = getFieldInfo(relNode, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.write(indent + "    public final " + builderClass + "<" + fieldInfo.typeName + ", " + terminatorClass + "> " + fieldName + " =\n");
            out.write(indent + "            new " + builderClass + "<>(" + whereBuilderClass + ".this::getContinuation, \"" + aliasName + "\", \"" + columnName + "\");\n");
        }

        for (RelationNode child : node.children.values()) {
            out.write("\n");
            generateNestedWhereFields(out, queryClassName, whereBuilderClass, terminatorClass, child, fieldsByAlias, nodesByAlias, indent + "    ");
        }

        out.write(indent + "}\n\n");
    }

    private void generateNestedTerminatorFields(Writer out, String queryClassName,
            String terminatorClass, RelationNode node, Map<String, List<GenField>> fieldsByAlias,
            Map<String, QueryNode> nodesByAlias, String indent) throws IOException {

        String relationName = node.name;
        String aliasName = node.fullAlias;
        QueryNode relNode = nodesByAlias.get(aliasName);
        List<GenField> relationFields = fieldsByAlias.getOrDefault(aliasName, List.of());

        String innerClassName = capitalize(relationName) + "TerminatorFields";
        out.write(indent + "/** Terminator fields for the {@code " + relationName + "} relationship */\n");
        out.write(indent + "public final " + innerClassName + " " + relationName + " = new " + innerClassName + "();\n\n");
        out.write(indent + "public class " + innerClassName + " {\n");

        for (GenField field : relationFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            String columnName = extractColumnNameFromExpression(field.expression);
            FieldInfo fieldInfo = getFieldInfo(relNode, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.write(indent + "    public final " + builderClass + "<" + fieldInfo.typeName + ", " + terminatorClass + "> " + fieldName + " =\n");
            out.write(indent + "            new " + builderClass + "<>(() -> " + terminatorClass + ".this, \"" + aliasName + "\", \"" + columnName + "\");\n");
        }

        for (RelationNode child : node.children.values()) {
            out.write("\n");
            generateNestedTerminatorFields(out, queryClassName, terminatorClass, child, fieldsByAlias, nodesByAlias, indent + "    ");
        }

        out.write(indent + "}\n\n");
    }
}
