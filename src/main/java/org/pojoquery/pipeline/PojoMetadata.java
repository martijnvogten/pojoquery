package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

import org.pojoquery.AnnotationHelper.TableInfo;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Transient;
import org.pojoquery.internal.MappingException;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.typemodel.AnnotationModel;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;


// Annotation metadata
// should follow an overridable pattern
// where the separate annotation transforms can override the
// default behaviour.
// There is one cannonical set of annotations used in the
// rest of the transforms. If library users want to use custom annotations
// they should write a transform that converts their custom annotations
// to the cannonical ones used by the rest of the pipeline. This way we can
// keep the rest of the pipeline simple and annotation-agnostic.
// Transform can only add annotations to the fieldmodel and typemodel
// annotations are already abstract as the fieldmodel and typemodel abstract
// away real classes and compile elements into a unified model.
// We can add transforms to FieldModel and Typemodel.
// That means FieldModel and TypeModel must also be immutable records
// that only represent data needed in the transforms.
// API should be something like: field.getAnnotationValue(Link.class, "linktable")
// and field.hasAnnotation(Embedded.class).

/**
 * Static utility methods for extracting metadata from POJO classes.
 * These methods are shared by QueryModel, CustomizableQueryBuilder, querytree transforms,
 * schema generation, and cascading updater.
 */
public final class PojoMetadata {

	private PojoMetadata() {
		// Utility class
	}

	/**
	 * Map extension used for passing row values during result processing.
	 */
	public static class Values extends HashMap<String, Object> {
		public Values() {
			super();
		}

		public Values(Values values) {
			super(values);
		}
	}

	// --- Field name determination ---

	/**
	 * Determines the SQL column name for a field.
	 * Checks @FieldName first, then falls back to field name.
	 */
	public static String determineSqlFieldName(FieldModel f) {
		Objects.requireNonNull(f, "field must not be null");
		return f.getAnnotation(FieldName.class).flatMap(ann -> ann.getStringValue()).orElse(f.getName());
	}

	/**
	 * Determines the SQL column name for a foreign key (link) field.
	 * Checks @Link(linkfield) first, then falls back to @FieldName,
	 * and finally defaults to fieldName_id.
	 */
	public static String determineLinkFieldName(FieldModel f) {
		Objects.requireNonNull(f, "field must not be null");
		// Check for @Link(linkfield) first
		String joinColumnName = f.getAnnotation(Link.class).flatMap(ann -> ann.getStringValue("linkfield")).orElse(null);
		if (joinColumnName != null) {
			return joinColumnName;
		}
		// Fall back to @FieldName
		return f.getAnnotation(FieldName.class).flatMap(ann -> ann.getStringValue())
		.orElse(f.getName() + "_id");
	}

	/**
	 * Determines the owner (parent) column name for a link table.
	 * Uses @Link(linkfield) if specified, otherwise defaults to tableName_id.
	 *
	 * @param ownerClass the class that owns the collection field
	 * @param linkAnn the @Link annotation on the collection field
	 * @return the column name for the owner's foreign key in the link table
	 */
	public static String determineLinkTableOwnerColumn(TypeModel ownerClass, AnnotationModel linkAnn) {
		return linkAnn != null ? linkAnn.getStringValue("linkfield").orElseGet(() -> {
			List<TableMapping> mappings = determineTableMapping(ownerClass);
			return mappings.isEmpty() ? ownerClass.getSimpleName().toLowerCase() + "_id" : mappings.get(0).tableName + "_id";
		}) : null;
	}

	/**
	 * Determines the foreign (target) column name for a link table.
	 * Uses @Link(foreignlinkfield) if specified, otherwise defaults to tableName_id.
	 * For value collections, uses @Link(fetchColumn) instead.
	 *
	 * @param foreignClass the target class of the collection (may be null for value collections)
	 * @param linkAnn the @Link annotation on the collection field
	 * @return the column name for the target's foreign key in the link table
	 */
	public static String determineLinkTableForeignColumn(TypeModel foreignClass, AnnotationModel linkAnn) {
		// Check for fetchColumn first (value collections like enums)
		return linkAnn.getStringValue("fetchColumn").orElseGet(() -> 
			linkAnn.getStringValue("foreignlinkfield").orElseGet(() -> 
				foreignClass != null ?
					determineTableMapping(foreignClass).stream()
						.findFirst()
						.map(m -> m.tableName + "_id")
						.orElse(foreignClass.getSimpleName().toLowerCase() + "_id")
					: 
					null
			)
		);
	}

	// --- ID field determination ---

	/**
	 * Determines all ID fields for a type.
	 */
	public static List<FieldModel> determineIdFields(TypeModel type) {
		List<FieldModel> fields = collectFieldsOfClass(type);
		ArrayList<FieldModel> result = new ArrayList<>();
		for (FieldModel f : fields) {
			if (isId(f)) {
				result.add(f);
			}
		}
		return result;
	}

	/**
	 * Determines the single ID field for a type.
	 * @throws MappingException if the type doesn't have exactly one @Id field
	 */
	public static FieldModel determineIdField(TypeModel type) {
		List<FieldModel> idFields = determineIdFields(type);
		if (idFields.size() != 1) {
			throw new MappingException("Need single id field annotated with @Id on type " + type.getQualifiedName());
		}
		return idFields.get(0);
	}

	/**
	 * Checks if a field is an ID field (has @Id annotation).
	 */
	public static boolean isId(FieldModel f) {
		return f.hasAnnotation(Id.class);
	}

	// --- Type checks ---

