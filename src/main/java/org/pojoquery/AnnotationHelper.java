package org.pojoquery;

import org.pojoquery.annotations.Column;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Helper class for reading annotations from classes and fields.
 * Uses canonical PojoQuery annotations via the AnnotatedElementModel interface.
 */
public class AnnotationHelper {
	/**
	 * Returns the table name from @Table annotation, or null if not present.
	 */
	public static String getTableName(TypeModel type) {
		return type.getAnnotation(Table.class)
			.flatMap(ann -> ann.getStringValue())
			.orElse(null);
	}
	
	/**
	 * Returns table info from @Table annotation, or null if not present.
	 */
	public static TableInfo getTableInfo(TypeModel type) {
		return type.getAnnotation(Table.class)
			.map(ann -> new TableInfo(
				ann.getStringValue().orElse(null),
				ann.getStringValue("schema").orElse("")
			))
			.orElse(null);
	}
	
	/**
	 * Returns column metadata from @Column annotation, or null if not present.
	 */
	public static ColumnMetadata getColumnMetadata(FieldModel f) {
		var columnAnn = f.getAnnotation(Column.class);
		if (columnAnn.isEmpty()) {
			return null;
		}
		var ann = columnAnn.get();
		return new ColumnMetadata(
			ann.getNumberAttribute("length").map(Number::intValue).orElse(255),
			ann.getNumberAttribute("precision").map(Number::intValue).orElse(0),
			ann.getNumberAttribute("scale").map(Number::intValue).orElse(0),
			ann.getBooleanAttribute("nullable").orElse(true),
			ann.getBooleanAttribute("unique").orElse(false)
		);
	}
	
	/**
	 * Checks if a field has @Embedded annotation.
	 */
	public static boolean isEmbedded(FieldModel f) {
		return f.hasAnnotation(Embedded.class);
	}
	
	/**
	 * Checks if a field has @Id annotation.
	 */
	public static boolean isId(FieldModel f) {
		return f.hasAnnotation(Id.class);
	}
	
	/**
	 * Determines the FK column name for an entity reference field.
	 * Checks @FieldName first, then @Link.linkfield, then defaults to null (caller uses fieldName_id).
	 * 
	 * @return column name from @FieldName or @Link.linkfield, or null if not specified
	 */
	public static String getJoinColumnName(FieldModel f) {
		// First check @FieldName
		String fieldName = f.getAnnotation(FieldName.class)
			.flatMap(ann -> ann.getStringValue())
			.orElse(null);
		if (fieldName != null) {
			return fieldName;
		}
		
		// Then check @Link.linkfield
		return f.getAnnotation(Link.class)
			.flatMap(ann -> ann.getStringValue("linkfield"))
			.filter(s -> !s.isEmpty())
			.orElse(null);
	}

	// ========== Helper classes ==========

	/**
	 * Holds table name and schema information from a @Table annotation.
	 */
	public static class TableInfo {
		public final String name;
		public final String schema;

		public TableInfo(String name, String schema) {
			this.name = name;
			this.schema = schema != null ? schema : "";
		}
	}

	/**
	 * Holds column metadata from a @Column annotation.
	 */
	public static class ColumnMetadata {
		public final int length;
		public final int precision;
		public final int scale;
		public final boolean nullable;
		public final boolean unique;

		public ColumnMetadata(int length, int precision, int scale, boolean nullable, boolean unique) {
			this.length = length;
			this.precision = precision;
			this.scale = scale;
			this.nullable = nullable;
			this.unique = unique;
		}
	}
}
