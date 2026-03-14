package org.pojoquery;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.pojoquery.pipeline.PojoMetadata;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.FieldSelectionBase;
import org.pojoquery.pipeline.querytree.JoinCondition;
import org.pojoquery.pipeline.querytree.JoinInfo;
import org.pojoquery.pipeline.querytree.JoinTableInfo;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.LinkedValueNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionFieldModel;

public final class CascadingUpdater {
    
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
	
	/**
	 * Represents an FK path for building nested subqueries.
	 * Used to construct DELETE statements with nested IN clauses.
	 * Produces SQL with curly brace markers like {column} that the caller resolves.
	 */
	record FkPath(
		String table,
		String schema,
		String idColumn,      // column to select (usually "id")
		String fkColumn,      // FK column pointing to parent
		FkPath parent         // null for root condition
	) {
		/** 
		 * Builds nested subquery condition with curly brace markers.
		 * Base case: {fkColumn} = ?
		 * Recursive: {fkColumn} IN (SELECT {idColumn} FROM {table} WHERE <parent condition>)
		 * The caller (DatabaseOperations.deleteWhere) resolves markers to quoted identifiers.
		 */
		SqlExpression toCondition(Object rootValue) {
			if (parent == null) {
				// Base case: direct equality
				return SqlExpression.sql("{" + fkColumn + "} = ?", rootValue);
			}
			// Recursive: build nested subquery
			SqlExpression parentCondition = parent.toCondition(rootValue);
			String fullTable = schema != null && !schema.isEmpty() 
				? "{" + schema + "}.{" + table + "}"
				: "{" + table + "}";
			String subquerySql = "{" + fkColumn + "} IN (SELECT {" + idColumn + "} FROM " + 
				fullTable + " WHERE " + parentCondition.getSql() + ")";
			return new SqlExpression(subquerySql, parentCondition.getParameters());
		}
	}

	public static <PK> PK insert(QueryTree tree, Object entity, DatabaseOperations db) {
		Objects.requireNonNull(tree, "QueryTree cannot be null");
		Objects.requireNonNull(entity, "Entity cannot be null");
		return insertRecursive(tree.root(), entity, null, null, db);
	}

	public static int update(QueryTree tree, Object entity, DatabaseOperations db) {
		Objects.requireNonNull(tree, "QueryTree cannot be null");
		Objects.requireNonNull(entity, "Entity cannot be null");
		return updateRecursive(tree.root(), entity, null, null, db);
	}

	public static int delete(QueryTree tree, Object entity, DatabaseOperations db) {
		Objects.requireNonNull(tree, "QueryTree cannot be null");
		Objects.requireNonNull(entity, "Entity cannot be null");
		return deleteRecursive(tree.root(), entity, db);
	}

	/** Collected field values from an entity */
	record FieldValues(Map<String, Object> values, Map<String, Object> idValues, String autoGenIdField) {}

	private static FieldValues collectFields(JoinedNode node, Object entity) {
		Map<String, Object> values = new LinkedHashMap<>();
		Map<String, Object> idValues = new LinkedHashMap<>();
		String autoGenIdField = null;

		for (FieldSelectionBase fieldBase : node.fields()) {
			if (fieldBase instanceof FieldSelection field && field.field() != null) {
				String fieldName = field.field().getName();
				String columnName = field.columnName() != null ? field.columnName() : fieldName;
				Object value = getFieldValue(entity, field.field());

				if (node.idFieldNames().contains(fieldName)) {
					if (value == null) {
						autoGenIdField = fieldName;
					} else {
						idValues.put(columnName, value);
					}
				} else {
					values.put(columnName, value);
				}
			}
		}
		return new FieldValues(values, idValues, autoGenIdField);
	}

