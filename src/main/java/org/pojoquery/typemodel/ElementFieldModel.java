package org.pojoquery.typemodel;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * FieldModel implementation for annotation processing.
 * Wraps a VariableElement for compile-time field introspection.
 * 
 * <p>Extends {@link AbstractAnnotatedElement} to support immutable annotation
 * transforms. Use {@link #withAddedAnnotation} to create a new instance with
 * additional canonical annotations.
 */
public class ElementFieldModel extends AbstractAnnotatedElement<FieldModel> implements FieldModel {

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
        super();
        this.variableElement = variableElement;
        this.declaringType = declaringType;
        this.elements = elements;
        this.types = types;
    }

    /**
     * Private constructor for creating instances with added annotations.
     */
    private ElementFieldModel(VariableElement variableElement, TypeModel declaringType,
                              Elements elements, Types types,
                              Map<Class<?>, AnnotationModel> addedAnnotations) {
        super(addedAnnotations);
        this.variableElement = variableElement;
        this.declaringType = declaringType;
        this.elements = elements;
        this.types = types;
    }

    // ========== AbstractAnnotatedElement implementation ==========

    @Override
    protected AnnotationModel[] getSourceAnnotations() {
        return variableElement.getAnnotationMirrors().stream()
            .map(am -> new ElementAnnotationModel(am, elements, types))
            .toArray(AnnotationModel[]::new);
    }

    // ========== FieldModel implementation ==========

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
    public boolean isStatic() {
        return variableElement.getModifiers().contains(Modifier.STATIC);
    }

    @Override
    public boolean isTransient() {
        // Check Java transient modifier
        return variableElement.getModifiers().contains(Modifier.TRANSIENT);
    }

    @Override
    public List<TypeModel> getTypeValuesFromAnnotation(AnnotationModel annotationModel, String attributeName) {
        return annotationModel.getClassValues(attributeName);
    }

    @Override
    protected FieldModel withAnnotations(Map<Class<?>, AnnotationModel> annotations) {
        return new ElementFieldModel(variableElement, declaringType, elements, types, annotations);
    }

    // ========== Compile-time specific methods ==========

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
        if (!(obj instanceof ElementFieldModel other)) return false;
        return variableElement.equals(other.variableElement) 
            && getAddedAnnotations().equals(other.getAddedAnnotations());
    }

    @Override
    public int hashCode() {
        return Objects.hash(variableElement, getAddedAnnotations());
    }
}