	/**
	 * Checks if a type is a list or array (collection type).
	 */
	public static boolean isListOrArray(TypeModel type) {
		if (type.isArray() && !type.getArrayComponentType().isPrimitive()) {
			return true;
		}
		if (type instanceof ReflectionTypeModel) {
			Class<?> clz = ((ReflectionTypeModel) type).getReflectionClass();
			return Iterable.class.isAssignableFrom(clz);
		}
		// For non-reflection types, check by name
		String name = type.getQualifiedName();
		return name.equals("java.util.List") ||
				name.equals("java.util.Set") ||
				name.equals("java.util.Collection") ||
				name.equals("java.lang.Iterable");
	}

	/**
	 * Checks if a type is a linked class (has @Table annotation).
	 */
	public static boolean isLinkedClass(TypeModel type) {
		return !type.isPrimitive() && determineTableMapping(type).size() > 0;
	}

	/** Backward compatible overload */
	public static boolean isLinkedClass(Class<?> type) {
		return isLinkedClass(new ReflectionTypeModel(type));
	}

	/**
	 * Checks if a field is embedded (has @Embedded annotation).
	 */
	public static boolean isEmbedded(FieldModel f) {
		return f.hasAnnotation(Embedded.class);
	}

	public static boolean isTransient(FieldModel field) {
		return field.hasAnnotation(Transient.class) || field.isTransient();
	}

	/**
	 * Gets the component type of a collection or array field.
	 */
	public static TypeModel getCollectionComponentType(FieldModel field) {
		TypeModel type = field.getType();
		if (type.isArray()) {
			return type.getArrayComponentType();
		}
		return type.getTypeArgument();
	}

	// --- Table mapping ---

	/**
	 * Determines the table mappings for a type by walking the class hierarchy.
	 */
	public static List<TableMapping> determineTableMapping(TypeModel type) {
		TypeModel current = type;
		TypeModel mappedType = type;
		List<TableMapping> tables = new ArrayList<>();
		List<FieldModel> fields = new ArrayList<>();
		while (current != null) {
			if (mappedType == null) {
				mappedType = current;
			}
			TableInfo tableInfo = PojoMetadata.getTableInfo(current);
			fields.addAll(0, collectFieldsOfClass(current, current.getSuperclass()));
			if (tableInfo != null) {
				String name = tableInfo.name;
				// Check if this is a redundant @Table annotation targeting the same table as an existing mapping
				if (!tables.isEmpty() && tables.get(0).tableName.equals(name)) {
					Logger.getLogger(PojoMetadata.class.getName())
							.warning("Redundant @Table(\"" + name + "\") annotation on " +
									tables.get(0).type.getQualifiedName() + " - same table already mapped by parent " + current.getQualifiedName());
					// Merge fields into existing mapping instead of creating a new one
					tables.get(0).fields.addAll(0, fields);
				} else {
					tables.add(0, new TableMapping(tableInfo.schema, name, mappedType, new ArrayList<>(fields)));
				}
				fields.clear();
				mappedType = null;
			}
			current = current.getSuperclass();
		}
		if (fields.size() > 0 && tables.size() > 0) {
			tables.get(0).fields.addAll(0, fields);
		}
		return tables;
	}

	// --- Field collection ---

	private static TableInfo getTableInfo(TypeModel current) {
		return current.getAnnotation(org.pojoquery.annotations.Table.class)
			.map(ann -> new TableInfo(
				ann.getStringValue().orElse(null),
				ann.getStringValue("schema").orElse("")
			))
			.orElse(null);
	}

	/**
	 * Collects all non-static, non-transient fields of a type.
	 */
	public static List<FieldModel> filterFields(TypeModel type) {
		List<FieldModel> result = new ArrayList<>();
		for (FieldModel f : type.getDeclaredFields()) {
			if (f.isStatic()) {
				continue;
			}
			if (isTransient(f)) {
				continue;
			}
			result.add(f);
		}
		return result;
	}

	/**
	 * Collects fields from a type up to (but not including) a stop type.
	 * If stopAtSuperType is null, collects all fields.
	 */
	public static List<FieldModel> collectFieldsOfClass(TypeModel type, TypeModel stopAtSuperType) {
		List<FieldModel> result = new ArrayList<>();
		Set<String> seenFieldNames = new HashSet<>();
		TypeModel current = type;
		// Walk from subclass to superclass, collecting fields
		// If a subclass declares a field with the same name as a superclass field,
		// the subclass version takes precedence (specialization)
		while (current != null && (stopAtSuperType == null || !current.isSameType(stopAtSuperType))) {
			List<FieldModel> currentFields = filterFields(current);
			// Add fields from current class at the front, but skip if already seen
			// (a subclass already declared a field with that name)
			List<FieldModel> toAdd = new ArrayList<>();
			for (FieldModel f : currentFields) {
				if (!seenFieldNames.contains(f.getName())) {
					seenFieldNames.add(f.getName());
					toAdd.add(f);
				}
			}
			result.addAll(0, toAdd);
			current = current.getSuperclass();
		}
		return result;
	}

	/**
	 * Collects all fields from a type.
	 */
	public static List<FieldModel> collectFieldsOfClass(TypeModel type) {
		return collectFieldsOfClass(type, null);
	}

	// --- Embedded prefix ---

	/**
	 * Determines the prefix for an embedded field.
	 */
    public static String determinePrefix(FieldModel f) {
        return f.getAnnotation(Embedded.class)
        .flatMap(ann -> ann.getStringValue("prefix"))
        .map(s -> s.equals(Embedded.DEFAULT) ? f.getName() + "_" : s)
        .orElse("");
    }
	
	/**
	 * Builds a list of qualified field names (table.fieldName).
	 */
	public static List<String> getFieldNames(String table, List<FieldModel> fields) {
		List<String> fieldNames = new ArrayList<>();
		for (FieldModel f : fields) {
			fieldNames.add(table + "." + f.getName());
		}
		return fieldNames;
	}
}
