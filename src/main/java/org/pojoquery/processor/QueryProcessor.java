package org.pojoquery.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Select;
import org.pojoquery.annotations.Table;
import org.pojoquery.annotations.Transient;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.processor.QueryMetadata.AliasMetadata;
import org.pojoquery.processor.QueryMetadata.FieldMetadata;
import org.pojoquery.processor.RecordingSqlQuery.RecordedField;
import org.pojoquery.processor.RecordingSqlQuery.RecordedJoin;

/**
 * Annotation processor that generates type-safe query builders for PojoQuery entities.
 *
 * <p>This processor attempts two strategies:
 * <ol>
 *   <li>If the entity class is already compiled (e.g., during test-compile), invoke
 *       CustomizableQueryBuilder to extract real metadata and generate explicit code.</li>
 *   <li>Otherwise, use the element model to gather information and delegate to
 *       CustomizableQueryBuilder at runtime.</li>
 * </ol>
 */
@SupportedAnnotationTypes("org.pojoquery.annotations.GenerateQuery")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class QueryProcessor extends AbstractProcessor {

    private Types typeUtils;
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
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

    private void processEntity(TypeElement typeElement) throws IOException {
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

        // Try to load the class and extract real metadata
        QueryMetadata metadata = tryExtractMetadata(qualifiedName);

        if (metadata != null) {
            // Success! Generate code using real metadata from CustomizableQueryBuilder
            messager.printMessage(Diagnostic.Kind.NOTE,
                "Using compile-time metadata for " + qualifiedName +
                " (" + metadata.getAliases().size() + " aliases, " +
                metadata.getFields().size() + " fields)", typeElement);

            generateFieldsClassFromMetadata(packageName, entityName, fieldsClassName, tableName, metadata);
            generateQueryClassFromMetadata(packageName, entityName, qualifiedName, queryClassName,
                fieldsClassName, tableName, metadata);
        } else {
            // Fall back to element model approach
            messager.printMessage(Diagnostic.Kind.NOTE,
                "Using element model fallback for " + qualifiedName +
                " (class not yet compiled)", typeElement);

            processEntityWithElementModel(typeElement, packageName, entityName, qualifiedName,
                fieldsClassName, queryClassName, tableName);
        }
    }

    /**
     * Attempts to load the entity class and extract metadata using CustomizableQueryBuilder.
     * Returns null if the class is not yet compiled.
     */
    private QueryMetadata tryExtractMetadata(String qualifiedName) {
        try {
            Class<?> entityClass = Class.forName(qualifiedName);
            return QueryMetadata.forClass(entityClass);
        } catch (ClassNotFoundException e) {
            // Class not yet compiled - this is expected during main compile
            return null;
        } catch (Exception e) {
            // Other error - log and fall back
            messager.printMessage(Diagnostic.Kind.NOTE,
                "Could not extract metadata: " + e.getMessage());
            return null;
        }
    }

    // === Code generation from real metadata ===

    private void generateFieldsClassFromMetadata(String packageName, String entityName,
            String className, String tableName, QueryMetadata metadata) throws IOException {

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
            out.println(" * <p>Generated from CustomizableQueryBuilder metadata:");
            out.println(" * <ul>");
            out.println(" *   <li>" + metadata.getAliases().size() + " aliases</li>");
            out.println(" *   <li>" + metadata.getFields().size() + " fields</li>");
            out.println(" * </ul>");
            out.println(" */");
            out.println("public final class " + className + " {");
            out.println();
            out.println("    public static final String TABLE = \"" + tableName + "\";");
            out.println();

            // Group fields by alias
            Map<String, List<FieldMetadata>> fieldsByAlias = new LinkedHashMap<>();
            for (FieldMetadata field : metadata.getFields()) {
                String alias = field.getAliasPrefix();
                fieldsByAlias.computeIfAbsent(alias, k -> new ArrayList<>()).add(field);
            }

            // Generate main entity fields
            List<FieldMetadata> mainFields = fieldsByAlias.getOrDefault(tableName, List.of());
            for (FieldMetadata field : mainFields) {
                String typeName = simplifyTypeName(field.getFieldTypeName());
                out.println("    public static final QueryField<" + entityName + ", " +
                    typeName + "> " + field.getFieldName() + " = new QueryField<>(TABLE, \"" +
                    field.getFieldName() + "\", \"" + field.getFieldName() + "\", " +
                    typeName + ".class);");
            }
            out.println();

            // Generate nested classes for relationships
            for (AliasMetadata alias : metadata.getAliases()) {
                if (alias.isPrimaryAlias()) continue;
                if (alias.isSubClass()) continue;

                List<FieldMetadata> aliasFields = fieldsByAlias.get(alias.getAlias());
                if (aliasFields == null || aliasFields.isEmpty()) continue;

                String innerClassName = capitalize(getLastPart(alias.getAlias()));
                out.println("    /** Fields for the {@code " + alias.getAlias() + "} relationship */");
                out.println("    public static final class " + innerClassName + " {");
                out.println("        public static final String ALIAS = \"" + alias.getAlias() + "\";");
                out.println();

                for (FieldMetadata field : aliasFields) {
                    String typeName = simplifyTypeName(field.getFieldTypeName());
                    String fieldPath = alias.getAlias() + "." + field.getFieldName();
                    out.println("        public static final QueryField<" + entityName + ", " +
                        typeName + "> " + field.getFieldName() +
                        " = new QueryField<>(ALIAS, \"" + field.getFieldName() + "\", \"" +
                        fieldPath.substring(tableName.length() + 1) + "\", " + typeName + ".class);");
                }

                out.println("        private " + innerClassName + "() {}");
                out.println("    }");
                out.println();
            }

            out.println("    private " + className + "() {}");
            out.println("}");
        }
    }

    private void generateQueryClassFromMetadata(String packageName, String entityName,
            String qualifiedEntityName, String className, String fieldsClassName,
            String tableName, QueryMetadata metadata) throws IOException {

        String qualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
        JavaFileObject fileObject = filer.createSourceFile(qualifiedName);

        // Find ID field for the root entity
        String idFieldName = null;
        String idFieldType = null;
        AliasMetadata rootAlias = metadata.getAlias(tableName);
        if (rootAlias != null && !rootAlias.getIdFieldNames().isEmpty()) {
            String firstIdField = rootAlias.getIdFieldNames().get(0);
            for (FieldMetadata field : metadata.getFields()) {
                if (field.getAliasPrefix().equals(tableName) && field.getFieldName().equals(firstIdField)) {
                    idFieldName = field.getFieldName();
                    idFieldType = simplifyTypeName(field.getFieldTypeName());
                    break;
                }
            }
        }

        try (PrintWriter out = new PrintWriter(fileObject.openWriter())) {
            if (!packageName.isEmpty()) {
                out.println("package " + packageName + ";");
                out.println();
            }

            out.println("import java.lang.reflect.Field;");
            out.println("import java.sql.Connection;");
            out.println("import java.sql.ResultSet;");
            out.println("import java.sql.SQLException;");
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
            out.println(" * <p>Generated from CustomizableQueryBuilder metadata at compile time:");
            out.println(" * <ul>");
            out.println(" *   <li>" + metadata.getAliases().size() + " aliases</li>");
            out.println(" *   <li>" + metadata.getFields().size() + " field mappings</li>");
            out.println(" * </ul>");
            out.println(" */");
            out.println("public class " + className + " extends TypedQuery<" + entityName + ", " + className + "> {");
            out.println();

            // Constructor
            out.println("    public " + className + "() {");
            out.println("        super(\"" + tableName + "\");");
            out.println("    }");
            out.println();
            out.println("    public " + className + "(DbContext dbContext) {");
            out.println("        super(dbContext, null, \"" + tableName + "\");");
            out.println("    }");
            out.println();

            // initializeQuery - set up fields and joins from metadata
            generateInitializeQuery(out, tableName, metadata);

            // mapRow - single row mapping (for simple entities without collections)
            out.println("    @Override");
            out.println("    protected " + entityName + " mapRow(ResultSet rs) throws SQLException {");
            out.println("        throw new UnsupportedOperationException(\"Use list() for entity graph mapping\");");
            out.println("    }");
            out.println();

            // getEntityClass
            out.println("    @Override");
            out.println("    protected Class<" + entityName + "> getEntityClass() {");
            out.println("        return " + entityName + ".class;");
            out.println("    }");
            out.println();

            // Generate explicit list() with result processing
            generateListMethod(out, entityName, tableName, metadata);

            // Generate helper methods for result processing
            generateHelperMethods(out, metadata);

            // findById if ID field exists
            if (idFieldName != null) {
                out.println("    public " + entityName + " findById(DataSource dataSource, " + idFieldType + " id) {");
                out.println("        return where(" + fieldsClassName + "." + idFieldName + ").is(id).first(dataSource);");
                out.println("    }");
                out.println();
                out.println("    public " + entityName + " findById(Connection connection, " + idFieldType + " id) {");
                out.println("        return where(" + fieldsClassName + "." + idFieldName + ").is(id).first(connection);");
                out.println("    }");
            }

            out.println("}");
        }
    }

    private void generateInitializeQuery(PrintWriter out, String tableName, QueryMetadata metadata) {
        out.println("    @Override");
        out.println("    protected void initializeQuery() {");
        out.println("        // Query structure captured at compile time from CustomizableQueryBuilder");
        out.println();

        // Generate joins first
        for (QueryMetadata.JoinMetadata join : metadata.getJoins()) {
            String schemaArg = join.getSchemaName() == null ? "null" : "\"" + escapeJava(join.getSchemaName()) + "\"";
            String conditionArg = join.getJoinConditionSql() == null ? "null" :
                "SqlExpression.sql(\"" + escapeJava(join.getJoinConditionSql()) + "\")";
            out.println("        query.addJoin(org.pojoquery.pipeline.SqlQuery.JoinType." + join.getJoinType() +
                ", " + schemaArg + ", \"" + escapeJava(join.getTableName()) +
                "\", \"" + escapeJava(join.getAlias()) + "\", " + conditionArg + ");");
        }

        if (!metadata.getJoins().isEmpty()) {
            out.println();
        }

        // Generate fields
        for (QueryMetadata.SqlFieldMetadata field : metadata.getSqlFields()) {
            out.println("        query.addField(SqlExpression.sql(\"" + escapeJava(field.getExpressionSql()) +
                "\"), \"" + escapeJava(field.getAlias()) + "\");");
        }

        out.println("    }");
        out.println();
    }

    private void generateListMethod(PrintWriter out, String entityName, String tableName, QueryMetadata metadata) {
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

        // Generate the processRows method
        generateProcessRowsMethod(out, entityName, tableName, metadata);
    }

    private void generateProcessRowsMethod(PrintWriter out, String entityName, String tableName, QueryMetadata metadata) {
        out.println("    @SuppressWarnings(\"unchecked\")");
        out.println("    private List<" + entityName + "> processRows(List<Map<String, Object>> rows) throws NoSuchFieldException, IllegalAccessException {");
        out.println();

        // Group fields by alias for code generation
        Map<String, List<FieldMetadata>> fieldsByAlias = new LinkedHashMap<>();
        for (FieldMetadata field : metadata.getFields()) {
            String alias = field.getAliasPrefix();
            fieldsByAlias.computeIfAbsent(alias, k -> new ArrayList<>()).add(field);
        }

        // Generate field mapping lookups for each alias
        for (AliasMetadata alias : metadata.getAliases()) {
            if (alias.isSubClass()) continue;

            String aliasName = alias.getAlias();
            String varPrefix = "fm" + capitalize(sanitizeVarName(aliasName));
            String className = getSimpleClassName(alias.getResultClassName());

            List<FieldMetadata> aliasFields = fieldsByAlias.get(aliasName);
            if (aliasFields != null) {
                out.println("        // " + className + " field mappings");
                for (FieldMetadata field : aliasFields) {
                    String varName = varPrefix + capitalize(field.getFieldName());
                    out.println("        FieldMapping " + varName + " = dbContext.getFieldMapping(" +
                        className + ".class.getDeclaredField(\"" + field.getFieldName() + "\"));");
                }
                out.println();
            }

            // For relationship fields (link fields), we need the Field directly for collection handling
            if (alias.getLinkFieldName() != null && !alias.isPrimaryAlias()) {
                String parentClassName = null;
                AliasMetadata parentAlias = metadata.getAlias(alias.getParentAlias());
                if (parentAlias != null) {
                    parentClassName = getSimpleClassName(parentAlias.getResultClassName());
                }
                if (parentClassName != null) {
                    String linkFieldVar = "f" + capitalize(sanitizeVarName(alias.getParentAlias())) + capitalize(alias.getLinkFieldName());
                    out.println("        // Link field: " + alias.getParentAlias() + "." + alias.getLinkFieldName());
                    out.println("        Field " + linkFieldVar + " = " + parentClassName + ".class.getDeclaredField(\"" + alias.getLinkFieldName() + "\");");
                    out.println("        " + linkFieldVar + ".setAccessible(true);");
                    out.println();
                }
            }
        }

        // Generate entity maps for deduplication
        out.println("        // Entity deduplication maps");
        out.println("        List<" + entityName + "> result = new ArrayList<>();");
        for (AliasMetadata alias : metadata.getAliases()) {
            if (alias.isSubClass() || alias.isEmbedded()) continue;
            String varName = sanitizeVarName(alias.getAlias()) + "ById";
            String className = getSimpleClassName(alias.getResultClassName());
            out.println("        Map<Object, " + className + "> " + varName + " = new HashMap<>();");
        }
        out.println();

        // Generate row processing loop
        out.println("        for (Map<String, Object> row : rows) {");

        // Process root entity
        AliasMetadata rootAlias = metadata.getAlias(tableName);
        if (rootAlias != null && !rootAlias.getIdFieldNames().isEmpty()) {
            String idField = rootAlias.getIdFieldNames().get(0);
            String rootVarPrefix = "fm" + capitalize(sanitizeVarName(tableName));

            out.println("            // Process root entity: " + entityName);
            out.println("            Object " + sanitizeVarName(tableName) + "Id = row.get(\"" + tableName + "." + idField + "\");");
            out.println("            if (" + sanitizeVarName(tableName) + "Id == null) continue;");
            out.println();
            out.println("            " + entityName + " " + sanitizeVarName(tableName) + " = " + sanitizeVarName(tableName) + "ById.get(" + sanitizeVarName(tableName) + "Id);");
            out.println("            if (" + sanitizeVarName(tableName) + " == null) {");
            out.println("                " + sanitizeVarName(tableName) + " = new " + entityName + "();");

            // Apply field mappings for root entity
            List<FieldMetadata> rootFields = fieldsByAlias.get(tableName);
            if (rootFields != null) {
                for (FieldMetadata field : rootFields) {
                    String fmVar = rootVarPrefix + capitalize(field.getFieldName());
                    out.println("                " + fmVar + ".apply(" + sanitizeVarName(tableName) + ", row.get(\"" + field.getFieldAlias() + "\"));");
                }
            }

            out.println("                " + sanitizeVarName(tableName) + "ById.put(" + sanitizeVarName(tableName) + "Id, " + sanitizeVarName(tableName) + ");");
            out.println("                result.add(" + sanitizeVarName(tableName) + ");");
            out.println("            }");
        }

        // Process related aliases
        for (AliasMetadata alias : metadata.getAliases()) {
            if (alias.isPrimaryAlias() || alias.isSubClass() || alias.isEmbedded()) continue;

            String aliasName = alias.getAlias();
            String parentAliasName = alias.getParentAlias();
            String linkFieldName = alias.getLinkFieldName();
            String className = getSimpleClassName(alias.getResultClassName());

            if (parentAliasName != null && linkFieldName != null && !alias.getIdFieldNames().isEmpty()) {
                String idField = alias.getIdFieldNames().get(0);
                String aliasVarPrefix = "fm" + capitalize(sanitizeVarName(aliasName));
                String entityVar = sanitizeVarName(aliasName);
                String byIdVar = sanitizeVarName(aliasName) + "ById";
                String parentVar = sanitizeVarName(parentAliasName);
                String linkFieldVar = "f" + capitalize(sanitizeVarName(parentAliasName)) + capitalize(linkFieldName);

                out.println();
                out.println("            // Process relationship: " + aliasName + " (" + className + ")");
                out.println("            Object " + entityVar + "Id = row.get(\"" + aliasName + "." + idField + "\");");
                out.println("            if (" + entityVar + "Id != null && !" + byIdVar + ".containsKey(" + entityVar + "Id)) {");
                out.println("                " + className + " " + entityVar + " = new " + className + "();");

                // Apply field mappings for this alias
                List<FieldMetadata> aliasFields = fieldsByAlias.get(aliasName);
                if (aliasFields != null) {
                    for (FieldMetadata field : aliasFields) {
                        String fmVar = aliasVarPrefix + capitalize(field.getFieldName());
                        out.println("                " + fmVar + ".apply(" + entityVar + ", row.get(\"" + field.getFieldAlias() + "\"));");
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
        }

        out.println("        }");
        out.println();
        out.println("        return result;");
        out.println("    }");
        out.println();
    }

    private String getSimpleClassName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    private void generateHelperMethods(PrintWriter out, QueryMetadata metadata) {
        // No helper methods needed - all logic is generated inline
    }

    private String sanitizeVarName(String alias) {
        return alias.replace(".", "_").replace("-", "_");
    }

    // === Fallback: Element model approach ===

    private void processEntityWithElementModel(TypeElement typeElement, String packageName,
            String entityName, String qualifiedName, String fieldsClassName,
            String queryClassName, String tableName) throws IOException {

        // Create recording SqlQuery to capture operations
        RecordingSqlQuery recorder = new RecordingSqlQuery(DbContext.getDefault());
        recorder.setTable(null, tableName);

        // Build up the query mimicking CustomizableQueryBuilder
        Map<String, AliasInfo> aliases = new LinkedHashMap<>();
        aliases.put(tableName, new AliasInfo(tableName, typeElement, null, null));

        buildQuery(typeElement, tableName, tableName, recorder, aliases, new HashSet<>());

        // Find ID field
        FieldInfo idField = findIdField(typeElement, tableName);

        // Generate the classes from recorded data
        generateFieldsClassFromElementModel(packageName, entityName, fieldsClassName, tableName,
            recorder.getRecordedFields(), aliases);
        generateQueryClassFromElementModel(packageName, entityName, qualifiedName, queryClassName,
            fieldsClassName, tableName, recorder, idField, aliases);
    }

    /**
     * Builds the query by walking the entity structure and recording all operations.
     */
    private void buildQuery(TypeElement typeElement, String alias, String fieldsAlias,
            RecordingSqlQuery recorder, Map<String, AliasInfo> aliases, Set<String> visited) {

        String typeName = typeElement.getQualifiedName().toString();
        if (visited.contains(typeName) || typeName.equals("java.lang.Object")) {
            return;
        }
        visited.add(typeName);

        // Process superclass first
        TypeMirror superclass = typeElement.getSuperclass();
        if (superclass.getKind() != TypeKind.NONE) {
            Element superElement = typeUtils.asElement(superclass);
            if (superElement instanceof TypeElement) {
                TypeElement superType = (TypeElement) superElement;
                Table superTable = superType.getAnnotation(Table.class);
                if (superTable != null) {
                    String superTableName = superTable.value();
                    FieldInfo superId = findIdField(superType, superTableName);
                    if (superId != null) {
                        String superAlias = alias.equals(recorder.getRecordedTable())
                            ? superTableName
                            : alias + "." + superTableName;
                        JoinType joinType = alias.equals(recorder.getRecordedTable())
                            ? JoinType.INNER
                            : JoinType.LEFT;

                        recorder.addJoin(joinType, null, superTableName, superAlias,
                            SqlExpression.sql("{" + superAlias + "}." + superId.columnName +
                                " = {" + alias + "}." + superId.columnName));

                        aliases.put(superAlias, new AliasInfo(superAlias, superType, alias, null));
                    }
                }
                addFields(superType, alias, fieldsAlias, recorder, aliases, null);
            }
        }

        addFields(typeElement, alias, fieldsAlias, recorder, aliases, null);
    }

    private void addFields(TypeElement typeElement, String alias, String fieldsAlias,
            RecordingSqlQuery recorder, Map<String, AliasInfo> aliases, String fieldNamePrefix) {

        String rootAlias = recorder.getRecordedTable();
        boolean isRoot = alias.equals(rootAlias);

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) continue;
            VariableElement field = (VariableElement) enclosed;

            if (field.getModifiers().contains(Modifier.STATIC)) continue;
            if (field.getAnnotation(Transient.class) != null) continue;

            TypeMirror fieldType = field.asType();
            String fieldName = field.getSimpleName().toString();

            if (isCollectionType(fieldType)) {
                TypeMirror componentType = getCollectionComponentType(fieldType);
                if (componentType != null && hasTableAnnotation(componentType)) {
                    TypeElement relatedType = (TypeElement) typeUtils.asElement(componentType);
                    Table relatedTable = relatedType.getAnnotation(Table.class);
                    if (relatedTable != null) {
                        String linkAlias = isRoot ? fieldName : (alias + "." + fieldName);

                        String linkField = getLinkFieldName(typeElement);
                        Link linkAnn = field.getAnnotation(Link.class);
                        if (linkAnn != null && !Link.NONE.equals(linkAnn.foreignlinkfield())) {
                            linkField = linkAnn.foreignlinkfield();
                        }

                        FieldInfo idField = findIdField(typeElement, alias);
                        String joinConditionStr = null;
                        JoinCondition joinConditionAnn = field.getAnnotation(JoinCondition.class);
                        if (joinConditionAnn != null) {
                            joinConditionStr = resolveAliases(joinConditionAnn.value(), alias, linkAlias);
                        } else if (idField != null) {
                            joinConditionStr = "{" + alias + "}." + idField.columnName +
                                " = {" + linkAlias + "}." + linkField;
                        }

                        if (joinConditionStr != null) {
                            recorder.addJoin(JoinType.LEFT, null, relatedTable.value(), linkAlias,
                                SqlExpression.sql(joinConditionStr));
                            aliases.put(linkAlias, new AliasInfo(linkAlias, relatedType, alias, field));
                            buildQuery(relatedType, linkAlias, linkAlias, recorder, aliases, new HashSet<>());
                        }
                    }
                }
            } else if (hasTableAnnotation(fieldType)) {
                TypeElement relatedType = (TypeElement) typeUtils.asElement(fieldType);
                Table relatedTable = relatedType.getAnnotation(Table.class);
                if (relatedTable != null) {
                    String linkAlias = isRoot ? fieldName : (alias + "." + fieldName);

                    FieldInfo relatedId = findIdField(relatedType, linkAlias);
                    String fkField = getForeignKeyField(field, relatedType);

                    if (relatedId != null) {
                        String joinCondition = "{" + alias + "}." + fkField +
                            " = {" + linkAlias + "}." + relatedId.columnName;

                        recorder.addJoin(JoinType.LEFT, null, relatedTable.value(), linkAlias,
                            SqlExpression.sql(joinCondition));
                        aliases.put(linkAlias, new AliasInfo(linkAlias, relatedType, alias, field));
                        buildQuery(relatedType, linkAlias, linkAlias, recorder, aliases, new HashSet<>());
                    }
                }
            } else if (isEmbedded(field)) {
                TypeElement embeddedType = (TypeElement) typeUtils.asElement(fieldType);
                if (embeddedType != null) {
                    String prefix = getEmbeddedPrefix(field);
                    String embedAlias = (isRoot && fieldNamePrefix == null)
                        ? fieldName
                        : fieldsAlias + "." + fieldName;

                    aliases.put(embedAlias, new AliasInfo(embedAlias, embeddedType, fieldsAlias, field, true));
                    String combinedPrefix = (fieldNamePrefix == null ? "" : fieldNamePrefix) + prefix;
                    addFields(embeddedType, alias, embedAlias, recorder, aliases, combinedPrefix);
                }
            } else {
                String columnName = getColumnName(field);
                String fullColumnName = (fieldNamePrefix == null ? "" : fieldNamePrefix) + columnName;

                Select selectAnn = field.getAnnotation(Select.class);
                String selectExpr;
                if (selectAnn != null) {
                    selectExpr = resolveAliases(selectAnn.value(), alias, null);
                } else {
                    selectExpr = "{" + alias + "}." + fullColumnName;
                }

                String fieldAlias = fieldsAlias + "." + fieldName;
                recorder.addField(SqlExpression.sql(selectExpr), fieldAlias);
            }
        }
    }

    private void generateFieldsClassFromElementModel(String packageName, String entityName,
            String className, String tableName, List<RecordedField> fields,
            Map<String, AliasInfo> aliases) throws IOException {

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
            out.println(" * <p>Generated from element model (class not yet compiled).");
            out.println(" */");
            out.println("public final class " + className + " {");
            out.println();
            out.println("    public static final String TABLE = \"" + tableName + "\";");
            out.println();

            Map<String, List<RecordedField>> fieldsByPrefix = new LinkedHashMap<>();
            for (RecordedField field : fields) {
                String alias = field.alias;
                int lastDot = alias.lastIndexOf('.');
                String prefix = lastDot > 0 ? alias.substring(0, lastDot) : tableName;
                fieldsByPrefix.computeIfAbsent(prefix, k -> new ArrayList<>()).add(field);
            }

            List<RecordedField> mainFields = fieldsByPrefix.getOrDefault(tableName, List.of());
            for (RecordedField field : mainFields) {
                String fieldName = extractFieldName(field.alias);
                out.println("    public static final QueryField<" + entityName + ", Object> " +
                    fieldName + " = new QueryField<>(TABLE, \"" +
                    extractColumnName(field.expression) + "\", \"" + fieldName + "\", Object.class);");
            }
            out.println();

            for (Map.Entry<String, List<RecordedField>> entry : fieldsByPrefix.entrySet()) {
                String prefix = entry.getKey();
                if (prefix.equals(tableName)) continue;

                List<RecordedField> relFields = entry.getValue();
                String innerClassName = capitalize(extractFieldName(prefix));

                out.println("    /** Fields for the {@code " + extractFieldName(prefix) + "} relationship */");
                out.println("    public static final class " + innerClassName + " {");
                out.println("        public static final String ALIAS = \"" + prefix + "\";");
                out.println();

                for (RecordedField field : relFields) {
                    String fieldName = extractFieldName(field.alias);
                    String relFieldPath = field.alias.startsWith(tableName + ".")
                        ? field.alias.substring(tableName.length() + 1)
                        : field.alias;
                    out.println("        public static final QueryField<" + entityName + ", Object> " +
                        fieldName + " = new QueryField<>(ALIAS, \"" +
                        extractColumnName(field.expression) + "\", \"" + relFieldPath + "\", Object.class);");
                }

                out.println("        private " + innerClassName + "() {}");
                out.println("    }");
                out.println();
            }

            out.println("    private " + className + "() {}");
            out.println("}");
        }
    }

    private void generateQueryClassFromElementModel(String packageName, String entityName,
            String qualifiedEntityName, String className, String fieldsClassName, String tableName,
            RecordingSqlQuery recorder, FieldInfo idField, Map<String, AliasInfo> aliases) throws IOException {

        String qualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
        JavaFileObject fileObject = filer.createSourceFile(qualifiedName);

        List<RecordedField> fields = recorder.getRecordedFields();
        List<RecordedJoin> joins = recorder.getRecordedJoins();

        try (PrintWriter out = new PrintWriter(fileObject.openWriter())) {
            if (!packageName.isEmpty()) {
                out.println("package " + packageName + ";");
                out.println();
            }

            out.println("import java.lang.reflect.Field;");
            out.println("import java.sql.Connection;");
            out.println("import java.sql.ResultSet;");
            out.println("import java.sql.SQLException;");
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
            out.println(" * <p>Generated from element model:");
            out.println(" * <ul>");
            out.println(" *   <li>" + fields.size() + " fields</li>");
            out.println(" *   <li>" + joins.size() + " joins</li>");
            out.println(" * </ul>");
            out.println(" */");
            out.println("public class " + className + " extends TypedQuery<" + entityName + ", " + className + "> {");
            out.println();

            out.println("    public " + className + "() {");
            out.println("        super(\"" + tableName + "\");");
            out.println("    }");
            out.println();
            out.println("    public " + className + "(DbContext dbContext) {");
            out.println("        super(dbContext, null, \"" + tableName + "\");");
            out.println("    }");
            out.println();

            out.println("    @Override");
            out.println("    protected void initializeQuery() {");
            // Generate joins
            for (RecordedJoin join : joins) {
                String schemaArg = join.schema == null ? "null" : "\"" + escapeJava(join.schema) + "\"";
                String conditionArg = join.joinCondition == null ? "null" :
                    "SqlExpression.sql(\"" + escapeJava(join.joinCondition) + "\")";
                out.println("        query.addJoin(org.pojoquery.pipeline.SqlQuery.JoinType." + join.joinType.name() +
                    ", " + schemaArg + ", \"" + escapeJava(join.table) +
                    "\", \"" + escapeJava(join.alias) + "\", " + conditionArg + ");");
            }
            // Generate fields
            for (RecordedField field : fields) {
                out.println("        query.addField(SqlExpression.sql(\"" + escapeJava(field.expression) +
                    "\"), \"" + escapeJava(field.alias) + "\");");
            }
            out.println("    }");
            out.println();

            out.println("    @Override");
            out.println("    protected " + entityName + " mapRow(ResultSet rs) throws SQLException {");
            out.println("        throw new UnsupportedOperationException(\"Use list() for entity graph mapping\");");
            out.println("    }");
            out.println();

            out.println("    @Override");
            out.println("    protected Class<" + entityName + "> getEntityClass() {");
            out.println("        return " + entityName + ".class;");
            out.println("    }");
            out.println();

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

            // Generate processRows method from element model data
            generateProcessRowsFromElementModel(out, entityName, tableName, fields, aliases);

            if (idField != null) {
                out.println("    public " + entityName + " findById(DataSource dataSource, " + idField.boxedType + " id) {");
                out.println("        return where(" + fieldsClassName + "." + idField.fieldName + ").is(id).first(dataSource);");
                out.println("    }");
                out.println();
                out.println("    public " + entityName + " findById(Connection connection, " + idField.boxedType + " id) {");
                out.println("        return where(" + fieldsClassName + "." + idField.fieldName + ").is(id).first(connection);");
                out.println("    }");
            }

            out.println("}");
        }
    }

    private void generateProcessRowsFromElementModel(PrintWriter out, String entityName, String tableName,
            List<RecordedField> fields, Map<String, AliasInfo> aliases) {
        out.println("    @SuppressWarnings(\"unchecked\")");
        out.println("    private List<" + entityName + "> processRows(List<Map<String, Object>> rows) throws NoSuchFieldException, IllegalAccessException {");
        out.println();

        // Group fields by alias
        Map<String, List<RecordedField>> fieldsByAlias = new LinkedHashMap<>();
        for (RecordedField field : fields) {
            String alias = extractAliasFromFieldAlias(field.alias);
            fieldsByAlias.computeIfAbsent(alias, k -> new ArrayList<>()).add(field);
        }

        // Generate field mapping lookups for each alias
        for (Map.Entry<String, AliasInfo> entry : aliases.entrySet()) {
            String aliasName = entry.getKey();
            AliasInfo aliasInfo = entry.getValue();
            if (aliasInfo.isEmbedded) continue;

            String className = aliasInfo.typeElement.getSimpleName().toString();
            String varPrefix = "fm" + capitalize(sanitizeVarName(aliasName));

            List<RecordedField> aliasFields = fieldsByAlias.get(aliasName);
            if (aliasFields != null) {
                out.println("        // " + className + " field mappings");
                for (RecordedField field : aliasFields) {
                    String fieldName = extractFieldNameFromAlias(field.alias);
                    String varName = varPrefix + capitalize(fieldName);
                    out.println("        FieldMapping " + varName + " = dbContext.getFieldMapping(" +
                        className + ".class.getDeclaredField(\"" + fieldName + "\"));");
                }
                out.println();
            }

            // For relationship fields (link fields), we need the Field directly for collection handling
            if (aliasInfo.linkField != null && aliasInfo.parentAlias != null) {
                AliasInfo parentInfo = aliases.get(aliasInfo.parentAlias);
                if (parentInfo != null) {
                    String parentClassName = parentInfo.typeElement.getSimpleName().toString();
                    String linkFieldName = aliasInfo.linkField.getSimpleName().toString();
                    String linkFieldVar = "f" + capitalize(sanitizeVarName(aliasInfo.parentAlias)) + capitalize(linkFieldName);
                    out.println("        // Link field: " + aliasInfo.parentAlias + "." + linkFieldName);
                    out.println("        Field " + linkFieldVar + " = " + parentClassName + ".class.getDeclaredField(\"" + linkFieldName + "\");");
                    out.println("        " + linkFieldVar + ".setAccessible(true);");
                    out.println();
                }
            }
        }

        // Generate entity maps for deduplication
        out.println("        // Entity deduplication maps");
        out.println("        List<" + entityName + "> result = new ArrayList<>();");
        for (Map.Entry<String, AliasInfo> entry : aliases.entrySet()) {
            AliasInfo aliasInfo = entry.getValue();
            if (aliasInfo.isEmbedded) continue;
            String varName = sanitizeVarName(entry.getKey()) + "ById";
            String className = aliasInfo.typeElement.getSimpleName().toString();
            out.println("        Map<Object, " + className + "> " + varName + " = new HashMap<>();");
        }
        out.println();

        // Generate row processing loop
        out.println("        for (Map<String, Object> row : rows) {");

        // Process root entity first
        AliasInfo rootAliasInfo = aliases.get(tableName);
        if (rootAliasInfo != null) {
            FieldInfo rootIdField = findIdField(rootAliasInfo.typeElement, tableName);
            if (rootIdField != null) {
                String rootVarPrefix = "fm" + capitalize(sanitizeVarName(tableName));
                String idFieldName = rootIdField.fieldName;

                out.println("            // Process root entity: " + entityName);
                out.println("            Object " + sanitizeVarName(tableName) + "Id = row.get(\"" + tableName + "." + idFieldName + "\");");
                out.println("            if (" + sanitizeVarName(tableName) + "Id == null) continue;");
                out.println();
                out.println("            " + entityName + " " + sanitizeVarName(tableName) + " = " + sanitizeVarName(tableName) + "ById.get(" + sanitizeVarName(tableName) + "Id);");
                out.println("            if (" + sanitizeVarName(tableName) + " == null) {");
                out.println("                " + sanitizeVarName(tableName) + " = new " + entityName + "();");

                // Apply field mappings for root entity
                List<RecordedField> rootFields = fieldsByAlias.get(tableName);
                if (rootFields != null) {
                    for (RecordedField field : rootFields) {
                        String fieldName = extractFieldNameFromAlias(field.alias);
                        String fmVar = rootVarPrefix + capitalize(fieldName);
                        out.println("                " + fmVar + ".apply(" + sanitizeVarName(tableName) + ", row.get(\"" + field.alias + "\"));");
                    }
                }

                out.println("                " + sanitizeVarName(tableName) + "ById.put(" + sanitizeVarName(tableName) + "Id, " + sanitizeVarName(tableName) + ");");
                out.println("                result.add(" + sanitizeVarName(tableName) + ");");
                out.println("            }");
            }
        }

        // Process related aliases
        for (Map.Entry<String, AliasInfo> entry : aliases.entrySet()) {
            String aliasName = entry.getKey();
            AliasInfo aliasInfo = entry.getValue();

            if (aliasName.equals(tableName) || aliasInfo.isEmbedded) continue;
            if (aliasInfo.parentAlias == null || aliasInfo.linkField == null) continue;

            String className = aliasInfo.typeElement.getSimpleName().toString();
            FieldInfo aliasIdField = findIdField(aliasInfo.typeElement, aliasName);
            if (aliasIdField == null) continue;

            String idFieldName = aliasIdField.fieldName;
            String aliasVarPrefix = "fm" + capitalize(sanitizeVarName(aliasName));
            String entityVar = sanitizeVarName(aliasName);
            String byIdVar = sanitizeVarName(aliasName) + "ById";
            String parentVar = sanitizeVarName(aliasInfo.parentAlias);
            String linkFieldName = aliasInfo.linkField.getSimpleName().toString();
            String linkFieldVar = "f" + capitalize(sanitizeVarName(aliasInfo.parentAlias)) + capitalize(linkFieldName);

            out.println();
            out.println("            // Process relationship: " + aliasName + " (" + className + ")");
            out.println("            Object " + entityVar + "Id = row.get(\"" + aliasName + "." + idFieldName + "\");");
            out.println("            if (" + entityVar + "Id != null && !" + byIdVar + ".containsKey(" + entityVar + "Id)) {");
            out.println("                " + className + " " + entityVar + " = new " + className + "();");

            // Apply field mappings for this alias
            List<RecordedField> aliasFields = fieldsByAlias.get(aliasName);
            if (aliasFields != null) {
                for (RecordedField field : aliasFields) {
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

    private String extractAliasFromFieldAlias(String fieldAlias) {
        int lastDot = fieldAlias.lastIndexOf('.');
        return lastDot > 0 ? fieldAlias.substring(0, lastDot) : fieldAlias;
    }

    private String extractFieldNameFromAlias(String fieldAlias) {
        int lastDot = fieldAlias.lastIndexOf('.');
        return lastDot >= 0 ? fieldAlias.substring(lastDot + 1) : fieldAlias;
    }

    // === Helper methods ===

    private String resolveAliases(String expr, String alias, String linkAlias) {
        String result = expr.replace("{this}", "{" + alias + "}");
        if (linkAlias != null) {
            result = result.replace("{link}", "{" + linkAlias + "}");
        }
        return result;
    }

    private String getLinkFieldName(TypeElement typeElement) {
        // Use table name from @Table annotation, fall back to class name
        Table tableAnn = typeElement.getAnnotation(Table.class);
        String name = tableAnn != null ? tableAnn.value() : typeElement.getSimpleName().toString();
        return name + "_id";
    }

    private String getForeignKeyField(VariableElement field, TypeElement relatedType) {
        FieldName fieldNameAnn = field.getAnnotation(FieldName.class);
        if (fieldNameAnn != null) {
            return fieldNameAnn.value();
        }
        return field.getSimpleName().toString() + "_id";
    }

    private boolean isEmbedded(VariableElement field) {
        return field.getAnnotation(Embedded.class) != null;
    }

    private String getEmbeddedPrefix(VariableElement field) {
        Embedded embedded = field.getAnnotation(Embedded.class);
        if (embedded != null && !embedded.prefix().isEmpty()) {
            return embedded.prefix();
        }
        return field.getSimpleName().toString() + "_";
    }

    private FieldInfo findIdField(TypeElement typeElement, String alias) {
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) continue;
            VariableElement field = (VariableElement) enclosed;
            if (field.getAnnotation(Id.class) != null) {
                String columnName = getColumnName(field);
                String boxedType = getBoxedTypeName(field.asType());
                return new FieldInfo(field.getSimpleName().toString(), columnName, boxedType, true, alias);
            }
        }
        TypeMirror superclass = typeElement.getSuperclass();
        if (superclass.getKind() != TypeKind.NONE) {
            Element superElement = typeUtils.asElement(superclass);
            if (superElement instanceof TypeElement) {
                return findIdField((TypeElement) superElement, alias);
            }
        }
        return null;
    }

    private String getPackageName(TypeElement element) {
        PackageElement pkg = elementUtils.getPackageOf(element);
        return pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();
    }

    private String getColumnName(VariableElement field) {
        FieldName ann = field.getAnnotation(FieldName.class);
        return ann != null ? ann.value() : field.getSimpleName().toString();
    }

    private boolean isCollectionType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return false;
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        String name = element.getQualifiedName().toString();
        return name.equals("java.util.List") || name.equals("java.util.Set") ||
               name.equals("java.util.Collection");
    }

    private TypeMirror getCollectionComponentType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return null;
        DeclaredType declaredType = (DeclaredType) type;
        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        return typeArgs.isEmpty() ? null : typeArgs.get(0);
    }

    private boolean hasTableAnnotation(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return false;
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        return element.getAnnotation(Table.class) != null;
    }

    private String getBoxedTypeName(TypeMirror type) {
        switch (type.getKind()) {
            case BOOLEAN: return "Boolean";
            case BYTE: return "Byte";
            case SHORT: return "Short";
            case INT: return "Integer";
            case LONG: return "Long";
            case FLOAT: return "Float";
            case DOUBLE: return "Double";
            case CHAR: return "Character";
            case DECLARED:
                return ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().toString();
            default:
                return "Object";
        }
    }

    private String simplifyTypeName(String fullName) {
        if (fullName.startsWith("java.lang.")) {
            return fullName.substring("java.lang.".length());
        }
        if (fullName.equals("int")) return "Integer";
        if (fullName.equals("long")) return "Long";
        if (fullName.equals("boolean")) return "Boolean";
        if (fullName.equals("double")) return "Double";
        if (fullName.equals("float")) return "Float";
        if (fullName.equals("short")) return "Short";
        if (fullName.equals("byte")) return "Byte";
        if (fullName.equals("char")) return "Character";
        return fullName;
    }

    private String extractFieldName(String alias) {
        int lastDot = alias.lastIndexOf('.');
        return lastDot >= 0 ? alias.substring(lastDot + 1) : alias;
    }

    private String extractColumnName(String expression) {
        int dotPos = expression.lastIndexOf('}');
        if (dotPos >= 0 && dotPos + 1 < expression.length()) {
            return expression.substring(dotPos + 2);
        }
        return expression;
    }

    private String getLastPart(String alias) {
        int lastDot = alias.lastIndexOf('.');
        return lastDot >= 0 ? alias.substring(lastDot + 1) : alias;
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String escapeJava(String s) {
        if (s == null) return null;
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // === Data classes ===

    private static class FieldInfo {
        final String fieldName;
        final String columnName;
        final String boxedType;
        final boolean isId;
        final String alias;

        FieldInfo(String fieldName, String columnName, String boxedType, boolean isId, String alias) {
            this.fieldName = fieldName;
            this.columnName = columnName;
            this.boxedType = boxedType;
            this.isId = isId;
            this.alias = alias;
        }
    }

    private static class AliasInfo {
        final String alias;
        final TypeElement typeElement;
        final String parentAlias;
        final VariableElement linkField;
        final boolean isEmbedded;

        AliasInfo(String alias, TypeElement typeElement, String parentAlias, VariableElement linkField) {
            this(alias, typeElement, parentAlias, linkField, false);
        }

        AliasInfo(String alias, TypeElement typeElement, String parentAlias, VariableElement linkField, boolean isEmbedded) {
            this.alias = alias;
            this.typeElement = typeElement;
            this.parentAlias = parentAlias;
            this.linkField = linkField;
            this.isEmbedded = isEmbedded;
        }
    }
}
