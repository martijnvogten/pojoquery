package org.pojoquery.internal;

import java.util.List;

import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

public class TableMapping {
	public final String schemaName;
	public final String tableName;
	public final TypeModel type; // The type on which the @Table is declared
	public final List<FieldModel> fields;

	public TableMapping(String schemaName, String tableName, TypeModel type, List<FieldModel> fields) {
		this.schemaName = "".equals(schemaName) ? null : schemaName;
		this.tableName = tableName;
		this.type = type;
		this.fields = fields;
	}

	public TypeModel getType() {
		return type;
	}
	
	/**
	 * Returns the fields as FieldModel objects.
	 * @return the list of FieldModel objects
	 */
	public List<FieldModel> getFields() {
		return fields;
	}

	@Override
	public String toString() {
		return "TableMapping [type=" + type.getSimpleName() + ",schemaName=" + schemaName + ",tableName=" + tableName + ",fields=" + fields + "]";
	}
}
