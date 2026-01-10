package org.pojoquery.pipeline;

import java.util.List;
import java.util.Map;

import java.lang.reflect.Field;

import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionFieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Represents an alias in a query, mapping a table alias to its type and relationship information.
 */
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

	/**
	 * Creates a new Alias.
	 * @param alias the alias name
	 * @param resultType the result type for this alias
	 * @param parentAlias the parent alias name, or null if this is a root alias
	 * @param linkField the field that links to the parent
	 * @param idFields the ID fields for this alias
	 */
	public Alias(String alias, TypeModel resultType, String parentAlias, FieldModel linkField, List<FieldModel> idFields) {
		this.alias = alias;
		this.resultType = resultType;
		this.parentAlias = parentAlias;
		this.linkField = linkField;
		this.idFields = idFields;
	}

	/**
	 * Returns the ID fields.
	 * @return the ID fields
	 */
	public List<FieldModel> getIdFields() {
		return idFields;
	}

	/**
	 * Returns the ID fields as reflection Field objects.
	 * @return the ID fields as reflection Field objects
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

	/**
	 * Returns the alias name.
	 * @return the alias name
	 */
	public String getAlias() {
		return alias;
	}

	/**
	 * Sets the alias name.
	 * @param alias the alias name
	 */
	public void setAlias(String alias) {
		this.alias = alias;
	}

	/**
	 * Returns the result type.
	 * @return the result type
	 */
	public TypeModel getResultType() {
		return resultType;
	}

	/**
	 * Returns the runtime Class for this alias.
	 * @return the runtime Class
	 * @throws IllegalStateException if the type is not a ReflectionTypeModel
	 */
	public Class<?> getResultClass() {
		if (resultType instanceof ReflectionTypeModel) {
			return ((ReflectionTypeModel) resultType).getReflectionClass();
		}
		throw new IllegalStateException("Cannot get reflection class from non-reflection type: " + resultType);
	}

	/**
	 * Sets the result type.
	 * @param resultType the result type
	 */
	public void setResultType(TypeModel resultType) {
		this.resultType = resultType;
	}

	/**
	 * Returns the parent alias name.
	 * @return the parent alias name, or null if this is a root alias
	 */
	public String getParentAlias() {
		return parentAlias;
	}

	/**
	 * Sets the parent alias name.
	 * @param parentAlias the parent alias name
	 */
	public void setParentAlias(String parentAlias) {
		this.parentAlias = parentAlias;
	}

	/**
	 * Returns the link field.
	 * @return the link field
	 */
	public FieldModel getLinkField() {
		return linkField;
	}

	/**
	 * Returns the link field as a reflection Field object.
	 * @return the link field as a reflection Field object, or null if the link field is null
	 * @throws IllegalStateException if the field is not a ReflectionFieldModel
	 */
	public Field getLinkReflectionField() {
		if (linkField == null) return null;
		if (linkField instanceof ReflectionFieldModel) {
			return ((ReflectionFieldModel) linkField).getReflectionField();
		}
		throw new IllegalStateException("Cannot get reflection field from non-reflection field model: " + linkField);
	}

	/**
	 * Sets the link field.
	 * @param linkField the link field
	 */
	public void setLinkField(FieldModel linkField) {
		this.linkField = linkField;
	}

	/**
	 * Returns whether this alias represents a linked value.
	 * @return true if this is a linked value
	 */
	public boolean isLinkedValue() {
		return this.isLinkedValue;
	}

	/**
	 * Sets whether this alias represents a linked value.
	 * @param isLinkedValue true if this is a linked value
	 */
	public void setIsLinkedValue(boolean isLinkedValue) {
		this.isLinkedValue = isLinkedValue;
	}

	@Override
	public String toString() {
		return "Alias [alias=" + alias + ", resultType=" + resultType + ", parentAlias=" + parentAlias
				+ ", linkField=" + linkField + ", idFields=" + idFields + ", isLinkedValue=" + isLinkedValue + "]";
	}

	/**
	 * Returns the other field (for {@code @Other} annotated fields).
	 * @return the other field
	 */
	public FieldModel getOtherField() {
		return otherField;
	}

	/**
	 * Sets the other field.
	 * @param f the other field
	 */
	public void setOtherField(FieldModel f) {
		this.otherField = f;
	}

	/**
	 * Sets the subclass aliases.
	 * @param subClassAliases the subclass aliases
	 */
	public void setSubClassAliases(List<String> subClassAliases) {
		this.subClassAliases = subClassAliases;
	}

	/**
	 * Returns the subclass aliases.
	 * @return the subclass aliases
	 */
	public List<String> getSubClassAliases() {
		return subClassAliases;
	}

	/**
	 * Sets whether this alias represents a subclass.
	 * @param isASubClass true if this is a subclass
	 */
	public void setIsASubClass(boolean isASubClass) {
		this.isASubClass = isASubClass;
	}

	/**
	 * Returns whether this alias represents a subclass.
	 * @return true if this is a subclass
	 */
	public boolean getIsASubClass() {
		return this.isASubClass;
	}

	/**
	 * Returns whether this alias is embedded.
	 * @return true if this alias is embedded
	 */
	public boolean getIsEmbedded() {
		return isEmbedded;
	}

	/**
	 * Sets whether this alias is embedded.
	 * @param isEmbedded true if this alias is embedded
	 */
	public void setIsEmbedded(boolean isEmbedded) {
		this.isEmbedded = isEmbedded;
	}

	/**
	 * Returns whether this alias uses single table inheritance.
	 * @return true if using single table inheritance
	 */
	public boolean isSingleTableInheritance() {
		return isSingleTableInheritance;
	}

	/**
	 * Sets whether this alias uses single table inheritance.
	 * @param isSingleTableInheritance true if using single table inheritance
	 */
	public void setSingleTableInheritance(boolean isSingleTableInheritance) {
		this.isSingleTableInheritance = isSingleTableInheritance;
	}

	/**
	 * Returns the discriminator column name.
	 * @return the discriminator column name
	 */
	public String getDiscriminatorColumn() {
		return discriminatorColumn;
	}

	/**
	 * Sets the discriminator column name.
	 * @param discriminatorColumn the discriminator column name
	 */
	public void setDiscriminatorColumn(String discriminatorColumn) {
		this.discriminatorColumn = discriminatorColumn;
	}

	/**
	 * Returns the discriminator values mapping.
	 * @return the map from discriminator values to types
	 */
	public Map<String, TypeModel> getDiscriminatorValues() {
		return discriminatorValues;
	}

	/**
	 * Sets the discriminator values mapping.
	 * @param discriminatorValues the map from discriminator values to types
	 */
	public void setDiscriminatorValues(Map<String, TypeModel> discriminatorValues) {
		this.discriminatorValues = discriminatorValues;
	}

}
