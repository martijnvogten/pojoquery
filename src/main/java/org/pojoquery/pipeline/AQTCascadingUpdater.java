package org.pojoquery.pipeline;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.AbstractQueryTree.ColumnFieldNode;
import org.pojoquery.pipeline.AbstractQueryTree.EmbeddedEntity;
import org.pojoquery.pipeline.AbstractQueryTree.EntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.EntityReference;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableJoin;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKeyField;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.AbstractQueryTree.TPSSuperClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.ValueCollection;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionFieldModel;

/**
 * Cascading insert/update/delete for the AbstractQueryTree model.
 */
public class AQTCascadingUpdater {

	/**
	 * Abstraction for database operations, allowing testability and flexibility.
	 */
	public interface DatabaseOperations {
		/** Returns generated ID (or null if no auto-generated key) */
		<PK> PK insert(String table, String schema, Map<String, Object> values);
		
		int update(String table, String schema, Map<String, Object> values, Map<String, Object> where);
		
		int delete(String table, String schema, Map<String, Object> where);
		
		/** Delete with a SQL expression condition (for subquery-based deletes) */
		int deleteWhere(String table, String schema, SqlExpression condition);
		
		/** Link table operations for many-to-many */
		void syncLinkTable(String table, String schema, 
			String ownerFkColumn, Object ownerId,
			String targetFkColumn, List<Object> targetIds);
	}

	/** Collected field values from an entity, split into regular values and ID values */
	record FieldValues(Map<String, Object> values, Map<String, Object> idValues, String autoGenIdField) {}

	/**
	 * FK path for building nested subqueries in cascading deletes.
	 * Produces SQL with curly brace markers that the DatabaseOperations resolves.
	 */
	record FkPath(String table, String schema, String idColumn, String fkColumn, FkPath parent) {
		SqlExpression toCondition(Object rootValue) {
			if (parent == null) {
				return SqlExpression.sql("{" + fkColumn + "} = ?", rootValue);
			}
			SqlExpression parentCondition = parent.toCondition(rootValue);
			String fullTable = schema != null && !schema.isEmpty()
				? "{" + schema + "}.{" + table + "}"
				: "{" + table + "}";
			return new SqlExpression(
				"{" + fkColumn + "} IN (SELECT {" + idColumn + "} FROM " + fullTable + " WHERE " + parentCondition.getSql() + ")",
				parentCondition.getParameters()
			);
		}
	}

	// ==================== INSERT ====================

	public static <PK> PK insert(RootNode tree, Object entity, DatabaseOperations db) {
		Objects.requireNonNull(tree, "QueryTree cannot be null");
		Objects.requireNonNull(entity, "Entity cannot be null");
		return insertNode(tree, entity, null, null, db);
	}

	@SuppressWarnings("unchecked")
	private static <PK> PK insertNode(TableNode node, Object entity, Object parentId, String parentFkColumn, DatabaseOperations db) {
		if (entity == null) return null;

		// For TPS inheritance, insert superclass first to get the ID
		TPSSuperClassNode superClass = findTPSSuperClass(node);
		PK inheritedId = null;
		if (superClass != null) {
			// Recursively insert superclass (handles multi-level TPS)
			inheritedId = insertNode(superClass, entity, null, null, db);
		}

		FieldValues fields = collectFieldValues(node, entity);
		Map<String, Object> values = new LinkedHashMap<>(fields.values());
		values.putAll(fields.idValues()); // non-null IDs go into INSERT

		// Collect values from embedded children
		collectEmbeddedValues(node, entity, values);
		
		// Collect FK values from EntityReference children (FK in parent)
		collectEntityReferenceValues(node, entity, values);

		// Add FK to parent if this is a child in a one-to-many
		if (parentId != null && parentFkColumn != null) {
			values.put(parentFkColumn, parentId);
		}

		// For TPS subclass, the ID comes from superclass insert
		PK generatedId;
		if (inheritedId != null) {
			// Use inherited ID as PK for this table (FK to superclass)
			String idColumn = superClass.join().fkColumnName();
			values.put(idColumn, inheritedId);
			db.insert(node.tableInfo().tableName(), node.tableInfo().schemaName(), values);
			generatedId = inheritedId;
		} else {
			// Normal insert with auto-generated ID
			generatedId = db.insert(node.tableInfo().tableName(), node.tableInfo().schemaName(), values);
			// Set auto-generated ID back on entity
			if (fields.autoGenIdField() != null && generatedId != null) {
				setFieldValue(entity, findPrimaryKeyField(node, fields.autoGenIdField()).field(), generatedId);
			}
		}

		PK entityId = generatedId != null ? generatedId : (PK) getIdValue(node, entity);

		// Process children: one-to-many, many-to-many, value collections (skip TPSSuperClassNode - already handled)
		for (QueryNode child : node.children()) {
			if (!(child instanceof TPSSuperClassNode)) {
				processChildForInsert(child, entity, entityId, db);
			}
		}

		return entityId;
	}

