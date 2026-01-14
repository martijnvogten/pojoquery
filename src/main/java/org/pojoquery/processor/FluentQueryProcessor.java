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

import org.pojoquery.annotations.GenerateFluentQuery;
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
 * Annotation processor that generates fluent query builders following the pattern
 * from FluentExperiments.
 *
 * <p>For each class annotated with {@code @GenerateFluentQuery}, this processor generates:
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
@SupportedAnnotationTypes("org.pojoquery.annotations.GenerateFluentQuery")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class FluentQueryProcessor extends AbstractProcessor {

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
        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateFluentQuery.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "@GenerateFluentQuery can only be applied to classes", element);
                continue;
            }

            TypeElement typeElement = (TypeElement) element;
            try {
                processEntity(typeElement);
            } catch (Exception e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate fluent query classes: " + e.getMessage() +
                    " (" + e.getClass().getSimpleName() + ")", element);
            }
        }
        return true;
    }

    private void processEntity(TypeElement typeElement) throws Exception {
        GenerateFluentQuery annotation = typeElement.getAnnotation(GenerateFluentQuery.class);
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
                "@GenerateFluentQuery requires @Table annotation on entity or its superclasses", typeElement);
            return;
        }
        String tableName = tableMapping.get(0).tableName;
        String tableAlias = abbreviate(tableName);

        CustomizableQueryBuilder<?, ?> builder = QueryBuilder.from(entityType);

        // Extract query structure
        var query = builder.getQuery();
        List<SqlField> fields = query.getFields();
        List<SqlJoin> joins = query.getJoins();
        LinkedHashMap<String, Alias> aliases = builder.getAliases();

        messager.printMessage(Diagnostic.Kind.NOTE,
            "Generating fluent query classes for " + qualifiedName +
            " (" + fields.size() + " fields, " + joins.size() + " joins, " +
            aliases.size() + " aliases)", typeElement);

        // Generate the query class
        generateFluentQueryClass(packageName, entityName, queryClassName, tableName, tableAlias,
            fields, joins, aliases);
    }

    // === Code generation ===

    private void generateFluentQueryClass(String packageName, String entityName,
            String queryClassName, String tableName, String tableAlias,
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

            out.println("import java.sql.Connection;");
            out.println("import java.util.ArrayList;");
            out.println("import java.util.List;");
            out.println("import java.util.function.Supplier;");
            out.println();
            out.println("import org.pojoquery.DbContext;");
            out.println("import org.pojoquery.SqlExpression;");
            out.println("import org.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;");
            out.println("import org.pojoquery.pipeline.SqlQuery;");
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
            out.println("    // Static condition builder fields");
            for (SqlField field : mainFields) {
                String fieldName = extractFieldNameFromAlias(field.alias);
                FieldInfo fieldInfo = getFieldInfo(mainAlias, fieldName);
                String builderClass = fieldInfo.isComparable
                    ? "ComparableConditionBuilderField"
                    : "ConditionBuilderField";
                out.println("    public final " + builderClass + "<" + fieldInfo.typeName + ", " + chainClassName + "> " + fieldName + " =");
                out.println("            new " + builderClass + "<>(() -> new " + chainClassName + "(), \"" + tableAlias + "\", \"" + fieldName + "\");");
            }
            out.println();

            // Generate the StaticConditionChain inner class
            generateStaticConditionChainClass(out, queryClassName, chainClassName, tableAlias, mainFields, mainAlias, "    ");
            out.println();

            // Generate the SqlQuery field and initialization
            out.println("    protected SqlQuery<?> query = new DefaultSqlQuery(DbContext.getDefault());");
            out.println();
            out.println("    protected void initializeQuery() {");
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

            out.println("    public List<" + entityName + "> list(Connection connection) {");
            out.println("        // TODO: Implement result mapping");
            out.println("        return null;");
            out.println("    }");
            out.println();

            // Generate GroupByBuilder inner class
            generateGroupByBuilderClass(out, queryClassName, groupByBuilderClass, tableAlias, mainFields, "    ");
            out.println();

            // Generate OrderByBuilder inner class
            generateOrderByBuilderClass(out, queryClassName, orderByBuilderClass, tableAlias, mainFields, "    ");
            out.println();

            // Generate delegate class for callback pattern
            generateDelegateClass(out, entityName, queryClassName, groupByBuilderClass, orderByBuilderClass, "    ");
            out.println();

            // Generate GroupByField inner class
            generateGroupByFieldClass(out, queryClassName, groupByBuilderClass, orderByBuilderClass, "    ");
            out.println();

            // Generate WhereBuilder inner class
            generateWhereBuilderClass(out, queryClassName, whereBuilderClass, tableAlias, mainFields, mainAlias, "    ");
            out.println();

            // Generate utility inner classes
            generateConditionBuilderInterfaces(out, chainClassName, "    ");

            out.println("}");
        }
    }

    private void generateStaticConditionChainClass(PrintWriter out, String queryClassName,
            String chainClassName, String tableAlias, List<SqlField> mainFields, Alias mainAlias, String indent) {

        out.println(indent + "/**");
        out.println(indent + " * Condition chain for building static conditions.");
        out.println(indent + " * Implements Supplier&lt;SqlExpression&gt; to be used in and()/or() methods.");
        out.println(indent + " */");
        out.println(indent + "public class " + chainClassName);
        out.println(indent + "        implements ConditionChain<" + chainClassName + ">, Supplier<SqlExpression> {");
        out.println();

        // Inner StaticConditionFields class
        out.println(indent + "    public class StaticConditionFields {");
        for (SqlField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            FieldInfo fieldInfo = getFieldInfo(mainAlias, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.println(indent + "        public final " + builderClass + "<" + fieldInfo.typeName + ", " + chainClassName + "> " + fieldName + " =");
            out.println(indent + "                new " + builderClass + "<>(() -> " + chainClassName + ".this, \"" + tableAlias + "\", \"" + fieldName + "\");");
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
        out.println(indent + "        return SqlExpression.implode(\"\", ((ConditionBuilderImpl) builder).expressions);");
        out.println(indent + "    }");
        out.println(indent + "}");
    }

    private void generateGroupByBuilderClass(PrintWriter out, String queryClassName,
            String groupByBuilderClass, String tableAlias, List<SqlField> mainFields, String indent) {

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
            out.println(indent + "        this." + fieldName + " = new " + queryClassName + "GroupByField(\"" + tableAlias + "\", \"" + fieldName + "\");");
        }
        out.println(indent + "    }");

        out.println(indent + "}");
    }

    private void generateOrderByBuilderClass(PrintWriter out, String queryClassName,
            String orderByBuilderClass, String tableAlias, List<SqlField> mainFields, String indent) {

        out.println(indent + "public class " + orderByBuilderClass + " implements OrderByTarget {");
        out.println();

        for (SqlField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            out.println(indent + "    public final OrderByField<" + queryClassName + "> " + fieldName + ";");
        }
        out.println();

        out.println(indent + "    public " + orderByBuilderClass + "() {");
        for (SqlField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            out.println(indent + "        this." + fieldName + " = new OrderByField<>(this, " + queryClassName + ".this, \"" + tableAlias + "\", \"" + fieldName + "\");");
        }
        out.println(indent + "    }");
        out.println();

        out.println(indent + "    @Override");
        out.println(indent + "    public void orderBy(String fieldExpression, boolean ascending) {");
        out.println(indent + "        query.addOrderBy(fieldExpression + (ascending ? \" ASC\" : \" DESC\"));");
        out.println(indent + "    }");

        out.println(indent + "}");
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
            String whereBuilderClass, String tableAlias, List<SqlField> mainFields, Alias mainAlias, String indent) {

        String terminatorClass = whereBuilderClass + "ConditionTerminator";

        out.println(indent + "/**");
        out.println(indent + " * Where clause builder for fluent condition chaining.");
        out.println(indent + " */");
        out.println(indent + "public class " + whereBuilderClass + " implements ConditionChain<" + whereBuilderClass + "." + terminatorClass + "> {");
        out.println();

        // Fields for condition building
        for (SqlField field : mainFields) {
            String fieldName = extractFieldNameFromAlias(field.alias);
            FieldInfo fieldInfo = getFieldInfo(mainAlias, fieldName);
            String builderClass = fieldInfo.isComparable
                ? "ComparableConditionBuilderField"
                : "ConditionBuilderField";
            out.println(indent + "    public final " + builderClass + "<" + fieldInfo.typeName + ", " + terminatorClass + "> " + fieldName + " =");
            out.println(indent + "            new " + builderClass + "<>(() -> this, \"" + tableAlias + "\", \"" + fieldName + "\");");
        }
        out.println();

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

    private void generateConditionBuilderInterfaces(PrintWriter out, String chainClassName, String indent) {
        // ConditionChain interface
        out.println(indent + "interface ConditionChain<C> {");
        out.println(indent + "    C getContinuation();");
        out.println(indent + "    ConditionBuilder getBuilder();");
        out.println(indent + "}");
        out.println();

        // ChainFactory interface
        out.println(indent + "interface ChainFactory<C> {");
        out.println(indent + "    ConditionChain<C> createChain();");
        out.println(indent + "}");
        out.println();

        // ConditionBuilder interface
        out.println(indent + "interface ConditionBuilder {");
        out.println(indent + "    ConditionBuilder startClause();");
        out.println(indent + "    ConditionBuilder endClause();");
        out.println(indent + "    ConditionBuilder add(SqlExpression expr);");
        out.println(indent + "}");
        out.println();

        // ConditionBuilderImpl
        out.println(indent + "public static class ConditionBuilderImpl implements ConditionBuilder {");
        out.println(indent + "    List<SqlExpression> expressions = new java.util.ArrayList<>();");
        out.println();
        out.println(indent + "    @Override");
        out.println(indent + "    public ConditionBuilder startClause() {");
        out.println(indent + "        expressions.add(sql(\"(\"));");
        out.println(indent + "        return this;");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    @Override");
        out.println(indent + "    public ConditionBuilder endClause() {");
        out.println(indent + "        expressions.add(sql(\")\"));");
        out.println(indent + "        return this;");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    @Override");
        out.println(indent + "    public ConditionBuilder add(SqlExpression expr) {");
        out.println(indent + "        expressions.add(expr);");
        out.println(indent + "        return this;");
        out.println(indent + "    }");
        out.println(indent + "}");
        out.println();

        // OrderByTarget interface
        out.println(indent + "interface OrderByTarget {");
        out.println(indent + "    void orderBy(String fieldExpression, boolean ascending);");
        out.println(indent + "}");
        out.println();

        // OrderByField class
        out.println(indent + "public static class OrderByField<Q> {");
        out.println(indent + "    private final String tableAlias;");
        out.println(indent + "    private final String columnName;");
        out.println(indent + "    private final OrderByTarget target;");
        out.println(indent + "    private final Q query;");
        out.println();
        out.println(indent + "    public OrderByField(OrderByTarget target, Q query, String tableAlias, String columnName) {");
        out.println(indent + "        this.target = target;");
        out.println(indent + "        this.query = query;");
        out.println(indent + "        this.tableAlias = tableAlias;");
        out.println(indent + "        this.columnName = columnName;");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    public Q asc() {");
        out.println(indent + "        target.orderBy(\"{\" + tableAlias + \".\" + columnName + \"}\", true);");
        out.println(indent + "        return query;");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    public Q desc() {");
        out.println(indent + "        target.orderBy(\"{\" + tableAlias + \".\" + columnName + \"}\", false);");
        out.println(indent + "        return query;");
        out.println(indent + "    }");
        out.println(indent + "}");
        out.println();

        // ConditionBuilderField class
        out.println(indent + "static class ConditionBuilderField<T, C> {");
        out.println(indent + "    protected final ChainFactory<C> chainFactory;");
        out.println(indent + "    protected final String tableAlias;");
        out.println(indent + "    protected final String columnName;");
        out.println();
        out.println(indent + "    public ConditionBuilderField(ChainFactory<C> chainFactory, String tableAlias, String columnName) {");
        out.println(indent + "        this.chainFactory = chainFactory;");
        out.println(indent + "        this.tableAlias = tableAlias;");
        out.println(indent + "        this.columnName = columnName;");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    public C eq(T other) {");
        out.println(indent + "        var op = chainFactory.createChain();");
        out.println(indent + "        op.getBuilder().add(sql(\"{\" + tableAlias + \".\" + columnName + \"} = ?\", other));");
        out.println(indent + "        return op.getContinuation();");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    public C ne(T other) {");
        out.println(indent + "        var op = chainFactory.createChain();");
        out.println(indent + "        op.getBuilder().add(sql(\"{\" + tableAlias + \".\" + columnName + \"} <> ?\", other));");
        out.println(indent + "        return op.getContinuation();");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    public C isNull() {");
        out.println(indent + "        var op = chainFactory.createChain();");
        out.println(indent + "        op.getBuilder().add(sql(\"{\" + tableAlias + \".\" + columnName + \"} IS NULL\"));");
        out.println(indent + "        return op.getContinuation();");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    public C isNotNull() {");
        out.println(indent + "        var op = chainFactory.createChain();");
        out.println(indent + "        op.getBuilder().add(sql(\"{\" + tableAlias + \".\" + columnName + \"} IS NOT NULL\"));");
        out.println(indent + "        return op.getContinuation();");
        out.println(indent + "    }");
        out.println(indent + "}");
        out.println();

        // ComparableConditionBuilderField class
        out.println(indent + "static class ComparableConditionBuilderField<T, C> extends ConditionBuilderField<T, C> {");
        out.println(indent + "    public ComparableConditionBuilderField(ChainFactory<C> chainFactory, String tableAlias, String columnName) {");
        out.println(indent + "        super(chainFactory, tableAlias, columnName);");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    public C gt(T other) {");
        out.println(indent + "        var op = chainFactory.createChain();");
        out.println(indent + "        op.getBuilder().add(sql(\"{\" + tableAlias + \".\" + columnName + \"} > ?\", other));");
        out.println(indent + "        return op.getContinuation();");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    public C lt(T other) {");
        out.println(indent + "        var op = chainFactory.createChain();");
        out.println(indent + "        op.getBuilder().add(sql(\"{\" + tableAlias + \".\" + columnName + \"} < ?\", other));");
        out.println(indent + "        return op.getContinuation();");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    public C ge(T other) {");
        out.println(indent + "        var op = chainFactory.createChain();");
        out.println(indent + "        op.getBuilder().add(sql(\"{\" + tableAlias + \".\" + columnName + \"} >= ?\", other));");
        out.println(indent + "        return op.getContinuation();");
        out.println(indent + "    }");
        out.println();
        out.println(indent + "    public C le(T other) {");
        out.println(indent + "        var op = chainFactory.createChain();");
        out.println(indent + "        op.getBuilder().add(sql(\"{\" + tableAlias + \".\" + columnName + \"} <= ?\", other));");
        out.println(indent + "        return op.getContinuation();");
        out.println(indent + "    }");
        out.println(indent + "}");
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

    /**
     * Creates a short alias from a table name (e.g., "book" -> "b", "article" -> "a").
     */
    private String abbreviate(String tableName) {
        if (tableName == null || tableName.isEmpty()) return "t";
        return tableName.substring(0, 1).toLowerCase();
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
}
