package org.pojoquery.pipeline;

import static org.pojoquery.util.Strings.implode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.DbContext;
import org.pojoquery.FieldMapping;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.GroupBy;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Join;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Joins;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.OrderBy;
import org.pojoquery.annotations.Other;
import org.pojoquery.annotations.Select;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Transient;
import org.pojoquery.internal.MappingException;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionFieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;
import org.pojoquery.util.CurlyMarkers;

public class CustomizableQueryBuilder<SQ extends SqlQuery<?>,T> {

	private static final java.util.regex.Pattern ALIAS_PATTERN = java.util.regex.Pattern.compile("\\{([a-zA-Z0-9_\\.]+)\\}");

	public static class Values extends HashMap<String,Object> {

		public Values() {
			super();
		}

		public Values(Values values) {
			super(values);
		}
	}

	public static class DefaultSqlQuery extends SqlQuery<DefaultSqlQuery> {

		public DefaultSqlQuery(DbContext context) {
			super(context);
		}
	}

	private LinkedHashMap<String,Alias> subClasses = new LinkedHashMap<>();
	private LinkedHashMap<String,Alias> aliases = new LinkedHashMap<>();
	private Map<String, List<String>> keysByAlias = new HashMap<String, List<String>>();
	private Map<String,FieldMapping> fieldMappings = new LinkedHashMap<>();

	private final TypeModel resultType;
	private final SqlQuery<SQ> query;
	private final String rootAlias;
	private final DbContext dbContext;

	/**
	 * Creates a CustomizableQueryBuilder for the given class.
	 * This constructor wraps the class in a ReflectionTypeModel.
	 */
	protected CustomizableQueryBuilder(SqlQuery<SQ> query, Class<T> clz) {
		this(query, new ReflectionTypeModel(clz));
	}

	/**
	 * Creates a CustomizableQueryBuilder for the given type model.
	 */
	protected CustomizableQueryBuilder(SqlQuery<SQ> query, TypeModel type) {
		this.dbContext = query.getDbContext();
		this.resultType = type;
		this.query = query;
		final TableMapping tableMapping = lookupTableMapping(type);
		query.setTable(tableMapping.schemaName, tableMapping.tableName);
		List<Join> joinAnns = getJoinAnnotations(type);
		for (Join joinAnn : joinAnns) {
			query.addJoin(joinAnn.type(), joinAnn.schemaName(), joinAnn.tableName(), joinAnn.alias(), SqlExpression.sql(joinAnn.joinCondition()));
		}
		GroupBy groupByAnn = type.getAnnotation(GroupBy.class);
		if (groupByAnn != null) {
			for(String groupBy : groupByAnn.value()) {
				query.addGroupBy(groupBy);
			}
		}
		OrderBy orderByAnn = type.getAnnotation(OrderBy.class);
		if (orderByAnn != null) {
			for(String orderBy : orderByAnn.value()) {
				query.addOrderBy(orderBy);
			}
		}

		rootAlias = query.getTable();
		addClass(type, rootAlias, null, null);
	}

	private List<Join> getJoinAnnotations(TypeModel type) {
		Joins multipleJoins = type.getAnnotation(Joins.class);
		if (multipleJoins != null) {
			return Arrays.asList(multipleJoins.value());
		}
		Join singleJoin = type.getAnnotation(Join.class);
		if (singleJoin != null) {
			return List.of(singleJoin);
		}
		return List.of();
	}

	public SqlExpression toStatement() {
		return query.toStatement();
	}

	public SqlExpression buildListIdsStatement(List<FieldModel> idFields) {
		return query.toListIdsStatement(new SqlExpression(implode("\n , ", getFieldNames(query.getTable(), idFields))));
	}

	/** Backward compatible overload that accepts {@code List<Field>} */
	public SqlExpression buildListIdsStatementFromFields(List<Field> idFields) {
		List<FieldModel> fieldModels = new ArrayList<>();
		for (Field f : idFields) {
			fieldModels.add(new ReflectionFieldModel(f));
		}
		return buildListIdsStatement(fieldModels);
	}

