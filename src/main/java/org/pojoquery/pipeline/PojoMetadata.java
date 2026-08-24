package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.pojoquery.TableInfo;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.annotations.Transient;
import org.pojoquery.internal.MappingException;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.typemodel.FieldModel;
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

	// --- Field name determination ---


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

	public static boolean isTransient(FieldModel field) {
		return field.hasAnnotation(Transient.class) || field.isTransient();
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
			TableInfo tableInfoAnnotation = PojoMetadata.getTableInfo(current);
			fields.addAll(0, collectFieldsOfClass(current, current.getSuperclass()));
			if (tableInfoAnnotation != null) {
				String name = tableInfoAnnotation.name;
				// Check if this is a redundant @Table annotation targeting the same table as an existing mapping
				if (!tables.isEmpty() && tables.get(0).tableName.equals(name)) {
					Logger.getLogger(PojoMetadata.class.getName())
							.warning("Redundant @Table(\"" + name + "\") annotation on " +
									tables.get(0).type.getQualifiedName() + " - same table already mapped by parent " + current.getQualifiedName());
					// Merge fields into existing mapping instead of creating a new one
					// tables.get(0).fields.addAll(0, fields);
				} else {
					tables.add(0, new TableMapping(tableInfoAnnotation.schema, name, mappedType, new ArrayList<>(fields)));
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
		if (current.hasAnnotation(Table.class)) {
			return new TableInfo(
				current.getAnnotationAttributeValue(Table.class, "value", String.class),
				current.getAnnotationAttributeValue(Table.class, "schema", String.class)
			);
		}
		return null;
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

	/**
	 * Builds a list of qualified field names in curly-marker form ({table.fieldName}),
	 * so the query pipeline resolves them to properly quoted identifiers.
	 */
	public static List<String> getFieldNames(String table, List<FieldModel> fields) {
		List<String> fieldNames = new ArrayList<>();
		for (FieldModel f : fields) {
			fieldNames.add("{" + table + "." + f.getName() + "}");
		}
		return fieldNames;
	}
}
