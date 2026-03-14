package org.pojoquery;

import org.pojoquery.annotations.Column;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Link;
import org.pojoquery.typemodel.FieldModel;

/**
 * Helper class for reading annotations from classes and fields.
 * Uses canonical PojoQuery annotations via the AnnotatedElementModel interface.
 */
public class AnnotationHelper {
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