	public SqlExpression buildCountStatement() {
		List<FieldModel> idFields = determineIdFields(resultType);
		String selectClause = "SELECT COUNT(DISTINCT " + implode(", ", getFieldNames(query.getTable(), idFields)) + ") ";

		return query.toStatement(new SqlExpression(selectClause), query.getSchema(), query.getTable(), query.getJoins(), query.getWheres(), null, null, -1, -1);
	}

	@SuppressWarnings("unchecked")
	public SQ getQuery() {
		return (SQ)query;
	}

	private static TableMapping lookupTableMapping(TypeModel type) {
		if (type == null)
			throw new NullPointerException("type");

		List<TableMapping> tableMappings = determineTableMapping(type);
		if (tableMappings.size() == 0) {
			throw new MappingException("Missing @Table annotation on type " + type.getQualifiedName() + " or any of its superclasses");
		}
		return tableMappings.get(tableMappings.size() - 1);
	}

	private void addClass(TypeModel type, String alias, String parentAlias, FieldModel linkField) {
		if (parentAlias != null) {
			checkForCyclicMapping(type, parentAlias);
		}

		List<TableMapping> tableMappings = determineTableMapping(type);

		Alias previousAlias = null;
		for(int i = tableMappings.size() - 1; i >= 0; i--) {
			TableMapping mapping = tableMappings.get(i);
			TableMapping superMapping = i > 0 ? tableMappings.get(i - 1) : null;

			String combinedAlias = mapping.type.isSameType(type) ? alias : alias + "." + mapping.tableName;
			if (alias.equals(rootAlias)) {
				combinedAlias = mapping.tableName;
			}

			if (superMapping != null) {
				String linkAlias = alias + "." + superMapping.tableName;
				JoinType joinType = JoinType.LEFT;
				if (alias.equals(rootAlias)) {
					linkAlias = superMapping.tableName;
					joinType = JoinType.INNER;
				}
				String idField = determineIdField(superMapping.type).getName();
				query.addJoin(joinType, superMapping.schemaName, superMapping.tableName, linkAlias, new SqlExpression("{" + linkAlias + "." + idField + "} = {" + combinedAlias + "." + idField + "}"));
			}

			Alias newAlias = new Alias(combinedAlias, mapping.type, parentAlias, linkField, determineIdFields(mapping.type));
			if (previousAlias != null) {
				newAlias.setSubClassAliases(Arrays.asList(previousAlias.getAlias()));
				newAlias.setParentAlias(previousAlias.getAlias());
			}
			previousAlias = newAlias;
			aliases.put(combinedAlias, newAlias);

			addFields(combinedAlias, alias, mapping.type, superMapping != null ? superMapping.type : null, null);
		}

		SubClasses subClassesAnn = type.getAnnotation(SubClasses.class);
		if (subClassesAnn != null) {
			DiscriminatorColumn discAnn = type.getAnnotation(DiscriminatorColumn.class);

			if (discAnn != null) {
				// Single table inheritance: all subclasses in same table with discriminator column
				String discriminatorColumnName = discAnn.name();

				// Add discriminator column to SELECT
				addField(new SqlExpression("{" + alias + "." + discriminatorColumnName + "}"),
						alias + "." + discriminatorColumnName, null);

				// Build discriminator values map
				Map<String, TypeModel> discriminatorValues = new HashMap<>();
				discriminatorValues.put(type.getSimpleName(), type);

				// Add fields for all subclasses (from same table, no JOINs)
				for (Class<?> subClass : subClassesAnn.value()) {
					TypeModel subType = new ReflectionTypeModel(subClass);
					discriminatorValues.put(subType.getSimpleName(), subType);
					// Add subclass-specific fields from the same table
					addFieldsForSingleTableInheritance(alias, subType, type);
				}

				// Store STI info in the alias
				Alias aliasObj = aliases.get(alias);
				aliasObj.setSingleTableInheritance(true);
				aliasObj.setDiscriminatorColumn(alias + "." + discriminatorColumnName);
				aliasObj.setDiscriminatorValues(discriminatorValues);
			} else {
				// Table-per-subclass inheritance: each subclass in its own table with JOINs
				TableMapping thisMapping = tableMappings.get(tableMappings.size() - 1);
				FieldModel thisIdField = determineIdField(thisMapping.type);
				List<String> subClassesAdded = new ArrayList<String>();
				for (Class<?> subClass : subClassesAnn.value()) {
					TypeModel subType = new ReflectionTypeModel(subClass);
					List<TableMapping> mappings = determineTableMapping(subType);
					TableMapping mapping = mappings.get(mappings.size() - 1);

					String linkAlias = alias + "." + mapping.tableName;
					String idField = determineIdField(mapping.type).getName();

					query.addJoin(JoinType.LEFT, mapping.schemaName, mapping.tableName, linkAlias, new SqlExpression("{" + linkAlias + "." + idField + "} = {" + alias + "." + idField + "}"));
					Alias subClassAlias = new Alias(linkAlias, mapping.type, alias, thisIdField, determineIdFields(mapping.type));
					subClassAlias.setIsASubClass(true);
					aliases.put(linkAlias, subClassAlias);
					subClasses.put(linkAlias, subClassAlias);

					// Also add the idfield of the linked alias, so we have at least one
					addField(new SqlExpression("{" + linkAlias + "." + idField + "}"), linkAlias + "." + idField, thisIdField);

					addFields(linkAlias, mapping.type, thisMapping.type);

					subClassesAdded.add(linkAlias);
				}
				aliases.get(alias).setSubClassAliases(subClassesAdded);
			}
		}

	}

