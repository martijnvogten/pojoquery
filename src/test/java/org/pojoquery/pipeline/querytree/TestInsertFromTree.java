package org.pojoquery.pipeline.querytree;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

public class TestInsertFromTree {

	@Table("person")
	static class Person {
		@Id
		Long id;
		String name;
	}

	@Table("article")
	static class Article {
		@Id
		Long id;
		String title;
		Person author;
	}

	@Table("order")
	static class Order {
		@Id
		Long id;
		String orderNumber;
		List<LineItem> items;
	}

	@Table("line_item")
	static class LineItem {
		@Id
		Long id;
		String product;
		Integer quantity;
	}

	interface DatabaseOperations {
		/** Returns generated ID (or null if no auto-generated key) */
		Object insert(String table, String schema, Map<String, Object> values);
		void update(String table, String schema, Map<String, Object> values, Map<String, Object> where);
		void delete(String table, String schema, Map<String, Object> where);
		
		/** Link table operations for many-to-many */
		void syncLinkTable(String table, String schema, 
			String ownerFkColumn, Object ownerId,
			String targetFkColumn, List<Object> targetIds);
	}

	/** Recording implementation for testing */
	static class RecordingDb implements DatabaseOperations {
		final List<String> operations = new ArrayList<>();
		final AtomicLong idGenerator = new AtomicLong(100);

		@Override
		public Object insert(String table, String schema, Map<String, Object> values) {
			String fullTable = schema != null ? schema + "." + table : table;
			Long generatedId = idGenerator.getAndIncrement();
			operations.add(String.format("INSERT INTO %s %s → id=%d", fullTable, values, generatedId));
			return generatedId;
		}

		@Override
		public void update(String table, String schema, Map<String, Object> values, Map<String, Object> where) {
			String fullTable = schema != null ? schema + "." + table : table;
			operations.add(String.format("UPDATE %s SET %s WHERE %s", fullTable, values, where));
		}

		@Override
		public void delete(String table, String schema, Map<String, Object> where) {
			String fullTable = schema != null ? schema + "." + table : table;
			operations.add(String.format("DELETE FROM %s WHERE %s", fullTable, where));
		}

		@Override
		public void syncLinkTable(String table, String schema, 
				String ownerFkColumn, Object ownerId,
				String targetFkColumn, List<Object> targetIds) {
			String fullTable = schema != null ? schema + "." + table : table;
			operations.add(String.format("DELETE FROM %s WHERE %s=%s", fullTable, ownerFkColumn, ownerId));
			for (Object targetId : targetIds) {
				operations.add(String.format("INSERT INTO %s {%s=%s, %s=%s}", 
					fullTable, ownerFkColumn, ownerId, targetFkColumn, targetId));
			}
		}
	}