	private static void processChildForInsert(QueryNode child, Object parentEntity, Object parentId, DatabaseOperations db) {
		if (child instanceof EntityCollection coll) {
			Object childValue = getFieldValue(parentEntity, coll.field());
			if (childValue instanceof Collection<?> items) {
				String fkColumn = coll.join().fkColumnName();
				for (Object item : items) {
					insertNode(coll, item, parentId, fkColumn, db);
				}
			}
		} else if (child instanceof JoinTableEntityCollection jtc) {
			Object childValue = getFieldValue(parentEntity, jtc.field());
			if (childValue instanceof Collection<?> items) {
				JoinTableJoin join = jtc.join();
				List<Object> targetIds = new ArrayList<>();
				for (Object item : items) {
					Object itemId = getIdValue(jtc, item);
					if (itemId == null) {
						// Insert new entity first
						insertNode(jtc, item, null, null, db);
						itemId = getIdValue(jtc, item);
					}
					targetIds.add(itemId);
				}
				db.syncLinkTable(
					join.joinTableInfo().tableInfo().tableName(),
					join.joinTableInfo().tableInfo().schemaName(),
					join.parentKey().fkColumnName(), parentId,
					join.childKey().fkColumnName(), targetIds);
			}
		} else if (child instanceof ValueCollection vc) {
			Object childValue = getFieldValue(parentEntity, vc.field());
			if (childValue instanceof Collection<?> items) {
				String fkColumn = vc.join().fkColumnName();
				for (Object item : items) {
					db.insert(vc.joinTable().tableName(), vc.joinTable().schemaName(),
						Map.of(fkColumn, parentId, vc.fetchColumn(), toStorableValue(item)));
				}
			}
		}
		// EntityReference handled in parent INSERT, Embedding handled separately
	}

	// ==================== UPDATE ====================

	public static int update(RootNode tree, Object entity, DatabaseOperations db) {
		Objects.requireNonNull(tree, "QueryTree cannot be null");
		Objects.requireNonNull(entity, "Entity cannot be null");
		return updateNode(tree, entity, null, null, db);
	}