	private void checkForCyclicMapping(TypeModel type, final String parentAlias) {
		Alias alias;
		String parent = parentAlias;
		List<TypeModel> parentTypes = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		parentTypes.add(type);
		while ((alias = aliases.get(parent)) != null) {
			if (visited.contains(parent)) {
				// Avoid infinite loop in parent chain
				break;
			}
			visited.add(parent);
			parentTypes.add(alias.getResultType());
			if (alias.getResultType().isSameType(type)) {
				String message = parentTypes.stream()
						.map(it -> it.getSimpleName())
						.collect(Collectors.joining(" -> "));
				throw new MappingException("Mapping cycle detected: " + message);
			}
			parent = alias.getParentAlias();
		}
	}

	private void addFields(String alias, TypeModel type, TypeModel superType) {
		addFields(alias, alias, type, superType, null);
	}

	private void addFields(String alias, String fieldsAlias, TypeModel type, TypeModel superType, String fieldNamePrefix) {
		for(FieldModel f : collectFieldsOfClass(type, superType)) {
			if (f instanceof ReflectionFieldModel) {
				((ReflectionFieldModel) f).getReflectionField().setAccessible(true);
			}

			TypeModel fieldType = f.getType();
			boolean isRoot = isRootOrSuperClassOfRoot(alias);
			if (isListOrArray(fieldType)) {
				TypeModel componentType = getCollectionComponentType(f);

				Link linkAnn = f.getAnnotation(Link.class);
				if (linkAnn != null) {
					if (!Link.NONE.equals(linkAnn.fetchColumn())) {
						String foreignlinkfieldname = f.getAnnotation(Link.class).foreignlinkfield();
						if (Link.NONE.equals(foreignlinkfieldname)) {
							foreignlinkfieldname = linkFieldName(type);
						}

						String linkAlias = alias.equals(rootAlias) ? f.getName() : (alias + "." + f.getName());
						String joinCondition = null;
						String idField = null;
						if (f.getAnnotation(JoinCondition.class) != null) {
							joinCondition = f.getAnnotation(JoinCondition.class).value();
						} else {
							idField = determineIdField(type).getName();
						}

						joinMany(query, alias, f.getName(), linkAnn.linkschema(), linkAnn.linktable(), idField, foreignlinkfieldname, joinCondition);

						Alias a = new Alias(linkAlias, componentType, alias, f, determineIdFields(componentType));
						a.setIsLinkedValue(true);
						aliases.put(linkAlias, a);
						addField(new SqlExpression("{" + linkAlias + "." + linkAnn.fetchColumn() + "}"), linkAlias + ".value", f);
					} else if (linkAnn.linktable().equals(Link.NONE)) {
						String linkAlias = joinMany(alias, query, f, componentType);
						addClass(componentType, linkAlias, alias, f);
					} else {
						// Many to many
						String linkTableAlias = alias + "_" + f.getName();
						String linkAlias = isRoot ? linkTableAlias : (alias + "." + linkTableAlias);
						String idField = determineIdField(type).getName();
						String linkfieldname = linkAnn.linkfield();
						if (Link.NONE.equals(linkfieldname)) {
							linkfieldname = linkFieldName(type);
						}

						SqlExpression joinCondition = new SqlExpression("{" + alias + "." + idField + "} = {" + linkAlias + "." + linkfieldname + "}");
						if (f.getAnnotation(JoinCondition.class) != null) {
							joinCondition = new SqlExpression(resolveJoinConditionAliases(f.getAnnotation(JoinCondition.class).value(), alias, linkAlias, linkAlias));
						}
						query.addJoin(JoinType.LEFT, linkAnn.linkschema(), linkAnn.linktable(), linkAlias, joinCondition);

						String foreignLinkAlias = isRoot ? f.getName() : (alias + "." + f.getName());
						String foreignIdField = determineIdField(componentType).getName();
						String foreignlinkfieldname = linkAnn.foreignlinkfield();
						if (Link.NONE.equals(foreignlinkfieldname)) {
							foreignlinkfieldname = linkFieldName(componentType);
						}
						SqlExpression foreignJoinCondition = new SqlExpression("{" + linkAlias + "." + foreignlinkfieldname + "} = {" + foreignLinkAlias + "." + foreignIdField + "}");
						query.addJoin(JoinType.LEFT, determineTableMapping(componentType).get(0).schemaName, determineTableMapping(componentType).get(0).tableName, foreignLinkAlias, foreignJoinCondition);

						addClass(componentType, foreignLinkAlias, alias, f);
					}
				} else if (determineTableMapping(componentType).size() > 0) {
					String linkAlias = joinMany(alias, query, f, componentType);
					addClass(componentType, linkAlias, alias, f);
				}

			} else if (isLinkedClass(fieldType)) {
				String parent = fieldNamePrefix == null ? alias : fieldsAlias;
				String linkAlias = joinOne(parent, query, f, fieldType, fieldNamePrefix);
				addClass(fieldType, linkAlias, parent, f);
			} else if (isEmbedded(f)) {

				String prefix = determinePrefix(f);
				String embedAlias = (isRoot && fieldNamePrefix == null) ? f.getName() : fieldsAlias + "." + f.getName();
				Alias newAlias = new Alias(embedAlias, f.getType(), fieldsAlias, f, Collections.emptyList());
				newAlias.setIsEmbedded(true);

				aliases.put(embedAlias, newAlias);
				addFields(alias, embedAlias, f.getType(), null, (fieldNamePrefix == null ? "" : fieldNamePrefix) + prefix);

			} else if (f.getAnnotation(Other.class) != null) {
				aliases.get(alias).setOtherField(f);
				// Also add the otherfield to the subclasses
				List<String> subClassAliases = aliases.get(alias).getSubClassAliases();
				if (subClassAliases != null) {
					for(String subClassAlias : subClassAliases) {
						aliases.get(subClassAlias).setOtherField(f);
					}
				}
			} else {
				SqlExpression selectExpression;
				if (f.getAnnotation(Select.class) != null) {
					selectExpression = new SqlExpression(resolveJoinConditionAliases(f.getAnnotation(Select.class).value(), alias, null, null));
				} else {
					String fieldName = determineSqlFieldName(f);
					selectExpression = new SqlExpression("{" + alias + "." + ((fieldNamePrefix == null ? "" : fieldNamePrefix) + fieldName) + "}");
				}
				addField(selectExpression, fieldsAlias + "." + f.getName(), f);
			}
		}
	}

