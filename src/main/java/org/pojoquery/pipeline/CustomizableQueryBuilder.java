package org.pojoquery.pipeline;

import static org.pojoquery.util.Strings.implode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.DbContext;
import org.pojoquery.FieldMapping;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Join;
import org.pojoquery.annotations.Transient;
import org.pojoquery.internal.MappingException;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.querytree.QueryTree;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionFieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Builds SQL queries from POJO type definitions and processes result rows into entities.
 * 
 * <p>The query building process is split into two phases:</p>
 * <ol>
 *   <li>Phase 1 (QueryModel): Build the model structure - aliases, field mappings, 
 *       join definitions, and field selections</li>
 *   <li>Phase 2 (this class): Apply the model to SqlQuery</li>
 * </ol>
 */
public class CustomizableQueryBuilder<SQ extends SqlQuery<?>,T> {

	private static final java.util.regex.Pattern ALIAS_PATTERN = java.util.regex.Pattern.compile("\\{([a-zA-Z0-9_\\.]+)\\}");

	/**
	 * @deprecated Use org.pojoquery.pipeline.PojoMetadata.Values instead
	 */
	@Deprecated
	public static class Values extends PojoMetadata.Values {
		public Values() {
			super();
		}

		public Values(Values values) {
			super(values);
		}
	}

	/**
	 * @deprecated Use org.pojoquery.pipeline.DefaultSqlQuery instead
	 */
	@Deprecated
	public static class DefaultSqlQuery extends org.pojoquery.pipeline.DefaultSqlQuery {
		public DefaultSqlQuery(DbContext context) {
			super(context);
		}
	}

	private final QueryModel model;
	private Map<String, List<String>> keysByAlias = new HashMap<String, List<String>>();
	private final SqlQuery<SQ> query;

	/**
	 * Creates a CustomizableQueryBuilder for the given class.
	 * This constructor wraps the class in a ReflectionTypeModel.
	 */
	CustomizableQueryBuilder(SqlQuery<SQ> query, Class<T> clz) {
		this(query, new ReflectionTypeModel(clz));
	}

	/**
	 * Creates a CustomizableQueryBuilder for the given type model.
	 */
	CustomizableQueryBuilder(SqlQuery<SQ> query, TypeModel type) {
		this.query = query;
		
		// Phase 1: Build the query model
		this.model = new QueryModel(type);
		
		// Phase 2: Apply the model to SqlQuery
		applyModelToQuery(query, model);
	}

	/**
	 * Applies the QueryModel to the SqlQuery (Phase 2).
	 */
	private static void applyModelToQuery(SqlQuery<?> query, QueryModel model) {
		// Set table
		query.setTable(model.getSchemaName(), model.getTableName());
		
		// Add class-level joins
		for (Join joinAnn : model.getClassLevelJoins()) {
			query.addJoin(joinAnn.type(), joinAnn.schemaName(), joinAnn.tableName(), joinAnn.alias(), SqlExpression.sql(joinAnn.joinCondition()));
		}
		
		// Add group by
		for (String groupBy : model.getGroupByClauses()) {
			query.addGroupBy(groupBy);
		}
		
		// Add order by
		for (String orderBy : model.getOrderByClauses()) {
			query.addOrderBy(orderBy);
		}
		
		// Add joins from model
		for (QueryModel.JoinInfo joinInfo : model.getJoins()) {
			if (joinInfo.isSubquery()) {
				DefaultSqlQuery subQuery = new DefaultSqlQuery(query.getDbContext());
				applyModelToQuery(subQuery, joinInfo.subquery);
				query.addSubqueryJoin(joinInfo.joinType, subQuery.toStatement(), joinInfo.alias, joinInfo.joinCondition);
			} else {
				query.addJoin(joinInfo.joinType, joinInfo.schemaName, joinInfo.tableName, joinInfo.alias, joinInfo.joinCondition);
			}
		}
		
		// Add fields from model
		for (QueryModel.FieldInfo fieldInfo : model.getFields()) {
			query.addField(fieldInfo.expression, fieldInfo.fieldAlias);
		}
	}

