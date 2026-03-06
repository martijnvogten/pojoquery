 package org.pojoquery.pipeline;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.pojoquery.DbContext;
import org.pojoquery.FieldMapping;
import org.pojoquery.internal.MappingException;
import org.pojoquery.pipeline.PojoMetadata.Values;
import org.pojoquery.pipeline.querytree.EmbeddedNode;
import org.pojoquery.pipeline.querytree.FieldSelection;
import org.pojoquery.pipeline.querytree.JoinedNode;
import org.pojoquery.pipeline.querytree.LinkedValueNode;
import org.pojoquery.pipeline.querytree.QueryNode;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.pipeline.querytree.TableNode;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionFieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Processes database result rows into entities using a QueryTree as the exclusive source
 * of field and type information.
 * 
 * <p>Unlike {@link CustomizableQueryBuilder} which derives mapping information from
 * POJO annotations via {@link Alias} objects, this class uses the QueryTree structure
 * directly. This allows processing rows from queries that were built or transformed
 * programmatically.</p>
 */
public class QueryTreeRowProcessor<T> {

    private final QueryTree tree;
    private final DbContext dbContext;
    
    // Cached lookups derived from tree
    private final Map<String, QueryNode> nodesByAlias;
    private final Map<String, QueryNode> parentNodes;
    private final Map<String, List<String>> subClassAliases;
    private final Map<String, FieldMapping> fieldMappings;
    
    // Cached during processing
    private Map<String, List<String>> keysByAlias = new HashMap<>();

    public QueryTreeRowProcessor(QueryTree tree, DbContext dbContext) {
        this.tree = tree;
        this.dbContext = dbContext;
        
        // Pre-compute lookups from tree
        this.nodesByAlias = tree.nodesByAlias();
        this.parentNodes = tree.parentNodes();
        this.subClassAliases = tree.subClassAliases();
        this.fieldMappings = buildFieldMappings();
    }