	public static String determineSqlFieldName(FieldModel f) {
		Objects.requireNonNull(f, "field must not be null");
		String columnName = AnnotationHelper.getColumnName(f);
		return columnName != null ? columnName : f.getName();
	}

	/**
	 * Determines the SQL field name for a reflection Field. Backward compatible version.
	 */
	public static String determineSqlFieldName(Field f) {
		Objects.requireNonNull(f, "field must not be null");
		String columnName = AnnotationHelper.getColumnName(f);
		return columnName != null ? columnName : f.getName();
	}

	private String joinMany(String alias, SqlQuery<?> result, FieldModel f, TypeModel componentType) {
		TableMapping tableMapping = lookupTableMapping(componentType);
		String idField = determineIdField(f.getDeclaringType()).getName();

		String linkField = linkFieldName(f.getDeclaringType());
		Link linkAnn = f.getAnnotation(Link.class);
		if (linkAnn != null && !Link.NONE.equals(linkAnn.foreignlinkfield())) {
			linkField = linkAnn.foreignlinkfield();
		}
		String joinCondition = null;
		if (f.getAnnotation(JoinCondition.class) != null) {
			joinCondition = f.getAnnotation(JoinCondition.class).value();
		}

		return joinMany(result, alias, f.getName(), tableMapping.schemaName, tableMapping.tableName, idField, linkField, joinCondition);
	}