	private static <PK> PK insertRecursive(QueryNode node, Object entity, Object parentId, String parentFkColumn, DatabaseOperations db) {
		if (node instanceof JoinedNode joinedNode) {
			TableInfo tableInfo = joinedNode.tableInfo();
			FieldValues fields = collectFields(joinedNode, entity);
			Map<String, Object> values = new LinkedHashMap<>(fields.values());
			values.putAll(fields.idValues()); // include non-null IDs in insert

			// Set FK to parent if this is a child in a one-to-many
			if (parentId != null && parentFkColumn != null) {
				values.put(parentFkColumn, parentId);
			}

			node.children().stream()
				.filter(c -> c.joinInfo() != null && c.joinInfo().joinCondition() instanceof JoinCondition.ForeignKeyInParent)
				.map(c -> (JoinedNode) c)
				.forEach(c -> {
					JoinCondition.ForeignKeyInParent fk = (JoinCondition.ForeignKeyInParent) c.joinInfo().joinCondition();
					String fkColumn = fk.foreignKeyColumn();
					Object childId = getIdValue(c, getFieldValue(entity, c.joinInfo().linkField()));
					values.put(fkColumn, childId);
				});

			PK generatedId = db.insert(tableInfo.tableName(), tableInfo.schemaName(), values);
			
			if (fields.autoGenIdField() != null && generatedId != null) {
				setFieldValue(entity, findField(joinedNode, fields.autoGenIdField()), generatedId);
			}

			PK thisEntityId = generatedId != null ? generatedId : getIdValue(joinedNode, entity);

			// Recurse into children
			for (QueryNode child : joinedNode.children()) {
				processChildForInsert(joinedNode, child, entity, thisEntityId, db);
			}

            return thisEntityId;
		}
        return null;
	}

	private static void processChildForInsert(JoinedNode parentNode, QueryNode child, Object entity, Object parentId, DatabaseOperations db) {
		if (child instanceof JoinedNode childJoined && childJoined.joinInfo() != null) {
			JoinInfo joinInfo = childJoined.joinInfo();
			if (joinInfo.linkField() == null) return;

			Object childValue = getFieldValue(entity, joinInfo.linkField());
			if (childValue == null) return;

			if (joinInfo.isCollection()) {
				Collection<?> items = (Collection<?>) childValue;

				if (joinInfo.isManyToMany()) {
					// Many-to-many: sync link table
					JoinTableInfo jti = joinInfo.joinTableInfo();
					List<Object> targetIds = new ArrayList<>();
					for (Object item : items) {
						Object itemId = getIdValue(childJoined, item);
						if (itemId == null) {
							// Insert new entity first
							insertRecursive(child, item, null, null, db);
							itemId = getIdValue(childJoined, item);
						}
						targetIds.add(itemId);
					}
					db.syncLinkTable(
						jti.joinTable().tableName(), jti.joinTable().schemaName(),
						jti.parentFkColumn(), parentId,
						jti.targetFkColumn(), targetIds
					);
				} else {
					if (joinInfo.joinCondition() instanceof JoinCondition.ForeignKeyInChild fkInChild) {
						// One-to-many with FK in child: set FK value on parent entity and insert
						String fkColumn = fkInChild.foreignKeyColumn();
						for (Object item : items) {
							insertRecursive(child, item, parentId, fkColumn, db);
						}
					} 
				}
			}
		}
		if (child instanceof LinkedValueNode valueNode) {
			// Linked value (e.g., @Link Set<Role> roles in UserDetail)
			Object linkedValue = getFieldValue(entity, valueNode.joinInfo().linkField());
			if (linkedValue != null) {
				// For simplicity, only handle collections of linked values here
				if (linkedValue instanceof Collection<?> collection) {
					for (Object item : collection) {
						if ((valueNode.joinInfo().joinCondition() instanceof JoinCondition.ForeignKeyInChild fkInChild)) {
							// Set FK to parent if needed (for non-many-to-many)
							String fkColumn = fkInChild.foreignKeyColumn();
							db.insert(
								valueNode.linkTableName(),
								valueNode.linkTableSchema(),
								Map.of(
									fkColumn, parentId,
									valueNode.fetchColumn(), item.toString() // Assuming fetchColumn is a simple value
								)
							);
						} else {
							// Many-to-many or no FK: just insert into link table
							db.insert(
								valueNode.linkTableName(),
								valueNode.linkTableSchema(),
								Map.of(valueNode.fetchColumn(), item.toString()) // Assuming fetchColumn is a simple value
							);
						}
					}
				}
			}

		}
	}

	private static int updateRecursive(QueryNode node, Object entity, Object parentId, String parentFkColumn, DatabaseOperations db) {
		if (node instanceof JoinedNode joinedNode) {
			TableInfo tableInfo = joinedNode.tableInfo();
			FieldValues fields = collectFields(joinedNode, entity);
			Map<String, Object> values = new LinkedHashMap<>(fields.values());

			// Set FK to parent if provided
			if (parentId != null && parentFkColumn != null) {
				values.put(parentFkColumn, parentId);
			}

			int affectedRows = db.update(tableInfo.tableName(), tableInfo.schemaName(), values, fields.idValues());

			Object thisEntityId = getIdValue(joinedNode, entity);

			// Recurse into children
			for (QueryNode child : joinedNode.children()) {
				processChildForUpdate(joinedNode, child, entity, thisEntityId, db);
			}
			return affectedRows;
		}
        return 0;
	}