	private static int updateNode(TableNode node, Object entity, Object parentId, String parentFkColumn, DatabaseOperations db) {
		if (entity == null) return 0;

		// For TPS inheritance, update superclass first
		TPSSuperClassNode superClass = findTPSSuperClass(node);
		if (superClass != null) {
			updateNode(superClass, entity, null, null, db);
		}

		FieldValues fields = collectFieldValues(node, entity);
		Map<String, Object> values = new LinkedHashMap<>(fields.values());

		if (parentId != null && parentFkColumn != null) {
			values.put(parentFkColumn, parentId);
		}

		collectEmbeddedValues(node, entity, values);
		collectEntityReferenceValues(node, entity, values);

		// For TPS subclass, use the superclass ID for the WHERE clause
		Map<String, Object> whereClause = fields.idValues();
		if (superClass != null && whereClause.isEmpty()) {
			// ID is in superclass, get it from there
			Object entityId = getIdValue(superClass, entity);
			String idColumn = superClass.join().fkColumnName();
			whereClause = Map.of(idColumn, entityId);
		}

		int affectedRows = db.update(node.tableInfo().tableName(), node.tableInfo().schemaName(), values, whereClause);

		Object entityId = getIdValue(node, entity);

		// Process children (skip TPSSuperClassNode - already handled)
		for (QueryNode child : node.children()) {
			if (!(child instanceof TPSSuperClassNode)) {
				processChildForUpdate(node, child, entity, entityId, db);
			}
		}

		return affectedRows;
	}

	private static void processChildForUpdate(TableNode parentNode, QueryNode child, Object parentEntity, Object parentId, DatabaseOperations db) {
		if (child instanceof EntityCollection coll) {
			Object childValue = getFieldValue(parentEntity, coll.field());
			Collection<?> items = childValue instanceof Collection<?> c ? c : List.of();
			String fkColumn = coll.join().fkColumnName();
			String idColumn = findIdColumnName(coll);

			// Build FK path for cascading delete of descendants
			FkPath rootPath = new FkPath(
				coll.tableInfo().tableName(),
				coll.tableInfo().schemaName(),
				idColumn,
				fkColumn,
				null
			);
			
			// Delete all descendants first (depth-first to respect FK constraints)
			deleteDescendants(coll, rootPath, parentId, db);
			
			// Delete existing children
			db.delete(coll.tableInfo().tableName(), coll.tableInfo().schemaName(), Map.of(fkColumn, parentId));
			
			// Re-insert children
			for (Object item : items) {
				clearIdField(coll, item);
				insertNode(coll, item, parentId, fkColumn, db);
			}
		} else if (child instanceof JoinTableEntityCollection jtc) {
			Object childValue = getFieldValue(parentEntity, jtc.field());
			Collection<?> items = childValue instanceof Collection<?> c ? c : List.of();
			List<Object> targetIds = new ArrayList<>();
			for (Object item : items) {
				Object itemId = getIdValue(jtc, item);
				if (itemId != null) {
					targetIds.add(itemId);
				}
			}
			JoinTableJoin join = jtc.join();
			db.syncLinkTable(
				join.joinTableInfo().tableInfo().tableName(),
				join.joinTableInfo().tableInfo().schemaName(),
				join.parentKey().fkColumnName(), parentId,
				join.childKey().fkColumnName(), targetIds);
		} else if (child instanceof ValueCollection vc) {
			Object childValue = getFieldValue(parentEntity, vc.field());
			Collection<?> items = childValue instanceof Collection<?> c ? c : List.of();
			String fkColumn = vc.join().fkColumnName();

			// Delete existing values
			db.delete(vc.joinTable().tableName(), vc.joinTable().schemaName(), Map.of(fkColumn, parentId));

			// Insert new values
			for (Object item : items) {
				db.insert(vc.joinTable().tableName(), vc.joinTable().schemaName(),
					Map.of(fkColumn, parentId, vc.fetchColumn(), toStorableValue(item)));
			}
		} else if (child instanceof EntityReference ref) {
			Object childEntity = getFieldValue(parentEntity, ref.field());
			if (childEntity != null) {
				updateNode(ref, childEntity, null, null, db);
			}
		}
	}

	// ==================== DELETE ====================

	public static int delete(RootNode tree, Object entity, DatabaseOperations db) {
		Objects.requireNonNull(tree, "QueryTree cannot be null");
		Objects.requireNonNull(entity, "Entity cannot be null");
		return deleteNode(tree, entity, db);
	}

