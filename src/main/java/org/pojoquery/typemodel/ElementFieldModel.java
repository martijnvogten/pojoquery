package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.pojoquery.AnnotationHelper;

/**
 * FieldModel implementation for annotation processing.
 * Wraps a VariableElement for compile-time field introspection.
 */
public class ElementFieldModel implements FieldModel {

    private final VariableElement variableElement;
    private final TypeModel declaringType;
    private final Elements elements;
    private final Types types;
    private TypeModel fieldType;

    /**
     * Creates an ElementFieldModel from a VariableElement.
     *
     * @param variableElement the variable element (field) to wrap
     * @param declaringType   the type that declares this field
     * @param elements        the Elements utility from the processing environment
     * @param types           the Types utility from the processing environment
     */
    public ElementFieldModel(VariableElement variableElement, TypeModel declaringType,
                             Elements elements, Types types) {
        this.variableElement = variableElement;
        this.declaringType = declaringType;
        this.elements = elements;
        this.types = types;
    }

    @Override
    public String getName() {
        return variableElement.getSimpleName().toString();
    }

    @Override
    public TypeModel getType() {
        if (fieldType == null) {
            TypeMirror typeMirror = variableElement.asType();
            fieldType = new ElementTypeModel(typeMirror, elements, types);
        }
        return fieldType;
    }

    @Override
    public TypeModel getDeclaringType() {
        return declaringType;
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return variableElement.getAnnotation(annotationType);
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return getAnnotation(annotationType) != null;
    }

    @Override
    public List<AnnotationModel> getAnnotationsByType(Class<? extends Annotation> annotationType) {
        List<AnnotationModel> result = new ArrayList<>();
        String targetTypeName = annotationType.getName();
        
        // Check for @Repeatable container
        Repeatable repeatable = annotationType.getAnnotation(Repeatable.class);
        String containerTypeName = repeatable != null ? repeatable.value().getName() : null;
        
        for (AnnotationMirror am : variableElement.getAnnotationMirrors()) {
            String annTypeName = am.getAnnotationType().toString();
            
            if (annTypeName.equals(targetTypeName)) {
                // Direct match
                result.add(new ElementAnnotationModel(am, elements, types));
            } else if (containerTypeName != null && annTypeName.equals(containerTypeName)) {
                // Container annotation - unwrap value()
                ElementAnnotationModel container = new ElementAnnotationModel(am, elements, types);
                result.addAll(container.getAnnotationValues("value"));
            }
        }
        return result;
    }

    @Override
    public AnnotationModel[] getAnnotations() {
        return variableElement.getAnnotationMirrors().stream()
            .map(am -> new ElementAnnotationModel(am, elements, types))
            .toArray(AnnotationModel[]::new);
    }

    @Override
    public boolean isStatic() {
        return variableElement.getModifiers().contains(Modifier.STATIC);
    }

    @Override
    public boolean isTransient() {
        // Check Java transient modifier
        return variableElement.getModifiers().contains(Modifier.TRANSIENT);
    }

    /**
     * Returns the underlying VariableElement.
     */
    public VariableElement getVariableElement() {
        return variableElement;
    }

    @Override
    public String toString() {
        return "ElementFieldModel[" + declaringType.getSimpleName() + "." + getName() + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof ElementFieldModel) {
            ElementFieldModel other = (ElementFieldModel) obj;
            return variableElement.equals(other.variableElement);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return variableElement.hashCode();
    }
}
