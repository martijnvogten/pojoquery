package org.pojoquery.pipeline;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.pojoquery.JdbcValueMapper;
import org.pojoquery.pipeline.AbstractQueryTree.ColumnFieldNode;
import org.pojoquery.pipeline.AbstractQueryTree.CustomQueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.EmbeddedEntity;
import org.pojoquery.pipeline.AbstractQueryTree.EntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.EntityNode;
import org.pojoquery.pipeline.AbstractQueryTree.EntityReference;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.MappedFieldNode;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKey;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.STISubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarNode;
import org.pojoquery.pipeline.AbstractQueryTree.SubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.SuperClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.AbstractQueryTree.ValueCollection;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionFieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;


public class AQTRowProcessor<R> {

	private final RootNode tree;
	private final Consumer<R> entityCallback;
	private R rootEntity = null;
	private Object rootEntityPrimaryKeyValue = null;
	private Map<EntityKey, Object> allEntitiesByPrimaryKey = new HashMap<>();
	record EntityKey(String tableAlias, Object pkValue) {
	}

	public AQTRowProcessor(RootNode tree, Consumer<R> entityCallback) {
		this.tree = tree;
		this.entityCallback = entityCallback;
	}
	
	public void processRow(Map<String,Object> row) throws SQLException{
		processRowRecursive(tree, row, new HashMap<>());
	}

	// Do a leave-first traversal of the query tree, constructing entities as we go
	// and setting their fields based on the row data. 
	@SuppressWarnings("unchecked")
	private void processRowRecursive(TableNode tableNode, Map<String, Object> row, Map<String, Object> entitiesOnThisRow) throws SQLException {
		for (AbstractQueryTree.QueryNode child : tableNode.children()) {
			if (child instanceof TableNode childTableNode && !(child instanceof SuperClassNode)) {
				processRowRecursive(childTableNode, row, entitiesOnThisRow);
			}
		}

		if (allNulls(tableNode, row)) {
			return;
		}
		
		Object pkValue = getPrimaryKeyValue(tableNode, row);
		if (pkValue != null && allEntitiesByPrimaryKey.containsKey(new EntityKey(tableNode.alias(), pkValue))) {
			entitiesOnThisRow.put(tableNode.alias(), allEntitiesByPrimaryKey.get(new EntityKey(tableNode.alias(), pkValue)));
		}
		Object entity = entitiesOnThisRow.get(tableNode.alias());
		if (entity == null) {
			if (tableNode instanceof STISubClassNode subClassNode) {
				String discValue = (String) row.get(subClassNode.alias() + "._discriminator");
				if (discValue == null || !discValue.equals(subClassNode.discriminatorValue())) {
					// This row does not correspond to this subclass, so skip processing it
					return;
				}
			}
			entity = constructEntity(tableNode.type());
			allEntitiesByPrimaryKey.put(new EntityKey(tableNode.alias(), pkValue), entity);
			entitiesOnThisRow.put(tableNode.alias(), entity);
		}

		// At this point subclasses have been handled, so the concrete entity
		// for this node has been constructed and is available in entitiesOnThisRow
		for (QueryNode child : tableNode.children()) {
			if (child instanceof SuperClassNode superClass) {
				entitiesOnThisRow.put(superClass.alias(), entity);
				processRowRecursive(superClass, row, entitiesOnThisRow);
			}
		}

		for (QueryNode child : tableNode.children()) {
			if (child instanceof CustomQueryNode customNode) {
				customNode.applyRowResultToEntity(tableNode, entity, row);
			} else if (child instanceof EntityNode ref && (child instanceof EmbeddedEntity || child instanceof EntityReference)) {
				Object referencedEntity = entitiesOnThisRow.get(ref.alias());
				if (referencedEntity != null) {
					setFieldValue(entity, ref.field(), referencedEntity);
				}
			} else if (child instanceof EntityCollection ec) {
				addToCollection(entity, ec.field(), entitiesOnThisRow.get(ec.alias()), ec.valueMapper(), ec.type());
			} else if (child instanceof JoinTableEntityCollection jtec) {
				addToCollection(entity, jtec.field(), entitiesOnThisRow.get(jtec.alias()), jtec.valueMapper(), jtec.type());
			} else if (child instanceof ValueCollection vc) {
				addToCollection(entity, vc.field(), row.get(vc.alias() + ".value"), vc.valueMapper(), vc.componentType());
			} else if (child instanceof MappedFieldNode mappedFieldNode) {
				Object value = row.get(tableNode.alias() + "." + mappedFieldNode.field().getName());
				JdbcValueMapper valueMapper = mappedFieldNode.valueMapper();
				if (valueMapper != null) {
					value = valueMapper.mapValue(value);
				}
				setFieldValue(entity, mappedFieldNode.field(), value);
			} else if (child instanceof ColumnFieldNode scalar) {
				Object value = row.get(tableNode.alias() + "." + scalar.field().getName());
				setFieldValue(entity, scalar.field(), scalar.valueMapper().mapValue(value));
			}
		}

		if (tableNode instanceof SubClassNode subClass) {
			entitiesOnThisRow.put(subClass.parentAlias(), entity);
		}

		if (tableNode instanceof RootNode) {
			if (rootEntity == null || pkValue == null || !pkValue.equals(rootEntityPrimaryKeyValue)) {
				if (rootEntity != null) {
					entityCallback.accept(rootEntity);
				}
				rootEntity = (R) entity;
				rootEntityPrimaryKeyValue = pkValue;
			}
		}
	}