	/**
	 * Applies a QueryTree to the SqlQuery.
	 */
	public static void applyQueryTreeToQuery(SqlQuery<?> query, QueryTree tree) {
		SQLQueryFromTree.applyQueryTreeToQuery(query, tree);
	}

	public SqlExpression toStatement() {
		return query.toStatement();
	}

	public SqlExpression buildListIdsStatement(List<FieldModel> idFields) {
		return query.toListIdsStatement(new SqlExpression(implode("\n , ", getFieldNames(query.getTable(), idFields))));
	}

	/** Backward compatible overload that accepts {@code List<Field>} */
	public SqlExpression buildListIdsStatementFromFields(List<FieldModel> idFields) {
		List<FieldModel> fieldModels = new ArrayList<>();
		for (FieldModel fieldModel : idFields) {
			fieldModels.add(new ReflectionFieldModel(((ReflectionFieldModel) fieldModel).getReflectionField()));
		}
		return buildListIdsStatement(fieldModels);
	}

	public SqlExpression buildCountStatement() {
		List<FieldModel> idFields = determineIdFields(model.getResultType());
		String selectClause = "SELECT COUNT(DISTINCT " + implode(", ", getFieldNames(query.getTable(), idFields)) + ") ";

		return query.toStatement(new SqlExpression(selectClause), query.getSchema(), query.getTable(), query.getJoins(), query.getWheres(), null, null, -1, -1);
	}

	@SuppressWarnings("unchecked")
	public SQ getQuery() {
		return (SQ)query;
	}

	/**
	 * Creates a row consumer for streaming mode that emits completed entities to the given consumer.
	 *
	 * <p>The returned consumer processes rows one at a time. When the primary entity's ID changes,
	 * the previously built entity is emitted. After all rows have been processed, call
	 * {@link Runnable#run()} on the returned flush action to emit the final entity.</p>
	 *
	 * <p><strong>Important:</strong> The query must be ordered by the primary entity's ID fields
	 * to ensure all rows for an entity are processed consecutively.</p>
	 *
	 * @param entityConsumer the consumer to receive completed entities
	 * @return a pair: the row consumer to process each row, and a flush action to emit the final entity
	 */
	public StreamingRowHandler<T> createStreamingRowHandler(Consumer<T> entityConsumer) {
		return new StreamingRowHandler<>(entityConsumer, this);
	}

	/**
	 * Handles streaming row processing, emitting completed entities when the primary ID changes.
	 */
	public static class StreamingRowHandler<T> implements Consumer<Map<String, Object>> {
		private final Consumer<T> entityConsumer;
		private final CustomizableQueryBuilder<?, T> queryBuilder;
		private Object currentPrimaryId = null;
		private T currentEntity = null;
		private Map<Object, Object> entityCache = new HashMap<>();

		StreamingRowHandler(Consumer<T> entityConsumer, CustomizableQueryBuilder<?, T> queryBuilder) {
			this.entityConsumer = entityConsumer;
			this.queryBuilder = queryBuilder;
		}

