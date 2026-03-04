package org.pojoquery.pipeline;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.pojoquery.AnnotationHelper;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Aggregate;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.From;
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
import org.pojoquery.annotations.Subquery;
import org.pojoquery.annotations.Transient;
import org.pojoquery.internal.MappingException;
import org.pojoquery.internal.TableMapping;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionFieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;
import org.pojoquery.util.CurlyMarkers;

/**
 * QueryModel represents the structure of a query without SqlQuery dependencies.
 * It holds the aliases, field mappings, joins and field selections that will
 * later be applied to a SqlQuery.
 * 
 * <p>The query building process is split into two phases:</p>
 * <ol>
 *   <li>Phase 1 (QueryModel): Build the model structure - aliases, field mappings, 
 *       join definitions, and field selections</li>
 *   <li>Phase 2 (CustomizableQueryBuilder): Apply the model to SqlQuery</li>
 * </ol>
 */
public class QueryModel {

	/**
	 * Represents a join to be added to the query.
	 */
	public static class JoinInfo {
		public final JoinType joinType;
		public final String schemaName;
		public final String tableName;
		public final String alias;
		public final SqlExpression joinCondition;
		public final QueryModel subquery; // For derived table joins

		public JoinInfo(JoinType joinType, String schemaName, String tableName, String alias, SqlExpression joinCondition) {
			this.joinType = joinType;
			this.schemaName = "".equals(schemaName) ? null : schemaName;
			this.tableName = tableName;
			this.alias = alias;
			this.joinCondition = joinCondition;
			this.subquery = null;
		}

		public JoinInfo(JoinType joinType, QueryModel subquery, String alias, SqlExpression joinCondition) {
			this.joinType = joinType;
			this.schemaName = null;
			this.tableName = null;
			this.alias = alias;
			this.joinCondition = joinCondition;
			this.subquery = subquery;
		}

		public boolean isSubquery() {
			return subquery != null;
		}
	}

	/**
	 * Represents a field to be added to the query's SELECT clause.
	 */
	public static class FieldInfo {
		public final SqlExpression expression;
		public final String fieldAlias;
		public final FieldModel field;

		public FieldInfo(SqlExpression expression, String fieldAlias, FieldModel field) {
			this.expression = expression;
			this.fieldAlias = fieldAlias;
			this.field = field;
		}
	}

	/**
	 * Helper class to store subquery field information for later processing.
	 */
	private static class SubqueryFieldInfo {
		final FieldModel field;
		final Subquery annotation;

		SubqueryFieldInfo(FieldModel field, Subquery annotation) {
			this.field = field;
			this.annotation = annotation;
		}
	}

	private static final java.util.regex.Pattern ALIAS_PATTERN = java.util.regex.Pattern.compile("\\{([a-zA-Z0-9_\\.]+)\\}");

	private final LinkedHashMap<String, Alias> aliases = new LinkedHashMap<>();
	private final Map<String, List<String>> keysByAlias = new HashMap<>();
	private final List<JoinInfo> joins = new ArrayList<>();
	private final List<FieldInfo> fields = new ArrayList<>();
	private final List<String> groupByClauses = new ArrayList<>();
	private final List<String> orderByClauses = new ArrayList<>();
	private final List<Join> classLevelJoins = new ArrayList<>();

	private final TypeModel resultType;
	private final String rootAlias;
	private final String schemaName;
	private final String tableName;

	/**
	 * Creates a QueryModel for the given type.
	 */
	public QueryModel(TypeModel type) {
		this.resultType = type;

		// Check for @From annotation - if present, use the source type for building
		// the query structure (tables/joins) while using resultType for field selection
		From fromAnn = type.getAnnotation(From.class);
		TypeModel sourceType = fromAnn != null ? new ReflectionTypeModel(fromAnn.value()) : type;

		final TableMapping tableMapping = lookupTableMapping(sourceType);
		this.schemaName = tableMapping.schemaName;
		this.tableName = tableMapping.tableName;
		this.rootAlias = tableName;

		// Collect class-level join annotations
		classLevelJoins.addAll(getJoinAnnotations(type));

		// Collect group by
		GroupBy groupByAnn = type.getAnnotation(GroupBy.class);
		if (groupByAnn != null) {
			for (String groupBy : groupByAnn.value()) {
				groupByClauses.add(groupBy);
			}
		}

		// Collect order by
		OrderBy orderByAnn = type.getAnnotation(OrderBy.class);
		if (orderByAnn != null) {
			for (String orderBy : orderByAnn.value()) {
				orderByClauses.add(orderBy);
			}
		}

		if (fromAnn != null) {
			// Build query structure from source type, but select fields from result type
			addClass(sourceType, rootAlias, null, null);
			addFieldsFromResultType(type, rootAlias);
		} else {
			addClass(type, rootAlias, null, null);
		}
	}

