package org.pojoquery;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.function.Function;

import org.pojoquery.annotations.Cascade;
import org.pojoquery.annotations.Column;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Lob;
import org.pojoquery.annotations.Table;
import org.pojoquery.annotations.Transient;
import org.pojoquery.typemodel.AnnotatedElementModel;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Helper class for reading annotations from classes and fields.
 * Supports both PojoQuery annotations and JPA annotations (javax.persistence and jakarta.persistence).
 * PojoQuery annotations always take precedence over JPA annotations.
 */
@SuppressWarnings("unchecked")
public class AnnotationHelper {

	// JPA annotation classes (loaded via reflection to avoid hard dependency)
	private static final Class<? extends Annotation> JPA_TABLE;
	private static final Class<? extends Annotation> JPA_ID;
	private static final Class<? extends Annotation> JPA_COLUMN;
	private static final Class<? extends Annotation> JPA_TRANSIENT;
	private static final Class<? extends Annotation> JPA_EMBEDDED;
	private static final Class<? extends Annotation> JPA_LOB;
	private static final Class<? extends Annotation> JPA_JOIN_COLUMN;

	private static final Class<? extends Annotation> JAKARTA_TABLE;
	private static final Class<? extends Annotation> JAKARTA_ID;
	private static final Class<? extends Annotation> JAKARTA_COLUMN;
	private static final Class<? extends Annotation> JAKARTA_TRANSIENT;
	private static final Class<? extends Annotation> JAKARTA_EMBEDDED;
	private static final Class<? extends Annotation> JAKARTA_LOB;
	private static final Class<? extends Annotation> JAKARTA_JOIN_COLUMN;

	static {
		// Try to load javax.persistence annotations
		JPA_TABLE = tryLoadAnnotationClass("javax.persistence.Table");
		JPA_ID = tryLoadAnnotationClass("javax.persistence.Id");
		JPA_COLUMN = tryLoadAnnotationClass("javax.persistence.Column");
		JPA_TRANSIENT = tryLoadAnnotationClass("javax.persistence.Transient");
		JPA_EMBEDDED = tryLoadAnnotationClass("javax.persistence.Embedded");
		JPA_LOB = tryLoadAnnotationClass("javax.persistence.Lob");
		JPA_JOIN_COLUMN = tryLoadAnnotationClass("javax.persistence.JoinColumn");

		// Try to load jakarta.persistence annotations
		JAKARTA_TABLE = tryLoadAnnotationClass("jakarta.persistence.Table");
		JAKARTA_ID = tryLoadAnnotationClass("jakarta.persistence.Id");
		JAKARTA_COLUMN = tryLoadAnnotationClass("jakarta.persistence.Column");
		JAKARTA_TRANSIENT = tryLoadAnnotationClass("jakarta.persistence.Transient");
		JAKARTA_EMBEDDED = tryLoadAnnotationClass("jakarta.persistence.Embedded");
		JAKARTA_LOB = tryLoadAnnotationClass("jakarta.persistence.Lob");
		JAKARTA_JOIN_COLUMN = tryLoadAnnotationClass("jakarta.persistence.JoinColumn");
	}

	/**
	 * Returns true if the field is marked as an ID field.
	 */
	public static boolean isId(FieldModel f) {
		return oneOf(marker(Id.class), marker(JPA_ID), marker(JAKARTA_ID)).apply(f).orElse(false);
	}

	/**
	 * Returns true if the field is marked as transient (excluded from persistence).
	 */
	public static boolean isTransient(FieldModel f) {
		return oneOf(marker(Transient.class), marker(JPA_TRANSIENT), marker(JAKARTA_TRANSIENT)).apply(f).orElse(false);
	}

	/**
	 * Returns true if the field is marked as embedded.
	 */
	public static boolean isEmbedded(FieldModel f) {
		return oneOf(marker(Embedded.class), marker(JPA_EMBEDDED), marker(JAKARTA_EMBEDDED)).apply(f).orElse(false);
	}

	/**
	 * Returns true if the field is marked as a LOB (large object).
	 */
	public static boolean isLob(FieldModel f) {
		return oneOf(marker(Lob.class), marker(JPA_LOB), marker(JAKARTA_LOB)).apply(f).orElse(false);
	}

	/**
	 * Returns the column name for a field, or null if not specified.
	 * Checks @FieldName/@Column first, then JPA @Column(name=...).
	 */
	public static String getColumnName(FieldModel f) {
		return oneOf(
			stringAttr(FieldName.class, "value"),
			stringAttr(Column.class, "name"),
			stringAttr(JPA_COLUMN, "name"),
			stringAttr(JAKARTA_COLUMN, "name")
		).apply(f).orElse(null);
	}

	/**
	 * Returns the join column name for a foreign key field.
	 * Checks PojoQuery @Link(linkfield=...) first, then JPA @JoinColumn(name=...).
	 */
	public static String getJoinColumnName(FieldModel f) {
		return oneOf(
			stringAttr(Link.class, "linkfield"),
			stringAttr(JPA_JOIN_COLUMN, "name"),
			stringAttr(JAKARTA_JOIN_COLUMN, "name")
		).apply(f).orElse(null);
	}

	public static Optional<Integer> getColumnLength(FieldModel f) {
		return oneOf(
			intAttr(Column.class, "length"),
			intAttr(JPA_COLUMN, "length"),
			intAttr(JAKARTA_COLUMN, "length")
		).apply(f);
	}

