package nl.pojoquery.pipeline;

import java.lang.reflect.Field;
import java.util.List;

public class Alias {

	private String alias;
	private Class<?> resultClass;
	private String parentAlias;
	private Field linkField;
	private Field otherField = null;
	private List<Field> idFields;
	private List<String> subClassAliases;
	private boolean isLinkedValue;
	private boolean isASubClass;

	public Alias(String alias, Class<?> resultClass, String parentAlias, Field linkField, List<Field> idFields) {
		this.alias = alias;
		this.resultClass = resultClass;
		this.parentAlias = parentAlias;
		this.linkField = linkField;
		this.idFields = idFields;
	}

	public List<Field> getIdFields() {
		return idFields;
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	public Class<?> getResultClass() {
		return resultClass;
	}

	public void setResultClass(Class<?> resultClass) {
		this.resultClass = resultClass;
	}

	public String getParentAlias() {
		return parentAlias;
	}

	public void setParentAlias(String parentAlias) {
		this.parentAlias = parentAlias;
	}

	public Field getLinkField() {
		return linkField;
	}

	public void setLinkField(Field linkField) {
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
		return "Alias [alias=" + alias + ", resultClass=" + resultClass + ", parentAlias=" + parentAlias
				+ ", linkField=" + linkField + ", idFields=" + idFields + ", isLinkedValue=" + isLinkedValue + "]";
	}

	public Field getOtherField() {
		return otherField;
	}

	public void setOtherField(Field f) {
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
	
}