	private static void processChildForUpdate(JoinedNode parentNode, QueryNode child, Object entity, Object parentId, DatabaseOperations db) {
		if (child instanceof JoinedNode childJoined && childJoined.joinInfo() != null) {
			JoinInfo joinInfo = childJoined.joinInfo();
			if (joinInfo.linkField() == null) return;

			Object childValue = getFieldValue(entity, joinInfo.linkField());

			if (joinInfo.isCollection()) {
				Collection<?> items = childValue != null ? (Collection<?>) childValue : List.of();

				if (joinInfo.isManyToMany()) {
					// Many-to-many: sync link table (delete all + insert)
					JoinTableInfo jti = joinInfo.joinTableInfo();
					List<Object> targetIds = new ArrayList<>();
					for (Object item : items) {
						Object itemId = getIdValue(childJoined, item);
						if (itemId != null) {
							targetIds.add(itemId);
						}
					}
					db.syncLinkTable(
						jti.joinTable().tableName(), jti.joinTable().schemaName(),
						jti.parentFkColumn(), parentId,
						jti.targetFkColumn(), targetIds
					);
				} else {
					if (joinInfo.joinCondition() instanceof JoinCondition.ForeignKeyInChild fkInChild) {
						// One-to-many: delete all descendants then reinsert
						String fkColumn = fkInChild.foreignKeyColumn();
						
						// Build root path for this child level
						FkPath rootPath = new FkPath(
							childJoined.tableInfo().tableName(),
							childJoined.tableInfo().schemaName(),
							childJoined.idFieldNames().isEmpty() ? "id" : childJoined.idFieldNames().get(0),
							fkColumn,
							null  // root - uses direct equality
						);
						
						// Delete all descendants first (grandchildren, great-grandchildren, etc.)
						deleteDescendantsRecursive(childJoined, rootPath, parentId, db);
						
						// Now safe to delete children
						db.delete(
							childJoined.tableInfo().tableName(), 
							childJoined.tableInfo().schemaName(),
							Map.of(fkColumn, parentId)
						);
						
						// Reinsert items
						for (Object item : items) {
							// Reset ID so it gets a new one
							clearIdField(childJoined, item);
							insertRecursive(child, item, parentId, fkColumn, db);
						}
					}
				}
			} else if (childValue != null) {
				// Single entity reference
				updateRecursive(child, childValue, null, null, db);
			}
		}
		if (child instanceof LinkedValueNode valueNode) {
			// Linked value collection (e.g., @Link(fetchColumn) List<AccessLevel>)
			Object linkedValue = getFieldValue(entity, valueNode.joinInfo().linkField());
			Collection<?> collection = linkedValue != null ? (Collection<?>) linkedValue : List.of();
			
			if (valueNode.joinInfo().joinCondition() instanceof JoinCondition.ForeignKeyInChild fkInChild) {
				String fkColumn = fkInChild.foreignKeyColumn();
				
				// Delete existing values
				db.delete(valueNode.linkTableName(), valueNode.linkTableSchema(), Map.of(fkColumn, parentId));
				
				// Insert new values
				for (Object item : collection) {
					db.insert(
						valueNode.linkTableName(),
						valueNode.linkTableSchema(),
						Map.of(
							fkColumn, parentId,
							valueNode.fetchColumn(), item.toString()
						)
					);
				}
			}
		}
	}