    /**
     * Builds field mappings from the QueryTree's FieldSelections.
     */
    private Map<String, FieldMapping> buildFieldMappings() {
        Map<String, FieldMapping> result = new HashMap<>();
        for (QueryNode node : tree.allNodes()) {
            if (node instanceof TableNode tableNode) {
                for (FieldSelection fs : tableNode.fields()) {
                    if (fs.customMapping() != null) {
                        result.put(fs.alias(), fs.customMapping());
                    } else if (fs.field() != null && fs.field() instanceof ReflectionFieldModel rfm) {
                        result.put(fs.alias(), dbContext.getFieldMapping(rfm.getReflectionField()));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Processes multiple rows and returns a list of entities.
     */
    public List<T> processRows(List<Map<String, Object>> rows) {
        try {
            List<T> result = new ArrayList<>(rows.size());
            Map<Object, Object> allEntities = new HashMap<>();
            
            if (!rows.isEmpty()) {
                keysByAlias = groupKeysByAlias(rows.get(0).keySet());
            }

            for (Map<String, Object> row : rows) {
                processRow(result, allEntities, row);
            }

            return result;
        } catch (Exception e) {
            throw new MappingException(e);
        }
    }

    /**
     * Processes a single row, adding new primary entities to the result list.
     */
    @SuppressWarnings("unchecked")
    public void processRow(List<T> result, Map<Object, Object> allEntities, Map<String, Object> row) {
        if (keysByAlias.isEmpty()) {
            keysByAlias = groupKeysByAlias(row.keySet());
        }
        Map<String, Values> onThisRow = collectValuesByAlias(row, keysByAlias);
        onThisRow = remapSubClasses(onThisRow);

        processRowInternal(onThisRow, allEntities, (id, entity) -> {
            result.add((T) entity);
        });
    }

    /**
     * Internal method that processes a single row's alias values and populates the allEntities map.
     */
    private void processRowInternal(Map<String, Values> onThisRow, Map<Object, Object> allEntities,
            BiConsumer<Object, Object> onNewPrimaryEntity) {
        
        for (QueryNode node : tree.allNodes()) {
            String alias = node.alias();
            Values values = onThisRow.get(alias);
            
            if (values == null || allNulls(values)) {
                continue;
            }

            // Skip subclasses - they're handled when the superclass is processed
            if (isSubClass(node)) {
                continue;
            }

            List<String> idFieldNames = getIdFieldNames(node);
            Object id = createId(alias, values, idFieldNames);
            Object subClassId = null;
            
            QueryNode parentNode = parentNodes.get(alias);
            
            if (parentNode == null) {
                // Root node - primary entity
                if (!allEntities.containsKey(id)) {
                    Values merged = new Values(values);
                    TypeModel entityType = tree.resultType();

                    if (isSingleTableInheritance(node)) {
                        // Single table inheritance: use discriminator column to determine type
                        String discriminatorColumn = getDiscriminatorColumn(node);
                        Object discriminatorValue = values.get(discriminatorColumn);
                        if (discriminatorValue != null) {
                            Map<String, TypeModel> discValues = getDiscriminatorValues(node);
                            TypeModel resolvedType = discValues.get(discriminatorValue.toString());
                            if (resolvedType != null) {
                                entityType = resolvedType;
                            }
                        }
                    } else {
                        // Table-per-subclass: check which subclass table has data
                        List<String> subs = subClassAliases.get(alias);
                        if (subs != null) {
                            for (String subClassAlias : subs) {
                                Values subClassValues = onThisRow.get(subClassAlias);
                                if (subClassValues == null || allNulls(subClassValues)) {
                                    continue;
                                }
                                merged.putAll(subClassValues);
                                subClassId = createId(subClassAlias, merged, idFieldNames);
                                QueryNode subClassNode = nodesByAlias.get(subClassAlias);
                                if (subClassNode != null) {
                                    entityType = subClassNode.type();
                                }
                            }
                        }
                    }

                    Object entity = buildEntity(entityType, merged, getOtherField(node), getDiscriminatorColumn(node));
                    allEntities.put(id, entity);
                    if (subClassId != null) {
                        allEntities.put(subClassId, entity);
                    }
                    onNewPrimaryEntity.accept(id, entity);
                }
            } else {
                // Child node - find parent and link
                String parentAlias = parentNode.alias();
                Values parentValues = onThisRow.get(parentAlias);
                Object parentId = null;
                Object parent = null;

                if (parentValues != null && !parentValues.isEmpty()) {
                    List<String> parentIdFields = getIdFieldNames(parentNode);
                    parentId = createId(parentAlias, parentValues, parentIdFields);
                    parent = allEntities.get(parentId);
                }

                // Try subclass aliases if parent not found
                if (parent == null) {
                    List<String> parentSubs = subClassAliases.get(parentAlias);
                    if (parentSubs != null) {
                        for (String sub : parentSubs) {
                            parentValues = onThisRow.get(sub);
                            if (parentValues != null && !parentValues.isEmpty()) {
                                QueryNode subNode = nodesByAlias.get(sub);
                                List<String> subIdFields = getIdFieldNames(subNode);
                                parentId = createId(sub, parentValues, subIdFields);
                                parent = allEntities.get(parentId);
                                if (parent != null) break;
                            }
                        }
                    }
                }
                
                // If parent is a superclass node, look up entity via first non-superclass ancestor
                if (parent == null && parentNode instanceof JoinedNode jn && jn.isSuperClass()) {
                    String ancestorAlias = findFirstNonSuperclassAncestor(parentAlias);
                    if (ancestorAlias != null) {
                        parentValues = onThisRow.get(ancestorAlias);
                        if (parentValues != null && !parentValues.isEmpty()) {
                            QueryNode ancestorNode = nodesByAlias.get(ancestorAlias);
                            if (ancestorNode != null) {
                                List<String> ancestorIdFields = getIdFieldNames(ancestorNode);
                                parentId = createId(ancestorAlias, parentValues, ancestorIdFields);
                                parent = allEntities.get(parentId);
                            }
                        }
                    }
                }

                FieldModel linkField = getLinkField(node);

                if (node instanceof LinkedValueNode lvn) {
                    // Linked value (scalar collection via link table)
                    Object value = values.values().iterator().next();
                    TypeModel valueType = lvn.type();
                    if (valueType.isEnum()) {
                        value = enumValueOf(getReflectionClass(valueType), (String) value);
                    }
                    putValueIntoField(parent, linkField, value);
                } else {
                    // Linked entity
                    TypeModel entityType = node.type();

                    if (isSingleTableInheritance(node)) {
                        String discriminatorColumn = getDiscriminatorColumn(node);
                        Object discriminatorValue = values.get(discriminatorColumn);
                        if (discriminatorValue != null) {
                            Map<String, TypeModel> discValues = getDiscriminatorValues(node);
                            TypeModel resolvedType = discValues.get(discriminatorValue.toString());
                            if (resolvedType != null) {
                                entityType = resolvedType;
                            }
                        }
                    } else {
                        // Table-per-subclass
                        List<String> subs = subClassAliases.get(alias);
                        if (subs != null) {
                            Values merged = new Values(values);
                            for (String subClassAlias : subs) {
                                Values subClassValues = onThisRow.get(subClassAlias);
                                if (subClassValues == null || allNulls(subClassValues)) {
                                    continue;
                                }
                                merged.putAll(subClassValues);
                                id = createId(subClassAlias, merged, idFieldNames);
                                QueryNode subClassNode = nodesByAlias.get(subClassAlias);
                                if (subClassNode != null) {
                                    entityType = subClassNode.type();
                                }
                                values = merged;
                            }
                        }
                    }

                    Object entity = allEntities.get(id);
                    if (entity == null) {
                        entity = buildEntity(entityType, values, getOtherField(node), getDiscriminatorColumn(node));
                        allEntities.put(id, entity);
                    }
                    putValueIntoField(parent, linkField, entity);
                }
            }
        }
    }

    // --- Helper methods to extract info from QueryNode ---

    private boolean isSubClass(QueryNode node) {
        if (node instanceof JoinedNode jn) {
            return jn.isSubClass();
        }
        if (node instanceof EmbeddedNode en) {
            return en.isSubClass();
        }
        return false;
    }

    private List<String> getIdFieldNames(QueryNode node) {
        if (node instanceof JoinedNode jn) {
            return jn.idFieldNames() != null ? jn.idFieldNames() : List.of();
        }
        return List.of();
    }

    private boolean isSingleTableInheritance(QueryNode node) {
        if (node instanceof JoinedNode jn) {
            return jn.isSingleTableInheritance();
        }
        return false;
    }

    private String getDiscriminatorColumn(QueryNode node) {
        if (node instanceof JoinedNode jn) {
            return jn.discriminatorColumn();
        }
        return null;
    }

    private Map<String, TypeModel> getDiscriminatorValues(QueryNode node) {
        if (node instanceof JoinedNode jn && jn.discriminatorValues() != null) {
            return jn.discriminatorValues();
        }
        return Map.of();
    }

    private FieldModel getOtherField(QueryNode node) {
        if (node instanceof JoinedNode jn) {
            return jn.otherField();
        }
        return null;
    }

    private FieldModel getLinkField(QueryNode node) {
        if (node.joinInfo() != null) {
            return node.joinInfo().linkField();
        }
        if (node.embedInfo() != null) {
            return node.embedInfo().linkField();
        }
        return null;
    }

    /**
     * Traverses up the parent chain to find the first non-superclass ancestor.
     * Used for table-per-subclass inheritance where superclass tables need to be 
     * mapped back to their subclass entities.
     */
    private String findFirstNonSuperclassAncestor(String alias) {
        QueryNode current = parentNodes.get(alias);
        while (current != null) {
            if (current instanceof JoinedNode jn && !jn.isSuperClass()) {
                return jn.alias();
            }
            current = parentNodes.get(current.alias());
        }
        return null;
    }

    // --- Row value collection ---

    private Map<String, Values> collectValuesByAlias(Map<String, Object> row, Map<String, List<String>> keysByAlias) {
        Map<String, Values> result = new HashMap<>();
        for (String alias : nodesByAlias.keySet()) {
            List<String> fieldList = keysByAlias.get(alias);
            if (fieldList != null) {
                Values values = getAliasValues(row, fieldList);
                result.put(alias, values);
            }
        }
        return result;
    }

    private Map<String, Values> remapSubClasses(Map<String, Values> onThisRow) {
        Map<String, Values> result = new LinkedHashMap<>();
        for (String alias : onThisRow.keySet()) {
            Values values = onThisRow.get(alias);
            QueryNode node = nodesByAlias.get(alias);
            if (isSubClass(node)) {
                if (values == null || allNulls(values)) {
                    continue;
                }
            }
            result.put(alias, values);
        }
        return result;
    }

    // --- Entity building ---

    private Object createId(String alias, Values values, List<String> idFieldNames) {
        if (idFieldNames.isEmpty()) {
            return values;
        }
        List<Object> result = new ArrayList<>();
        result.add(alias);
        for (String fieldName : idFieldNames) {
            result.add(values.get(alias + "." + fieldName));
        }
        return result;
    }

    private <E> E buildEntity(TypeModel type, Values values, FieldModel otherField, String discriminatorColumn) {
        if (allNulls(values)) {
            return null;
        }
        Class<E> clazz = getReflectionClass(type);
        E entity = createInstance(clazz);
        Values other = applyValues(entity, values, discriminatorColumn);
        if (otherField != null) {
            if (!(otherField instanceof ReflectionFieldModel)) {
                throw new MappingException("Cannot set other field without reflection: " + otherField);
            }
            Field field = ((ReflectionFieldModel) otherField).getReflectionField();
            field.setAccessible(true);
            try {
                field.set(entity, other);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new MappingException(e);
            }
        }
        return entity;
    }

    private Values applyValues(Object entity, Values aliasValues, String discriminatorColumn) {
        Values other = new Values();
        for (String fieldAlias : aliasValues.keySet()) {
            if (fieldAlias.equals(discriminatorColumn)) {
                continue;
            }
            FieldMapping mapping = fieldMappings.get(fieldAlias);
            if (mapping != null) {
                mapping.apply(entity, aliasValues.get(fieldAlias));
            } else {
                String fieldName = fieldAlias.substring(fieldAlias.lastIndexOf(".") + 1);
                other.put(fieldName, aliasValues.get(fieldAlias));
            }
        }
        return other;
    }

    // --- Static utilities ---

    private static Values getAliasValues(Map<String, Object> row, List<String> fieldList) {
        Values result = new Values();
        for (String key : fieldList) {
            result.put(key, row.get(key));
        }
        return result;
    }

    private static boolean allNulls(Map<String, Object> values) {
        for (Object val : values.values()) {
            if (val != null) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <E> Class<E> getReflectionClass(TypeModel type) {
        if (!(type instanceof ReflectionTypeModel)) {
            throw new MappingException("Cannot get runtime class from non-reflection type: " + type);
        }
        return (Class<E>) ((ReflectionTypeModel) type).getReflectionClass();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <E> E enumValueOf(Class<E> enumClass, String name) {
        return (E) Enum.valueOf((Class<? extends Enum>) enumClass, name);
    }

    private static <T> T createInstance(Class<T> valClass) {
        try {
            Constructor<T> constructor = valClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new MappingException("Exception creating instance of class " + valClass, e);
        }
    }

    private static void putValueIntoField(Object parentEntity, FieldModel linkField, Object entity) {
        if (linkField == null || parentEntity == null) {
            return;
        }
        if (!(linkField instanceof ReflectionFieldModel)) {
            throw new MappingException("Cannot set field value without reflection: " + linkField);
        }
        Field field = ((ReflectionFieldModel) linkField).getReflectionField();
        org.pojoquery.util.FieldHelper.putValueIntoField(parentEntity, field, entity);
    }

	public static Map<String, List<String>> groupKeysByAlias(Set<String> allKeys) {
		Map<String, List<String>> keysByAlias = new HashMap<>();

		for (String key : allKeys) {
			int dotPos = key.lastIndexOf(".");
			if (dotPos < 0) {
				throw new RuntimeException("Key does not contain a dot: '" + key + "', allKeys: " + allKeys);
			}
			String alias = key.substring(0, dotPos);
			if (!keysByAlias.containsKey(alias)) {
				keysByAlias.put(alias, new ArrayList<>());
			}
			keysByAlias.get(alias).add(key);
		}

		return keysByAlias;
	}

    public StreamingRowHandler createStreamingRowHandler(Consumer<T> entityConsumer) {
        return new StreamingRowHandler(entityConsumer);
    }
    	/**
	 * Handles streaming row processing, emitting completed entities when the primary ID changes.
	 */
	public class StreamingRowHandler implements Consumer<Map<String, Object>> {
		private final Consumer<T> entityConsumer;
		private Object currentPrimaryId = null;
		private T currentEntity = null;
		private Map<Object, Object> entityCache = new HashMap<>();

		StreamingRowHandler(Consumer<T> entityConsumer) {
			this.entityConsumer = entityConsumer;
		}

		@Override
		@SuppressWarnings("unchecked")
		public void accept(Map<String, Object> row) {
			if (keysByAlias.size() == 0) {
				keysByAlias = groupKeysByAlias(row.keySet());
			}
			Map<String, Values> onThisRow = collectValuesByAlias(row, keysByAlias);
			onThisRow = remapSubClasses(onThisRow);

			// Find the primary ID for this row
			Object primaryId = null;
            Values values = onThisRow.get(tree.rootAlias());
            if (values != null && !allNulls(values)) {
                primaryId = createId(tree.rootAlias(), values, ((JoinedNode) tree.root()).idFieldNames());
            }

			// If primary ID changed, emit the current entity and clear cache
			if (primaryId != null && currentPrimaryId != null && !primaryId.equals(currentPrimaryId)) {
				emitCurrentEntity();
			}

			// Process the row
			final Object finalPrimaryId = primaryId;
			processRowInternal(onThisRow, entityCache, (id, entity) -> {
				currentPrimaryId = finalPrimaryId;
				currentEntity = (T) entity;
			});
		}

		/**
		 * Emits the final entity after all rows have been processed.
		 * Must be called after the last row to ensure the final entity is emitted.
		 */
		public void flush() {
			emitCurrentEntity();
		}

		private void emitCurrentEntity() {
			if (currentEntity != null) {
				entityConsumer.accept(currentEntity);
				entityCache.clear();
				currentEntity = null;
				currentPrimaryId = null;
			}
		}
	}


}
