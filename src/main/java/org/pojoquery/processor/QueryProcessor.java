package org.pojoquery.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
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
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.PojoMetadata;
import org.pojoquery.typemodel.ElementTypeModel;
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

        List<TableMapping> tableMapping = PojoMetadata.determineTableMapping(entityType);
        if (tableMapping.size() == 0) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@GenerateQuery requires @Table annotation on entity or its superclasses", typeElement);
            return;
        }

        // Build RootNode from the entity type
        RootNode tree = AQTTransformer.buildQueryTreeForType(entityType);

        messager.printMessage(Diagnostic.Kind.NOTE,
            "Generating query classes for " + qualifiedName + " using FluentAQTCodeGenerator", typeElement);

        // Generate the query class using FluentAQTCodeGenerator
        generateQueryClassFromTree(packageName, entityName, queryClassName, tree);
    }

    /**
     * Generates the query class using FluentAQTCodeGenerator.
     * This approach uses RootNode from AbstractQueryTree.
     */
    private void generateQueryClassFromTree(String packageName, String entityName,
            String queryClassName, RootNode tree) throws IOException {

        String qualifiedName = packageName.isEmpty() ? queryClassName : packageName + "." + queryClassName;
        JavaFileObject fileObject = filer.createSourceFile(qualifiedName);

        try (PrintWriter out = new PrintWriter(fileObject.openWriter())) {
            FluentAQTCodeGenerator generator = new FluentAQTCodeGenerator();
            generator.generate(tree, packageName, entityName, queryClassName, out);
        }
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
}