	/**
	 * Creates a QueryModel for the given class (convenience method).
	 */
	public QueryModel(Class<?> clz) {
		this(new ReflectionTypeModel(clz));
	}

	// --- Getters for model data ---

	public LinkedHashMap<String, Alias> getAliases() {
		return aliases;
	}

	public Map<String, List<String>> getKeysByAlias() {
		return keysByAlias;
	}

	public List<JoinInfo> getJoins() {
		return joins;
	}

	public List<FieldInfo> getFields() {
		return fields;
	}

	public List<String> getGroupByClauses() {
		return groupByClauses;
	}

	public List<String> getOrderByClauses() {
		return orderByClauses;
	}

	public List<Join> getClassLevelJoins() {
		return classLevelJoins;
	}

	public TypeModel getResultType() {
		return resultType;
	}

	public String getRootAlias() {
		return rootAlias;
	}

	public String getSchemaName() {
		return schemaName;
	}

	public String getTableName() {
		return tableName;
	}

	// --- Model building methods ---

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
		for (int i = tableMappings.size() - 1; i >= 0; i--) {
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
				addJoin(joinType, superMapping.schemaName, superMapping.tableName, linkAlias,
						new SqlExpression("{" + linkAlias + "." + idField + "} = {" + combinedAlias + "." + idField + "}"));
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
				for (TypeModel subType : type.getTypeValuesFromAnnotation(subClassesAnn, "value")) {
					discriminatorValues.put(subType.getSimpleName(), subType);
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
				List<String> subClassesAdded = new ArrayList<>();
				for (Class<?> subClass : subClassesAnn.value()) {
					TypeModel subType = new ReflectionTypeModel(subClass);
					List<TableMapping> mappings = determineTableMapping(subType);
					TableMapping mapping = mappings.get(mappings.size() - 1);

					String linkAlias = alias + "." + mapping.tableName;
					String idField = determineIdField(mapping.type).getName();

					addJoin(JoinType.LEFT, mapping.schemaName, mapping.tableName, linkAlias,
							new SqlExpression("{" + linkAlias + "." + idField + "} = {" + alias + "." + idField + "}"));
					Alias subClassAlias = new Alias(linkAlias, mapping.type, alias, thisIdField, determineIdFields(mapping.type));
					subClassAlias.setIsASubClass(true);
					aliases.put(linkAlias, subClassAlias);

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
		for (FieldModel f : collectFieldsOfClass(type, superType)) {
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
							idField = determineSqlFieldName(determineIdField(type));
						}

						joinMany(alias, f.getName(), linkAnn.linkschema(), linkAnn.linktable(), idField, foreignlinkfieldname, joinCondition);

						Alias a = new Alias(linkAlias, componentType, alias, f, determineIdFields(componentType));
						a.setIsLinkedValue(true);
						aliases.put(linkAlias, a);
						addField(new SqlExpression("{" + linkAlias + "." + linkAnn.fetchColumn() + "}"), linkAlias + ".value", f);
					} else if (linkAnn.linktable().equals(Link.NONE)) {
						String linkAlias = joinMany(alias, f, componentType);
						addClass(componentType, linkAlias, alias, f);
					} else {
						// Many to many
						String linkTableAlias = alias + "_" + f.getName();
						String linkAlias = isRoot ? linkTableAlias : (alias + "." + linkTableAlias);
						String idField = determineSqlFieldName(determineIdField(type));
						String linkfieldname = linkAnn.linkfield();
						if (Link.NONE.equals(linkfieldname)) {
							linkfieldname = linkFieldName(type);
						}

						SqlExpression joinCondition = new SqlExpression("{" + alias + "." + idField + "} = {" + linkAlias + "." + linkfieldname + "}");
						if (f.getAnnotation(JoinCondition.class) != null) {
							joinCondition = new SqlExpression(resolveJoinConditionAliases(f.getAnnotation(JoinCondition.class).value(), alias, linkAlias, linkAlias));
						}
						addJoin(JoinType.LEFT, linkAnn.linkschema(), linkAnn.linktable(), linkAlias, joinCondition);

						String foreignLinkAlias = isRoot ? f.getName() : (alias + "." + f.getName());
						String foreignIdField = determineSqlFieldName(determineIdField(componentType));
						String foreignlinkfieldname = linkAnn.foreignlinkfield();
						if (Link.NONE.equals(foreignlinkfieldname)) {
							foreignlinkfieldname = linkFieldName(componentType);
						}
						SqlExpression foreignJoinCondition = new SqlExpression("{" + linkAlias + "." + foreignlinkfieldname + "} = {" + foreignLinkAlias + "." + foreignIdField + "}");
						addJoin(JoinType.LEFT, determineTableMapping(componentType).get(0).schemaName, determineTableMapping(componentType).get(0).tableName, foreignLinkAlias, foreignJoinCondition);

						addClass(componentType, foreignLinkAlias, alias, f);
					}
				} else if (determineTableMapping(componentType).size() > 0) {
					String linkAlias = joinMany(alias, f, componentType);
					addClass(componentType, linkAlias, alias, f);
				}

			} else if (isLinkedClass(fieldType)) {
				String parent = fieldNamePrefix == null ? alias : fieldsAlias;
				String linkAlias = joinOne(parent, f, fieldType, fieldNamePrefix);
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
					for (String subClassAlias : subClassAliases) {
						aliases.get(subClassAlias).setOtherField(f);
					}
				}
			} else if (f.getAnnotation(Subquery.class) != null) {
				// Process @Subquery field by building a derived table join
				processSubqueryField(f, f.getAnnotation(Subquery.class), alias);
			} else {
				SqlExpression selectExpression;
				Aggregate aggAnn = f.getAnnotation(Aggregate.class);
				if (aggAnn != null) {
					selectExpression = new SqlExpression(resolveJoinConditionAliases(aggAnn.value(), alias, null, null));
				} else if (f.getAnnotation(Select.class) != null) {
					selectExpression = new SqlExpression(resolveJoinConditionAliases(f.getAnnotation(Select.class).value(), alias, null, null));
				} else {
					String fieldName = determineSqlFieldName(f);
					selectExpression = new SqlExpression("{" + alias + "." + ((fieldNamePrefix == null ? "" : fieldNamePrefix) + fieldName) + "}");
				}
				addField(selectExpression, fieldsAlias + "." + f.getName(), f);
			}
		}
	}

	/**
	 * Adds fields from result type when using @From annotation.
	 * The source type's structure (joins) is already built; this method adds
	 * field selections from the result type, supporting @Aggregate expressions
	 * and @Subquery fields for derived table joins.
	 */
	private void addFieldsFromResultType(TypeModel resultType, String rootAlias) {
		// Clear field mappings added by addClass (we only want result type fields)
		fields.clear();

		boolean hasAggregates = false;
		List<String> nonAggregateFields = new ArrayList<>();
		Set<String> referencedAliases = new HashSet<>();
		List<SubqueryFieldInfo> subqueryFields = new ArrayList<>();

		for (FieldModel f : collectFieldsOfClass(resultType, null)) {
			if (f instanceof ReflectionFieldModel) {
				((ReflectionFieldModel) f).getReflectionField().setAccessible(true);
			}

			if (f.getAnnotation(Transient.class) != null) {
				continue;
			}
			if (f.isStatic()) {
				continue;
			}

			Aggregate aggAnn = f.getAnnotation(Aggregate.class);
			Select selectAnn = f.getAnnotation(Select.class);
			Subquery subqueryAnn = f.getAnnotation(Subquery.class);

			if (subqueryAnn != null) {
				// Collect subquery fields for later processing
				subqueryFields.add(new SubqueryFieldInfo(f, subqueryAnn));
				continue;
			}

			SqlExpression selectExpression;
			if (aggAnn != null) {
				hasAggregates = true;
				String resolved = resolveJoinConditionAliases(aggAnn.value(), rootAlias, null, null);
				selectExpression = new SqlExpression(resolved);
				collectReferencedAliases(resolved, referencedAliases);
			} else if (selectAnn != null) {
				String resolved = resolveJoinConditionAliases(selectAnn.value(), rootAlias, null, null);
				selectExpression = new SqlExpression(resolved);
				nonAggregateFields.add(selectExpression.getSql());
				collectReferencedAliases(resolved, referencedAliases);
			} else {
				String fieldName = determineSqlFieldName(f);
				selectExpression = new SqlExpression("{" + rootAlias + "." + fieldName + "}");
				nonAggregateFields.add("{" + rootAlias + "." + fieldName + "}");
				referencedAliases.add(rootAlias);
			}

			addField(selectExpression, rootAlias + "." + f.getName(), f);
		}

		// Auto-add GROUP BY for non-aggregate fields when aggregates are present
		if (hasAggregates && !nonAggregateFields.isEmpty()) {
			for (String field : nonAggregateFields) {
				groupByClauses.add(field);
			}
		}

		// Prune unused joins to prevent row multiplication
		pruneUnusedJoins(referencedAliases, rootAlias);

		// Process subquery fields (add derived table joins)
		for (SubqueryFieldInfo sqInfo : subqueryFields) {
			processSubqueryField(sqInfo.field, sqInfo.annotation, rootAlias);
		}
	}

	/**
	 * Processes a @Subquery field by building a derived table join.
	 * The field's type must have @From and @Aggregate annotations.
	 * Supports both single-object fields and collection (List) fields for to-many relationships.
	 */
	private void processSubqueryField(FieldModel field, Subquery subqueryAnn, String rootAlias) {
		TypeModel fieldType = field.getType();
		String subqueryAlias = field.getName();
		String joinOn = subqueryAnn.joinOn();

		// Check if this is a to-many (collection) subquery
		boolean isToMany = isListOrArray(fieldType);
		TypeModel subqueryType = isToMany ? getCollectionComponentType(field) : fieldType;

		// The subquery type should have @From annotation
		From fromAnn = subqueryType.getAnnotation(From.class);
		if (fromAnn == null) {
			throw new MappingException("@Subquery field '" + field.getName() + "' type must have @From annotation");
		}

		// Build the subquery with simple column aliases (no table prefix)
		QueryModel subqueryModel = buildSubquery(subqueryType);

		// Add the subquery as a derived table join
		// Join condition: {subqueryAlias.joinOn} = {rootAlias.joinOn}
		SqlExpression joinCondition = new SqlExpression(
				"{" + subqueryAlias + "." + joinOn + "} = {" + rootAlias + "." + joinOn + "}"
		);
		addSubqueryJoin(JoinType.LEFT, subqueryModel, subqueryAlias, joinCondition);

		// For to-many: need id fields for deduplication in result mapping
		List<FieldModel> idFields = isToMany ? determineIdFields(subqueryType) : Collections.emptyList();

		// Register the subquery alias for result mapping
		Alias newAlias = new Alias(subqueryAlias, subqueryType, rootAlias, field, idFields);
		if (!isToMany) {
			newAlias.setIsEmbedded(true);  // Treat single-object like embedded for result mapping
		}
		aliases.put(subqueryAlias, newAlias);

		// Add field selections for subquery fields
		for (FieldModel sqField : collectFieldsOfClass(subqueryType, null)) {
			if (sqField.getAnnotation(Transient.class) != null || sqField.isStatic()) {
				continue;
			}

			String fieldAlias = subqueryAlias + "." + sqField.getName();
			SqlExpression fieldExpr = new SqlExpression("{" + fieldAlias + "}");
			addField(fieldExpr, fieldAlias, sqField);
		}
	}

	/**
	 * Builds a subquery statement with simple column aliases (just field names, not prefixed).
	 * This is used for @Subquery derived table joins.
	 */
	private QueryModel buildSubquery(TypeModel subqueryType) {
		From fromAnn = subqueryType.getAnnotation(From.class);
		TypeModel sourceType = fromAnn != null ? new ReflectionTypeModel(fromAnn.value()) : subqueryType;

		// Build a new QueryModel for the subquery
		QueryModel subqueryModel = new QueryModel(sourceType);

		// Clear the auto-generated fields - we'll add them with simple aliases
		subqueryModel.fields.clear();

		String subqueryRootAlias = subqueryModel.tableName;
		boolean hasAggregates = false;
		List<String> nonAggregateFields = new ArrayList<>();
		Set<String> referencedAliases = new HashSet<>();

		for (FieldModel f : collectFieldsOfClass(subqueryType, null)) {
			if (f.getAnnotation(Transient.class) != null || f.isStatic()) {
				continue;
			}

			Aggregate aggAnn = f.getAnnotation(Aggregate.class);
			Select selectAnn = f.getAnnotation(Select.class);

			String simpleAlias = f.getName();  // Just the field name, no prefix
			SqlExpression selectExpression;

			if (aggAnn != null) {
				hasAggregates = true;
				String resolved = subqueryModel.resolveJoinConditionAliases(aggAnn.value(), subqueryRootAlias, null, null);
				selectExpression = new SqlExpression(resolved);
				collectReferencedAliases(resolved, referencedAliases);
			} else if (selectAnn != null) {
				String resolved = subqueryModel.resolveJoinConditionAliases(selectAnn.value(), subqueryRootAlias, null, null);
				selectExpression = new SqlExpression(resolved);
				nonAggregateFields.add(selectExpression.getSql());
				collectReferencedAliases(resolved, referencedAliases);
			} else {
				String fieldName = determineSqlFieldName(f);
				selectExpression = new SqlExpression("{" + subqueryRootAlias + "." + fieldName + "}");
				nonAggregateFields.add("{" + subqueryRootAlias + "." + fieldName + "}");
				referencedAliases.add(subqueryRootAlias);
			}

			// Add field with simple alias (just field name)
			subqueryModel.fields.add(new FieldInfo(selectExpression, simpleAlias, f));
		}

		// Auto-add GROUP BY for non-aggregate fields when aggregates are present
		if (hasAggregates && !nonAggregateFields.isEmpty()) {
			for (String field : nonAggregateFields) {
				subqueryModel.groupByClauses.add(field);
			}
		}

		// Prune unused joins
		subqueryModel.pruneUnusedJoins(referencedAliases, subqueryRootAlias);

		return subqueryModel;
	}

	/**
	 * Extracts all aliases referenced in an expression using {alias.field} syntax.
	 */
	private void collectReferencedAliases(String expression, Set<String> aliases) {
		java.util.regex.Matcher matcher = ALIAS_PATTERN.matcher(expression);
		while (matcher.find()) {
			String fullMatch = matcher.group(1);
			// Extract the alias part (everything before the last dot, or the whole thing if no field)
			int lastDot = fullMatch.lastIndexOf('.');
			if (lastDot > 0) {
				aliases.add(fullMatch.substring(0, lastDot));
			} else {
				aliases.add(fullMatch);
			}
		}
	}

	/**
	 * Removes joins that aren't referenced by any field expression.
	 * Computes transitive closure: if join A is needed and its condition references join B,
	 * then B is also needed.
	 */
	private void pruneUnusedJoins(Set<String> referencedAliases, String rootAlias) {
		if (joins.isEmpty()) {
			return;
		}

		// Build dependency map: which joins does each join's condition reference?
		Map<String, Set<String>> joinDependencies = new HashMap<>();
		for (JoinInfo join : joins) {
			Set<String> deps = new HashSet<>();
			if (join.joinCondition != null) {
				collectReferencedAliases(join.joinCondition.getSql(), deps);
			}
			// Remove self-reference and root alias from dependencies
			deps.remove(join.alias);
			deps.remove(rootAlias);
			joinDependencies.put(join.alias, deps);
		}

		// Compute transitive closure of required aliases
		Set<String> requiredAliases = new HashSet<>(referencedAliases);
		requiredAliases.add(rootAlias);

		boolean changed = true;
		while (changed) {
			changed = false;
			for (JoinInfo join : joins) {
				if (requiredAliases.contains(join.alias)) {
					// This join is needed, so its dependencies are also needed
					Set<String> deps = joinDependencies.get(join.alias);
					if (deps != null) {
						for (String dep : deps) {
							if (!requiredAliases.contains(dep)) {
								requiredAliases.add(dep);
								changed = true;
							}
						}
					}
				}
			}
		}

		// Filter joins to only keep required ones
		List<JoinInfo> prunedJoins = joins.stream()
				.filter(j -> requiredAliases.contains(j.alias))
				.collect(Collectors.toList());

		joins.clear();
		joins.addAll(prunedJoins);
	}

	public static String determineSqlFieldName(FieldModel f) {
		Objects.requireNonNull(f, "field must not be null");
		String columnName = AnnotationHelper.getColumnName(f);
		return columnName != null ? columnName : f.getName();
	}

	/**
	 * Determines the SQL column name for a foreign key (link) field.
	 * Checks @JoinColumn and @Link(linkfield) first, then falls back to @Column,
	 * and finally defaults to fieldName_id.
	 */
	public static String determineLinkFieldName(FieldModel f) {
		Objects.requireNonNull(f, "field must not be null");
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

	/**
	 * Determines the owner (parent) column name for a link table.
	 * Uses @Link(linkfield) if specified, otherwise defaults to tableName_id.
	 *
	 * @param ownerClass the class that owns the collection field
	 * @param linkAnn the @Link annotation on the collection field
	 * @return the column name for the owner's foreign key in the link table
	 */
	public static String determineLinkTableOwnerColumn(TypeModel ownerClass, Link linkAnn) {
		if (linkAnn != null && !Link.NONE.equals(linkAnn.linkfield())) {
			return linkAnn.linkfield();
		}
		List<TableMapping> mappings = determineTableMapping(ownerClass);
		return mappings.isEmpty() ? ownerClass.getSimpleName().toLowerCase() + "_id" : mappings.get(0).tableName + "_id";
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
	public static String determineLinkTableForeignColumn(TypeModel foreignClass, Link linkAnn) {
		// Check for fetchColumn first (value collections like enums)
		if (linkAnn != null && !Link.NONE.equals(linkAnn.fetchColumn())) {
			return linkAnn.fetchColumn();
		}
		if (linkAnn != null && !Link.NONE.equals(linkAnn.foreignlinkfield())) {
			return linkAnn.foreignlinkfield();
		}
		if (foreignClass != null) {
			List<TableMapping> mappings = determineTableMapping(foreignClass);
			if (!mappings.isEmpty()) {
				return mappings.get(0).tableName + "_id";
			}
		}
		return null;
	}

	private String joinMany(String alias, FieldModel f, TypeModel componentType) {
		TableMapping tableMapping = lookupTableMapping(componentType);
		String idField = determineSqlFieldName(determineIdField(f.getDeclaringType()));

		String linkField = linkFieldName(f.getDeclaringType());
		Link linkAnn = f.getAnnotation(Link.class);
		if (linkAnn != null && !Link.NONE.equals(linkAnn.foreignlinkfield())) {
			linkField = linkAnn.foreignlinkfield();
		}
		String joinCondition = null;
		if (f.getAnnotation(JoinCondition.class) != null) {
			joinCondition = f.getAnnotation(JoinCondition.class).value();
		}

		return joinMany(alias, f.getName(), tableMapping.schemaName, tableMapping.tableName, idField, linkField, joinCondition);
	}

	private boolean isRootOrSuperClassOfRoot(String alias) {
		if (alias.equals(rootAlias)) {
			return true;
		}
		List<String> subs = aliases.get(alias).getSubClassAliases();
		return subs != null && subs.contains(rootAlias);
	}

	private String joinMany(String alias, String fieldName, String schemaName, String tableName, String idField, String linkField, String joinCondition) {
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
		addJoin(JoinType.LEFT, schemaName, tableName, linkAlias, new SqlExpression(joinCondition));
		return linkAlias;
	}

	private String joinOne(String alias, FieldModel f, TypeModel type, String linkFieldPrefix) {
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
			joinCondition = new SqlExpression("{" + linkFieldAlias + "." + linkField + "} = {" + linkAlias + "." + determineSqlFieldName(idField) + "}");
		}
		addJoin(JoinType.LEFT, table.schemaName, table.tableName, linkAlias, joinCondition);
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
				// Handle alias.column patterns - check if it refers to the linkAlias
				int dotIndex = marker.indexOf('.');
				String markerAlias = marker.substring(0, dotIndex);
				String rest = marker.substring(dotIndex + 1);

				// If linkAlias ends with the marker alias (e.g., linkAlias="articles.authors" and markerAlias="authors"),
				// resolve to the full linkAlias
				if (linkAlias != null && (linkAlias.equals(markerAlias) || linkAlias.endsWith("." + markerAlias))) {
					return "{" + linkAlias + "." + rest + "}";
				}
				// Otherwise keep as marker for resolveAliases to handle
				return "{" + marker + "}";
			} else {
				// Simple field reference - prefix with current alias if not at root level
				return isRootOrSuperClassOfRoot(alias) ? "{" + marker + "}" : "{" + alias + "." + marker + "}";
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

	private void addJoin(JoinType joinType, String schemaName, String tableName, String alias, SqlExpression joinCondition) {
		joins.add(new JoinInfo(joinType, schemaName, tableName, alias, joinCondition));
	}

	private void addSubqueryJoin(JoinType joinType, QueryModel subquery, String alias, SqlExpression joinCondition) {
		joins.add(new JoinInfo(joinType, subquery, alias, joinCondition));
	}

	private void addField(SqlExpression expression, String fieldAlias, FieldModel f) {
		fields.add(new FieldInfo(expression, fieldAlias, f));
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
		List<TableMapping> tables = new ArrayList<>();
		List<FieldModel> fields = new ArrayList<>();
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
					Logger.getLogger(QueryModel.class.getName())
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

	/**
	 * Gets table info from a type, supporting both reflection and annotation processing.
	 */
	private static AnnotationHelper.TableInfo getTableInfo(TypeModel type) {
		return AnnotationHelper.getTableInfo(type);
	}

	public static List<String> getFieldNames(String table, List<FieldModel> fields) {
		List<String> fieldNames = new ArrayList<>();
		for (FieldModel f : fields) {
			fieldNames.add(table + "." + f.getName());
		}
		return fieldNames;
	}

	public static List<FieldModel> filterFields(TypeModel type) {
		List<FieldModel> result = new ArrayList<>();
		for (FieldModel f : type.getDeclaredFields()) {
			if (f.isStatic()) {
				continue;
			}
			if (f.isTransient() || f.getAnnotation(Transient.class) != null) {
				continue;
			}
			result.add(f);
		}
		return result;
	}

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

	public static List<FieldModel> collectFieldsOfClass(TypeModel type) {
		return collectFieldsOfClass(type, null);
	}

	public static final List<FieldModel> determineIdFields(TypeModel type) {
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
	 * Sets the joins list (used for pruning).
	 */
	public void setJoins(List<JoinInfo> newJoins) {
		joins.clear();
		joins.addAll(newJoins);
	}

	/**
	 * Sets the orderBy clauses.
	 */
	public void setOrderBy(List<String> orderBy) {
		orderByClauses.clear();
		orderByClauses.addAll(orderBy);
	}

	/**
	 * Ensures that the query is ordered by the primary entity's ID fields.
	 * This is required for streaming mode to work correctly.
	 */
	public void ensureOrderByPrimaryId() {
		List<FieldModel> idFields = determineIdFields(resultType);
		List<String> currentOrderBy = new ArrayList<>(orderByClauses);

		// Validate that ORDER BY clauses only reference the root alias
		validateOrderByAliases(currentOrderBy);

		// Append ID fields to the ORDER BY (if not already present) as a tiebreaker
		for (FieldModel idField : idFields) {
			String quotedFieldRef = "{" + rootAlias + "." + idField.getName() + "}";
			boolean alreadyPresent = currentOrderBy.stream()
					.anyMatch(o -> o.toUpperCase().contains(rootAlias.toUpperCase() + "}." + idField.getName().toUpperCase()));
			if (!alreadyPresent) {
				currentOrderBy.add(quotedFieldRef);
			}
		}
		orderByClauses.clear();
		orderByClauses.addAll(currentOrderBy);
	}

	/**
	 * Validates that all ORDER BY clauses only reference the root alias.
	 */
	private void validateOrderByAliases(List<String> orderByClauses) {
		for (String clause : orderByClauses) {
			java.util.regex.Matcher matcher = ALIAS_PATTERN.matcher(clause);
			while (matcher.find()) {
				String alias = matcher.group(1);
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
}
