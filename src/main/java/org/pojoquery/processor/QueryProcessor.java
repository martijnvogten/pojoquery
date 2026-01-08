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

import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Select;
import org.pojoquery.annotations.Table;
import org.pojoquery.annotations.Transient;

/**
 * Annotation processor that generates type-safe query builders for PojoQuery entities.
 * Supports entity relationships (one-to-one, one-to-many).
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
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate query classes: " + e.getMessage(), element);
            }
        }
        return true;
    }

    private void processEntity(TypeElement entity) throws IOException {
        GenerateQuery annotation = entity.getAnnotation(GenerateQuery.class);
        String querySuffix = annotation.querySuffix();
        String fieldsSuffix = annotation.fieldsSuffix();

        String packageName = getPackageName(entity);
        String entityName = entity.getSimpleName().toString();
        String fieldsClassName = entityName + fieldsSuffix;
        String queryClassName = entityName + querySuffix;

        Table tableAnnotation = entity.getAnnotation(Table.class);
        if (tableAnnotation == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@GenerateQuery requires @Table annotation", entity);
            return;
        }
        String tableName = tableAnnotation.value();
        String tableSchema = tableAnnotation.schema();

        // Collect entity info including relationships
        EntityInfo entityInfo = new EntityInfo(tableName, tableSchema, entityName,
            entity.getQualifiedName().toString());
        collectEntityInfo(entity, entityInfo, tableName, new HashSet<>());

        // Generate classes
        generateFieldsClass(packageName, entityName, fieldsClassName, entityInfo);
        generateQueryClass(packageName, entityName, queryClassName, fieldsClassName, entityInfo);
    }

    private void collectEntityInfo(TypeElement typeElement, EntityInfo entityInfo,
                                   String alias, Set<String> visitedTypes) {
        // Prevent infinite recursion
        String typeName = typeElement.getQualifiedName().toString();
        if (visitedTypes.contains(typeName)) {
            return;
        }
        visitedTypes.add(typeName);

        // Process superclass first
        TypeMirror superclass = typeElement.getSuperclass();
        if (superclass.getKind() != TypeKind.NONE) {
            Element superElement = typeUtils.asElement(superclass);
            if (superElement instanceof TypeElement) {
                TypeElement superType = (TypeElement) superElement;
                if (!superType.getQualifiedName().toString().equals("java.lang.Object")) {
                    collectEntityInfo(superType, entityInfo, alias, visitedTypes);
                }
            }
        }

        // Process fields
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.FIELD) {
                continue;
            }

            VariableElement field = (VariableElement) enclosedElement;

            if (field.getModifiers().contains(Modifier.STATIC)) continue;
            if (field.getAnnotation(Transient.class) != null) continue;
            if (field.getAnnotation(Select.class) != null) continue;

            // Handle @Embedded
            Embedded embedded = field.getAnnotation(Embedded.class);
            if (embedded != null) {
                TypeElement embeddedType = getTypeElement(field.asType());
                if (embeddedType != null) {
                    String prefix = embedded.prefix();
                    collectEmbeddedFields(embeddedType, entityInfo, alias, prefix);
                }
                continue;
            }

            // Check for relationships
            TypeMirror fieldType = field.asType();
            Link linkAnn = field.getAnnotation(Link.class);

            if (isCollectionType(fieldType)) {
                // One-to-many relationship
                TypeMirror componentType = getCollectionComponentType(fieldType);
                if (componentType != null && hasTableAnnotation(componentType)) {
                    RelationshipInfo rel = createOneToManyRelationship(field, componentType, alias, linkAnn);
                    if (rel != null) {
                        entityInfo.relationships.add(rel);
                        // Recursively collect related entity fields
                        TypeElement relatedType = getTypeElement(componentType);
                        if (relatedType != null) {
                            collectRelatedEntityInfo(relatedType, rel, visitedTypes);
                        }
                    }
                }
                continue;
            }

            if (hasTableAnnotation(fieldType)) {
                // One-to-one relationship
                RelationshipInfo rel = createOneToOneRelationship(field, fieldType, alias, linkAnn);
                if (rel != null) {
                    entityInfo.relationships.add(rel);
                    // Recursively collect related entity fields
                    TypeElement relatedType = getTypeElement(fieldType);
                    if (relatedType != null) {
                        collectRelatedEntityInfo(relatedType, rel, visitedTypes);
                    }
                }
                continue;
            }

            // Regular field
            String fieldName = field.getSimpleName().toString();
            String columnName = getColumnName(field);
            String javaType = getJavaType(fieldType);
            String boxedType = getBoxedType(fieldType);
            boolean isId = field.getAnnotation(Id.class) != null;

            FieldInfo fieldInfo = new FieldInfo(fieldName, columnName, javaType, boxedType,
                isId, fieldType, alias);
            entityInfo.fields.add(fieldInfo);

            if (isId) {
                entityInfo.idField = fieldInfo;
            }
        }
    }

    private void collectEmbeddedFields(TypeElement embeddedType, EntityInfo entityInfo,
                                       String alias, String prefix) {
        for (Element enclosedElement : embeddedType.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.FIELD) continue;

            VariableElement field = (VariableElement) enclosedElement;
            if (field.getModifiers().contains(Modifier.STATIC)) continue;
            if (field.getAnnotation(Transient.class) != null) continue;

            String fieldName = field.getSimpleName().toString();
            String columnName = prefix + getColumnName(field);
            TypeMirror fieldType = field.asType();

            FieldInfo fieldInfo = new FieldInfo(fieldName, columnName, getJavaType(fieldType),
                getBoxedType(fieldType), false, fieldType, alias);
            entityInfo.fields.add(fieldInfo);
        }
    }

    private void collectRelatedEntityInfo(TypeElement relatedType, RelationshipInfo rel,
                                          Set<String> visitedTypes) {
        String typeName = relatedType.getQualifiedName().toString();
        if (visitedTypes.contains(typeName)) {
            return;
        }

        // Find ID field and collect fields
        for (Element enclosedElement : relatedType.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.FIELD) continue;

            VariableElement field = (VariableElement) enclosedElement;
            if (field.getModifiers().contains(Modifier.STATIC)) continue;
            if (field.getAnnotation(Transient.class) != null) continue;
            if (field.getAnnotation(Select.class) != null) continue;

            // Skip relationships in related entities (only one level deep for now)
            TypeMirror fieldType = field.asType();
            if (isCollectionType(fieldType) || hasTableAnnotation(fieldType)) {
                continue;
            }

            String fieldName = field.getSimpleName().toString();
            String columnName = getColumnName(field);
            String javaType = getJavaType(fieldType);
            String boxedType = getBoxedType(fieldType);
            boolean isId = field.getAnnotation(Id.class) != null;

            FieldInfo fieldInfo = new FieldInfo(fieldName, columnName, javaType, boxedType,
                isId, fieldType, rel.alias);
            rel.fields.add(fieldInfo);

            if (isId) {
                rel.idField = fieldInfo;
            }
        }

        // Process superclass
        TypeMirror superclass = relatedType.getSuperclass();
        if (superclass.getKind() != TypeKind.NONE) {
            Element superElement = typeUtils.asElement(superclass);
            if (superElement instanceof TypeElement) {
                TypeElement superType = (TypeElement) superElement;
                if (!superType.getQualifiedName().toString().equals("java.lang.Object")) {
                    collectRelatedEntityInfo(superType, rel, new HashSet<>(visitedTypes));
                }
            }
        }
    }

    private RelationshipInfo createOneToOneRelationship(VariableElement field, TypeMirror relatedType,
                                                        String parentAlias, Link linkAnn) {
        TypeElement relatedElement = getTypeElement(relatedType);
        if (relatedElement == null) return null;

        Table relatedTable = relatedElement.getAnnotation(Table.class);
        if (relatedTable == null) return null;

        String fieldName = field.getSimpleName().toString();
        String alias = fieldName;
        String tableName = relatedTable.value();
        String schema = relatedTable.schema();

        // Determine foreign key column (default: fieldName_id or from @Link)
        String foreignKeyColumn = fieldName + "_id";
        if (linkAnn != null && !Link.NONE.equals(linkAnn.linkfield())) {
            foreignKeyColumn = linkAnn.linkfield();
        }

        // Find ID field in related entity
        String relatedIdColumn = "id";
        for (Element enclosed : relatedElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD &&
                enclosed.getAnnotation(Id.class) != null) {
                relatedIdColumn = getColumnName((VariableElement) enclosed);
                break;
            }
        }

        RelationshipInfo rel = new RelationshipInfo();
        rel.fieldName = fieldName;
        rel.alias = alias;
        rel.tableName = tableName;
        rel.schema = schema;
        rel.relatedTypeName = relatedElement.getQualifiedName().toString();
        rel.relatedSimpleName = relatedElement.getSimpleName().toString();
        rel.isCollection = false;
        rel.parentAlias = parentAlias;
        rel.foreignKeyColumn = foreignKeyColumn;
        rel.relatedIdColumn = relatedIdColumn;

        return rel;
    }

    private RelationshipInfo createOneToManyRelationship(VariableElement field, TypeMirror componentType,
                                                         String parentAlias, Link linkAnn) {
        TypeElement relatedElement = getTypeElement(componentType);
        if (relatedElement == null) return null;

        Table relatedTable = relatedElement.getAnnotation(Table.class);
        if (relatedTable == null) return null;

        String fieldName = field.getSimpleName().toString();
        String alias = fieldName;
        String tableName = relatedTable.value();
        String schema = relatedTable.schema();

        // For one-to-many, the FK is in the child table
        // Default: parentTableName_id or from @Link(foreignlinkfield)
        String foreignKeyColumn = parentAlias + "_id";
        if (linkAnn != null && !Link.NONE.equals(linkAnn.foreignlinkfield())) {
            foreignKeyColumn = linkAnn.foreignlinkfield();
        }

        // Find ID field in related entity
        String relatedIdColumn = "id";
        for (Element enclosed : relatedElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD &&
                enclosed.getAnnotation(Id.class) != null) {
                relatedIdColumn = getColumnName((VariableElement) enclosed);
                break;
            }
        }

        RelationshipInfo rel = new RelationshipInfo();
        rel.fieldName = fieldName;
        rel.alias = alias;
        rel.tableName = tableName;
        rel.schema = schema;
        rel.relatedTypeName = relatedElement.getQualifiedName().toString();
        rel.relatedSimpleName = relatedElement.getSimpleName().toString();
        rel.isCollection = true;
        rel.parentAlias = parentAlias;
        rel.foreignKeyColumn = foreignKeyColumn;
        rel.relatedIdColumn = relatedIdColumn;

        return rel;
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

    private TypeElement getTypeElement(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return null;
        return (TypeElement) typeUtils.asElement(type);
    }

    private String getColumnName(VariableElement field) {
        FieldName fieldNameAnn = field.getAnnotation(FieldName.class);
        if (fieldNameAnn != null) {
            return fieldNameAnn.value();
        }
        return field.getSimpleName().toString();
    }

    private String getJavaType(TypeMirror type) {
        if (type.getKind().isPrimitive()) return type.toString();
        if (type.getKind() == TypeKind.DECLARED) {
            return ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().toString();
        }
        return type.toString();
    }

    private String getBoxedType(TypeMirror type) {
        switch (type.getKind()) {
            case BOOLEAN: return "Boolean";
            case BYTE: return "Byte";
            case SHORT: return "Short";
            case INT: return "Integer";
            case LONG: return "Long";
            case FLOAT: return "Float";
            case DOUBLE: return "Double";
            case CHAR: return "Character";
            default: return getJavaType(type);
        }
    }

    private String getPackageName(TypeElement element) {
        PackageElement pkg = elementUtils.getPackageOf(element);
        return pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();
    }

    // === Code Generation ===

    private void generateFieldsClass(String packageName, String entityName,
                                     String className, EntityInfo entityInfo) throws IOException {
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
            out.println("    public static final String ALIAS = \"" + entityInfo.tableName + "\";");
            out.println();

            // Main entity fields
            for (FieldInfo field : entityInfo.fields) {
                out.println("    public static final QueryField<" + entityName + ", " + field.boxedType + "> " +
                    field.fieldName + " = new QueryField<>(ALIAS, \"" + field.columnName + "\", \"" +
                    field.fieldName + "\", " + field.boxedType + ".class);");
            }
            out.println();

            // Nested classes for relationships
            for (RelationshipInfo rel : entityInfo.relationships) {
                generateRelationshipFieldsClass(out, entityName, rel);
            }

            out.println("    private " + className + "() {}");
            out.println("}");
        }
    }

    private void generateRelationshipFieldsClass(PrintWriter out, String rootEntityName,
                                                 RelationshipInfo rel) {
        String innerClassName = capitalize(rel.fieldName);

        out.println("    /** Fields for the {@code " + rel.fieldName + "} relationship */");
        out.println("    public static final class " + innerClassName + " {");
        out.println("        public static final String ALIAS = \"" + rel.alias + "\";");
        out.println();

        for (FieldInfo field : rel.fields) {
            out.println("        public static final QueryField<" + rootEntityName + ", " +
                field.boxedType + "> " + field.fieldName +
                " = new QueryField<>(ALIAS, \"" + field.columnName + "\", \"" +
                rel.fieldName + "." + field.fieldName + "\", " + field.boxedType + ".class);");
        }

        out.println("        private " + innerClassName + "() {}");
        out.println("    }");
        out.println();
    }

    private void generateQueryClass(String packageName, String entityName, String className,
                                    String fieldsClassName, EntityInfo entityInfo) throws IOException {
        String qualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
        JavaFileObject fileObject = filer.createSourceFile(qualifiedName);

        boolean hasRelationships = !entityInfo.relationships.isEmpty();
        boolean hasCollections = entityInfo.relationships.stream().anyMatch(r -> r.isCollection);

        try (PrintWriter out = new PrintWriter(fileObject.openWriter())) {
            if (!packageName.isEmpty()) {
                out.println("package " + packageName + ";");
                out.println();
            }

            out.println("import java.sql.ResultSet;");
            out.println("import java.sql.SQLException;");
            if (hasRelationships) {
                out.println("import java.util.ArrayList;");
                out.println("import java.util.LinkedHashMap;");
                out.println("import java.util.List;");
                out.println("import java.util.Map;");
            }
            out.println();
            out.println("import org.pojoquery.DbContext;");
            out.println("import org.pojoquery.SqlExpression;");
            out.println("import org.pojoquery.pipeline.SqlQuery.JoinType;");
            out.println("import org.pojoquery.typedquery.TypedQuery;");
            out.println();
            out.println("public class " + className + " extends TypedQuery<" + entityName + ", " + className + "> {");
            out.println();
            out.println("    private static final String TABLE_NAME = \"" + entityInfo.tableName + "\";");
            if (entityInfo.tableSchema != null && !entityInfo.tableSchema.isEmpty()) {
                out.println("    private static final String SCHEMA = \"" + entityInfo.tableSchema + "\";");
            } else {
                out.println("    private static final String SCHEMA = null;");
            }
            out.println();

            // Entity cache for deduplication
            if (hasRelationships) {
                out.println("    private final Map<Object, " + entityName + "> entityCache = new LinkedHashMap<>();");
                for (RelationshipInfo rel : entityInfo.relationships) {
                    out.println("    private final Map<Object, " + rel.relatedSimpleName +
                        "> " + rel.fieldName + "Cache = new LinkedHashMap<>();");
                }
                out.println();
            }

            // Constructors
            out.println("    public " + className + "() {");
            out.println("        super(SCHEMA, TABLE_NAME);");
            out.println("    }");
            out.println();
            out.println("    public " + className + "(DbContext dbContext) {");
            out.println("        super(dbContext, SCHEMA, TABLE_NAME);");
            out.println("    }");
            out.println();

            // initializeQuery
            out.println("    @Override");
            out.println("    protected void initializeQuery() {");

            // Add main entity fields
            for (FieldInfo field : entityInfo.fields) {
                out.println("        query.addField(SqlExpression.sql(\"{\" + TABLE_NAME + \"}.\" + \"" +
                    field.columnName + "\"), \"" + entityInfo.tableName + "." + field.columnName + "\");");
            }

            // Add relationships (joins and fields)
            for (RelationshipInfo rel : entityInfo.relationships) {
                out.println();
                out.println("        // Join " + rel.fieldName + " (" +
                    (rel.isCollection ? "one-to-many" : "one-to-one") + ")");

                if (rel.isCollection) {
                    // One-to-many: parent.id = child.parent_id
                    out.println("        query.addJoin(JoinType.LEFT, " +
                        (rel.schema != null && !rel.schema.isEmpty() ? "\"" + rel.schema + "\"" : "null") +
                        ", \"" + rel.tableName + "\", \"" + rel.alias + "\", " +
                        "SqlExpression.sql(\"{\" + TABLE_NAME + \"}.\" + \"" +
                        (entityInfo.idField != null ? entityInfo.idField.columnName : "id") +
                        "\" + \" = {" + rel.alias + "}.\" + \"" + rel.foreignKeyColumn + "\"));");
                } else {
                    // One-to-one: parent.fk_id = child.id
                    out.println("        query.addJoin(JoinType.LEFT, " +
                        (rel.schema != null && !rel.schema.isEmpty() ? "\"" + rel.schema + "\"" : "null") +
                        ", \"" + rel.tableName + "\", \"" + rel.alias + "\", " +
                        "SqlExpression.sql(\"{\" + TABLE_NAME + \"}.\" + \"" + rel.foreignKeyColumn +
                        "\" + \" = {" + rel.alias + "}.\" + \"" + rel.relatedIdColumn + "\"));");
                }

                // Add fields from related entity
                for (FieldInfo field : rel.fields) {
                    out.println("        query.addField(SqlExpression.sql(\"{" + rel.alias + "}.\" + \"" +
                        field.columnName + "\"), \"" + rel.alias + "." + field.columnName + "\");");
                }
            }
            out.println("    }");
            out.println();

            // Generate mapRow or processResults depending on relationships
            if (hasRelationships) {
                generateMapRowWithRelationships(out, entityName, entityInfo);
                generateProcessResultsMethod(out, entityName, entityInfo);
            } else {
                generateSimpleMapRow(out, entityName, entityInfo);
            }

            // getEntityClass
            out.println("    @Override");
            out.println("    protected Class<" + entityName + "> getEntityClass() {");
            out.println("        return " + entityName + ".class;");
            out.println("    }");
            out.println();

            // findById
            if (entityInfo.idField != null) {
                out.println("    public " + entityName + " findById(javax.sql.DataSource dataSource, " +
                    entityInfo.idField.boxedType + " id) {");
                out.println("        return where(" + fieldsClassName + "." +
                    entityInfo.idField.fieldName + ").is(id).first(dataSource);");
                out.println("    }");
                out.println();
                out.println("    public " + entityName + " findById(java.sql.Connection connection, " +
                    entityInfo.idField.boxedType + " id) {");
                out.println("        return where(" + fieldsClassName + "." +
                    entityInfo.idField.fieldName + ").is(id).first(connection);");
                out.println("    }");
                out.println();
            }

            // Generate helper methods
            generateResultSetHelpers(out, entityInfo);

            out.println("}");
        }
    }

    private void generateSimpleMapRow(PrintWriter out, String entityName, EntityInfo entityInfo) {
        out.println("    @Override");
        out.println("    protected " + entityName + " mapRow(ResultSet rs) throws SQLException {");
        out.println("        " + entityName + " entity = new " + entityName + "();");
        for (FieldInfo field : entityInfo.fields) {
            String getter = getResultSetGetter(field);
            out.println("        entity." + field.fieldName + " = " + getter +
                "(rs, \"" + entityInfo.tableName + "." + field.columnName + "\");");
        }
        out.println("        return entity;");
        out.println("    }");
        out.println();
    }

    private void generateMapRowWithRelationships(PrintWriter out, String entityName, EntityInfo entityInfo) {
        out.println("    @Override");
        out.println("    protected " + entityName + " mapRow(ResultSet rs) throws SQLException {");

        // Get/create main entity
        out.println("        // Get or create main entity");
        String idGetter = entityInfo.idField != null ?
            getResultSetGetter(entityInfo.idField) + "(rs, \"" + entityInfo.tableName + "." +
            entityInfo.idField.columnName + "\")" : "null";
        out.println("        Object entityId = " + idGetter + ";");
        out.println("        " + entityName + " entity = entityCache.get(entityId);");
        out.println("        if (entity == null) {");
        out.println("            entity = new " + entityName + "();");
        for (FieldInfo field : entityInfo.fields) {
            String getter = getResultSetGetter(field);
            out.println("            entity." + field.fieldName + " = " + getter +
                "(rs, \"" + entityInfo.tableName + "." + field.columnName + "\");");
        }
        // Initialize collections
        for (RelationshipInfo rel : entityInfo.relationships) {
            if (rel.isCollection) {
                out.println("            entity." + rel.fieldName + " = new ArrayList<>();");
            }
        }
        out.println("            entityCache.put(entityId, entity);");
        out.println("        }");
        out.println();

        // Process relationships
        for (RelationshipInfo rel : entityInfo.relationships) {
            out.println("        // Process " + rel.fieldName + " relationship");

            // Get related entity ID
            String relIdGetter = rel.idField != null ?
                getResultSetGetter(rel.idField) + "(rs, \"" + rel.alias + "." +
                rel.idField.columnName + "\")" : "null";
            out.println("        Object " + rel.fieldName + "Id = " + relIdGetter + ";");
            out.println("        if (" + rel.fieldName + "Id != null) {");
            out.println("            " + rel.relatedSimpleName + " " + rel.fieldName +
                " = " + rel.fieldName + "Cache.get(" + rel.fieldName + "Id);");
            out.println("            if (" + rel.fieldName + " == null) {");
            out.println("                " + rel.fieldName + " = new " + rel.relatedSimpleName + "();");

            for (FieldInfo field : rel.fields) {
                String getter = getResultSetGetter(field);
                out.println("                " + rel.fieldName + "." + field.fieldName + " = " +
                    getter + "(rs, \"" + rel.alias + "." + field.columnName + "\");");
            }

            out.println("                " + rel.fieldName + "Cache.put(" + rel.fieldName + "Id, " +
                rel.fieldName + ");");
            out.println("            }");

            if (rel.isCollection) {
                out.println("            if (!entity." + rel.fieldName + ".contains(" + rel.fieldName + ")) {");
                out.println("                entity." + rel.fieldName + ".add(" + rel.fieldName + ");");
                out.println("            }");
            } else {
                out.println("            entity." + rel.fieldName + " = " + rel.fieldName + ";");
            }
            out.println("        }");
            out.println();
        }

        out.println("        return entity;");
        out.println("    }");
        out.println();
    }

    private void generateProcessResultsMethod(PrintWriter out, String entityName, EntityInfo entityInfo) {
        out.println("    @Override");
        out.println("    public java.util.List<" + entityName + "> list(java.sql.Connection connection) {");
        out.println("        entityCache.clear();");
        for (RelationshipInfo rel : entityInfo.relationships) {
            out.println("        " + rel.fieldName + "Cache.clear();");
        }
        out.println("        super.list(connection);");
        out.println("        return new ArrayList<>(entityCache.values());");
        out.println("    }");
        out.println();
    }

    private String getResultSetGetter(FieldInfo field) {
        TypeMirror type = field.type;
        if (type.getKind().isPrimitive()) {
            switch (type.getKind()) {
                case BOOLEAN: return "getBoolean";
                case BYTE: return "getByte";
                case SHORT: return "getShort";
                case INT: return "getInt";
                case LONG: return "getLong";
                case FLOAT: return "getFloat";
                case DOUBLE: return "getDouble";
                case CHAR: return "getChar";
                default: return "getObject";
            }
        }

        switch (field.javaType) {
            case "java.lang.String": return "getString";
            case "java.lang.Boolean": return "getBooleanObject";
            case "java.lang.Byte": return "getByteObject";
            case "java.lang.Short": return "getShortObject";
            case "java.lang.Integer": return "getIntegerObject";
            case "java.lang.Long": return "getLongObject";
            case "java.lang.Float": return "getFloatObject";
            case "java.lang.Double": return "getDoubleObject";
            case "java.math.BigDecimal": return "getBigDecimal";
            case "java.math.BigInteger": return "getBigInteger";
            case "java.util.Date": return "getDate";
            case "java.sql.Date": return "getSqlDate";
            case "java.sql.Time": return "getSqlTime";
            case "java.sql.Timestamp": return "getSqlTimestamp";
            case "java.time.LocalDate": return "getLocalDate";
            case "java.time.LocalTime": return "getLocalTime";
            case "java.time.LocalDateTime": return "getLocalDateTime";
            case "java.time.Instant": return "getInstant";
            case "java.util.UUID": return "getUUID";
            case "byte[]": return "getBytes";
            default:
                if (isEnumType(field.type)) {
                    return "getEnum_" + getSimpleName(field.javaType);
                }
                return "getObject";
        }
    }

    private boolean isEnumType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return false;
        TypeElement element = (TypeElement) typeUtils.asElement(type);
        return element.getKind() == ElementKind.ENUM;
    }

    private String getSimpleName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void generateResultSetHelpers(PrintWriter out, EntityInfo entityInfo) {
        Set<String> neededHelpers = new HashSet<>();

        // Collect from main entity
        for (FieldInfo field : entityInfo.fields) {
            neededHelpers.add(getResultSetGetter(field));
        }

        // Collect from relationships
        for (RelationshipInfo rel : entityInfo.relationships) {
            for (FieldInfo field : rel.fields) {
                neededHelpers.add(getResultSetGetter(field));
            }
        }

        Map<String, String> enumTypes = new LinkedHashMap<>();

        // Generate needed helpers
        if (neededHelpers.contains("getBoolean") || neededHelpers.contains("getBooleanObject")) {
            out.println("    private static boolean getBoolean(ResultSet rs, String col) throws SQLException { return rs.getBoolean(col); }");
            out.println("    private static Boolean getBooleanObject(ResultSet rs, String col) throws SQLException { boolean v = rs.getBoolean(col); return rs.wasNull() ? null : v; }");
        }
        if (neededHelpers.contains("getByte") || neededHelpers.contains("getByteObject")) {
            out.println("    private static byte getByte(ResultSet rs, String col) throws SQLException { return rs.getByte(col); }");
            out.println("    private static Byte getByteObject(ResultSet rs, String col) throws SQLException { byte v = rs.getByte(col); return rs.wasNull() ? null : v; }");
        }
        if (neededHelpers.contains("getShort") || neededHelpers.contains("getShortObject")) {
            out.println("    private static short getShort(ResultSet rs, String col) throws SQLException { return rs.getShort(col); }");
            out.println("    private static Short getShortObject(ResultSet rs, String col) throws SQLException { short v = rs.getShort(col); return rs.wasNull() ? null : v; }");
        }
        if (neededHelpers.contains("getInt") || neededHelpers.contains("getIntegerObject")) {
            out.println("    private static int getInt(ResultSet rs, String col) throws SQLException { return rs.getInt(col); }");
            out.println("    private static Integer getIntegerObject(ResultSet rs, String col) throws SQLException { int v = rs.getInt(col); return rs.wasNull() ? null : v; }");
        }
        if (neededHelpers.contains("getLong") || neededHelpers.contains("getLongObject")) {
            out.println("    private static long getLong(ResultSet rs, String col) throws SQLException { return rs.getLong(col); }");
            out.println("    private static Long getLongObject(ResultSet rs, String col) throws SQLException { long v = rs.getLong(col); return rs.wasNull() ? null : v; }");
        }
        if (neededHelpers.contains("getFloat") || neededHelpers.contains("getFloatObject")) {
            out.println("    private static float getFloat(ResultSet rs, String col) throws SQLException { return rs.getFloat(col); }");
            out.println("    private static Float getFloatObject(ResultSet rs, String col) throws SQLException { float v = rs.getFloat(col); return rs.wasNull() ? null : v; }");
        }
        if (neededHelpers.contains("getDouble") || neededHelpers.contains("getDoubleObject")) {
            out.println("    private static double getDouble(ResultSet rs, String col) throws SQLException { return rs.getDouble(col); }");
            out.println("    private static Double getDoubleObject(ResultSet rs, String col) throws SQLException { double v = rs.getDouble(col); return rs.wasNull() ? null : v; }");
        }
        if (neededHelpers.contains("getString")) {
            out.println("    private static String getString(ResultSet rs, String col) throws SQLException { return rs.getString(col); }");
        }
        if (neededHelpers.contains("getBigDecimal")) {
            out.println("    private static java.math.BigDecimal getBigDecimal(ResultSet rs, String col) throws SQLException { return rs.getBigDecimal(col); }");
        }
        if (neededHelpers.contains("getBigInteger")) {
            out.println("    private static java.math.BigInteger getBigInteger(ResultSet rs, String col) throws SQLException { java.math.BigDecimal bd = rs.getBigDecimal(col); return bd != null ? bd.toBigInteger() : null; }");
        }
        if (neededHelpers.contains("getDate")) {
            out.println("    private static java.util.Date getDate(ResultSet rs, String col) throws SQLException { java.sql.Timestamp ts = rs.getTimestamp(col); return ts != null ? new java.util.Date(ts.getTime()) : null; }");
        }
        if (neededHelpers.contains("getSqlDate")) {
            out.println("    private static java.sql.Date getSqlDate(ResultSet rs, String col) throws SQLException { return rs.getDate(col); }");
        }
        if (neededHelpers.contains("getSqlTime")) {
            out.println("    private static java.sql.Time getSqlTime(ResultSet rs, String col) throws SQLException { return rs.getTime(col); }");
        }
        if (neededHelpers.contains("getSqlTimestamp")) {
            out.println("    private static java.sql.Timestamp getSqlTimestamp(ResultSet rs, String col) throws SQLException { return rs.getTimestamp(col); }");
        }
        if (neededHelpers.contains("getLocalDate")) {
            out.println("    private static java.time.LocalDate getLocalDate(ResultSet rs, String col) throws SQLException { java.sql.Date d = rs.getDate(col); return d != null ? d.toLocalDate() : null; }");
        }
        if (neededHelpers.contains("getLocalTime")) {
            out.println("    private static java.time.LocalTime getLocalTime(ResultSet rs, String col) throws SQLException { java.sql.Time t = rs.getTime(col); return t != null ? t.toLocalTime() : null; }");
        }
        if (neededHelpers.contains("getLocalDateTime")) {
            out.println("    private static java.time.LocalDateTime getLocalDateTime(ResultSet rs, String col) throws SQLException { java.sql.Timestamp ts = rs.getTimestamp(col); return ts != null ? ts.toLocalDateTime() : null; }");
        }
        if (neededHelpers.contains("getInstant")) {
            out.println("    private static java.time.Instant getInstant(ResultSet rs, String col) throws SQLException { java.sql.Timestamp ts = rs.getTimestamp(col); return ts != null ? ts.toInstant() : null; }");
        }
        if (neededHelpers.contains("getUUID")) {
            out.println("    private static java.util.UUID getUUID(ResultSet rs, String col) throws SQLException { Object o = rs.getObject(col); if (o == null) return null; if (o instanceof java.util.UUID) return (java.util.UUID) o; return java.util.UUID.fromString(o.toString()); }");
        }
        if (neededHelpers.contains("getBytes")) {
            out.println("    private static byte[] getBytes(ResultSet rs, String col) throws SQLException { return rs.getBytes(col); }");
        }
        if (neededHelpers.contains("getObject")) {
            out.println("    @SuppressWarnings(\"unchecked\") private static <T> T getObject(ResultSet rs, String col) throws SQLException { return (T) rs.getObject(col); }");
        }

        // Collect and generate enum helpers
        List<FieldInfo> allFields = new ArrayList<>(entityInfo.fields);
        for (RelationshipInfo rel : entityInfo.relationships) {
            allFields.addAll(rel.fields);
        }
        for (FieldInfo field : allFields) {
            String getter = getResultSetGetter(field);
            if (getter.startsWith("getEnum_")) {
                enumTypes.put(getter, field.javaType);
            }
        }
        for (Map.Entry<String, String> entry : enumTypes.entrySet()) {
            out.println("    private static " + entry.getValue() + " " + entry.getKey() +
                "(ResultSet rs, String col) throws SQLException { String v = rs.getString(col); return v != null ? " +
                entry.getValue() + ".valueOf(v) : null; }");
        }
    }

    // === Data Classes ===

    private static class EntityInfo {
        final String tableName;
        final String tableSchema;
        final String entityName;
        final String qualifiedName;
        final List<FieldInfo> fields = new ArrayList<>();
        final List<RelationshipInfo> relationships = new ArrayList<>();
        FieldInfo idField;

        EntityInfo(String tableName, String tableSchema, String entityName, String qualifiedName) {
            this.tableName = tableName;
            this.tableSchema = tableSchema;
            this.entityName = entityName;
            this.qualifiedName = qualifiedName;
        }
    }

    private static class FieldInfo {
        final String fieldName;
        final String columnName;
        final String javaType;
        final String boxedType;
        final boolean isId;
        final TypeMirror type;
        final String alias;

        FieldInfo(String fieldName, String columnName, String javaType, String boxedType,
                  boolean isId, TypeMirror type, String alias) {
            this.fieldName = fieldName;
            this.columnName = columnName;
            this.javaType = javaType;
            this.boxedType = boxedType;
            this.isId = isId;
            this.type = type;
            this.alias = alias;
        }
    }

    private static class RelationshipInfo {
        String fieldName;
        String alias;
        String tableName;
        String schema;
        String relatedTypeName;
        String relatedSimpleName;
        boolean isCollection;
        String parentAlias;
        String foreignKeyColumn;
        String relatedIdColumn;
        final List<FieldInfo> fields = new ArrayList<>();
        FieldInfo idField;
    }
}
