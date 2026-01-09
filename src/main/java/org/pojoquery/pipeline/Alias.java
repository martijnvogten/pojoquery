package org.pojoquery.pipeline;

import java.util.List;
import java.util.Map;

import java.lang.reflect.Field;

import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionFieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;

public class Alias {

	private String alias;
	private TypeModel resultType;
	private String parentAlias;
	private FieldModel linkField;
	private FieldModel otherField = null;
	private List<FieldModel> idFields;
	private List<String> subClassAliases;
	private boolean isLinkedValue;
	private boolean isASubClass = false;
	private boolean isEmbedded = false;
	private boolean isSingleTableInheritance = false;
	private String discriminatorColumn;
	private Map<String, TypeModel> discriminatorValues;

	public Alias(String alias, TypeModel resultType, String parentAlias, FieldModel linkField, List<FieldModel> idFields) {
		this.alias = alias;
		this.resultType = resultType;
		this.parentAlias = parentAlias;
		this.linkField = linkField;
		this.idFields = idFields;
	}

	public List<FieldModel> getIdFields() {
		return idFields;
	}

	/**
	 * Returns the ID fields as reflection Field objects.
	 * @throws IllegalStateException if any field is not a ReflectionFieldModel
	 */
	public List<Field> getIdReflectionFields() {
		if (idFields == null) return null;
		List<Field> result = new java.util.ArrayList<>();
		for (FieldModel fm : idFields) {
			if (fm instanceof ReflectionFieldModel) {
				result.add(((ReflectionFieldModel) fm).getReflectionField());
			} else {
				throw new IllegalStateException("Cannot get reflection field from non-reflection field model: " + fm);
			}
		}
		return result;
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	public TypeModel getResultType() {
		return resultType;
	}

	/**
	 * Returns the runtime Class for this alias.
	 * @throws IllegalStateException if the type is not a ReflectionTypeModel
	 */
	public Class<?> getResultClass() {
		if (resultType instanceof ReflectionTypeModel) {
			return ((ReflectionTypeModel) resultType).getReflectionClass();
		}
		throw new IllegalStateException("Cannot get reflection class from non-reflection type: " + resultType);
	}

	public void setResultType(TypeModel resultType) {
		this.resultType = resultType;
	}

	public String getParentAlias() {
		return parentAlias;
	}

	public void setParentAlias(String parentAlias) {
		this.parentAlias = parentAlias;
	}

	public FieldModel getLinkField() {
		return linkField;
	}

	/**
	 * Returns the link field as a reflection Field object.
	 * @throws IllegalStateException if the field is not a ReflectionFieldModel
	 */
	public Field getLinkReflectionField() {
		if (linkField == null) return null;
		if (linkField instanceof ReflectionFieldModel) {
			return ((ReflectionFieldModel) linkField).getReflectionField();
		}
		throw new IllegalStateException("Cannot get reflection field from non-reflection field model: " + linkField);
	}

	public void setLinkField(FieldModel linkField) {
		this.linkField = linkField;
	}

	public boolean isLinkedValue() {
		return this.isLinkedValue;
	}

	public void setIsLinkedValue(boolean isLinkedValue) {
		this.isLinkedValue = isLinkedValue;
	}

	@Override
	public String toString() {
		return "Alias [alias=" + alias + ", resultType=" + resultType + ", parentAlias=" + parentAlias
				+ ", linkField=" + linkField + ", idFields=" + idFields + ", isLinkedValue=" + isLinkedValue + "]";
	}

	public FieldModel getOtherField() {
		return otherField;
	}

	public void setOtherField(FieldModel f) {
		this.otherField = f;
	}

	public void setSubClassAliases(List<String> subClassAliases) {
		this.subClassAliases = subClassAliases;
	}

	public List<String> getSubClassAliases() {
		return subClassAliases;
	}

	public void setIsASubClass(boolean isASubClass) {
		this.isASubClass = isASubClass;
	}

	public boolean getIsASubClass() {
		return this.isASubClass;
	}

	public boolean getIsEmbedded() {
		return isEmbedded;
	}

	public void setIsEmbedded(boolean isEmbedded) {
		this.isEmbedded = isEmbedded;
	}

	public boolean isSingleTableInheritance() {
		return isSingleTableInheritance;
	}

	public void setSingleTableInheritance(boolean isSingleTableInheritance) {
		this.isSingleTableInheritance = isSingleTableInheritance;
	}

	public String getDiscriminatorColumn() {
		return discriminatorColumn;
	}

	public void setDiscriminatorColumn(String discriminatorColumn) {
		this.discriminatorColumn = discriminatorColumn;
	}

	public Map<String, TypeModel> getDiscriminatorValues() {
		return discriminatorValues;
	}

	public void setDiscriminatorValues(Map<String, TypeModel> discriminatorValues) {
		this.discriminatorValues = discriminatorValues;
	}

}
