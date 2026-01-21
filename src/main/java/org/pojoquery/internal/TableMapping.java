package org.pojoquery.internal;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionFieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
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

	/**
	 * Returns the runtime Class for this mapping.
	 * @throws IllegalStateException if the type is not a ReflectionTypeModel
	 */
	public Class<?> getReflectionClass() {
		if (type instanceof ReflectionTypeModel) {
			return ((ReflectionTypeModel) type).getReflectionClass();
		}
		throw new IllegalStateException("Cannot get reflection class from non-reflection type: " + type);
	}

	/**
	 * Returns the fields as runtime Field objects.
	 * @throws IllegalStateException if any field is not a ReflectionFieldModel
	 */
	public List<Field> getReflectionFields() {
		List<Field> result = new ArrayList<>();
		for (FieldModel f : fields) {
			if (f instanceof ReflectionFieldModel) {
				result.add(((ReflectionFieldModel) f).getReflectionField());
			} else {
				throw new IllegalStateException("Cannot get reflection field from non-reflection field: " + f);
			}
		}
		return result;
	}

	@Override
	public String toString() {
		return "TableMapping [type=" + type.getSimpleName() + ",schemaName=" + schemaName + ",tableName=" + tableName + ",fields=" + fields + "]";
	}
}