	@Test
	public void testInsertFromTree() {
		Article article = new Article();
		article.title = "My Article";
		article.author = new Person();
		article.author.name = "Alice";

		QueryTree tree = QueryTreeBuilder.from(Article.class);
		System.out.println("Query Tree:" + tree);

		RecordingDb db = new RecordingDb();
		insert(tree, article, db);
		
		System.out.println("\nOperations:");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	@Test
	public void testUpdateFromTree() {
		Article article = new Article();
		article.id = 1L;
		article.title = "Updated Title";
		article.author = new Person();
		article.author.id = 2L;
		article.author.name = "Bob";

		QueryTree tree = QueryTreeBuilder.from(Article.class);
		System.out.println("Query Tree:" + tree);

		RecordingDb db = new RecordingDb();
		update(tree, article, db);
		
		System.out.println("\nOperations:");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	@Test
	public void testInsertWithCollection() {
		Order order = new Order();
		order.orderNumber = "ORD-001";
		order.items = new ArrayList<>();
		order.items.add(createLineItem("Widget", 5));
		order.items.add(createLineItem("Gadget", 3));

		QueryTree tree = QueryTreeBuilder.from(Order.class);
		System.out.println("Query Tree:" + tree);

		RecordingDb db = new RecordingDb();
		insert(tree, order, db);
		
		System.out.println("\nOperations:");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	@Test
	public void testUpdateWithCollection() {
		Order order = new Order();
		order.id = 1L;
		order.orderNumber = "ORD-001-UPDATED";
		order.items = new ArrayList<>();
		order.items.add(createLineItem("New Widget", 10));
		order.items.add(createLineItem("New Gadget", 7));

		QueryTree tree = QueryTreeBuilder.from(Order.class);
		System.out.println("Query Tree:" + tree);

		RecordingDb db = new RecordingDb();
		update(tree, order, db);
		
		System.out.println("\nOperations:");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	private LineItem createLineItem(String product, int quantity) {
		LineItem item = new LineItem();
		item.product = product;
		item.quantity = quantity;
		return item;
	}

	void insert(QueryTree tree, Object entity, DatabaseOperations db) {
		Objects.requireNonNull(tree, "QueryTree cannot be null");
		Objects.requireNonNull(entity, "Entity cannot be null");
		insertRecursive(tree.root(), entity, null, null, db);
	}

	void update(QueryTree tree, Object entity, DatabaseOperations db) {
		Objects.requireNonNull(tree, "QueryTree cannot be null");
		Objects.requireNonNull(entity, "Entity cannot be null");
		updateRecursive(tree.root(), entity, null, null, db);
	}

	void delete(QueryTree tree, Object entity, DatabaseOperations db) {
		Objects.requireNonNull(tree, "QueryTree cannot be null");
		Objects.requireNonNull(entity, "Entity cannot be null");
		deleteRecursive(tree.root(), entity, db);
	}

	/** Collected field values from an entity */
	record FieldValues(Map<String, Object> values, Map<String, Object> idValues, String autoGenIdField) {}

	FieldValues collectFields(JoinedNode node, Object entity) {
		Map<String, Object> values = new LinkedHashMap<>();
		Map<String, Object> idValues = new LinkedHashMap<>();
		String autoGenIdField = null;

		for (FieldSelectionBase fieldBase : node.fields()) {
			if (fieldBase instanceof FieldSelection field && field.field() != null) {
				String fieldName = field.field().getName();
				String columnName = field.columnName() != null ? field.columnName() : fieldName;
				Object value = getFieldValue(entity, fieldName);

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

	void insertRecursive(QueryNode node, Object entity, Object parentId, String parentFkColumn, DatabaseOperations db) {
		if (node instanceof JoinedNode joinedNode) {
			TableInfo tableInfo = joinedNode.tableInfo();
			FieldValues fields = collectFields(joinedNode, entity);
			Map<String, Object> values = new LinkedHashMap<>(fields.values());
			values.putAll(fields.idValues()); // include non-null IDs in insert

			// Set FK to parent if this is a child in a one-to-many
			if (parentId != null && parentFkColumn != null) {
				values.put(parentFkColumn, parentId);
			}

			Object generatedId = db.insert(tableInfo.tableName(), tableInfo.schemaName(), values);
			
			if (fields.autoGenIdField() != null && generatedId != null) {
				setFieldValue(entity, fields.autoGenIdField(), generatedId);
			}

			Object thisEntityId = generatedId != null ? generatedId : getIdValue(joinedNode, entity);

			// Recurse into children
			for (QueryNode child : joinedNode.children()) {
				processChildForInsert(joinedNode, child, entity, thisEntityId, db);
			}
		}
	}

	private void processChildForInsert(JoinedNode parentNode, QueryNode child, Object entity, Object parentId, DatabaseOperations db) {
		if (child instanceof JoinedNode childJoined && childJoined.joinInfo() != null) {
			JoinInfo joinInfo = childJoined.joinInfo();
			if (joinInfo.linkField() == null) return;

			Object childValue = getFieldValue(entity, joinInfo.linkField().getName());
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
					// One-to-many: insert children with FK to parent
					String fkColumn = determineForeignKeyColumn(parentNode);
					for (Object item : items) {
						insertRecursive(child, item, parentId, fkColumn, db);
					}
				}
			} else {
				// Single entity reference
				insertRecursive(child, childValue, null, null, db);
			}
		}
	}

	void updateRecursive(QueryNode node, Object entity, Object parentId, String parentFkColumn, DatabaseOperations db) {
		if (node instanceof JoinedNode joinedNode) {
			TableInfo tableInfo = joinedNode.tableInfo();
			FieldValues fields = collectFields(joinedNode, entity);
			Map<String, Object> values = new LinkedHashMap<>(fields.values());

			// Set FK to parent if provided
			if (parentId != null && parentFkColumn != null) {
				values.put(parentFkColumn, parentId);
			}

			db.update(tableInfo.tableName(), tableInfo.schemaName(), values, fields.idValues());

			Object thisEntityId = getIdValue(joinedNode, entity);

			// Recurse into children
			for (QueryNode child : joinedNode.children()) {
				processChildForUpdate(joinedNode, child, entity, thisEntityId, db);
			}
		}
	}

	private void processChildForUpdate(JoinedNode parentNode, QueryNode child, Object entity, Object parentId, DatabaseOperations db) {
		if (child instanceof JoinedNode childJoined && childJoined.joinInfo() != null) {
			JoinInfo joinInfo = childJoined.joinInfo();
			if (joinInfo.linkField() == null) return;

			Object childValue = getFieldValue(entity, joinInfo.linkField().getName());

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
					// One-to-many: delete all children then reinsert
					String fkColumn = determineForeignKeyColumn(parentNode);
					db.delete(
						childJoined.tableInfo().tableName(), 
						childJoined.tableInfo().schemaName(),
						Map.of(fkColumn, parentId)
					);
					for (Object item : items) {
						// Reset ID so it gets a new one
						clearIdField(childJoined, item);
						insertRecursive(child, item, parentId, fkColumn, db);
					}
				}
			} else if (childValue != null) {
				// Single entity reference
				updateRecursive(child, childValue, null, null, db);
			}
		}
	}

	void deleteRecursive(QueryNode node, Object entity, DatabaseOperations db) {
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
						// Delete owned children
						String fkColumn = determineForeignKeyColumn(joinedNode);
						db.delete(
							childJoined.tableInfo().tableName(),
							childJoined.tableInfo().schemaName(),
							Map.of(fkColumn, thisEntityId)
							);
						}
					} else {
						Object childEntity = getFieldValue(entity, joinInfo.linkField().getName());
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
				Object idValue = getFieldValue(entity, idFieldName);
				where.put(idFieldName, idValue);
			}
			db.delete(tableInfo.tableName(), tableInfo.schemaName(), where);
		}
	}

	private String determineForeignKeyColumn(JoinedNode parentNode) {
		// Convention: parentTableName_id
		return parentNode.tableInfo().tableName() + "_id";
	}

	private Object getIdValue(JoinedNode node, Object entity) {
		if (node.idFieldNames().isEmpty()) return null;
		return getFieldValue(entity, node.idFieldNames().get(0));
	}

	private void clearIdField(JoinedNode node, Object entity) {
		if (!node.idFieldNames().isEmpty()) {
			setFieldValue(entity, node.idFieldNames().get(0), null);
		}
	}

	private Object getFieldValue(Object entity, String fieldName) {
		try {
			Field field = entity.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(entity);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			return null;
		}
	}

	private void setFieldValue(Object entity, String fieldName, Object value) {
		try {
			Field field = entity.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(entity, value);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException("Cannot set field " + fieldName, e);
		}
	}
}