	private Object constructEntity(TypeModel type) {
		Class<?> clz = ((ReflectionTypeModel)type).getReflectionClass();
		try {
			Constructor<?> declaredConstructor = clz.getDeclaredConstructor();
			declaredConstructor.setAccessible(true);
			return declaredConstructor.newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Failed to construct entity of type " + clz.getName(), e);
		}
	}

	public static void setFieldValue(Object entity, FieldModel fieldModel, Object value) {
		try {
			java.lang.reflect.Field field = ((ReflectionFieldModel)fieldModel).getReflectionField();
			field.setAccessible(true);
			field.set(entity, value);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Failed to set field value for field " + fieldModel.getName(), e);
		}
	}

	public static void addToCollection(Object entity, FieldModel field, Object value, JdbcValueMapper valueMapper, TypeModel componentType) throws SQLException {
		if (value != null) {
			Field targetField = getField(field);
			Object mappedValue = valueMapper.mapValue(value);
			setFieldValue(entity, field, DefaultValueMappers.addValueToCollection(
				targetField.getType(), 
				getFieldValue(entity, field), 
				getClass(componentType),
				mappedValue));
		}
	}

	private static Object getFieldValue(Object entity, FieldModel fieldModel) {
		try {
			java.lang.reflect.Field field = ((ReflectionFieldModel)fieldModel).getReflectionField();
			field.setAccessible(true);
			return field.get(entity);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Failed to get field value for field " + fieldModel.getName(), e);
		}
	}

	private Object getPrimaryKeyValue(TableNode node, Map<String, Object> row) {
		List<Object> pkValues = new ArrayList<>();
		for (QueryNode child : node.children()) {
			if (child instanceof PrimaryKey pk) {
				Object value = row.get(node.alias() + "." + pk.field().getName());
				if (value != null) {
					pkValues.add(value);
				}
			}
		}
		return pkValues.size() > 0 ? List.copyOf(pkValues) : null;
	}

	private boolean allNulls(TableNode node, Map<String, Object> row) {
		for (QueryNode child : node.children()) {
			if (child instanceof ScalarNode scalar) {
				Object value = row.get(node.alias() + "." + scalar.field().getName());
				if (value != null) {
					return false;
				}
			}
		}
		return true;
	}

	public void flush() {
		if (rootEntity != null) {
			entityCallback.accept(rootEntity);
			rootEntity = null;
			rootEntityPrimaryKeyValue = null;
		}
	}

	public static Class<?> getClass(TypeModel type) {
		return ((ReflectionTypeModel)type).getReflectionClass();
	}

	public static Field getField(FieldModel fieldModel) {
		return ((ReflectionFieldModel)fieldModel).getReflectionField();
	}

	public static <R> List<R> processRows(RootNode tree, List<Map<String,Object>> rows) throws SQLException{
		List<R> result = new ArrayList<>();
		AQTRowProcessor<R> processor = new AQTRowProcessor<>(tree, result::add);
		for (Map<String, Object> row : rows) {
			processor.processRow(row);
		}
		processor.flush();
		return result;
	}

}
