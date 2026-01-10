package org.pojoquery;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.pojoquery.annotations.Column;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Lob;
import org.pojoquery.annotations.Table;
import org.pojoquery.annotations.Transient;

/**
 * Helper class for reading annotations from classes and fields.
 * Supports both PojoQuery annotations and JPA annotations (javax.persistence and jakarta.persistence).
 * PojoQuery annotations always take precedence over JPA annotations.
 */
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

	@SuppressWarnings("unchecked")
	private static Class<? extends Annotation> tryLoadAnnotationClass(String name) {
		try {
			return (Class<? extends Annotation>) Class.forName(name);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	// ========== Class-level helpers ==========

	/**
	 * Returns true if the class has a @Table annotation (PojoQuery or JPA).
	 * @param clz the class to check
	 * @return true if the class has a @Table annotation
	 */
	public static boolean hasTableAnnotation(Class<?> clz) {
		if (clz.getAnnotation(Table.class) != null) {
			return true;
		}
		if (JPA_TABLE != null && clz.getAnnotation(JPA_TABLE) != null) {
			return true;
		}
		if (JAKARTA_TABLE != null && clz.getAnnotation(JAKARTA_TABLE) != null) {
			return true;
		}
		return false;
	}

	/**
	 * Returns the table name from @Table annotation, or null if not specified.
	 * Checks PojoQuery @Table first, then JPA @Table.
	 * @param clz the class to check
	 * @return the table name, or null if not specified
	 */
	public static String getTableName(Class<?> clz) {
		Table tableAnn = clz.getAnnotation(Table.class);
		if (tableAnn != null) {
			return tableAnn.value();
		}

		// Try JPA @Table (uses "name" attribute)
		String jpaName = getJpaTableName(clz, JPA_TABLE);
		if (jpaName != null) {
			return jpaName;
		}

		return getJpaTableName(clz, JAKARTA_TABLE);
	}

	/**
	 * Returns the table schema from @Table annotation, or empty string if not specified.
	 * @param clz the class to check
	 * @return the table schema, or empty string if not specified
	 */
	public static String getTableSchema(Class<?> clz) {
		Table tableAnn = clz.getAnnotation(Table.class);
		if (tableAnn != null) {
			return tableAnn.schema();
		}

		// Try JPA @Table
		String jpaSchema = getJpaTableSchema(clz, JPA_TABLE);
		if (jpaSchema != null && !jpaSchema.isEmpty()) {
			return jpaSchema;
		}

		String jakartaSchema = getJpaTableSchema(clz, JAKARTA_TABLE);
		return jakartaSchema != null ? jakartaSchema : "";
	}

	/**
	 * Returns a TableInfo object with both name and schema, or null if no @Table annotation.
	 * @param clz the class to check
	 * @return the TableInfo, or null if no @Table annotation
	 */
	public static TableInfo getTableInfo(Class<?> clz) {
		Table tableAnn = clz.getAnnotation(Table.class);
		if (tableAnn != null) {
			return new TableInfo(tableAnn.value(), tableAnn.schema());
		}

		// Try JPA @Table
		TableInfo jpaInfo = getJpaTableInfo(clz, JPA_TABLE);
		if (jpaInfo != null) {
			return jpaInfo;
		}

		return getJpaTableInfo(clz, JAKARTA_TABLE);
	}

	// ========== TypeModel overloads ==========

	/**
	 * Returns true if the type has a @Table annotation (PojoQuery or JPA).
	 * @param type the type to check
	 * @return true if the type has a @Table annotation
	 */
	public static boolean hasTableAnnotation(TypeModel type) {
		if (type.getAnnotation(Table.class) != null) {
			return true;
		}
		if (JPA_TABLE != null && type.getAnnotation(JPA_TABLE) != null) {
			return true;
		}
		if (JAKARTA_TABLE != null && type.getAnnotation(JAKARTA_TABLE) != null) {
			return true;
		}
		return false;
	}

	/**
	 * Returns a TableInfo object with both name and schema, or null if no @Table annotation.
	 * @param type the type to check
	 * @return the TableInfo, or null if no @Table annotation
	 */
	public static TableInfo getTableInfo(TypeModel type) {
		Table tableAnn = type.getAnnotation(Table.class);
		if (tableAnn != null) {
			return new TableInfo(tableAnn.value(), tableAnn.schema());
		}

		// Try JPA @Table
		if (JPA_TABLE != null) {
			Annotation ann = type.getAnnotation(JPA_TABLE);
			if (ann != null) {
				String name = invokeStringMethod(ann, "name");
				String schema = invokeStringMethod(ann, "schema");
				return new TableInfo(name, schema);
			}
		}

		if (JAKARTA_TABLE != null) {
			Annotation ann = type.getAnnotation(JAKARTA_TABLE);
			if (ann != null) {
				String name = invokeStringMethod(ann, "name");
				String schema = invokeStringMethod(ann, "schema");
				return new TableInfo(name, schema);
			}
		}

		return null;
	}

	// ========== Field-level helpers ==========

	/**
	 * Returns true if the field is marked as an ID field.
	 * @param f the field to check
	 * @return true if the field is an ID field
	 */
	public static boolean isId(Field f) {
		if (f.getAnnotation(Id.class) != null) {
			return true;
		}
		if (JPA_ID != null && f.getAnnotation(JPA_ID) != null) {
			return true;
		}
		if (JAKARTA_ID != null && f.getAnnotation(JAKARTA_ID) != null) {
			return true;
		}
		return false;
	}

	/**
	 * Returns true if the field is marked as transient (excluded from persistence).
	 * @param f the field to check
	 * @return true if the field is transient
	 */
	public static boolean isTransient(Field f) {
		if (f.getAnnotation(Transient.class) != null) {
			return true;
		}
		if (JPA_TRANSIENT != null && f.getAnnotation(JPA_TRANSIENT) != null) {
			return true;
		}
		if (JAKARTA_TRANSIENT != null && f.getAnnotation(JAKARTA_TRANSIENT) != null) {
			return true;
		}
		return false;
	}

	/**
	 * Returns true if the field is marked as embedded.
	 * @param f the field to check
	 * @return true if the field is embedded
	 */
	public static boolean isEmbedded(Field f) {
		if (f.getAnnotation(Embedded.class) != null) {
			return true;
		}
		if (JPA_EMBEDDED != null && f.getAnnotation(JPA_EMBEDDED) != null) {
			return true;
		}
		if (JAKARTA_EMBEDDED != null && f.getAnnotation(JAKARTA_EMBEDDED) != null) {
			return true;
		}
		return false;
	}

	/**
	 * Returns true if the field is marked as a LOB (large object).
	 * @param f the field to check
	 * @return true if the field is a LOB
	 */
	public static boolean isLob(Field f) {
		if (f.getAnnotation(Lob.class) != null) {
			return true;
		}
		if (JPA_LOB != null && f.getAnnotation(JPA_LOB) != null) {
			return true;
		}
		if (JAKARTA_LOB != null && f.getAnnotation(JAKARTA_LOB) != null) {
			return true;
		}
		return false;
	}

	/**
	 * Returns the column name for a field.
	 * Checks PojoQuery @FieldName first, then JPA @Column(name=...).
	 * Returns null if no custom column name is specified.
	 * @param f the field to check
	 * @return the column name, or null if not specified
	 */
	public static String getColumnName(Field f) {
		FieldName fieldNameAnn = f.getAnnotation(FieldName.class);
		if (fieldNameAnn != null) {
			return fieldNameAnn.value();
		}

		// Try JPA @Column(name=...)
		String jpaName = getJpaColumnName(f, JPA_COLUMN);
		if (jpaName != null && !jpaName.isEmpty()) {
			return jpaName;
		}

		String jakartaName = getJpaColumnName(f, JAKARTA_COLUMN);
		if (jakartaName != null && !jakartaName.isEmpty()) {
			return jakartaName;
		}

		return null;
	}

	/**
	 * Returns the join column name for a foreign key field.
	 * Checks PojoQuery @Link(linkfield=...) first, then JPA @JoinColumn(name=...).
	 * Returns null if no custom join column name is specified.
	 * @param f the field to check
	 * @return the join column name, or null if not specified
	 */
	public static String getJoinColumnName(Field f) {
		Link linkAnn = f.getAnnotation(Link.class);
		if (linkAnn != null && !Link.NONE.equals(linkAnn.linkfield())) {
			return linkAnn.linkfield();
		}

		// Try JPA @JoinColumn(name=...)
		String jpaName = getJpaJoinColumnName(f, JPA_JOIN_COLUMN);
		if (jpaName != null && !jpaName.isEmpty()) {
			return jpaName;
		}

		String jakartaName = getJpaJoinColumnName(f, JAKARTA_JOIN_COLUMN);
		if (jakartaName != null && !jakartaName.isEmpty()) {
			return jakartaName;
		}

		return null;
	}

	/**
	 * Returns column metadata (length, precision, scale, nullable, unique).
	 * Merges information from PojoQuery @Column and JPA @Column.
	 * @param f the field to check
	 * @return the column metadata, or null if no @Column annotation
	 */
	public static ColumnMetadata getColumnMetadata(Field f) {
		Column pojoColumn = f.getAnnotation(Column.class);
		if (pojoColumn != null) {
			return new ColumnMetadata(
				pojoColumn.length(),
				pojoColumn.precision(),
				pojoColumn.scale(),
				pojoColumn.nullable(),
				pojoColumn.unique()
			);
		}

		// Try JPA @Column
		ColumnMetadata jpaMetadata = getJpaColumnMetadata(f, JPA_COLUMN);
		if (jpaMetadata != null) {
			return jpaMetadata;
		}

		ColumnMetadata jakartaMetadata = getJpaColumnMetadata(f, JAKARTA_COLUMN);
		if (jakartaMetadata != null) {
			return jakartaMetadata;
		}

		return null;
	}

	// ========== FieldModel overloads ==========

	/**
	 * Returns true if the field is marked as an ID field.
	 * @param f the field to check
	 * @return true if the field is an ID field
	 */
	public static boolean isId(FieldModel f) {
		if (f.getAnnotation(Id.class) != null) {
			return true;
		}
		if (JPA_ID != null && f.getAnnotation(JPA_ID) != null) {
			return true;
		}
		if (JAKARTA_ID != null && f.getAnnotation(JAKARTA_ID) != null) {
			return true;
		}
		return false;
	}

	/**
	 * Returns true if the field is marked as transient (excluded from persistence).
	 * @param f the field to check
	 * @return true if the field is transient
	 */
	public static boolean isTransient(FieldModel f) {
		if (f.getAnnotation(Transient.class) != null) {
			return true;
		}
		if (JPA_TRANSIENT != null && f.getAnnotation(JPA_TRANSIENT) != null) {
			return true;
		}
		if (JAKARTA_TRANSIENT != null && f.getAnnotation(JAKARTA_TRANSIENT) != null) {
			return true;
		}
		return false;
	}

	/**
	 * Returns true if the field is marked as embedded.
	 * @param f the field to check
	 * @return true if the field is embedded
	 */
	public static boolean isEmbedded(FieldModel f) {
		if (f.getAnnotation(Embedded.class) != null) {
			return true;
		}
		if (JPA_EMBEDDED != null && f.getAnnotation(JPA_EMBEDDED) != null) {
			return true;
		}
		if (JAKARTA_EMBEDDED != null && f.getAnnotation(JAKARTA_EMBEDDED) != null) {
			return true;
		}
		return false;
	}

	/**
	 * Returns true if the field is marked as a LOB (large object).
	 * @param f the field to check
	 * @return true if the field is a LOB
	 */
	public static boolean isLob(FieldModel f) {
		if (f.getAnnotation(Lob.class) != null) {
			return true;
		}
		if (JPA_LOB != null && f.getAnnotation(JPA_LOB) != null) {
			return true;
		}
		if (JAKARTA_LOB != null && f.getAnnotation(JAKARTA_LOB) != null) {
			return true;
		}
		return false;
	}

	/**
	 * Returns the column name for a field.
	 * Checks PojoQuery @FieldName first, then JPA @Column(name=...).
	 * Returns null if no custom column name is specified.
	 * @param f the field to check
	 * @return the column name, or null if not specified
	 */
	public static String getColumnName(FieldModel f) {
		FieldName fieldNameAnn = f.getAnnotation(FieldName.class);
		if (fieldNameAnn != null) {
			return fieldNameAnn.value();
		}

		// Try JPA @Column(name=...)
		if (JPA_COLUMN != null) {
			Annotation ann = f.getAnnotation(JPA_COLUMN);
			if (ann != null) {
				String name = invokeStringMethod(ann, "name");
				if (name != null && !name.isEmpty()) {
					return name;
				}
			}
		}

		if (JAKARTA_COLUMN != null) {
			Annotation ann = f.getAnnotation(JAKARTA_COLUMN);
			if (ann != null) {
				String name = invokeStringMethod(ann, "name");
				if (name != null && !name.isEmpty()) {
					return name;
				}
			}
		}

		return null;
	}

	/**
	 * Returns the join column name for a foreign key field.
	 * Checks PojoQuery @Link(linkfield=...) first, then JPA @JoinColumn(name=...).
	 * Returns null if no custom join column name is specified.
	 * @param f the field to check
	 * @return the join column name, or null if not specified
	 */
	public static String getJoinColumnName(FieldModel f) {
		Link linkAnn = f.getAnnotation(Link.class);
		if (linkAnn != null && !Link.NONE.equals(linkAnn.linkfield())) {
			return linkAnn.linkfield();
		}

		// Try JPA @JoinColumn(name=...)
		if (JPA_JOIN_COLUMN != null) {
			Annotation ann = f.getAnnotation(JPA_JOIN_COLUMN);
			if (ann != null) {
				String name = invokeStringMethod(ann, "name");
				if (name != null && !name.isEmpty()) {
					return name;
				}
			}
		}

		if (JAKARTA_JOIN_COLUMN != null) {
			Annotation ann = f.getAnnotation(JAKARTA_JOIN_COLUMN);
			if (ann != null) {
				String name = invokeStringMethod(ann, "name");
				if (name != null && !name.isEmpty()) {
					return name;
				}
			}
		}

		return null;
	}

	// ========== Helper classes ==========

	/**
	 * Holds table name and schema information from a @Table annotation.
	 */
	public static class TableInfo {
		/** The table name. */
		public final String name;
		/** The schema name. */
		public final String schema;

		/**
		 * Creates a new TableInfo.
		 * @param name the table name
		 * @param schema the schema name (may be null)
		 */
		public TableInfo(String name, String schema) {
			this.name = name;
			this.schema = schema != null ? schema : "";
		}
	}

	/**
	 * Holds column metadata from a @Column annotation.
	 */
	public static class ColumnMetadata {
		/** The column length (for VARCHAR). */
		public final int length;
		/** The precision (for DECIMAL). */
		public final int precision;
		/** The scale (for DECIMAL). */
		public final int scale;
		/** Whether the column allows NULL. */
		public final boolean nullable;
		/** Whether the column has a UNIQUE constraint. */
		public final boolean unique;

		/**
		 * Creates a new ColumnMetadata.
		 * @param length the column length
		 * @param precision the precision
		 * @param scale the scale
		 * @param nullable whether nullable
		 * @param unique whether unique
		 */
		public ColumnMetadata(int length, int precision, int scale, boolean nullable, boolean unique) {
			this.length = length;
			this.precision = precision;
			this.scale = scale;
			this.nullable = nullable;
			this.unique = unique;
		}
	}

	// ========== JPA reflection helpers ==========

	private static String getJpaTableName(Class<?> clz, Class<? extends Annotation> jpaTableClass) {
		if (jpaTableClass == null) {
			return null;
		}
		Annotation ann = clz.getAnnotation(jpaTableClass);
		if (ann == null) {
			return null;
		}
		return invokeStringMethod(ann, "name");
	}

	private static String getJpaTableSchema(Class<?> clz, Class<? extends Annotation> jpaTableClass) {
		if (jpaTableClass == null) {
			return null;
		}
		Annotation ann = clz.getAnnotation(jpaTableClass);
		if (ann == null) {
			return null;
		}
		return invokeStringMethod(ann, "schema");
	}

	private static TableInfo getJpaTableInfo(Class<?> clz, Class<? extends Annotation> jpaTableClass) {
		if (jpaTableClass == null) {
			return null;
		}
		Annotation ann = clz.getAnnotation(jpaTableClass);
		if (ann == null) {
			return null;
		}
		String name = invokeStringMethod(ann, "name");
		String schema = invokeStringMethod(ann, "schema");
		return new TableInfo(name, schema);
	}

	private static String getJpaColumnName(Field f, Class<? extends Annotation> jpaColumnClass) {
		if (jpaColumnClass == null) {
			return null;
		}
		Annotation ann = f.getAnnotation(jpaColumnClass);
		if (ann == null) {
			return null;
		}
		return invokeStringMethod(ann, "name");
	}

	private static String getJpaJoinColumnName(Field f, Class<? extends Annotation> jpaJoinColumnClass) {
		if (jpaJoinColumnClass == null) {
			return null;
		}
		Annotation ann = f.getAnnotation(jpaJoinColumnClass);
		if (ann == null) {
			return null;
		}
		return invokeStringMethod(ann, "name");
	}

	private static ColumnMetadata getJpaColumnMetadata(Field f, Class<? extends Annotation> jpaColumnClass) {
		if (jpaColumnClass == null) {
			return null;
		}
		Annotation ann = f.getAnnotation(jpaColumnClass);
		if (ann == null) {
			return null;
		}

		int length = invokeIntMethod(ann, "length", 255);
		int precision = invokeIntMethod(ann, "precision", 0);
		int scale = invokeIntMethod(ann, "scale", 0);
		boolean nullable = invokeBooleanMethod(ann, "nullable", true);
		boolean unique = invokeBooleanMethod(ann, "unique", false);

		return new ColumnMetadata(length, precision, scale, nullable, unique);
	}

	private static String invokeStringMethod(Annotation ann, String methodName) {
		try {
			Method method = ann.getClass().getMethod(methodName);
			return (String) method.invoke(ann);
		} catch (Exception e) {
			return null;
		}
	}

	private static int invokeIntMethod(Annotation ann, String methodName, int defaultValue) {
		try {
			Method method = ann.getClass().getMethod(methodName);
			return (Integer) method.invoke(ann);
		} catch (Exception e) {
			return defaultValue;
		}
	}

	private static boolean invokeBooleanMethod(Annotation ann, String methodName, boolean defaultValue) {
		try {
			Method method = ann.getClass().getMethod(methodName);
			return (Boolean) method.invoke(ann);
		} catch (Exception e) {
			return defaultValue;
		}
	}
}