	private boolean isRootOrSuperClassOfRoot(String alias) {
		if (alias.equals(rootAlias)) {
			return true;
		}
		List<String> subs = aliases.get(alias).getSubClassAliases();
		return subs != null && subs.contains(rootAlias);
	}

	private String joinMany(SqlQuery<?> result, String alias, String fieldName, String schemaName, String tableName, String idField, String linkField, String joinCondition) {
		String linkAlias = alias.equals(rootAlias) ? fieldName : (alias + "." + fieldName);
		Alias parentAlias = aliases.get(alias);
		while (parentAlias.getSubClassAliases() != null && parentAlias.getSubClassAliases().size() == 1) {
			String parentAliasStr = parentAlias.getParentAlias();
			if (rootAlias.equals(parentAliasStr)) {
				linkAlias = fieldName;
				break;
			}
			linkAlias = parentAliasStr + "." + fieldName;
			parentAlias = aliases.get(parentAliasStr);
		}

		if (joinCondition == null) {
			joinCondition = "{" + alias + "." + idField + "} = {" + linkAlias + "." + linkField + "}";
		} else {
			joinCondition = resolveJoinConditionAliases(joinCondition, alias, linkAlias, linkAlias);
		}
		result.addJoin(JoinType.LEFT, schemaName, tableName, linkAlias, new SqlExpression(joinCondition));
		return linkAlias;
	}

	private String joinOne(String alias, SqlQuery<?> result, FieldModel f, TypeModel type, String linkFieldPrefix) {
		TableMapping table = lookupTableMapping(type);
		boolean isEmbeddedLinkfield = linkFieldPrefix != null;
		String linkAlias = alias.equals(rootAlias) ? f.getName() : (alias + "." + f.getName());

		Alias parentAlias = aliases.get(alias);
		while (parentAlias.getSubClassAliases() != null && parentAlias.getSubClassAliases().size() == 1) {
			String parentAliasStr = parentAlias.getParentAlias();
			if (rootAlias.equals(parentAliasStr)) {
				linkAlias = f.getName();
				break;
			}
			linkAlias = parentAliasStr + "." + f.getName();
			parentAlias = aliases.get(parentAliasStr);
		}

		String linkField = (isEmbeddedLinkfield ? linkFieldPrefix : "") + linkFieldName(f);

		while (parentAlias.getParentAlias() != null && aliases.get(parentAlias.getParentAlias()).getIsEmbedded()) {
			parentAlias = aliases.get(parentAlias.getParentAlias());
		}

		String linkFieldAlias = isEmbeddedLinkfield ? parentAlias.getParentAlias() : alias;

		SqlExpression joinCondition = null;
		if (f.getAnnotation(JoinCondition.class) != null) {
			joinCondition = new SqlExpression(resolveJoinConditionAliases(f.getAnnotation(JoinCondition.class).value(), linkFieldAlias, linkAlias, null));
		} else {
			FieldModel idField = determineIdField(type);
			joinCondition = new SqlExpression("{" + linkFieldAlias + "." + linkField + "} = {" + linkAlias + "." + idField.getName() + "}");
		}
		result.addJoin(JoinType.LEFT, table.schemaName, table.tableName, linkAlias, joinCondition);
		return linkAlias;
	}

