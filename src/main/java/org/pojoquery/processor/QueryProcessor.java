package org.pojoquery.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.Alias;
import org.pojoquery.pipeline.CustomizableQueryBuilder;
import org.pojoquery.pipeline.QueryBuilder;
import org.pojoquery.pipeline.SqlQuery.SqlField;
import org.pojoquery.pipeline.SqlQuery.SqlJoin;
import org.pojoquery.typemodel.ElementTypeModel;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Annotation processor that generates fluent query builders.
 *
 * <p>For each class annotated with {@code @GenerateQuery}, this processor generates:
 * <ul>
 *   <li>{@code EntityNameQuery} - Fluent query builder with static condition chains</li>
 * </ul>
 *
 * <p>The generated code follows the pattern:
 * <pre>
 * BookQuery q = new BookQuery();
 * q.title.eq("John").and().title.isNotNull().and(q.id.gt(123L))
 * </pre>
 */
@SupportedAnnotationTypes("org.pojoquery.annotations.GenerateQuery")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class QueryProcessor extends AbstractProcessor {

    private Filer filer;
    private Messager messager;
    private Elements elementUtils;
    private Types typeUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateQuery.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "@GenerateQuery can only be applied to classes", element);
                continue;
            }

            TypeElement typeElement = (TypeElement) element;
            try {
                processEntity(typeElement);
            } catch (Exception e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate query classes: " + e.getMessage() +
                    " (" + e.getClass().getSimpleName() + ")", element);
            }
        }
        return true;
    }

    private void processEntity(TypeElement typeElement) throws Exception {
        GenerateQuery annotation = typeElement.getAnnotation(GenerateQuery.class);
        String querySuffix = annotation.querySuffix();

        String packageName = getPackageName(typeElement);
        String entityName = typeElement.getSimpleName().toString();
        String qualifiedName = typeElement.getQualifiedName().toString();

        // For inner classes, prefix generated class names with enclosing class names
        String classNamePrefix = getEnclosingClassPrefix(typeElement);
        String queryClassName = classNamePrefix + entityName + querySuffix;

        // Use ElementTypeModel to process the entity at compile time
        TypeModel entityType = new ElementTypeModel(typeElement, elementUtils, typeUtils);

        List<TableMapping> tableMapping = QueryBuilder.determineTableMapping(entityType);
        if (tableMapping.size() == 0) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@GenerateQuery requires @Table annotation on entity or its superclasses", typeElement);
            return;
        }
        String schemaName = tableMapping.get(0).schemaName;
        String tableName = tableMapping.get(0).tableName;

        CustomizableQueryBuilder<?, ?> builder = QueryBuilder.from(entityType);

        // Extract query structure
        var query = builder.getQuery();
        List<SqlField> fields = query.getFields();
        List<SqlJoin> joins = query.getJoins();
        LinkedHashMap<String, Alias> aliases = builder.getAliases();

        messager.printMessage(Diagnostic.Kind.NOTE,
            "Generating query classes for " + qualifiedName +
            " (" + fields.size() + " fields, " + joins.size() + " joins, " +
            aliases.size() + " aliases)", typeElement);

        // Generate the query class
        generateQueryClass(packageName, entityName, queryClassName, schemaName, tableName,
            fields, joins, aliases);
    }

    // === Code generation ===

    private void generateQueryClass(String packageName, String entityName,
            String queryClassName, String schemaName, String tableName,
            List<SqlField> fields, List<SqlJoin> joins,
            LinkedHashMap<String, Alias> aliases) throws IOException {

        String qualifiedName = packageName.isEmpty() ? queryClassName : packageName + "." + queryClassName;
        JavaFileObject fileObject = filer.createSourceFile(qualifiedName);

        String chainClassName = queryClassName + "StaticConditionChain";

        try (PrintWriter out = new PrintWriter(fileObject.openWriter())) {
            if (!packageName.isEmpty()) {
                out.println("package " + packageName + ";");
                out.println();
            }

            out.println("import java.lang.reflect.Field;");
            out.println("import java.sql.Connection;");
            out.println("import java.util.ArrayList;");
            out.println("import java.util.HashMap;");
            out.println("import java.util.List;");
            out.println("import java.util.Map;");
            out.println("import java.util.function.Supplier;");
            out.println();
            out.println("import org.pojoquery.DB;");
            out.println("import org.pojoquery.DbContext;");
            out.println("import org.pojoquery.FieldMapping;");
            out.println("import org.pojoquery.SqlExpression;");
            out.println("import org.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;");
            out.println("import org.pojoquery.pipeline.SqlQuery;");
            out.println("import org.pojoquery.util.FieldHelper;");
            out.println();
            out.println("import org.pojoquery.typedquery.ChainFactory;");
            out.println("import org.pojoquery.typedquery.ComparableConditionBuilderField;");
            out.println("import org.pojoquery.typedquery.ConditionBuilder;");
            out.println("import org.pojoquery.typedquery.ConditionBuilderField;");
            out.println("import org.pojoquery.typedquery.ConditionBuilderImpl;");
            out.println("import org.pojoquery.typedquery.ConditionChain;");
            out.println("import org.pojoquery.typedquery.OrderByField;");
            out.println("import org.pojoquery.typedquery.OrderByTarget;");
            out.println();
            out.println("import static org.pojoquery.SqlExpression.sql;");
            out.println();
            out.println("/**");
            out.println(" * Generated fluent query builder for {@link " + entityName + "}.");
            out.println(" * <p>Usage example:");
            out.println(" * <pre>");
            out.println(" * " + queryClassName + " q = new " + queryClassName + "();");
            out.println(" * q.title.eq(\"John\").and().title.isNotNull();");
            out.println(" * </pre>");
            out.println(" */");
            out.println("@SuppressWarnings(\"all\")");
            out.println("public class " + queryClassName + " {");
            out.println();

            // Group fields by alias
            Map<String, List<SqlField>> fieldsByAlias = new LinkedHashMap<>();
            for (SqlField field : fields) {
                String alias = extractAliasFromFieldAlias(field.alias);
                fieldsByAlias.computeIfAbsent(alias, k -> new ArrayList<>()).add(field);
            }

            // Generate static condition builder fields for main entity
            Alias mainAlias = aliases.get(tableName);
            List<SqlField> mainFields = fieldsByAlias.getOrDefault(tableName, List.of());
            out.println("    // Static condition builder fields for main entity");
            for (SqlField field : mainFields) {
                String fieldName = extractFieldNameFromAlias(field.alias);
                FieldInfo fieldInfo = getFieldInfo(mainAlias, fieldName);
                String builderClass = fieldInfo.isComparable
                    ? "ComparableConditionBuilderField"
                    : "ConditionBuilderField";
                out.println("    public final " + builderClass + "<" + fieldInfo.typeName + ", " + chainClassName + "> " + fieldName + " =");
                out.println("            new " + builderClass + "<>(() -> new " + chainClassName + "(), \"" + tableName + "\", \"" + fieldName + "\");");
            }
            out.println();

            // Build tree structure for nested relationships
            RelationNode root = buildRelationTree(tableName, fieldsByAlias.keySet());
            
            // Generate nested static field classes for relationships
            for (RelationNode child : root.children.values()) {
                generateNestedStaticFields(out, queryClassName, chainClassName, child, fieldsByAlias, aliases, "    ");
            }

            // Generate the StaticConditionChain inner class
            generateStaticConditionChainClass(out, queryClassName, chainClassName, tableName, mainFields, mainAlias, fieldsByAlias, aliases, "    ");
            out.println();

            // Generate the SqlQuery field and initialization
            out.println("    protected SqlQuery<?> query = new DefaultSqlQuery(DbContext.getDefault());");
            out.println("    protected DbContext dbContext = DbContext.getDefault();");
            out.println();
            out.println("    protected void initializeQuery() {");
            // Set the table first
            String tableSchemaArg = schemaName == null ? "null" : "\"" + escapeJava(schemaName) + "\"";
            out.println("        query.setTable(" + tableSchemaArg + ", \"" + escapeJava(tableName) + "\");");
            // Add joins
            for (SqlJoin join : joins) {
                String joinSchemaArg = join.schema == null ? "null" : "\"" + escapeJava(join.schema) + "\"";
                String conditionArg = join.joinCondition == null ? "null" :
                    "SqlExpression.sql(\"" + escapeJava(join.joinCondition.getSql()) + "\")";
                out.println("        query.addJoin(org.pojoquery.pipeline.SqlQuery.JoinType." + join.joinType.name() +
                    ", " + joinSchemaArg + ", \"" + escapeJava(join.table) +
                    "\", \"" + escapeJava(join.alias) + "\", " + conditionArg + ");");
            }
            // Then add fields
            for (SqlField field : fields) {
                out.println("        query.addField(sql(\"" + escapeJava(field.expression.getSql()) + "\"), \"" + escapeJava(field.alias) + "\");");
            }
            out.println("    }");
            out.println();

            // Constructor
            out.println("    public " + queryClassName + "() {");
            out.println("        initializeQuery();");
            out.println("    }");
            out.println();

            // where() method returning WhereBuilder
            String whereBuilderClass = queryClassName + "WhereBuilder";
            out.println("    public " + whereBuilderClass + " where() {");
            out.println("        return new " + whereBuilderClass + "(this);");
            out.println("    }");
            out.println();

            // orderBy() method
            String orderByBuilderClass = queryClassName + "OrderByBuilder";
            out.println("    public " + orderByBuilderClass + " orderBy() {");
            out.println("        return new " + orderByBuilderClass + "();");
            out.println("    }");
            out.println();

            // groupBy() method
            String groupByBuilderClass = queryClassName + "GroupByBuilder";
            out.println("    public " + groupByBuilderClass + " groupBy() {");
            out.println("        return new " + groupByBuilderClass + "();");
            out.println("    }");
            out.println();

            out.println("    public " + queryClassName + " groupBy(String fieldExpression) {");
            out.println("        query.addGroupBy(fieldExpression);");
            out.println("        return this;");
            out.println("    }");
            out.println();

            out.println("    public " + queryClassName + " orderBy(String fieldExpression, boolean ascending) {");
            out.println("        query.addOrderBy(fieldExpression + (ascending ? \" ASC\" : \" DESC\"));");
            out.println("        return this;");
            out.println("    }");
            out.println();

            // list() method
            out.println("    public List<" + entityName + "> list(Connection connection) {");
            out.println("        SqlExpression stmt = query.toStatement();");
            out.println("        List<Map<String, Object>> rows = DB.queryRows(connection, stmt);");
            out.println("        try {");
            out.println("            return processRows(rows);");
            out.println("        } catch (NoSuchFieldException | IllegalAccessException e) {");
            out.println("            throw new RuntimeException(e);");
            out.println("        }");
            out.println("    }");
            out.println();

            // Generate processRows method
            generateProcessRows(out, entityName, tableName, fieldsByAlias, aliases);
            out.println();

            // Generate GroupByBuilder inner class
            generateGroupByBuilderClass(out, queryClassName, groupByBuilderClass, tableName, mainFields, "    ");
            out.println();

            // Generate OrderByBuilder inner class
            generateOrderByBuilderClass(out, queryClassName, orderByBuilderClass, tableName, mainFields, fieldsByAlias, aliases, "    ");
            out.println();

            // Generate delegate class for callback pattern
            generateDelegateClass(out, entityName, queryClassName, groupByBuilderClass, orderByBuilderClass, "    ");
            out.println();

            // Generate GroupByField inner class
            generateGroupByFieldClass(out, queryClassName, groupByBuilderClass, orderByBuilderClass, "    ");
            out.println();

            // Generate WhereBuilder inner class
            generateWhereBuilderClass(out, queryClassName, whereBuilderClass, tableName, mainFields, mainAlias, fieldsByAlias, aliases, "    ");
            out.println();

            out.println("}");
        }
    }

    /**
     * Generates nested static field classes for relationships.
     * Creates inner classes like: public final class author { ... } with condition fields for author.id, author.name, etc.
     */
    private void generateNestedStaticFields(PrintWriter out, String queryClassName, String chainClassName,
            RelationNode node, Map<String, List<SqlField>> fieldsByAlias, LinkedHashMap<String, Alias> aliases, String indent) {
        
        String relationName = node.name;
        String aliasName = node.fullAlias;
        Alias relAlias = aliases.get(aliasName);
        List<SqlField> relationFields = fieldsByAlias.getOrDefault(aliasName, List.of());
        
        out.println(indent + "/** Static condition builder fields for the {@code " + relationName + "} relationship */");
        out.println(indent + "public final " + capitalize(relationName) + "Fields " + relationName + " = new " + capitalize(relationName) + "Fields();");
        out.println();
        out.println(indent + "public class " + capitalize(relationName) + "Fields {");
        
        // Generate condition fields for this relationship
        for (SqlField field : relationFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            FieldInfo fieldInfo = getFieldInfo(relAlias, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.println(indent + "    public final " + builderClass + "<" + fieldInfo.typeName + ", " + chainClassName + "> " + fieldName + " =");
            out.println(indent + "            new " + builderClass + "<>(() -> new " + chainClassName + "(), \"" + aliasName + "\", \"" + fieldName + "\");");
        }
        
        // Recursively generate for child relations
        for (RelationNode child : node.children.values()) {
            out.println();
            generateNestedStaticFields(out, queryClassName, chainClassName, child, fieldsByAlias, aliases, indent + "    ");
        }
        
        out.println(indent + "}");
        out.println();
    }

    /**
     * Generates the processRows method for mapping query results to entities.
     * Similar to QueryProcessor's generateProcessRows.
     */
    private void generateProcessRows(PrintWriter out, String entityName, String tableName,
            Map<String, List<SqlField>> fieldsByAlias, LinkedHashMap<String, Alias> aliases) {

        out.println("    @SuppressWarnings(\"unchecked\")");
        out.println("    private List<" + entityName + "> processRows(List<Map<String, Object>> rows) throws NoSuchFieldException, IllegalAccessException {");
        out.println();

        // Generate field mapping lookups using Alias metadata
        for (Map.Entry<String, Alias> entry : aliases.entrySet()) {
            String aliasName = entry.getKey();
            Alias alias = entry.getValue();
            if (alias.getIsEmbedded()) continue;

            String className = alias.getResultType().getSimpleName();
            String varPrefix = "fm" + capitalize(sanitizeVarName(aliasName));

            List<SqlField> aliasFields = fieldsByAlias.get(aliasName);
            if (aliasFields != null) {
                out.println("        // " + className + " field mappings");
                for (SqlField field : aliasFields) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    String varName = varPrefix + capitalize(fieldName);
                    out.println("        FieldMapping " + varName + " = dbContext.getFieldMapping(FieldHelper.getField(" +
                        className + ".class, \"" + fieldName + "\"));");
                }
                out.println();
            }

            // Link field for relationships
            FieldModel linkField = alias.getLinkField();
            if (linkField != null && alias.getParentAlias() != null) {
                Alias parentAlias = aliases.get(alias.getParentAlias());
                if (parentAlias != null) {
                    String parentClassName = parentAlias.getResultType().getSimpleName();
                    String linkFieldName = linkField.getName();
                    String linkFieldVar = "f" + capitalize(sanitizeVarName(alias.getParentAlias())) + capitalize(linkFieldName);
                    out.println("        // Link field: " + alias.getParentAlias() + "." + linkFieldName);
                    out.println("        Field " + linkFieldVar + " = FieldHelper.getField(" + parentClassName + ".class, \"" + linkFieldName + "\");");
                    out.println("        " + linkFieldVar + ".setAccessible(true);");
                    out.println();
                }
            }
        }

        // Entity deduplication maps
        out.println("        // Entity deduplication maps");
        out.println("        List<" + entityName + "> result = new ArrayList<>();");
        for (Map.Entry<String, Alias> entry : aliases.entrySet()) {
            Alias alias = entry.getValue();
            if (alias.getIsEmbedded()) continue;
            String varName = sanitizeVarName(entry.getKey()) + "ById";
            String className = alias.getResultType().getSimpleName();
            out.println("        Map<Object, " + className + "> " + varName + " = new HashMap<>();");
        }
        out.println();

        // Row processing loop
        out.println("        for (Map<String, Object> row : rows) {");

        // Process root entity
        Alias rootAlias = aliases.get(tableName);
        if (rootAlias != null && rootAlias.getIdFields() != null && !rootAlias.getIdFields().isEmpty()) {
            FieldModel rootIdField = rootAlias.getIdFields().get(0);
            String rootVarPrefix = "fm" + capitalize(sanitizeVarName(tableName));

            out.println("            // Process root entity: " + entityName);
            out.println("            Object " + sanitizeVarName(tableName) + "Id = row.get(\"" + tableName + "." + rootIdField.getName() + "\");");
            out.println("            if (" + sanitizeVarName(tableName) + "Id == null) continue;");
            out.println();
            out.println("            " + entityName + " " + sanitizeVarName(tableName) + " = " + sanitizeVarName(tableName) + "ById.get(" + sanitizeVarName(tableName) + "Id);");
            out.println("            if (" + sanitizeVarName(tableName) + " == null) {");
            out.println("                " + sanitizeVarName(tableName) + " = new " + entityName + "();");

            List<SqlField> rootFields = fieldsByAlias.get(tableName);
            if (rootFields != null) {
                for (SqlField field : rootFields) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    String fmVar = rootVarPrefix + capitalize(fieldName);
                    out.println("                " + fmVar + ".apply(" + sanitizeVarName(tableName) + ", row.get(\"" + field.alias + "\"));");
                }
            }

            out.println("                " + sanitizeVarName(tableName) + "ById.put(" + sanitizeVarName(tableName) + "Id, " + sanitizeVarName(tableName) + ");");
            out.println("                result.add(" + sanitizeVarName(tableName) + ");");
            out.println("            }");
        }

        // Process related aliases
        for (Map.Entry<String, Alias> entry : aliases.entrySet()) {
            String aliasName = entry.getKey();
            Alias alias = entry.getValue();

            if (aliasName.equals(tableName) || alias.getIsEmbedded()) continue;
            if (alias.getParentAlias() == null || alias.getLinkField() == null) continue;
            if (alias.getIdFields() == null || alias.getIdFields().isEmpty()) continue;

            String className = alias.getResultType().getSimpleName();
            FieldModel aliasIdField = alias.getIdFields().get(0);

            String aliasVarPrefix = "fm" + capitalize(sanitizeVarName(aliasName));
            String entityVar = sanitizeVarName(aliasName);
            String byIdVar = sanitizeVarName(aliasName) + "ById";
            String parentVar = sanitizeVarName(alias.getParentAlias());
            String linkFieldName = alias.getLinkField().getName();
            String linkFieldVar = "f" + capitalize(sanitizeVarName(alias.getParentAlias())) + capitalize(linkFieldName);

            out.println();
            out.println("            // Process relationship: " + aliasName + " (" + className + ")");
            out.println("            Object " + entityVar + "Id = row.get(\"" + aliasName + "." + aliasIdField.getName() + "\");");
            out.println("            if (" + entityVar + "Id != null) {");
            out.println("                " + className + " " + entityVar + " = " + byIdVar + ".get(" + entityVar + "Id);");
            out.println("                if (" + entityVar + " == null) {");
            out.println("                    " + entityVar + " = new " + className + "();");

            List<SqlField> aliasFields = fieldsByAlias.get(aliasName);
            if (aliasFields != null) {
                for (SqlField field : aliasFields) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    String fmVar = aliasVarPrefix + capitalize(fieldName);
                    out.println("                    " + fmVar + ".apply(" + entityVar + ", row.get(\"" + field.alias + "\"));");
                }
            }

            out.println("                    " + byIdVar + ".put(" + entityVar + "Id, " + entityVar + ");");
            out.println("                }");
            out.println();
            out.println("                // Link to parent");
            // For nested relationships, we need to look up the parent from its ById map
            Alias parentAlias = aliases.get(alias.getParentAlias());
            if (alias.getParentAlias().equals(tableName)) {
                // Parent is the root entity, use the variable directly
                out.println("                FieldHelper.putValueIntoField(" + parentVar + ", " + linkFieldVar + ", " + entityVar + ");");
            } else if (parentAlias != null && parentAlias.getIdFields() != null && !parentAlias.getIdFields().isEmpty()) {
                // Parent is a nested entity, look it up from its ById map
                String parentIdField = parentAlias.getIdFields().get(0).getName();
                String parentByIdVar = sanitizeVarName(alias.getParentAlias()) + "ById";
                String parentIdVarName = entityVar + "_parentId";
                String parentVarName = entityVar + "_parent";
                out.println("                Object " + parentIdVarName + " = row.get(\"" + alias.getParentAlias() + "." + parentIdField + "\");");
                out.println("                " + parentAlias.getResultType().getSimpleName() + " " + parentVarName + " = " + parentByIdVar + ".get(" + parentIdVarName + ");");
                out.println("                if (" + parentVarName + " != null) {");
                out.println("                    FieldHelper.putValueIntoField(" + parentVarName + ", " + linkFieldVar + ", " + entityVar + ");");
                out.println("                }");
            }
            out.println("            }");
        }

        out.println("        }");
        out.println();
        out.println("        return result;");
        out.println("    }");
    }

    private void generateStaticConditionChainClass(PrintWriter out, String queryClassName,
            String chainClassName, String tableName, List<SqlField> mainFields, Alias mainAlias,
            Map<String, List<SqlField>> fieldsByAlias, LinkedHashMap<String, Alias> aliases, String indent) {

        out.println(indent + "/**");
        out.println(indent + " * Condition chain for building static conditions.");
        out.println(indent + " * Implements Supplier&lt;SqlExpression&gt; to be used in and()/or() methods.");
        out.println(indent + " */");
        out.println(indent + "public class " + chainClassName);
        out.println(indent + "        implements ConditionChain<" + chainClassName + ">, Supplier<SqlExpression> {");
        out.println();

        // Inner StaticConditionFields class with main entity fields
        out.println(indent + "    public class StaticConditionFields {");
        for (SqlField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            FieldInfo fieldInfo = getFieldInfo(mainAlias, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.println(indent + "        public final " + builderClass + "<" + fieldInfo.typeName + ", " + chainClassName + "> " + fieldName + " =");
            out.println(indent + "                new " + builderClass + "<>(() -> " + chainClassName + ".this, \"" + tableName + "\", \"" + fieldName + "\");");
        }
        
        // Add nested relationship field classes
        RelationNode root = buildRelationTree(tableName, fieldsByAlias.keySet());
        for (RelationNode child : root.children.values()) {
            out.println();
            generateNestedStaticConditionFields(out, chainClassName, child, fieldsByAlias, aliases, indent + "        ");
        }
        
        out.println(indent + "    }");
        out.println();

        out.println(indent + "    ConditionBuilder builder = new ConditionBuilderImpl();");
        out.println();

        out.println(indent + "    @Override");
        out.println(indent + "    public ConditionBuilder getBuilder() {");
        out.println(indent + "        return builder;");
        out.println(indent + "    }");
        out.println();

        out.println(indent + "    public StaticConditionFields and() {");
        out.println(indent + "        builder.add(sql(\" AND \"));");
        out.println(indent + "        return new StaticConditionFields();");
        out.println(indent + "    }");
        out.println();

        out.println(indent + "    public StaticConditionFields or() {");
        out.println(indent + "        builder.add(sql(\" OR \"));");
        out.println(indent + "        return new StaticConditionFields();");
        out.println(indent + "    }");
        out.println();

        out.println(indent + "    public " + chainClassName + " and(Supplier<SqlExpression> expr) {");
        out.println(indent + "        builder.add(sql(\" AND \")).startClause().add(expr.get()).endClause();");
        out.println(indent + "        return this;");
        out.println(indent + "    }");
        out.println();

        out.println(indent + "    public " + chainClassName + " or(Supplier<SqlExpression> expr) {");
        out.println(indent + "        builder.add(sql(\" OR \")).startClause().add(expr.get()).endClause();");
        out.println(indent + "        return this;");
        out.println(indent + "    }");
        out.println();

        out.println(indent + "    @Override");
        out.println(indent + "    public " + chainClassName + " getContinuation() {");
        out.println(indent + "        return this;");
        out.println(indent + "    }");
        out.println();

        out.println(indent + "    @Override");
        out.println(indent + "    public SqlExpression get() {");
        out.println(indent + "        return SqlExpression.implode(\"\", ((ConditionBuilderImpl) builder).getExpressions());");
        out.println(indent + "    }");
        out.println(indent + "}");
    }

    /**
     * Generates nested field classes for relationships within StaticConditionFields.
     */
    private void generateNestedStaticConditionFields(PrintWriter out, String chainClassName,
            RelationNode node, Map<String, List<SqlField>> fieldsByAlias, LinkedHashMap<String, Alias> aliases, String indent) {
        
        String relationName = node.name;
        String aliasName = node.fullAlias;
        Alias relAlias = aliases.get(aliasName);
        List<SqlField> relationFields = fieldsByAlias.getOrDefault(aliasName, List.of());
        
        String innerClassName = capitalize(relationName) + "ConditionFields";
        out.println(indent + "/** Condition fields for the {@code " + relationName + "} relationship */");
        out.println(indent + "public final " + innerClassName + " " + relationName + " = new " + innerClassName + "();");
        out.println();
        out.println(indent + "public class " + innerClassName + " {");
        
        // Generate condition fields for this relationship
        for (SqlField field : relationFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            FieldInfo fieldInfo = getFieldInfo(relAlias, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.println(indent + "    public final " + builderClass + "<" + fieldInfo.typeName + ", " + chainClassName + "> " + fieldName + " =");
            out.println(indent + "            new " + builderClass + "<>(() -> " + chainClassName + ".this, \"" + aliasName + "\", \"" + fieldName + "\");");
        }
        
        // Recursively generate for child relations
        for (RelationNode child : node.children.values()) {
            out.println();
            generateNestedStaticConditionFields(out, chainClassName, child, fieldsByAlias, aliases, indent + "    ");
        }
        
        out.println(indent + "}");
    }

    private void generateGroupByBuilderClass(PrintWriter out, String queryClassName,
            String groupByBuilderClass, String tableName, List<SqlField> mainFields, String indent) {

        out.println(indent + "public class " + groupByBuilderClass + " {");
        out.println();

        for (SqlField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            out.println(indent + "    public final " + queryClassName + "GroupByField " + fieldName + ";");
        }
        out.println();

        out.println(indent + "    public " + groupByBuilderClass + "() {");
        for (SqlField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            out.println(indent + "        this." + fieldName + " = new " + queryClassName + "GroupByField(\"" + tableName + "\", \"" + fieldName + "\");");
        }
        out.println(indent + "    }");

        out.println(indent + "}");
    }

    private void generateOrderByBuilderClass(PrintWriter out, String queryClassName,
            String orderByBuilderClass, String tableName, List<SqlField> mainFields,
            Map<String, List<SqlField>> fieldsByAlias, LinkedHashMap<String, Alias> aliases, String indent) {

        out.println(indent + "public class " + orderByBuilderClass + " implements OrderByTarget {");
        out.println();

        // Main entity fields
        for (SqlField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            out.println(indent + "    public final OrderByField<" + queryClassName + "> " + fieldName + ";");
        }
        out.println();

        // Nested relationship fields
        RelationNode root = buildRelationTree(tableName, fieldsByAlias.keySet());
        for (RelationNode child : root.children.values()) {
            generateNestedOrderByFields(out, queryClassName, child, fieldsByAlias, aliases, indent + "    ");
        }

        out.println(indent + "    public " + orderByBuilderClass + "() {");
        for (SqlField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            out.println(indent + "        this." + fieldName + " = new OrderByField<>(this, " + queryClassName + ".this, \"" + tableName + "\", \"" + fieldName + "\");");
        }
        out.println(indent + "    }");
        out.println();

        out.println(indent + "    @Override");
        out.println(indent + "    public void orderBy(String fieldExpression, boolean ascending) {");
        out.println(indent + "        query.addOrderBy(fieldExpression + (ascending ? \" ASC\" : \" DESC\"));");
        out.println(indent + "    }");

        out.println(indent + "}");
    }

    /**
     * Generates nested OrderBy field classes for relationships.
     */
    private void generateNestedOrderByFields(PrintWriter out, String queryClassName,
            RelationNode node, Map<String, List<SqlField>> fieldsByAlias, LinkedHashMap<String, Alias> aliases, String indent) {
        
        String relationName = node.name;
        String aliasName = node.fullAlias;
        List<SqlField> relationFields = fieldsByAlias.getOrDefault(aliasName, List.of());
        
        String innerClassName = capitalize(relationName) + "OrderByFields";
        out.println(indent + "/** OrderBy fields for the {@code " + relationName + "} relationship */");
        out.println(indent + "public final " + innerClassName + " " + relationName + " = new " + innerClassName + "();");
        out.println();
        out.println(indent + "public class " + innerClassName + " {");
        
        // Generate OrderBy fields for this relationship
        for (SqlField field : relationFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            out.println(indent + "    public final OrderByField<" + queryClassName + "> " + fieldName + " =");
            out.println(indent + "            new OrderByField<>(" + queryClassName + "OrderByBuilder.this, " + queryClassName + ".this, \"" + aliasName + "\", \"" + fieldName + "\");");
        }
        
        // Recursively generate for child relations
        for (RelationNode child : node.children.values()) {
            out.println();
            generateNestedOrderByFields(out, queryClassName, child, fieldsByAlias, aliases, indent + "    ");
        }
        
        out.println(indent + "}");
        out.println();
    }

    private void generateDelegateClass(PrintWriter out, String entityName, String queryClassName,
            String groupByBuilderClass, String orderByBuilderClass, String indent) {

        out.println(indent + "/**");
        out.println(indent + " * Delegate class for callback pattern - allows groupBy().field.list() syntax.");
        out.println(indent + " */");
        out.println(indent + "private class " + queryClassName + "Delegate {");
        out.println(indent + "    protected void callback() {}");
        out.println();
        out.println(indent + "    public List<" + entityName + "> list(Connection connection) {");
        out.println(indent + "        callback();");
        out.println(indent + "        return " + queryClassName + ".this.list(connection);");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    public " + groupByBuilderClass + " groupBy() {");
        out.println(indent + "        callback();");
        out.println(indent + "        return " + queryClassName + ".this.groupBy();");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    public " + orderByBuilderClass + " orderBy() {");
        out.println(indent + "        callback();");
        out.println(indent + "        return " + queryClassName + ".this.orderBy();");
        out.println(indent + "    }");
        out.println(indent + "}");
    }

    private void generateGroupByFieldClass(PrintWriter out, String queryClassName,
            String groupByBuilderClass, String orderByBuilderClass, String indent) {

        out.println(indent + "public class " + queryClassName + "GroupByField extends " + queryClassName + "Delegate {");
        out.println(indent + "    private String tableAlias;");
        out.println(indent + "    private String columnName;");
        out.println();
        out.println(indent + "    public " + queryClassName + "GroupByField(String tableAlias, String columnName) {");
        out.println(indent + "        this.tableAlias = tableAlias;");
        out.println(indent + "        this.columnName = columnName;");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    @Override");
        out.println(indent + "    protected void callback() {");
        out.println(indent + "        query.addGroupBy(\"{\" + tableAlias + \".\" + columnName + \"}\");");
        out.println(indent + "    }");
        out.println(indent + "}");
    }

    private void generateWhereBuilderClass(PrintWriter out, String queryClassName,
            String whereBuilderClass, String tableName, List<SqlField> mainFields, Alias mainAlias,
            Map<String, List<SqlField>> fieldsByAlias, LinkedHashMap<String, Alias> aliases, String indent) {

        String terminatorClass = whereBuilderClass + "ConditionTerminator";

        out.println(indent + "/**");
        out.println(indent + " * Where clause builder for fluent condition chaining.");
        out.println(indent + " */");
        out.println(indent + "public class " + whereBuilderClass + " implements ConditionChain<" + whereBuilderClass + "." + terminatorClass + "> {");
        out.println();

        // Fields for condition building on main entity
        for (SqlField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            FieldInfo fieldInfo = getFieldInfo(mainAlias, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.println(indent + "    public final " + builderClass + "<" + fieldInfo.typeName + ", " + terminatorClass + "> " + fieldName + " =");
            out.println(indent + "            new " + builderClass + "<>(() -> this, \"" + tableName + "\", \"" + fieldName + "\");");
        }
        out.println();

        // Nested relationship fields
        RelationNode root = buildRelationTree(tableName, fieldsByAlias.keySet());
        for (RelationNode child : root.children.values()) {
            generateNestedWhereFields(out, queryClassName, whereBuilderClass, terminatorClass, child, fieldsByAlias, aliases, indent + "    ");
        }

        out.println(indent + "    List<SqlExpression> collectedConditions = new java.util.ArrayList<>();");
        out.println(indent + "    ConditionBuilder builder = new WhereBuilderImpl();");
        out.println();

        out.println(indent + "    protected " + whereBuilderClass + "(" + queryClassName + " query) {");
        out.println(indent + "    }");
        out.println();

        out.println(indent + "    public void accept(SqlExpression expr) {");
        out.println(indent + "        collectedConditions.add(expr);");
        out.println(indent + "    }");
        out.println();

        // Inner BuilderImpl class
        out.println(indent + "    public class WhereBuilderImpl implements ConditionBuilder {");
        out.println(indent + "        public ConditionBuilder startClause() {");
        out.println(indent + "            collectedConditions.add(sql(\" (\"));");
        out.println(indent + "            return this;");
        out.println(indent + "        }");
        out.println();
        out.println(indent + "        public ConditionBuilder endClause() {");
        out.println(indent + "            collectedConditions.add(sql(\") \"));");
        out.println(indent + "            return this;");
        out.println(indent + "        }");
        out.println();
        out.println(indent + "        @Override");
        out.println(indent + "        public ConditionBuilder add(SqlExpression expr) {");
        out.println(indent + "            collectedConditions.add(expr);");
        out.println(indent + "            return this;");
        out.println(indent + "        }");
        out.println(indent + "    }");
        out.println();

        // ConditionTerminator inner class
        out.println(indent + "    public class " + terminatorClass + " extends " + queryClassName + "Delegate {");
        out.println();
        out.println(indent + "        @Override");
        out.println(indent + "        protected void callback() {");
        out.println(indent + "            SqlExpression whereExpr = SqlExpression.implode(\"\", collectedConditions);");
        out.println(indent + "            collectedConditions.clear();");
        out.println(indent + "            " + queryClassName + ".this.query.addWhere(whereExpr);");
        out.println(indent + "        }");
        out.println();
        out.println(indent + "        public " + whereBuilderClass + " and() {");
        out.println(indent + "            builder.add(sql(\" AND \"));");
        out.println(indent + "            return " + whereBuilderClass + ".this;");
        out.println(indent + "        }");
        out.println();
        out.println(indent + "        public " + whereBuilderClass + " or() {");
        out.println(indent + "            builder.add(sql(\" OR \"));");
        out.println(indent + "            return " + whereBuilderClass + ".this;");
        out.println(indent + "        }");
        out.println();
        out.println(indent + "        public " + terminatorClass + " and(Supplier<SqlExpression> expr) {");
        out.println(indent + "            builder.add(sql(\" AND \")).startClause().add(expr.get()).endClause();");
        out.println(indent + "            return this;");
        out.println(indent + "        }");
        out.println();
        out.println(indent + "        public " + terminatorClass + " or(Supplier<SqlExpression> expr) {");
        out.println(indent + "            builder.add(sql(\" OR \")).startClause().add(expr.get()).endClause();");
        out.println(indent + "            return this;");
        out.println(indent + "        }");
        out.println(indent + "    }");
        out.println();

        out.println(indent + "    @Override");
        out.println(indent + "    public " + terminatorClass + " getContinuation() {");
        out.println(indent + "        return new " + terminatorClass + "();");
        out.println(indent + "    }");
        out.println();

        out.println(indent + "    @Override");
        out.println(indent + "    public ConditionBuilder getBuilder() {");
        out.println(indent + "        return builder;");
        out.println(indent + "    }");

        out.println(indent + "}");
    }

    /**
     * Generates nested Where field classes for relationships.
     */
    private void generateNestedWhereFields(PrintWriter out, String queryClassName, String whereBuilderClass,
            String terminatorClass, RelationNode node, Map<String, List<SqlField>> fieldsByAlias,
            LinkedHashMap<String, Alias> aliases, String indent) {
        
        String relationName = node.name;
        String aliasName = node.fullAlias;
        Alias relAlias = aliases.get(aliasName);
        List<SqlField> relationFields = fieldsByAlias.getOrDefault(aliasName, List.of());
        
        // Find the ID field for the relationship (for isNull/isNotNull checks)
        String idFieldName = null;
        if (relAlias != null && relAlias.getIdFields() != null && !relAlias.getIdFields().isEmpty()) {
            idFieldName = relAlias.getIdFields().get(0).getName();
        }
        
        String innerClassName = capitalize(relationName) + "WhereFields";
        out.println(indent + "/** Where fields for the {@code " + relationName + "} relationship */");
        out.println(indent + "public final " + innerClassName + " " + relationName + " = new " + innerClassName + "();");
        out.println();
        out.println(indent + "public class " + innerClassName + " {");
        
        // Generate condition fields for this relationship
        for (SqlField field : relationFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            FieldInfo fieldInfo = getFieldInfo(relAlias, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.println(indent + "    public final " + builderClass + "<" + fieldInfo.typeName + ", " + terminatorClass + "> " + fieldName + " =");
            out.println(indent + "            new " + builderClass + "<>(() -> " + whereBuilderClass + ".this, \"" + aliasName + "\", \"" + fieldName + "\");");
        }
        out.println();
        
        // Add isNull()/isNotNull() methods to check if the relationship is null
        if (idFieldName != null) {
            out.println(indent + "    /** Check if this relationship is null (by checking the ID field) */");
            out.println(indent + "    public " + terminatorClass + " isNull() {");
            out.println(indent + "        return " + idFieldName + ".isNull();");
            out.println(indent + "    }");
            out.println();
            out.println(indent + "    /** Check if this relationship is not null (by checking the ID field) */");
            out.println(indent + "    public " + terminatorClass + " isNotNull() {");
            out.println(indent + "        return " + idFieldName + ".isNotNull();");
            out.println(indent + "    }");
        }
        
        // Recursively generate for child relations
        for (RelationNode child : node.children.values()) {
            out.println();
            generateNestedWhereFields(out, queryClassName, whereBuilderClass, terminatorClass, child, fieldsByAlias, aliases, indent + "    ");
        }
        
        out.println(indent + "}");
        out.println();
    }

    // === Helper methods ===

    private String getPackageName(TypeElement typeElement) {
        Element enclosing = typeElement.getEnclosingElement();
        while (enclosing != null && !(enclosing instanceof PackageElement)) {
            enclosing = enclosing.getEnclosingElement();
        }
        if (enclosing instanceof PackageElement) {
            String packageName = ((PackageElement) enclosing).getQualifiedName().toString();
            return packageName.isEmpty() ? "" : packageName;
        }
        return "";
    }

    private String getEnclosingClassPrefix(TypeElement typeElement) {
        StringBuilder prefix = new StringBuilder();
        Element enclosing = typeElement.getEnclosingElement();
        while (enclosing != null && enclosing.getKind() == ElementKind.CLASS) {
            prefix.insert(0, enclosing.getSimpleName().toString() + "_");
            enclosing = enclosing.getEnclosingElement();
        }
        return prefix.toString();
    }

    private String extractAliasFromFieldAlias(String fieldAlias) {
        int lastDot = fieldAlias.lastIndexOf('.');
        return lastDot > 0 ? fieldAlias.substring(0, lastDot) : fieldAlias;
    }

    private String extractFieldNameFromAlias(String fieldAlias) {
        int lastDot = fieldAlias.lastIndexOf('.');
        return lastDot >= 0 ? fieldAlias.substring(lastDot + 1) : fieldAlias;
    }

    private String escapeJava(String s) {
        if (s == null) return null;
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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

    private static class FieldInfo {
        final String typeName;
        final boolean isComparable;

        FieldInfo(String typeName, boolean isComparable) {
            this.typeName = typeName;
            this.isComparable = isComparable;
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

    private boolean isComparableType(TypeModel type) {
        if (type == null) return false;
        String name = type.getQualifiedName();
        return COMPARABLE_TYPES.contains(name) || type.isEnum();
    }

    private FieldInfo getFieldInfo(Alias alias, String fieldName) {
        if (alias == null) return new FieldInfo("Object", false);
        TypeModel type = alias.getResultType();
        while (type != null) {
            for (FieldModel field : type.getDeclaredFields()) {
                if (field.getName().equals(fieldName)) {
                    TypeModel fieldType = field.getType();
                    return new FieldInfo(getBoxedType(fieldType), isComparableType(fieldType));
                }
            }
            type = type.getSuperclass();
        }
        return new FieldInfo("Object", false);
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String sanitizeVarName(String alias) {
        return alias.replace(".", "_").replace("-", "_");
    }

    /**
     * Represents a node in the relationship tree structure for generating nested classes.
     * Each node represents an alias and can have child nodes for nested relationships.
     */
    private static class RelationNode {
        final String name;              // Simple name of this relation (e.g., "author")
        final String fullAlias;         // Full alias path (e.g., "author" or "comments.author")
        final Map<String, RelationNode> children = new LinkedHashMap<>();

        RelationNode(String name, String fullAlias) {
            this.name = name;
            this.fullAlias = fullAlias;
        }
    }

    /**
     * Builds a tree structure from relation aliases.
     * For example, given aliases ["author", "comments", "comments.author"], builds:
     * root
     *   ├─ author
     *   └─ comments
     *        └─ author
     */
    private RelationNode buildRelationTree(String tableName, java.util.Set<String> aliases) {
        RelationNode root = new RelationNode(tableName, tableName);
        
        for (String alias : aliases) {
            if (alias.equals(tableName)) continue;
            
            // Parse the alias path (e.g., "comments.author" -> ["comments", "author"])
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
}