	private static int deleteNode(TableNode node, Object entity, DatabaseOperations db) {
		Object entityId = getIdValue(node, entity);

		// Delete children first (reverse order for FK constraints)
		// Skip TPSSuperClassNode - will be deleted after this node
		for (QueryNode child : node.children()) {
			if (!(child instanceof TPSSuperClassNode)) {
				processChildForDelete(child, entity, entityId, db);
			}
		}

		// For TPS subclass, find the FK column name
		TPSSuperClassNode superClass = findTPSSuperClass(node);
		String idColumn;
		if (superClass != null) {
			idColumn = superClass.join().fkColumnName();
		} else {
			idColumn = findIdColumnName(node);
		}

		// Delete this entity
		Map<String, Object> where = new LinkedHashMap<>();
		where.put(idColumn, entityId);
		int result = db.delete(node.tableInfo().tableName(), node.tableInfo().schemaName(), where);

		// For TPS inheritance, delete superclass after subclass (FK constraint order)
		if (superClass != null) {
			deleteNode(superClass, entity, db);
		}

		return result;
	}

	private static void processChildForDelete(QueryNode child, Object parentEntity, Object parentId, DatabaseOperations db) {
		if (child instanceof EntityCollection coll) {
			String fkColumn = coll.join().fkColumnName();
			String idColumn = findIdColumnName(coll);
			
			FkPath rootPath = new FkPath(
				coll.tableInfo().tableName(),
				coll.tableInfo().schemaName(),
				idColumn,
				fkColumn,
				null
			);
			deleteDescendants(coll, rootPath, parentId, db);
			db.delete(coll.tableInfo().tableName(), coll.tableInfo().schemaName(), Map.of(fkColumn, parentId));
		} else if (child instanceof JoinTableEntityCollection jtc) {
			JoinTableJoin join = jtc.join();
			db.delete(
				join.joinTableInfo().tableInfo().tableName(),
				join.joinTableInfo().tableInfo().schemaName(),
				Map.of(join.parentKey().fkColumnName(), parentId));
		} else if (child instanceof ValueCollection vc) {
			String fkColumn = vc.join().fkColumnName();
			db.delete(vc.joinTable().tableName(), vc.joinTable().schemaName(), Map.of(fkColumn, parentId));
		}
		// EntityReference: Don't cascade delete referenced entities (they're not owned)
	}

	/**
	 * Recursively delete descendants using nested subqueries.
	 * Depth-first to respect FK constraints (grandchildren before children).
	 */
	private static void deleteDescendants(TableNode node, FkPath parentPath, Object rootValue, DatabaseOperations db) {
		for (QueryNode child : node.children()) {
			if (child instanceof EntityCollection childColl) {
				String childFkColumn = childColl.join().fkColumnName();
				
				FkPath childPath = new FkPath(
					node.tableInfo().tableName(),
					node.tableInfo().schemaName(),
					findIdColumnName(node),
					childFkColumn,
					parentPath
				);
				
				// Recurse deeper first
				deleteDescendants(childColl, childPath, rootValue, db);
				
				// Delete this level using nested subquery
				SqlExpression condition = childPath.toCondition(rootValue);
				db.deleteWhere(childColl.tableInfo().tableName(), childColl.tableInfo().schemaName(), condition);
			}
		}
	}

	// ==================== HELPER METHODS ====================

	private static FieldValues collectFieldValues(TableNode node, Object entity) {
		Map<String, Object> values = new LinkedHashMap<>();
		Map<String, Object> idValues = new LinkedHashMap<>();
		String autoGenIdField = null;

		for (QueryNode child : node.children()) {
			if (child instanceof ColumnFieldNode col && col.field() != null) {
				String fieldName = col.field().getName();
				String columnName = col.columnName();
				Object value = getFieldValue(entity, col.field());

				if (col instanceof PrimaryKeyField pk) {
					if (Boolean.TRUE.equals(pk.isAutoGenerated())) {
						autoGenIdField = fieldName;
					}
					// Always include ID value in idValues if it has a value
					// (needed for UPDATE WHERE clause)
					if (value != null) {
						idValues.put(columnName, value);
					}
				} else if (col instanceof ScalarValue) {
					values.put(columnName, value);
				}
				// EntityReference handled separately in collectEntityReferenceValues
			}
		}
		return new FieldValues(values, idValues, autoGenIdField);
	}