	private String resolveJoinConditionAliases(String expression, String alias, String linkAlias, String linkTableAlias) {
		return CurlyMarkers.processMarkers(expression, marker -> {
			if ("linktable".equals(marker)) {
				return "{" + linkTableAlias + "}";
			} else if (marker.startsWith("linktable.")) {
				String rest = marker.substring("linktable.".length());
				return "{" + linkTableAlias + "." + rest + "}";
			} else if ("this".equals(marker)) {
				return "{" + alias + "}";
			} else if (marker.startsWith("this.")) {
				String rest = marker.substring("this.".length());
				return "{" + alias + "." + rest + "}";
			} else if (marker.contains(".")) {
				// Handle alias.column patterns - keep as marker for resolveAliases to handle
				return "{" + marker + "}";
			} else {
				// Simple field reference - prefix with current alias if not at root level
				return isRootOrSuperClassOfRoot(alias) ? "{" + marker + "}" : "{" + alias + "." + marker + "}" ;
			}
		});
	}

	/**
	 * Adds fields for a subclass in single table inheritance mode.
	 * These fields come from the same table as the parent, so we use the parent alias.
	 */
	private void addFieldsForSingleTableInheritance(String alias, TypeModel subType, TypeModel parentType) {
		// Collect only the fields declared in the subclass (not inherited from parent)
		for (FieldModel f : collectFieldsOfClass(subType, parentType)) {
			// Skip complex types - only simple fields are supported in STI
			TypeModel fieldType = f.getType();
			if (isListOrArray(fieldType) || isLinkedClass(fieldType)) {
				continue;
			}

			// Skip @Transient, @Id (already added from parent), and @Other fields
			if (f.getAnnotation(Transient.class) != null ||
				f.getAnnotation(Id.class) != null ||
				f.getAnnotation(Other.class) != null) {
				continue;
			}

			String fieldName = f.getName();
			FieldName fieldNameAnn = f.getAnnotation(FieldName.class);
			if (fieldNameAnn != null) {
				fieldName = fieldNameAnn.value();
			}

			addField(new SqlExpression("{" + alias + "." + fieldName + "}"),
					alias + "." + f.getName(), f);
		}
	}

	private void addField(SqlExpression expression, String fieldAlias, FieldModel f) {
		if (f instanceof ReflectionFieldModel) {
			fieldMappings.put(fieldAlias, dbContext.getFieldMapping(((ReflectionFieldModel) f).getReflectionField()));
		}
		query.addField(expression, fieldAlias);
	}

	private static String linkFieldName(TypeModel type) {
		return lookupTableMapping(type).tableName + "_id";
	}

	private static String linkFieldName(FieldModel f) {
		// Check for @JoinColumn or @Link(linkfield) first
		String joinColumnName = AnnotationHelper.getJoinColumnName(f);
		if (joinColumnName != null) {
			return joinColumnName;
		}
		// Fall back to @FieldName or @Column(name)
		String columnName = AnnotationHelper.getColumnName(f);
		if (columnName != null) {
			return columnName;
		}
		return f.getName() + "_id";
	}

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

	public static boolean isLinkedClass(TypeModel type) {
		return !type.isPrimitive() && determineTableMapping(type).size() > 0;
	}