	private static int deleteRecursive(QueryNode node, Object entity, DatabaseOperations db) {
		if (node instanceof JoinedNode joinedNode) {
			Object thisEntityId = getIdValue(joinedNode, entity);

			// Delete children first (reverse order)
			for (QueryNode child : joinedNode.children()) {
				if (child instanceof JoinedNode childJoined && childJoined.joinInfo() != null) {
					JoinInfo joinInfo = childJoined.joinInfo();
					if (joinInfo.linkField() == null) continue;

					if (joinInfo.isCollection()) {
						if (joinInfo.isManyToMany()) {
							// Delete link table rows
							JoinTableInfo jti = joinInfo.joinTableInfo();
						db.delete(
							jti.joinTable().tableName(), jti.joinTable().schemaName(),
							Map.of(jti.parentFkColumn(), thisEntityId)
						);
					} else {
						if (joinInfo.joinCondition() instanceof JoinCondition.ForeignKeyInChild fkInChild) {
							// Delete owned children (and their descendants)
							String fkColumn = fkInChild.foreignKeyColumn();
							
							// Build root path for this child level
							FkPath rootPath = new FkPath(
								childJoined.tableInfo().tableName(),
								childJoined.tableInfo().schemaName(),
								childJoined.idFieldNames().isEmpty() ? "id" : childJoined.idFieldNames().get(0),
								fkColumn,
								null
							);
							
							// Delete all descendants first
							deleteDescendantsRecursive(childJoined, rootPath, thisEntityId, db);
							
							// Now safe to delete children
							db.delete(
								childJoined.tableInfo().tableName(),
								childJoined.tableInfo().schemaName(),
								Map.of(fkColumn, thisEntityId)
							);
							}
						}
					} else {
						Object childEntity = getFieldValue(entity, joinInfo.linkField());
						if (childEntity != null) {
							deleteRecursive(child, childEntity, db);
						}
					}
				}
			}

			// Delete this entity
			TableInfo tableInfo = joinedNode.tableInfo();
			Map<String, Object> where = new LinkedHashMap<>();
			for (String idFieldName : joinedNode.idFieldNames()) {
				Object idValue = getFieldValue(entity, findField(joinedNode, idFieldName));
				where.put(idFieldName, idValue);
			}
			return db.delete(tableInfo.tableName(), tableInfo.schemaName(), where);
		}
        return 0;
	}

	/**
	 * Recursively deletes all descendants of nodes matching the given FK path.
	 * Depth-first traversal ensures grandchildren are deleted before children.
	 * 
	 * @param node The node whose descendants should be deleted
	 * @param parentPath The FK path to reach entities at the parent level
	 * @param rootValue The root FK value (e.g., owner.id = 5)
	 */
	private static void deleteDescendantsRecursive(
			JoinedNode node,
			FkPath parentPath,
			Object rootValue,
			DatabaseOperations db) {
		
		// Check each child of this node
		for (QueryNode child : node.children()) {
			if (child instanceof JoinedNode childJoined && isCascadedCollection(childJoined)) {
				JoinCondition.ForeignKeyInChild fk = 
					(JoinCondition.ForeignKeyInChild) childJoined.joinInfo().joinCondition();
				
				// Build path to this child level
				// table/schema/idColumn are from the PARENT (the table we SELECT FROM in subquery)
				// fkColumn is the child's FK that references the parent
				FkPath childPath = new FkPath(
					node.tableInfo().tableName(),
					node.tableInfo().schemaName(),
					node.idFieldNames().isEmpty() ? "id" : node.idFieldNames().get(0),
					fk.foreignKeyColumn(),
					parentPath
				);
				
				// Recurse deeper first (depth-first)
				deleteDescendantsRecursive(childJoined, childPath, rootValue, db);
				
				// Now delete this level using nested subquery
				SqlExpression condition = childPath.toCondition(rootValue);
				db.deleteWhere(
					childJoined.tableInfo().tableName(),
					childJoined.tableInfo().schemaName(),
					condition
				);
			}
		}
	}
	
	/**
	 * Checks if a node represents a cascaded one-to-many collection.
	 */
	private static boolean isCascadedCollection(JoinedNode node) {
		JoinInfo ji = node.joinInfo();
		return ji != null
			&& ji.isCollection()
			&& !ji.isManyToMany()
			&& ji.joinCondition() instanceof JoinCondition.ForeignKeyInChild;
	}

    @SuppressWarnings("unchecked")
	private static <PK> PK getIdValue(JoinedNode node, Object entity) {
		if (node.idFieldNames().isEmpty()) return null;
		return (PK) getFieldValue(entity, findField(node, node.idFieldNames().get(0)));
	}

	private static void clearIdField(JoinedNode node, Object entity) {
		if (!node.idFieldNames().isEmpty()) {
			setFieldValue(entity, findField(node, node.idFieldNames().get(0)), null);
		}
	}

	private static Object getFieldValue(Object entity, FieldModel fieldModel) {
		try {
			Field field = ((ReflectionFieldModel)fieldModel).getReflectionField();
			field.setAccessible(true);
			return field.get(entity);
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	private static FieldModel findField(JoinedNode node, String fieldName) {
		return PojoMetadata.collectFieldsOfClass(node.type()).stream()
			.filter(f -> f.getName().equals(fieldName))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("Field not found: " + fieldName));
	}

	private static void setFieldValue(Object entity, FieldModel fieldModel, Object value) {
		try {
			Field field = ((ReflectionFieldModel)fieldModel).getReflectionField();
			field.setAccessible(true);
			field.set(entity, value);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Cannot set field " + fieldModel.getName(), e);
		}
	}

}