	private static void collectEmbeddedValues(TableNode node, Object entity, Map<String, Object> values) {
		for (QueryNode child : node.children()) {
			if (child instanceof EmbeddedEntity emb) {
				Object embeddedValue = getFieldValue(entity, emb.field());
				if (embeddedValue != null) {
					for (QueryNode embChild : emb.children()) {
						if (embChild instanceof ColumnFieldNode col) {
							values.put(col.columnName(), getFieldValue(embeddedValue, col.field()));
						}
					}
				}
			}
		}
	}

	private static void collectEntityReferenceValues(TableNode node, Object entity, Map<String, Object> values) {
		for (QueryNode child : node.children()) {
			if (child instanceof EntityReference ref) {
				Object refValue = getFieldValue(entity, ref.field());
				String fkColumn = ref.columnName();
				if (refValue != null) {
					Object refId = getIdValue(ref, refValue);
					values.put(fkColumn, refId);
				} else {
					values.put(fkColumn, null);
				}
			}
		}
	}

	private static Object getIdValue(TableNode node, Object entity) {
		if (entity == null) return null;
		PrimaryKeyField pk = findPrimaryKeyField(node);
		if (pk != null) {
			return getFieldValue(entity, pk.field());
		}
		// For TPS subclass, ID is in the superclass
		TPSSuperClassNode superClass = findTPSSuperClass(node);
		if (superClass != null) {
			return getIdValue(superClass, entity);
		}
		return null;
	}

	/** Find TPSSuperClassNode child if this is a TPS subclass */
	private static TPSSuperClassNode findTPSSuperClass(TableNode node) {
		return node.children().stream()
			.filter(TPSSuperClassNode.class::isInstance)
			.map(TPSSuperClassNode.class::cast)
			.findFirst()
			.orElse(null);
	}

	private static PrimaryKeyField findPrimaryKeyField(TableNode node) {
		return node.children().stream()
			.filter(PrimaryKeyField.class::isInstance)
			.map(PrimaryKeyField.class::cast)
			.findFirst()
			.orElse(null);
	}

	private static PrimaryKeyField findPrimaryKeyField(TableNode node, String fieldName) {
		return node.children().stream()
			.filter(c -> c instanceof PrimaryKeyField pk && pk.field().getName().equals(fieldName))
			.map(PrimaryKeyField.class::cast)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Primary key field '" + fieldName + "' not found"));
	}

	private static String findIdColumnName(TableNode node) {
		PrimaryKeyField pk = findPrimaryKeyField(node);
		return pk != null ? ((ColumnFieldNode) pk).columnName() : "id";
	}

	private static void clearIdField(TableNode node, Object entity) {
		PrimaryKeyField pk = findPrimaryKeyField(node);
		if (pk != null && Boolean.TRUE.equals(pk.isAutoGenerated())) {
			setFieldValue(entity, pk.field(), null);
		}
	}

	private static Object toStorableValue(Object value) {
		// Convert enums and other types to storable form
		return value instanceof Enum<?> e ? e.name() : value;
	}

	private static Object getFieldValue(Object entity, FieldModel fieldModel) {
		try {
			Field field = ((ReflectionFieldModel) fieldModel).getReflectionField();
			field.setAccessible(true);
			return field.get(entity);
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	private static void setFieldValue(Object entity, FieldModel fieldModel, Object value) {
		try {
			Field field = ((ReflectionFieldModel) fieldModel).getReflectionField();
			field.setAccessible(true);
			field.set(entity, value);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Cannot set field " + fieldModel.getName(), e);
		}
	}
}
