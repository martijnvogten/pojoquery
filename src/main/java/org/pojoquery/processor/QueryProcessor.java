package org.pojoquery.processor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
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
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.Alias;
import org.pojoquery.pipeline.CustomizableQueryBuilder;
import org.pojoquery.pipeline.QueryBuilder;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.pipeline.SqlQuery.SqlField;
import org.pojoquery.pipeline.SqlQuery.SqlJoin;

/**
 * Annotation processor that generates type-safe query builders for PojoQuery entities.
 *
 * <p>Uses {@link CustomizableQueryBuilder} at compile time as the single source of truth
 * for query structure and entity metadata. The entity class must be compiled before
 * this processor runs (e.g., entities in main module, processor runs during test-compile).
 *
 * <p>For each class annotated with {@code @GenerateQuery}, this processor generates:
 * <ul>
 *   <li>{@code EntityName_} - Static field references for type-safe queries</li>
 *   <li>{@code EntityNameQuery} - Fluent query builder with compile-time generated result mapping</li>
 * </ul>
 */
@SupportedAnnotationTypes("org.pojoquery.annotations.GenerateQuery")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class QueryProcessor extends AbstractProcessor {

    private Filer filer;
    private Messager messager;
    private ClassLoader entityClassLoader;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
        entityClassLoader = createEntityClassLoader();
    }

    /**
     * Creates a classloader that can load entity classes from the compilation output directory.
     * This allows the annotation processor to load classes that were compiled in a previous phase.
     */
    private ClassLoader createEntityClassLoader() {
        try {
            // Create a temporary resource to determine the output directory
            FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "temp_marker_" + System.nanoTime());
            String resourcePath = resource.toUri().getPath();
            // Delete the temp file
            new File(resourcePath).delete();
            // Get the parent directory (class output)
            File classOutputDir = new File(resourcePath).getParentFile();

            // Also try to find the test-classes directory (entities may be compiled there)
            // Walk up to find target directory and look for test-classes
            List<URL> urls = new ArrayList<>();

            if (classOutputDir != null && classOutputDir.exists()) {
                urls.add(classOutputDir.toURI().toURL());

                // If we're in annotation-processor-output or similar, also add test-classes
                File targetDir = classOutputDir.getParentFile();
                if (targetDir != null && targetDir.getName().equals("target")) {
                    File testClassesDir = new File(targetDir, "test-classes");
                    if (testClassesDir.exists()) {
                        urls.add(testClassesDir.toURI().toURL());
                    }
                }
            }

            if (!urls.isEmpty()) {
                messager.printMessage(Diagnostic.Kind.NOTE,
                    "Using class output directories: " + urls);
                return new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
            }
        } catch (Exception e) {
            // Fall back to default classloader
            messager.printMessage(Diagnostic.Kind.NOTE,
                "Could not determine class output directory: " + e.getMessage());
        }
        return getClass().getClassLoader();
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
            } catch (ClassNotFoundException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Entity class not yet compiled. Ensure entities are compiled before " +
                    "annotation processing (e.g., put entities in main module, run processor during test-compile)",
                    element);
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
        String fieldsSuffix = annotation.fieldsSuffix();

        String packageName = getPackageName(typeElement);
        String entityName = typeElement.getSimpleName().toString();
        String qualifiedName = typeElement.getQualifiedName().toString();
        String fieldsClassName = entityName + fieldsSuffix;
        String queryClassName = entityName + querySuffix;

        Table tableAnnotation = typeElement.getAnnotation(Table.class);
        if (tableAnnotation == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@GenerateQuery requires @Table annotation", typeElement);
            return;
        }
        String tableName = tableAnnotation.value();

        // Load the entity class using our custom classloader that has access to the compilation output
        // This works because Maven runs test compilation in phases: entities are compiled before annotation processing
        Class<?> entityClass = Class.forName(qualifiedName, true, entityClassLoader);
        CustomizableQueryBuilder<?, ?> builder = QueryBuilder.from(entityClass);

        // Extract query structure
        SqlQuery<?> query = builder.getQuery();
        List<SqlField> fields = query.getFields();
        List<SqlJoin> joins = query.getJoins();
        LinkedHashMap<String, Alias> aliases = builder.getAliases();

        messager.printMessage(Diagnostic.Kind.NOTE,
            "Generating query classes for " + qualifiedName +
            " (" + fields.size() + " fields, " + joins.size() + " joins, " +
            aliases.size() + " aliases)", typeElement);

        // Generate the classes
        generateFieldsClass(packageName, entityName, fieldsClassName, tableName, fields, aliases);
        generateQueryClass(packageName, entityName, queryClassName, fieldsClassName, tableName,
            fields, joins, aliases);
    }

    // === Code generation ===

    private void generateFieldsClass(String packageName, String entityName,
            String className, String tableName, List<SqlField> fields,
            LinkedHashMap<String, Alias> aliases) throws IOException {

        String qualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
        JavaFileObject fileObject = filer.createSourceFile(qualifiedName);

        try (PrintWriter out = new PrintWriter(fileObject.openWriter())) {
            if (!packageName.isEmpty()) {
                out.println("package " + packageName + ";");
                out.println();
            }

            out.println("import org.pojoquery.typedquery.QueryField;");
            out.println();
            out.println("/**");
            out.println(" * Generated field references for {@link " + entityName + "}.");
            out.println(" */");
            out.println("public final class " + className + " {");
            out.println();
            out.println("    public static final String TABLE = \"" + tableName + "\";");
            out.println();

            // Group fields by alias
            Map<String, List<SqlField>> fieldsByAlias = new LinkedHashMap<>();
            for (SqlField field : fields) {
                String alias = extractAliasFromFieldAlias(field.alias);
                fieldsByAlias.computeIfAbsent(alias, k -> new ArrayList<>()).add(field);
            }

            // Main entity fields
            List<SqlField> mainFields = fieldsByAlias.getOrDefault(tableName, List.of());
            for (SqlField field : mainFields) {
                String fieldName = extractFieldNameFromAlias(field.alias);
                out.println("    public static final QueryField<" + entityName + ", Object> " +
                    fieldName + " = new QueryField<>(TABLE, \"" +
                    fieldName + "\", \"" + fieldName + "\", Object.class);");
            }
            out.println();

            // Nested classes for relationships
            for (Map.Entry<String, List<SqlField>> entry : fieldsByAlias.entrySet()) {
                String alias = entry.getKey();
                if (alias.equals(tableName)) continue;

                String innerClassName = capitalize(extractFieldNameFromAlias(alias));
                out.println("    /** Fields for the {@code " + extractFieldNameFromAlias(alias) + "} relationship */");
                out.println("    public static final class " + innerClassName + " {");
                out.println("        public static final String ALIAS = \"" + alias + "\";");
                out.println();

                for (SqlField field : entry.getValue()) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    String relFieldPath = field.alias.startsWith(tableName + ".")
                        ? field.alias.substring(tableName.length() + 1)
                        : field.alias;
                    out.println("        public static final QueryField<" + entityName + ", Object> " +
                        fieldName + " = new QueryField<>(ALIAS, \"" +
                        fieldName + "\", \"" + relFieldPath + "\", Object.class);");
                }

                out.println("        private " + innerClassName + "() {}");
                out.println("    }");
                out.println();
            }

            out.println("    private " + className + "() {}");
            out.println("}");
        }
    }

    private void generateQueryClass(String packageName, String entityName,
            String className, String fieldsClassName, String tableName,
            List<SqlField> fields, List<SqlJoin> joins,
            LinkedHashMap<String, Alias> aliases) throws IOException {

        String qualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
        JavaFileObject fileObject = filer.createSourceFile(qualifiedName);

        // Find ID field for the root entity
        Alias rootAlias = aliases.get(tableName);
        Field idField = rootAlias != null && rootAlias.getIdFields() != null && !rootAlias.getIdFields().isEmpty()
            ? rootAlias.getIdFields().get(0) : null;

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
            out.println();
            out.println("import javax.sql.DataSource;");
            out.println();
            out.println("import org.pojoquery.DB;");
            out.println("import org.pojoquery.DbContext;");
            out.println("import org.pojoquery.FieldMapping;");
            out.println("import org.pojoquery.SqlExpression;");
            out.println("import org.pojoquery.typedquery.TypedQuery;");
            out.println();
            out.println("/**");
            out.println(" * Generated type-safe query builder for {@link " + entityName + "}.");
            out.println(" */");
            out.println("public class " + className + " extends TypedQuery<" + entityName + ", " + className + "> {");
            out.println();

            // Constructors
            out.println("    public " + className + "() {");
            out.println("        super(\"" + tableName + "\");");
            out.println("    }");
            out.println();
            out.println("    public " + className + "(DbContext dbContext) {");
            out.println("        super(dbContext, null, \"" + tableName + "\");");
            out.println("    }");
            out.println();

            // initializeQuery - replay exact joins and fields from CustomizableQueryBuilder
            generateInitializeQuery(out, fields, joins);

            // getEntityClass
            out.println("    @Override");
            out.println("    protected Class<" + entityName + "> getEntityClass() {");
            out.println("        return " + entityName + ".class;");
            out.println("    }");
            out.println();

            // list
            out.println("    @Override");
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

            // processRows - use Alias metadata from CustomizableQueryBuilder
            generateProcessRows(out, entityName, tableName, fields, aliases);

            // findById
            if (idField != null) {
                String boxedType = getBoxedType(idField.getType());
                out.println("    public " + entityName + " findById(DataSource dataSource, " + boxedType + " id) {");
                out.println("        return where(" + fieldsClassName + "." + idField.getName() + ").is(id).first(dataSource);");
                out.println("    }");
                out.println();
                out.println("    public " + entityName + " findById(Connection connection, " + boxedType + " id) {");
                out.println("        return where(" + fieldsClassName + "." + idField.getName() + ").is(id).first(connection);");
                out.println("    }");
            }

            out.println("}");
        }
    }

    private void generateInitializeQuery(PrintWriter out, List<SqlField> fields, List<SqlJoin> joins) {
        out.println("    @Override");
        out.println("    protected void initializeQuery() {");

        // Replay exact joins from CustomizableQueryBuilder
        for (SqlJoin join : joins) {
            String schemaArg = join.schema == null ? "null" : "\"" + escapeJava(join.schema) + "\"";
            String conditionArg = join.joinCondition == null ? "null" :
                "SqlExpression.sql(\"" + escapeJava(join.joinCondition.getSql()) + "\")";
            out.println("        query.addJoin(org.pojoquery.pipeline.SqlQuery.JoinType." + join.joinType.name() +
                ", " + schemaArg + ", \"" + escapeJava(join.table) +
                "\", \"" + escapeJava(join.alias) + "\", " + conditionArg + ");");
        }

        // Replay exact fields from CustomizableQueryBuilder
        for (SqlField field : fields) {
            out.println("        query.addField(SqlExpression.sql(\"" + escapeJava(field.expression.getSql()) +
                "\"), \"" + escapeJava(field.alias) + "\");");
        }

        out.println("    }");
        out.println();
    }

    private void generateProcessRows(PrintWriter out, String entityName, String tableName,
            List<SqlField> fields, LinkedHashMap<String, Alias> aliases) {

        out.println("    @SuppressWarnings(\"unchecked\")");
        out.println("    private List<" + entityName + "> processRows(List<Map<String, Object>> rows) throws NoSuchFieldException, IllegalAccessException {");
        out.println();

        // Group fields by alias
        Map<String, List<SqlField>> fieldsByAlias = new LinkedHashMap<>();
        for (SqlField field : fields) {
            String alias = extractAliasFromFieldAlias(field.alias);
            fieldsByAlias.computeIfAbsent(alias, k -> new ArrayList<>()).add(field);
        }

        // Generate field mapping lookups using Alias metadata
        for (Map.Entry<String, Alias> entry : aliases.entrySet()) {
            String aliasName = entry.getKey();
            Alias alias = entry.getValue();
            if (alias.getIsEmbedded()) continue;

            String className = alias.getResultClass().getSimpleName();
            String varPrefix = "fm" + capitalize(sanitizeVarName(aliasName));

            List<SqlField> aliasFields = fieldsByAlias.get(aliasName);
            if (aliasFields != null) {
                out.println("        // " + className + " field mappings");
                for (SqlField field : aliasFields) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    String varName = varPrefix + capitalize(fieldName);
                    out.println("        FieldMapping " + varName + " = dbContext.getFieldMapping(" +
                        className + ".class.getDeclaredField(\"" + fieldName + "\"));");
                }
                out.println();
            }

            // Link field for relationships
            Field linkField = alias.getLinkField();
            if (linkField != null && alias.getParentAlias() != null) {
                Alias parentAlias = aliases.get(alias.getParentAlias());
                if (parentAlias != null) {
                    String parentClassName = parentAlias.getResultClass().getSimpleName();
                    String linkFieldName = linkField.getName();
                    String linkFieldVar = "f" + capitalize(sanitizeVarName(alias.getParentAlias())) + capitalize(linkFieldName);
                    out.println("        // Link field: " + alias.getParentAlias() + "." + linkFieldName);
                    out.println("        Field " + linkFieldVar + " = " + parentClassName + ".class.getDeclaredField(\"" + linkFieldName + "\");");
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
            String className = alias.getResultClass().getSimpleName();
            out.println("        Map<Object, " + className + "> " + varName + " = new HashMap<>();");
        }
        out.println();

        // Row processing loop
        out.println("        for (Map<String, Object> row : rows) {");

        // Process root entity
        Alias rootAlias = aliases.get(tableName);
        if (rootAlias != null && rootAlias.getIdFields() != null && !rootAlias.getIdFields().isEmpty()) {
            Field rootIdField = rootAlias.getIdFields().get(0);
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

            String className = alias.getResultClass().getSimpleName();
            Field aliasIdField = alias.getIdFields().get(0);

            String aliasVarPrefix = "fm" + capitalize(sanitizeVarName(aliasName));
            String entityVar = sanitizeVarName(aliasName);
            String byIdVar = sanitizeVarName(aliasName) + "ById";
            String parentVar = sanitizeVarName(alias.getParentAlias());
            String linkFieldName = alias.getLinkField().getName();
            String linkFieldVar = "f" + capitalize(sanitizeVarName(alias.getParentAlias())) + capitalize(linkFieldName);

            out.println();
            out.println("            // Process relationship: " + aliasName + " (" + className + ")");
            out.println("            Object " + entityVar + "Id = row.get(\"" + aliasName + "." + aliasIdField.getName() + "\");");
            out.println("            if (" + entityVar + "Id != null && !" + byIdVar + ".containsKey(" + entityVar + "Id)) {");
            out.println("                " + className + " " + entityVar + " = new " + className + "();");

            List<SqlField> aliasFields = fieldsByAlias.get(aliasName);
            if (aliasFields != null) {
                for (SqlField field : aliasFields) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    String fmVar = aliasVarPrefix + capitalize(fieldName);
                    out.println("                " + fmVar + ".apply(" + entityVar + ", row.get(\"" + field.alias + "\"));");
                }
            }

            out.println("                " + byIdVar + ".put(" + entityVar + "Id, " + entityVar + ");");
            out.println();
            out.println("                // Link to parent");
            out.println("                if (List.class.isAssignableFrom(" + linkFieldVar + ".getType())) {");
            out.println("                    List<" + className + "> coll = (List<" + className + ">) " + linkFieldVar + ".get(" + parentVar + ");");
            out.println("                    if (coll == null) {");
            out.println("                        coll = new ArrayList<>();");
            out.println("                        " + linkFieldVar + ".set(" + parentVar + ", coll);");
            out.println("                    }");
            out.println("                    coll.add(" + entityVar + ");");
            out.println("                } else {");
            out.println("                    " + linkFieldVar + ".set(" + parentVar + ", " + entityVar + ");");
            out.println("                }");
            out.println("            }");
        }

        out.println("        }");
        out.println();
        out.println("        return result;");
        out.println("    }");
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

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String sanitizeVarName(String alias) {
        return alias.replace(".", "_").replace("-", "_");
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

    private String getBoxedType(Class<?> type) {
        if (type == int.class) return "Integer";
        if (type == long.class) return "Long";
        if (type == boolean.class) return "Boolean";
        if (type == double.class) return "Double";
        if (type == float.class) return "Float";
        if (type == short.class) return "Short";
        if (type == byte.class) return "Byte";
        if (type == char.class) return "Character";
        return type.getSimpleName();
    }
}
