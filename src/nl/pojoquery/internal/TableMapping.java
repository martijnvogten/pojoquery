package nl.pojoquery.internal;

import java.lang.reflect.Field;
import java.util.List;

public class TableMapping {
	public final String schemaName;
	public final String tableName;
	public final Class<?> clazz; // The class on which the @Table is declared
	public final List<Field> fields;

	public TableMapping(String schemaName, String tableName, Class<?> clazz, List<Field> fields) {
		this.schemaName = "".equals(schemaName) ? null : schemaName;
		this.tableName = tableName;
		this.clazz = clazz;
		this.fields = fields;
	}
	
	@Override
	public String toString() {
		return "TableMapping [clazz=" + clazz.getSimpleName() + ",schemaName=" + schemaName + ",tableName=" + tableName + ",fields=" + fields + "]";
	}
}