		@Override
		@SuppressWarnings("unchecked")
		public void accept(Map<String, Object> row) {
			if (queryBuilder.keysByAlias.size() == 0) {
				queryBuilder.keysByAlias = groupKeysByAlias(row.keySet());
			}
			Map<String, Values> onThisRow = queryBuilder.collectValuesByAlias(row, queryBuilder.keysByAlias);
			onThisRow = queryBuilder.remapSubClasses(onThisRow);

			// Find the primary ID for this row
			Object primaryId = null;
			for (Alias a : queryBuilder.getAliases().values()) {
				if (a.getParentAlias() == null) {
					Values values = onThisRow.get(a.getAlias());
					if (values != null && !allNulls(values)) {
						primaryId = queryBuilder.createId(a.getAlias(), values, a.getIdFields());
					}
					break;
				}
			}

			// If primary ID changed, emit the current entity and clear cache
			if (primaryId != null && currentPrimaryId != null && !primaryId.equals(currentPrimaryId)) {
				emitCurrentEntity();
			}

			// Process the row
			final Object finalPrimaryId = primaryId;
			queryBuilder.processRowInternal(onThisRow, entityCache, (id, entity) -> {
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

	/**
	 * Ensures that the query is ordered by the primary entity's ID fields.
	 * This is required for streaming mode to work correctly, as it ensures
	 * all rows belonging to the same entity are consecutive in the result set.
	 *
	 * <p>The primary ID fields are appended to the ORDER BY clause as a tiebreaker
	 * to ensure entity grouping while preserving the user's primary ordering.</p>
	 *
	 * @throws MappingException if any ORDER BY clause references a non-root alias (joined table)
	 */
	public void ensureOrderByPrimaryId() {
		List<FieldModel> idFields = determineIdFields(getResultType());
		String tableName = query.getTable();
		List<String> currentOrderBy = new ArrayList<>(query.getOrderBy());

		// Validate that ORDER BY clauses only reference the root alias
		validateOrderByAliases(currentOrderBy);

		// Append ID fields to the ORDER BY (if not already present) as a tiebreaker
		// Use curly brace syntax for alias + quoted field name
		for (FieldModel idField : idFields) {
			String quotedFieldRef = "{" + tableName + "." + idField.getName() + "}";
			boolean alreadyPresent = currentOrderBy.stream()
				.anyMatch(o -> o.toUpperCase().contains(tableName.toUpperCase() + "}." + idField.getName().toUpperCase()));
			if (!alreadyPresent) {
				currentOrderBy.add(quotedFieldRef);
			}
		}
		query.setOrderBy(currentOrderBy);
	}

	/**
	 * Validates that all ORDER BY clauses only reference the root alias.
	 * Ordering by fields from joined tables would cause rows from different entities
	 * to interleave, breaking streaming mode.
	 *
	 * @param orderByClauses the ORDER BY clauses to validate
	 * @throws MappingException if any clause references a non-root alias
	 */
	private void validateOrderByAliases(List<String> orderByClauses) {
		String rootAlias = model.getRootAlias();
		for (String clause : orderByClauses) {
			java.util.regex.Matcher matcher = ALIAS_PATTERN.matcher(clause);
			while (matcher.find()) {
				String alias = matcher.group(1);
				// Extract just the table/alias part (before the dot if there's a field reference)
				String tableAlias = alias.contains(".") ? alias.substring(0, alias.indexOf('.')) : alias;

				if (!tableAlias.equals(rootAlias)) {
					throw new MappingException(
						"executeStreaming with consumer does not support ORDER BY on joined tables. " +
						"Found ORDER BY clause '" + clause + "' referencing alias '" + tableAlias + "', " +
						"but only the root alias '" + rootAlias + "' is allowed. " +
						"Ordering by joined table fields would cause incomplete entities. " +
						"Use executeStreaming() without consumer or execute() if you need this ordering.");
				}
			}
		}
	}

	public List<T> processRows(List<Map<String, Object>> rows) {
		try {
			List<T> result = new ArrayList<T>(rows.size());
			Map<Object, Object> allEntities = new HashMap<Object, Object>();
			if (rows.size() > 0) {
				Set<String> allKeys = rows.get(0).keySet();
				keysByAlias = groupKeysByAlias(allKeys);
			}

			for(Map<String,Object> row : rows) {
				processRow(result, allEntities, row);
			}

			return result;
		} catch (Exception e) {
			throw new MappingException(e);
		}
	}

	public static Map<String, List<String>> groupKeysByAlias(Set<String> allKeys) {
		Map<String, List<String>> keysByAlias = new HashMap<String, List<String>>();

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


	@SuppressWarnings("unchecked")
	public void processRow(List<T> result, Map<Object, Object> allEntities, Map<String, Object> row) {
		if (keysByAlias.size() == 0) {
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
	 * When a new primary entity is created, the onNewPrimaryEntity callback is invoked.
	 *
	 * @param onThisRow the values for each alias in this row
	 * @param allEntities map of all entities seen so far (keyed by their ID)
	 * @param onNewPrimaryEntity callback invoked when a new primary entity is created, receives (id, entity)
	 */
	private void processRowInternal(Map<String, Values> onThisRow, Map<Object, Object> allEntities,
			java.util.function.BiConsumer<Object, Object> onNewPrimaryEntity) {
		for (Alias a : getAliases().values()) {
			Values values = onThisRow.get(a.getAlias());
			if (values == null || allNulls(values)) {
				continue;
			}
			Object id = createId(a.getAlias(), values, a.getIdFields());
			Object subClassId = null;
			if (a.getParentAlias() == null) {
				// Primary alias
				if (!allEntities.containsKey(id)) {
					// Merge subclass values into the values for this entity
					Values merged = new Values(values);
					TypeModel entityType = getResultType();

					if (a.isSingleTableInheritance()) {
						// Single table inheritance: use discriminator column to determine type
						String discriminatorColumn = a.getDiscriminatorColumn();
						Object discriminatorValue = values.get(discriminatorColumn);
						if (discriminatorValue != null) {
							TypeModel resolvedType = a.getDiscriminatorValues().get(discriminatorValue.toString());
							if (resolvedType != null) {
								entityType = resolvedType;
							}
						}
					} else if (a.getSubClassAliases() != null) {
						// Table-per-subclass: check which subclass table has data
						for(String subClassAlias : a.getSubClassAliases()) {
							Values subClassValues = onThisRow.get(subClassAlias);
							if (subClassValues == null || allNulls(subClassValues)) {
								continue;
							}

							merged.putAll(onThisRow.get(subClassAlias));

							subClassId = createId(subClassAlias, merged, a.getIdFields());
							entityType = getAliases().get(subClassAlias).getResultType();
						}
					}

					Object entity = buildEntity(entityType, merged, a.getOtherField(), a.getDiscriminatorColumn());
					allEntities.put(id, entity);
					allEntities.put(subClassId, entity);
					onNewPrimaryEntity.accept(id, entity);
				}
			} else {
				if (a.getIsASubClass()) {
					// Subclasses are handled when the superclass is processed
					continue;
				}

				// Find the parent
				Values parentValues = onThisRow.get(a.getParentAlias());
				String parentAlias = a.getParentAlias();
				Object parentId = null;
				Object parent = null;
				if (parentValues != null && parentValues.size() > 0) {
					parentId = createId(parentAlias, parentValues, getAliases().get(parentAlias).getIdFields());
					parent = allEntities.get(parentId);
				}

				if (parent == null) {
					Alias parentAliasObject = getAliases().get(a.getParentAlias());
					List<String> subs = parentAliasObject.getSubClassAliases();
					if (subs != null && subs.size() > 0) {
						for (String sub : subs) {
							parentValues = onThisRow.get(sub);
							if (parentValues != null && parentValues.size() > 0) {
								parentAlias = sub;
								parentId = createId(parentAlias, parentValues, getAliases().get(parentAlias).getIdFields());
								parent = allEntities.get(parentId);
								break;
							}
						}
					}
				}

				if (a.isLinkedValue()) {
					// Linked value
					Object value = values.values().iterator().next();
					if (a.getResultType().isEnum()) {
						value = enumValueOf(getReflectionClass(a.getResultType()), (String) value);
					}
					putValueIntoField(parent, a.getLinkField(), value);
				} else {
					TypeModel entityType = a.getResultType();

					if (a.isSingleTableInheritance()) {
						// Single table inheritance: use discriminator column to determine type
						String discriminatorColumn = a.getDiscriminatorColumn();
						Object discriminatorValue = values.get(discriminatorColumn);
						if (discriminatorValue != null) {
							TypeModel resolvedType = a.getDiscriminatorValues().get(discriminatorValue.toString());
							if (resolvedType != null) {
								entityType = resolvedType;
							}
						}
					} else if (a.getSubClassAliases() != null) {
						// Table-per-subclass: check which subclass table has data
						Values merged = new Values();
						merged.putAll(onThisRow.get(a.getAlias()));
						for (String subClassAlias : a.getSubClassAliases()) {
							Values subClassValues = onThisRow.get(subClassAlias);
							if (subClassValues == null || allNulls(subClassValues)) {
								continue;
							}

							merged.putAll(onThisRow.get(subClassAlias));
							id = createId(subClassAlias, merged, a.getIdFields());
							entityType = getAliases().get(subClassAlias).getResultType();
							values = merged;
						}
					}

					// Linked entity
					Object entity = allEntities.get(id);
					if (entity == null) {
						entity = buildEntity(entityType, values, a.getOtherField(), a.getDiscriminatorColumn());
						allEntities.put(id, entity);
					}
					putValueIntoField(parent, a.getLinkField(), entity);
				}
			}
		}
	}

	public Map<String, Values> remapSubClasses(Map<String, Values> onThisRow) {
		Map<String,Values> result = new LinkedHashMap<>();

		for(String alias : onThisRow.keySet()) {
			Values values = onThisRow.get(alias);

			Alias a = getAliases().get(alias);
			if (a.getIsASubClass()) {
				if (values == null || allNulls(values)) {
					continue;
				}
			}
			result.put(alias, values);
		}
		return result;
	}

	private static void putValueIntoField(Object parentEntity, FieldModel linkField, Object entity) {
		if (!(linkField instanceof ReflectionFieldModel)) {
			throw new MappingException("Cannot set field value without reflection: " + linkField);
		}
		Field field = ((ReflectionFieldModel) linkField).getReflectionField();
		org.pojoquery.util.FieldHelper.putValueIntoField(parentEntity, field, entity);
	}

	private Object createId(String alias, Values values, List<FieldModel> idFields) {
		if (idFields.size() == 0) {
			return values;
		}
		List<Object> result = new ArrayList<Object>();
		result.add(alias);
		for(FieldModel f : idFields) {
			result.add(values.get(alias + "." + f.getName()));
		}
		return result;
	}

	private Map<String,Values> collectValuesByAlias(Map<String, Object> row, Map<String, List<String>> keysByAlias) {
		Map<String,Values> result = new HashMap<>();
		for(Alias a : getAliases().values()) {
			String alias = a.getAlias();
			List<String> fieldList = keysByAlias.get(alias);
			if (fieldList != null) {
				Values values = getAliasValues(row, fieldList);
				result.put(alias, values);
			}
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

	@SuppressWarnings("unchecked")
	private static <E> Class<E> getReflectionClass(TypeModel type) {
		if (!(type instanceof ReflectionTypeModel)) {
			throw new MappingException("Cannot get runtime class from non-reflection type: " + type);
		}
		return (Class<E>) ((ReflectionTypeModel) type).getReflectionClass();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <E> E enumValueOf(Class<E> enumClass, String name) {
		return (E) Enum.valueOf((Class<? extends Enum>) enumClass, name);
	}

	private Values applyValues(Object entity, Values aliasValues, String discriminatorColumn) {
		Values other = new Values();
		for(String fieldAlias : aliasValues.keySet()) {
			// Skip discriminator column - it's used internally for type resolution
			if (fieldAlias.equals(discriminatorColumn)) {
				continue;
			}
			FieldMapping mapping = getFieldMappings().get(fieldAlias);
			if (mapping != null) {
				mapping.apply(entity, aliasValues.get(fieldAlias));
			} else {
				String fieldName = fieldAlias.substring(fieldAlias.lastIndexOf(".") + 1);
				other.put(fieldName, aliasValues.get(fieldAlias));
			}
		}
		return other;
	}

	private static boolean allNulls(Map<String, Object> values) {
		for(Object val : values.values()) {
			if (val != null) {
				return false;
			}
		}
		return true;
	}

	private static Values getAliasValues(Map<String, Object> row, List<String> fieldList) {
		Values result = new Values();
		for(String key : fieldList) {
			result.put(key, row.get(key));
		}
		return result;
	}

	public LinkedHashMap<String, Alias> getAliases() {
		return model.getAliases();
	}

	public Map<String, FieldMapping> getFieldMappings() {
		return model.getFields().stream()
			.filter(f -> f.field != null)
			.collect(
				HashMap::new,
				(m, f) -> m.put(
					f.fieldAlias,
					getQuery().getDbContext().getFieldMapping(
						((ReflectionFieldModel) f.field).getReflectionField()
					)
				),
				HashMap::putAll
			);
	}

	public TypeModel getResultType() {
		return model.getResultType();
	}

	@SuppressWarnings("unchecked")
	public Class<T> getResultClass() {
		return (Class<T>) getReflectionClass(getResultType());
	}

	public static List<FieldModel> assertIdFields(TypeModel type) {
		List<FieldModel> idFields = determineIdFields(type);
		if (idFields.size() == 0) {
			throw new MappingException("No @Id annotations found on fields of type " + type.getQualifiedName());
		}
		return idFields;
	}

	public static List<SqlExpression> buildIdCondition(DbContext context, TypeModel type, Object id) {
		List<FieldModel> idFields = assertIdFields(type);
		List<TableMapping> tables = determineTableMapping(type);
		String tableName = tables.get(tables.size() - 1).tableName;
		if (idFields.size() == 1) {
			return Arrays.asList(new SqlExpression((context.quoteAlias(tableName) + "." + context.quoteObjectNames(idFields.get(0).getName())) + "=?", Arrays.asList((Object) id)));
		} else {
			if (id instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> idvalues = (Map<String, Object>) id;

				List<SqlExpression> result = new ArrayList<SqlExpression>();
				for (String field : idvalues.keySet()) {
					result.add(new SqlExpression(context.quoteObjectNames(tableName, field) + "=?", Arrays.asList((Object) idvalues.get(field))));
				}
				return result;
			} else {
				throw new MappingException("Multiple @Id annotations on type " + type.getQualifiedName() + ": expecting a map id.");
			}
		}
	}

	public static <T> T createInstance(Class<T> valClass) {
		try {
			Constructor<T> constructor = valClass.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (Exception e) {
			throw new MappingException("Exception creating instance of class " + valClass, e);
		}
	}

	public static String determinePrefix(FieldModel f) {
		String prefix;
		Embedded embeddedAnn = f.getAnnotation(Embedded.class);
		if (embeddedAnn != null) {
			// PojoQuery @Embedded annotation
			prefix = embeddedAnn.prefix();
			if (prefix.equals(Embedded.DEFAULT)) {
				// PojoQuery @Embedded with no explicit prefix - use field name with underscore
				prefix = f.getName() + "_";
			}
			// If prefix was explicitly set, use it as-is (user controls whether to include underscore)
		} else {
			// JPA @Embedded without PojoQuery annotation - true JPA semantics (no prefix)
			prefix = "";
		}
		return prefix;
	}

	/** Backward compatible overload */
	public static String determinePrefix(Field f) {
		return determinePrefix(new ReflectionFieldModel(f));
	}

	public static List<TableMapping> determineTableMapping(TypeModel type) {
		TypeModel current = type;
		TypeModel mappedType = type;
		List<TableMapping> tables = new ArrayList<TableMapping>();
		List<FieldModel> fields = new ArrayList<FieldModel>();
		while (current != null) {
			if (mappedType == null) {
				mappedType = current;
			}
			AnnotationHelper.TableInfo tableInfo = getTableInfo(current);
			fields.addAll(0, collectFieldsOfClass(current, current.getSuperclass()));
			if (tableInfo != null) {
				String name = tableInfo.name;
				// Check if this is a redundant @Table annotation targeting the same table as an existing mapping
				if (!tables.isEmpty() && tables.get(0).tableName.equals(name)) {
					Logger.getLogger(CustomizableQueryBuilder.class.getName())
						.warning("Redundant @Table(\"" + name + "\") annotation on " +
							tables.get(0).type.getQualifiedName() + " - same table already mapped by parent " + current.getQualifiedName());
					// Merge fields into existing mapping instead of creating a new one
					tables.get(0).fields.addAll(0, fields);
				} else {
					tables.add(0, new TableMapping(tableInfo.schema, name, mappedType, new ArrayList<FieldModel>(fields)));
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

	/**
	 * Gets table info from a type, supporting both reflection and annotation processing.
	 */
	private static AnnotationHelper.TableInfo getTableInfo(TypeModel type) {
		return AnnotationHelper.getTableInfo(type);
	}

	public static List<String> getFieldNames(String table, List<FieldModel> fields) {
		List<String> fieldNames = new ArrayList<String>();
		for (FieldModel f : fields) {
			fieldNames.add(table + "." + f.getName());
		}
		return fieldNames;
	}

	public static List<FieldModel> filterFields(TypeModel type) {
		List<FieldModel> result = new ArrayList<FieldModel>();
		for (FieldModel f : type.getDeclaredFields()) {
			if (f.isStatic()) {
				continue;
			}
			if (f.isTransient() || f.hasAnnotation(Transient.class)) {
				continue;
			}
			result.add(f);
		}
		return result;
	}

	public static List<FieldModel> collectFieldsOfClass(TypeModel type, TypeModel stopAtSuperType) {
		List<FieldModel> result = new ArrayList<FieldModel>();
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

	public static List<FieldModel> collectFieldsOfClass(TypeModel type) {
		return collectFieldsOfClass(type, null);
	}

	public static final List<FieldModel> determineIdFields(TypeModel type) {
		List<FieldModel> fields = collectFieldsOfClass(type);
		ArrayList<FieldModel> result = new ArrayList<FieldModel>();
		for (FieldModel f : fields) {
			if (isId(f)) {
				result.add(f);
			}
		}
		return result;
	}

	/**
	 * Checks if a field is an ID field (has @Id annotation).
	 */
	public static boolean isId(FieldModel f) {
		return AnnotationHelper.isId(f);
	}

	public static FieldModel determineIdField(TypeModel type) {
		List<FieldModel> idFields = determineIdFields(type);
		if (idFields.size() != 1) {
			throw new MappingException("Need single id field annotated with @Id on type " + type.getQualifiedName());
		}
		return idFields.get(0);
	}

	/**
	 * Determines the SQL column name for a field.
	 * Checks @Column and @FieldName first, then falls back to field name.
	 */
	public static String determineSqlFieldName(FieldModel f) {
		return QueryModel.determineSqlFieldName(f);
	}

	/**
	 * Determines the SQL column name for a foreign key (link) field.
	 * Checks @JoinColumn and @Link(linkfield) first, then falls back to @Column,
	 * and finally defaults to fieldName_id.
	 */
	public static String determineLinkFieldName(FieldModel f) {
		return QueryModel.determineLinkFieldName(f);
	}

	/**
	 * Determines the owner (parent) column name for a link table.
	 */
	public static String determineLinkTableOwnerColumn(TypeModel ownerClass, org.pojoquery.annotations.Link linkAnn) {
		return QueryModel.determineLinkTableOwnerColumn(ownerClass, linkAnn);
	}

	/**
	 * Determines the foreign (target) column name for a link table.
	 */
	public static String determineLinkTableForeignColumn(TypeModel foreignClass, org.pojoquery.annotations.Link linkAnn) {
		return QueryModel.determineLinkTableForeignColumn(foreignClass, linkAnn);
	}

	/**
	 * Checks if a type is a list or array (collection type).
	 */
	public static boolean isListOrArray(TypeModel type) {
		return QueryModel.isListOrArray(type);
	}

	/**
	 * Checks if a type is a linked class (has @Table annotation).
	 */
	public static boolean isLinkedClass(TypeModel type) {
		return QueryModel.isLinkedClass(type);
	}

	/** Backward compatible overload */
	public static boolean isLinkedClass(Class<?> type) {
		return QueryModel.isLinkedClass(new ReflectionTypeModel(type));
	}

	/**
	 * Gets the component type of a collection or array field.
	 */
	public static TypeModel getCollectionComponentType(FieldModel field) {
		return QueryModel.getCollectionComponentType(field);
	}

	/**
	 * Checks if a field is embedded (has @Embedded annotation).
	 */
	public static boolean isEmbedded(FieldModel f) {
		return QueryModel.isEmbedded(f);
	}
}