	public static Optional<Integer> getColumnPrecision(FieldModel f) {
		return oneOf(
			intAttr(Column.class, "precision"),
			intAttr(JPA_COLUMN, "precision"),
			intAttr(JAKARTA_COLUMN, "precision")
		).apply(f);
	}

	public static Optional<Integer> getColumnScale(FieldModel f) {
		return oneOf(
			intAttr(Column.class, "scale"),
			intAttr(JPA_COLUMN, "scale"),
			intAttr(JAKARTA_COLUMN, "scale")
		).apply(f);
	}

	public static Optional<Boolean> getNullable(FieldModel f) {
		return oneOf(
			markerAttr(Column.class, "nullable"),
			markerAttr(JPA_COLUMN, "nullable"),
			markerAttr(JAKARTA_COLUMN, "nullable")
		).apply(f);
	}

	public static Optional<Boolean> getUnique(FieldModel f) {
		return oneOf(
			markerAttr(Column.class, "unique"),
			markerAttr(JPA_COLUMN, "unique"),
			markerAttr(JAKARTA_COLUMN, "unique")
		).apply(f);
	}

	/**
	 * Returns column metadata from @Column annotation, or null if not present.
	 */
	public static ColumnMetadata getColumnMetadata(FieldModel f) {
		return new ColumnMetadata(
			getColumnLength(f).orElse(255),
			getColumnPrecision(f).orElse(0),
			getColumnScale(f).orElse(0),
			getNullable(f).orElse(true),
			getUnique(f).orElse(false)
		);
	}

	/**
	 * Returns true if the field is marked as a @Cascade annotation.
	 */
	public static boolean isCascade(FieldModel f) {
		return oneOf(marker(Cascade.class)).apply(f).orElse(false);
	}

	/**
	 * Returns true if the field is marked as a @Link annotation.
	 */
	public static boolean isLinkTable(FieldModel f) {
		return oneOf(marker(Link.class)).apply(f).orElse(false);
	}

	// ========== Type-level public API ==========

	/**
	 * Returns the table name from @Table annotation, or null if not specified.
	 */
	public static String getTableName(TypeModel type) {
		return oneOf(
			stringAttr(Table.class, "value"),
			stringAttr(JPA_TABLE, "name"),
			stringAttr(JAKARTA_TABLE, "name")
		).apply(type).orElse("");
	}

	/**
	 * Returns the table schema from @Table annotation, or empty string if not specified.
	 */
	public static String getTableSchema(TypeModel type) {
		return oneOf(
			stringAttr(Table.class, "schema"),
			stringAttr(JPA_TABLE, "schema"),
			stringAttr(JAKARTA_TABLE, "schema")
		).apply(type).orElse("");
	}

	/**
	 * Returns true if the type has a @Table annotation (PojoQuery or JPA).
	 */
	public static boolean hasTableAnnotation(TypeModel type) {
		return oneOf(
			marker(Table.class),
			marker(JPA_TABLE),
			marker(JAKARTA_TABLE)
		).apply(type).orElse(false);
	}

	/**
	 * Returns a TableInfo object with both name and schema, or null if no @Table annotation.
	 */
	public static Optional<TableInfo> getTableInfo(TypeModel type) {
		return hasTableAnnotation(type) ? Optional.of(new TableInfo(getTableName(type), getTableSchema(type))) : Optional.empty();
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

	private static Class<? extends Annotation> tryLoadAnnotationClass(String name) {
		try {
			return (Class<? extends Annotation>) Class.forName(name);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	@SafeVarargs
	private static <M,T> Function<M, Optional<T>> oneOf(Function<M, Optional<T>>... options) {
		return field -> {
			for (Function<M, Optional<T>> option : options) {
				Optional<T> result = option.apply(field);
				if (result.isPresent()) {
					return result;
				}
			}
			return Optional.empty();
		};
	}

	private static Function<AnnotatedElementModel, Optional<Boolean>> marker(Class<? extends Annotation> annotationClass) {
		if (annotationClass == null) return field -> Optional.empty();
		return field -> field.hasAnnotation(annotationClass) ? Optional.of(true) : Optional.empty();
	}

	private static Function<AnnotatedElementModel, Optional<String>> stringAttr(Class<? extends Annotation> annotationClass, String attributeName) {
		if (annotationClass == null) return field -> Optional.empty();
		return field -> field.getAnnotationsByType(annotationClass).stream()
			.findFirst()
			.map(annotation -> annotation.getStringValue(attributeName))
			.filter(s -> s != null && !s.isEmpty());
	}

	private static Function<AnnotatedElementModel, Optional<Integer>> intAttr(Class<? extends Annotation> annotationClass, String attributeName) {
		if (annotationClass == null) return field -> Optional.empty();
		return field -> field.getAnnotationsByType(annotationClass).stream()
			.findFirst()
			.map(annotation -> (Integer) annotation.getNumberValue(attributeName))
			.filter(i -> i != null);
	}

	private static Function<AnnotatedElementModel, Optional<Boolean>> markerAttr(Class<? extends Annotation> annotationClass, String attributeName) {
		if (annotationClass == null) return field -> Optional.empty();
		return field -> field.getAnnotationsByType(annotationClass).stream()
			.findFirst()
			.map(annotation -> (Boolean) annotation.getBooleanValue(attributeName))
			.filter(b -> b != null);
	}

}
