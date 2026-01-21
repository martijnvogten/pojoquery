package org.pojoquery.typemodel;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

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

import org.pojoquery.AnnotationHelper;

/**
 * TypeModel implementation for annotation processing.
 * Wraps a TypeElement or TypeMirror for compile-time type introspection.
 */
public class ElementTypeModel implements TypeModel {

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
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        if (typeElement != null) {
            return typeElement.getAnnotation(annotationType);
        }
        return null;
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return getAnnotation(annotationType) != null;
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
    public boolean hasTableMapping() {
        return AnnotationHelper.hasTableAnnotation(this);
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
        if (obj instanceof ElementTypeModel) {
            return types.isSameType(this.typeMirror, ((ElementTypeModel) obj).typeMirror);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getQualifiedName().hashCode();
    }
}
