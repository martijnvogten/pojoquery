package org.pojoquery.typemodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * TypeModel implementation for annotation processing.
 * Wraps a TypeElement or TypeMirror for compile-time type introspection.
 * 
 * <p>Extends {@link AbstractAnnotatedElement} to support immutable annotation
 * transforms. Use {@link #withAddedAnnotation} to create a new instance with
 * additional canonical annotations.
 */
public class ElementTypeModel extends AbstractAnnotatedElement<TypeModel> implements TypeModel {

    private final TypeElement typeElement;
    private final TypeMirror typeMirror;
    private final Elements elements;
    private final Types types;

    /**
     * Creates an ElementTypeModel from a TypeElement.
     *
     * @param typeElement the type element to wrap
     * @param elements    the Elements utility from the processing environment
     * @param types       the Types utility from the processing environment
     */
    public ElementTypeModel(TypeElement typeElement, Elements elements, Types types) {
        super();
        this.typeElement = typeElement;
        this.typeMirror = typeElement.asType();
        this.elements = elements;
        this.types = types;
    }

    /**
     * Creates an ElementTypeModel from a TypeMirror.
     *
     * @param typeMirror the type mirror to wrap
     * @param elements   the Elements utility from the processing environment
     * @param types      the Types utility from the processing environment
     */
    public ElementTypeModel(TypeMirror typeMirror, Elements elements, Types types) {
        super();
        this.typeMirror = typeMirror;
        this.elements = elements;
        this.types = types;

        // Extract TypeElement if this is a declared type
        if (typeMirror.getKind() == TypeKind.DECLARED) {
            this.typeElement = (TypeElement) ((DeclaredType) typeMirror).asElement();
        } else {
            this.typeElement = null;
        }
    }

    /**
     * Private constructor for creating instances with added annotations.
     */
    private ElementTypeModel(TypeElement typeElement, TypeMirror typeMirror,
                             Elements elements, Types types,
                             Map<Class<?>, AnnotationModel> addedAnnotations) {
        super(addedAnnotations);
        this.typeElement = typeElement;
        this.typeMirror = typeMirror;
        this.elements = elements;
        this.types = types;
    }

    // ========== AbstractAnnotatedElement implementation ==========

    @Override
    protected AnnotationModel[] getSourceAnnotations() {
        if (typeElement == null) return new AnnotationModel[0];
        return typeElement.getAnnotationMirrors().stream()
            .map(am -> new ElementAnnotationModel(am, elements, types))
            .toArray(AnnotationModel[]::new);
    }

    // ========== TypeModel implementation ==========

    @Override
    public String getQualifiedName() {
        if (typeElement != null) {
            return typeElement.getQualifiedName().toString();
        }
        return typeMirror.toString();
    }

    @Override
    public String getSimpleName() {
        if (typeElement != null) {
            return typeElement.getSimpleName().toString();
        }
        String qualifiedName = typeMirror.toString();
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    @Override
    public TypeModel getSuperclass() {
        if (typeElement == null) {
            return null;
        }
        TypeMirror superclass = typeElement.getSuperclass();
        if (superclass.getKind() == TypeKind.NONE || superclass.getKind() == TypeKind.NULL) {
            return null;
        }
        // Don't return Object as superclass
        if (superclass.toString().equals("java.lang.Object")) {
            return null;
        }
        return new ElementTypeModel(superclass, elements, types);
    }

    @Override
    public List<FieldModel> getDeclaredFields() {
        List<FieldModel> fields = new ArrayList<>();
        if (typeElement != null) {
            for (Element enclosed : typeElement.getEnclosedElements()) {
                if (enclosed.getKind() == ElementKind.FIELD) {
                    VariableElement field = (VariableElement) enclosed;
                    fields.add(new ElementFieldModel(field, this, elements, types));
                }
            }
        }
        return fields;
    }

    @Override
    public boolean isPrimitive() {
        return typeMirror.getKind().isPrimitive();
    }

    @Override
    public boolean isEnum() {
        if (typeElement != null) {
            return typeElement.getKind() == ElementKind.ENUM;
        }
        return false;
    }

    @Override
    public boolean isArray() {
        return typeMirror.getKind() == TypeKind.ARRAY;
    }

    @Override
    public TypeModel getArrayComponentType() {
        if (typeMirror.getKind() == TypeKind.ARRAY) {
            TypeMirror componentType = ((ArrayType) typeMirror).getComponentType();
            return new ElementTypeModel(componentType, elements, types);
        }
        return null;
    }

    @Override
    public TypeModel getTypeArgument() {
        if (typeMirror.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) typeMirror;
            List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
            if (!typeArgs.isEmpty()) {
                return new ElementTypeModel(typeArgs.get(0), elements, types);
            }
        }
        return null;
    }

    @Override
    public boolean isMap() {
        TypeElement mapElement = elements.getTypeElement("java.util.Map");
        if (mapElement == null) {
            return false;
        }
        TypeMirror mapType = types.erasure(mapElement.asType());
        TypeMirror thisErased = types.erasure(typeMirror);
        return types.isAssignable(thisErased, mapType);
    }

    @Override
    public boolean isSameType(TypeModel other) {
        if (other instanceof ElementTypeModel) {
            return types.isSameType(this.typeMirror, ((ElementTypeModel) other).typeMirror);
        }
        if (other instanceof ReflectionTypeModel) {
            return getQualifiedName().equals(other.getQualifiedName());
        }
        return false;
    }

    @Override
    public boolean isSameType(Class<?> other) {
        return getQualifiedName().equals(other.getName());
    }

    @Override
    public List<TypeModel> getTypeValuesFromAnnotation(AnnotationModel annotationModel, String attributeName) {
        return annotationModel.getClassValues(attributeName);
    }

    // ========== Factory method for annotation transforms ==========

    @Override
    protected TypeModel withAnnotations(Map<Class<?>, AnnotationModel> annotations) {
        return new ElementTypeModel(typeElement, typeMirror, elements, types, annotations);
    }

    // ========== Compile-time specific methods ==========

    /**
     * Returns the underlying TypeElement, or null if this wraps a non-declared type.
     */
    public TypeElement getTypeElement() {
        return typeElement;
    }

    /**
     * Returns the underlying TypeMirror.
     */
    public TypeMirror getTypeMirror() {
        return typeMirror;
    }

    @Override
    public String toString() {
        return "ElementTypeModel[" + getQualifiedName() + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ElementTypeModel other)) return false;
        return types.isSameType(this.typeMirror, other.typeMirror)
            && getAddedAnnotations().equals(other.getAddedAnnotations());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getQualifiedName(), getAddedAnnotations());
    }
}
