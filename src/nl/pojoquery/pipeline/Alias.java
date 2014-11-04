package nl.pojoquery.pipeline;

import java.lang.reflect.Field;
import java.util.List;

public class Alias {

	private String alias;
	private Class<?> resultClass;
	private String parentAlias;
	private Field linkField;
	private List<Field> idFields;
	private boolean isLinkedValue;

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
	
}
