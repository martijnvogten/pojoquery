package nl.pojoquery.internal;

import java.lang.reflect.Field;
import java.util.List;

public class TableMapping {
	public final String tableName;
	public final Class<?> clazz; // The class on which the @Table is declared
	public final List<Field> fields;

	public TableMapping(String tableName, Class<?> clazz, List<Field> fields) {
		this.tableName = tableName;
		this.clazz = clazz;
		this.fields = fields;
	}
	
	@Override
	public String toString() {
		return "TableMapping [clazz=" + clazz.getSimpleName() + ",tableName=" + tableName + ",fields=" + fields + "]";
	}
}

