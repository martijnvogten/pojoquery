package org.pojoquery;

/**
 * Holds table name and schema information from a @Table annotation.
 */
public class TableInfo {
	public final String name;
	public final String schema;

	public TableInfo(String name, String schema) {
		this.name = name;
		this.schema = schema != null ? schema : "";
	}
}
