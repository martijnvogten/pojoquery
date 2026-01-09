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
import org.pojoquery.annotations.Table;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.Alias;
import org.pojoquery.pipeline.CustomizableQueryBuilder;
import org.pojoquery.pipeline.QueryBuilder;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.pipeline.SqlQuery.SqlField;
import org.pojoquery.pipeline.SqlQuery.SqlJoin;
import org.pojoquery.typemodel.ElementTypeModel;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

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
        String fieldsSuffix = annotation.fieldsSuffix();

        String packageName = getPackageName(typeElement);
        String entityName = typeElement.getSimpleName().toString();
        String qualifiedName = typeElement.getQualifiedName().toString();
        String fieldsClassName = entityName + fieldsSuffix;
        String queryClassName = entityName + querySuffix;

        // Use ElementTypeModel to process the entity at compile time
        TypeModel entityType = new ElementTypeModel(typeElement, elementUtils, typeUtils);

        List<TableMapping> tableMapping = QueryBuilder.determineTableMapping(entityType);
        if (tableMapping.size() == 0) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@GenerateQuery requires @Table annotation on entity or its superclasses", typeElement);
            return;
        }
        String tableName = tableMapping.get(0).tableName;

        CustomizableQueryBuilder<?, ?> builder = QueryBuilder.from(entityType);

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

            out.println("import org.pojoquery.typedquery.ComparableQueryField;");
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
            Alias mainAlias = aliases.get(tableName);
            List<SqlField> mainFields = fieldsByAlias.getOrDefault(tableName, List.of());
            for (SqlField field : mainFields) {
                String fieldName = extractFieldNameFromAlias(field.alias);
                FieldInfo fieldInfo = getFieldInfo(mainAlias, fieldName);
                String queryFieldClass = fieldInfo.isComparable ? "ComparableQueryField" : "QueryField";
                out.println("    public static final " + queryFieldClass + "<" + entityName + ", " + fieldInfo.typeName + "> " +
                    fieldName + " = new " + queryFieldClass + "<>(TABLE, \"" +
                    fieldName + "\", \"" + fieldName + "\", " + fieldInfo.typeName + ".class);");
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

                Alias relAlias = aliases.get(alias);
                for (SqlField field : entry.getValue()) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    FieldInfo fieldInfo = getFieldInfo(relAlias, fieldName);
                    String queryFieldClass = fieldInfo.isComparable ? "ComparableQueryField" : "QueryField";
                    String relFieldPath = field.alias.startsWith(tableName + ".")
                        ? field.alias.substring(tableName.length() + 1)
                        : field.alias;
                    out.println("        public static final " + queryFieldClass + "<" + entityName + ", " + fieldInfo.typeName + "> " +
                        fieldName + " = new " + queryFieldClass + "<>(ALIAS, \"" +
                        fieldName + "\", \"" + relFieldPath + "\", " + fieldInfo.typeName + ".class);");
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
        FieldModel idField = rootAlias != null && rootAlias.getIdFields() != null && !rootAlias.getIdFields().isEmpty()
            ? rootAlias.getIdFields().get(0) : null;

        try (PrintWriter out = new PrintWriter(fileObject.openWriter())) {
            if (!packageName.isEmpty()) {
                out.println("package " + packageName + ";");
                out.println();
            }

            out.println("import java.lang.reflect.Array;");
            out.println("import java.lang.reflect.Field;");
            out.println("import java.sql.Connection;");
            out.println("import java.util.ArrayList;");
            out.println("import java.util.HashMap;");
            out.println("import java.util.HashSet;");
            out.println("import java.util.List;");
            out.println("import java.util.Map;");
            out.println("import java.util.Set;");
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

            // processRowsToEntities - for streaming support
            out.println("    @Override");
            out.println("    protected List<" + entityName + "> processRowsToEntities(List<Map<String, Object>> rows) {");
            out.println("        try {");
            out.println("            return processRows(rows);");
            out.println("        } catch (NoSuchFieldException | IllegalAccessException e) {");
            out.println("            throw new RuntimeException(e);");
            out.println("        }");
            out.println("    }");
            out.println();

            // getIdOrderByClause - for streaming support
            if (idField != null) {
                out.println("    @Override");
                out.println("    protected String getIdOrderByClause() {");
                out.println("        return \"{" + tableName + "." + idField.getName() + "} ASC\";");
                out.println("    }");
                out.println();
            }

            // getPrimaryIdFromRow - for streaming support
            if (idField != null) {
                out.println("    @Override");
                out.println("    protected Object getPrimaryIdFromRow(Map<String, Object> row) {");
                out.println("        return row.get(\"" + tableName + "." + idField.getName() + "\");");
                out.println("    }");
                out.println();
            }

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

            String className = alias.getResultType().getSimpleName();
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
            FieldModel linkField = alias.getLinkField();
            if (linkField != null && alias.getParentAlias() != null) {
                Alias parentAlias = aliases.get(alias.getParentAlias());
                if (parentAlias != null) {
                    String parentClassName = parentAlias.getResultType().getSimpleName();
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
            String className = alias.getResultType().getSimpleName();
            out.println("        Map<Object, " + className + "> " + varName + " = new HashMap<>();");
        }
        out.println();

        // Row processing loop
        out.println("        for (Map<String, Object> row : rows) {");

        // Process root entity
        Alias rootAlias2 = aliases.get(tableName);
        if (rootAlias2 != null && rootAlias2.getIdFields() != null && !rootAlias2.getIdFields().isEmpty()) {
            FieldModel rootIdField = rootAlias2.getIdFields().get(0);
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
            out.println("                // Link to parent (if not already linked)");
            out.println("                if (List.class.isAssignableFrom(" + linkFieldVar + ".getType())) {");
            out.println("                    List<" + className + "> coll = (List<" + className + ">) " + linkFieldVar + ".get(" + parentVar + ");");
            out.println("                    if (coll == null) {");
            out.println("                        coll = new ArrayList<>();");
            out.println("                        " + linkFieldVar + ".set(" + parentVar + ", coll);");
            out.println("                    }");
            out.println("                    if (!coll.contains(" + entityVar + ")) {");
            out.println("                        coll.add(" + entityVar + ");");
            out.println("                    }");
            out.println("                } else if (Set.class.isAssignableFrom(" + linkFieldVar + ".getType())) {");
            out.println("                    Set<" + className + "> coll = (Set<" + className + ">) " + linkFieldVar + ".get(" + parentVar + ");");
            out.println("                    if (coll == null) {");
            out.println("                        coll = new HashSet<>();");
            out.println("                        " + linkFieldVar + ".set(" + parentVar + ", coll);");
            out.println("                    }");
            out.println("                    coll.add(" + entityVar + ");");
            out.println("                } else if (" + linkFieldVar + ".getType().isArray()) {");
            out.println("                    Object arr = " + linkFieldVar + ".get(" + parentVar + ");");
            out.println("                    int len = (arr == null) ? 0 : Array.getLength(arr);");
            out.println("                    boolean contains = false;");
            out.println("                    for (int i = 0; i < len; i++) {");
            out.println("                        if (" + entityVar + ".equals(Array.get(arr, i))) { contains = true; break; }");
            out.println("                    }");
            out.println("                    if (!contains) {");
            out.println("                        Object extended = Array.newInstance(" + linkFieldVar + ".getType().getComponentType(), len + 1);");
            out.println("                        if (len > 0) System.arraycopy(arr, 0, extended, 0, len);");
            out.println("                        Array.set(extended, len, " + entityVar + ");");
            out.println("                        " + linkFieldVar + ".set(" + parentVar + ", extended);");
            out.println("                    }");
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
        // Use simple name for java.lang types, fully qualified for others
        if (name.startsWith("java.lang.")) {
            return type.getSimpleName();
        }
        return name;
    }

    /**
     * Holds field type information including whether it's Comparable.
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
     * Known Comparable types - primitives box to Comparable, plus common types.
     */
    private static final java.util.Set<String> COMPARABLE_TYPES = java.util.Set.of(
        // Primitive wrappers (all implement Comparable)
        "int", "Integer", "java.lang.Integer",
        "long", "Long", "java.lang.Long",
        "short", "Short", "java.lang.Short",
        "byte", "Byte", "java.lang.Byte",
        "double", "Double", "java.lang.Double",
        "float", "Float", "java.lang.Float",
        "char", "Character", "java.lang.Character",
        // String
        "String", "java.lang.String",
        // Date/Time types
        "java.time.LocalDate", "java.time.LocalDateTime", "java.time.LocalTime",
        "java.time.Instant", "java.time.ZonedDateTime", "java.time.OffsetDateTime",
        "java.util.Date", "java.sql.Date", "java.sql.Timestamp",
        // Big numbers
        "java.math.BigDecimal", "java.math.BigInteger",
        // UUID
        "java.util.UUID"
    );

    /**
     * Checks if a type is Comparable.
     */
    private boolean isComparableType(TypeModel type) {
        if (type == null) return false;
        String name = type.getQualifiedName();
        return COMPARABLE_TYPES.contains(name) || type.isEnum();
    }

    /**
     * Looks up field info (type name and whether it's Comparable) from the Alias's TypeModel.
     * Walks up the class hierarchy to find inherited fields.
     */
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