	/** Backward compatible overload */
	public static boolean isLinkedClass(Class<?> type) {
		return isLinkedClass(new ReflectionTypeModel(type));
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

	/**
	 * Checks if a field is embedded (has @Embedded annotation).
	 */
	public static boolean isEmbedded(FieldModel f) {
		return AnnotationHelper.isEmbedded(f);
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
			for (Alias a : queryBuilder.aliases.values()) {
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
		List<FieldModel> idFields = determineIdFields(resultType);
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

	public List<T> processRowsStreaming(Callable<Map<String,Object>> rowProvider) {

		return new ArrayList<>();
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
		for (Alias a : aliases.values()) {
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
					TypeModel entityType = resultType;

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
							entityType = aliases.get(subClassAlias).getResultType();
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
					parentId = createId(parentAlias, parentValues, aliases.get(parentAlias).getIdFields());
					parent = allEntities.get(parentId);
				}

				if (parent == null) {
					Alias parentAliasObject = aliases.get(a.getParentAlias());
					List<String> subs = parentAliasObject.getSubClassAliases();
					if (subs != null && subs.size() > 0) {
						for (String sub : subs) {
							parentValues = onThisRow.get(sub);
							if (parentValues != null && parentValues.size() > 0) {
								parentAlias = sub;
								parentId = createId(parentAlias, parentValues, aliases.get(parentAlias).getIdFields());
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
							entityType = aliases.get(subClassAlias).getResultType();
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

			Alias a = aliases.get(alias);
			if (a.getIsASubClass()) {
				if (values == null || allNulls(values)) {
					continue;
				}
				a.getParentAlias();
			} else {
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
		for(Alias a : aliases.values()) {
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
		return aliases;
	}

	public Map<String, FieldMapping> getFieldMappings() {
		return fieldMappings;
	}

	public TypeModel getResultType() {
		return resultType;
	}

	@SuppressWarnings("unchecked")
	public Class<T> getResultClass() {
		return (Class<T>) getReflectionClass(resultType);
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
			if (f.isTransient()) {
				continue;
			}
			result.add(f);
		}
		return result;
	}

	public static List<FieldModel> collectFieldsOfClass(TypeModel type, TypeModel stopAtSuperType) {
		List<FieldModel> result = new ArrayList<FieldModel>();
		TypeModel current = type;
		while (current != null && (stopAtSuperType == null || !current.isSameType(stopAtSuperType))) {
			result.addAll(0, filterFields(current));
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

	// ========== Backward compatibility methods using Class<?> ==========

	/**
	 * Determines table mappings for a class. This method is kept for backward compatibility.
	 */
	public static List<TableMapping> determineTableMapping(Class<?> clz) {
		return determineTableMapping(new ReflectionTypeModel(clz));
	}

	/**
	 * Determines ID fields for a class. Returns reflection Field objects for backward compatibility.
	 */
	public static List<Field> determineIdFields(Class<?> clz) {
		List<FieldModel> idFields = determineIdFields(new ReflectionTypeModel(clz));
		List<Field> result = new ArrayList<>();
		for (FieldModel f : idFields) {
			if (f instanceof ReflectionFieldModel) {
				result.add(((ReflectionFieldModel) f).getReflectionField());
			}
		}
		return result;
	}

	/**
	 * Determines the single ID field for a class. Returns reflection Field for backward compatibility.
	 */
	public static Field determineIdField(Class<?> clz) {
		FieldModel f = determineIdField(new ReflectionTypeModel(clz));
		if (f instanceof ReflectionFieldModel) {
			return ((ReflectionFieldModel) f).getReflectionField();
		}
		throw new MappingException("ID field is not a reflection field");
	}

	/**
	 * Collects fields from a class up to a stop class. Returns reflection Field objects for backward compatibility.
	 */
	public static List<Field> collectFieldsOfClass(Class<?> clz, Class<?> stopAtSuperClass) {
		TypeModel type = new ReflectionTypeModel(clz);
		TypeModel stopType = stopAtSuperClass != null ? new ReflectionTypeModel(stopAtSuperClass) : null;
		List<FieldModel> fields = collectFieldsOfClass(type, stopType);
		List<Field> result = new ArrayList<>();
		for (FieldModel f : fields) {
			if (f instanceof ReflectionFieldModel) {
				result.add(((ReflectionFieldModel) f).getReflectionField());
			}
		}
		return result;
	}

	/**
	 * Collects all fields from a class. Returns reflection Field objects for backward compatibility.
	 */
	public static Iterable<Field> collectFieldsOfClass(Class<?> type) {
		return collectFieldsOfClass(type, (Class<?>) null);
	}

	/**
	 * Filters fields from a class. Returns reflection Field objects for backward compatibility.
	 */
	public static Collection<Field> filterFields(Class<?> clz) {
		List<Field> result = new ArrayList<Field>();
		for (Field f : clz.getDeclaredFields()) {
			if ((f.getModifiers() & Modifier.STATIC) > 0) {
				continue;
			}
			if ((f.getModifiers() & Modifier.TRANSIENT) > 0) {
				continue;
			}
			if (AnnotationHelper.isTransient(f)) {
				continue;
			}
			result.add(f);
		}
		return result;
	}

	/**
	 * Checks if a type is a list or array. Backward compatible version.
	 */
	public static boolean isListOrArray(Class<?> type) {
		return isListOrArray(new ReflectionTypeModel(type));
	}

	/**
	 * Builds ID condition for a class. Backward compatible version.
	 */
	public static List<SqlExpression> buildIdCondition(DbContext context, Class<?> clz, Object id) {
		return buildIdCondition(context, new ReflectionTypeModel(clz), id);
	}

}
