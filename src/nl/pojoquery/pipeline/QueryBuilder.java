package nl.pojoquery.pipeline;

import static nl.pojoquery.util.Strings.implode;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import nl.pojoquery.DB;
import nl.pojoquery.FieldMapping;
import nl.pojoquery.SqlExpression;
import nl.pojoquery.annotations.Embedded;
import nl.pojoquery.annotations.FieldName;
import nl.pojoquery.annotations.GroupBy;
import nl.pojoquery.annotations.Id;
import nl.pojoquery.annotations.Join;
import nl.pojoquery.annotations.JoinCondition;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.OrderBy;
import nl.pojoquery.annotations.Other;
import nl.pojoquery.annotations.Select;
import nl.pojoquery.annotations.SubClasses;
import nl.pojoquery.annotations.Table;
import nl.pojoquery.annotations.Transient;
import nl.pojoquery.internal.MappingException;
import nl.pojoquery.internal.TableMapping;
import nl.pojoquery.pipeline.SqlQuery.JoinType;
import nl.pojoquery.util.Types;

public class QueryBuilder<T> {
	
	@SuppressWarnings("serial")
	public static class Values extends HashMap<String,Object> {

		public Values() {
			super();
		}
		
		public Values(Values values) {
			super(values);
		}
	}
	
	private LinkedHashMap<String,Alias> subClasses = new LinkedHashMap<>();
	private LinkedHashMap<String,Alias> aliases = new LinkedHashMap<>();
	private Map<String,FieldMapping> fieldMappings = new LinkedHashMap<>();
	
	private final Class<T> resultClass;
	private final SqlQuery query;
	private final String rootAlias;

	private QueryBuilder(Class<T> clz) {
		this.resultClass = clz;
		query = new SqlQuery();
		final TableMapping tableMapping = lookupTableMapping(clz);
		query.setTable(tableMapping.schemaName, tableMapping.tableName);
		Join joinAnn = clz.getAnnotation(Join.class);
		if (joinAnn != null) {
			query.addJoin(joinAnn.type(), joinAnn.schemaName(), joinAnn.tableName(), joinAnn.alias(), SqlExpression.sql(joinAnn.joinCondition()));
		}
		GroupBy groupByAnn = clz.getAnnotation(GroupBy.class);
		if (groupByAnn != null) {
			for(String groupBy : groupByAnn.value()) {
				query.addGroupBy(groupBy);
			}
		}
		OrderBy orderByAnn = clz.getAnnotation(OrderBy.class);
		if (orderByAnn != null) {
			for(String orderBy : orderByAnn.value()) {
				query.addOrderBy(orderBy);
			}
		}

		rootAlias = query.getTable();
		addClass(clz, rootAlias, null, null);
	}

	public static <R> QueryBuilder<R> from(Class<R> clz) {
		return new QueryBuilder<R>(clz);
	}
	
	public SqlExpression toStatement() {
		return query.toStatement();
	}
	
	public SqlExpression buildListIdsStatement(List<Field> idFields) {
		return query.toStatement(new SqlExpression("SELECT DISTINCT " + implode("\n , ", QueryBuilder.getFieldNames(query.getTable(), idFields))), query.getSchema(), query.getTable(), query.getJoins(), query.getWheres(), null, query.getOrderBy(), query.getOffset(), query.getRowCount());
	}

	public SqlExpression buildCountStatement() {
		List<Field> idFields = QueryBuilder.determineIdFields(resultClass);
		String selectClause = "SELECT COUNT(DISTINCT " + implode(", ", QueryBuilder.getFieldNames(query.getTable(), idFields)) + ") ";

		return query.toStatement(new SqlExpression(selectClause), query.getSchema(), query.getTable(), query.getJoins(), query.getWheres(), null, null, -1, -1);
	}

	public SqlQuery getQuery() {
		return query;
	}

	private static TableMapping lookupTableMapping(Class<?> clz) {
		if (clz == null)
			throw new NullPointerException("clz");

		List<TableMapping> tableMappings = QueryBuilder.determineTableMapping(clz);
		if (tableMappings.size() == 0) {
			throw new MappingException("Missing @Table annotation on class " + clz.getName() + " or any of its superclasses");
		}
		return tableMappings.get(tableMappings.size() - 1);
	}
	
	private void addClass(Class<?> clz, String alias, String parentAlias, Field linkField) {
		List<TableMapping> tableMappings = QueryBuilder.determineTableMapping(clz);
		
		Alias previousAlias = null;
		for(int i = tableMappings.size() - 1; i >= 0; i--) {
			TableMapping mapping = tableMappings.get(i);
			TableMapping superMapping = i > 0 ? tableMappings.get(i - 1) : null;
			
			String combinedAlias = mapping.clazz.equals(clz) ? alias : alias + "." + mapping.tableName; 
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
				String idField = QueryBuilder.determineIdField(superMapping.clazz).getName();
				query.addJoin(joinType, superMapping.schemaName, superMapping.tableName, linkAlias, new SqlExpression("{" + linkAlias + "}." + idField + " = {" + combinedAlias + "}." + idField));
			}
			
			Alias newAlias = new Alias(combinedAlias, mapping.clazz, parentAlias, linkField, QueryBuilder.determineIdFields(mapping.clazz));
			if (previousAlias != null) {
				newAlias.setSubClassAliases(Arrays.asList(previousAlias.getAlias()));
				newAlias.setParentAlias(previousAlias.getAlias());
			}
			previousAlias = newAlias;
			aliases.put(combinedAlias, newAlias);
			
			addFields(combinedAlias, alias, mapping.clazz, superMapping != null ? superMapping.clazz : null);
		}
		
		SubClasses subClassesAnn = clz.getAnnotation(SubClasses.class);
		if (subClassesAnn != null) {
			TableMapping thisMapping = tableMappings.get(tableMappings.size() - 1);
			Field thisIdField = QueryBuilder.determineIdField(thisMapping.clazz);
			List<String> subClassesAdded = new ArrayList<String>();
			for (Class<?> subClass : subClassesAnn.value()) {
				List<TableMapping> mappings = QueryBuilder.determineTableMapping(subClass);
				TableMapping mapping = mappings.get(mappings.size() - 1);
				
				String linkAlias = alias + "." + mapping.tableName;
				String idField = QueryBuilder.determineIdField(mapping.clazz).getName();
				
				query.addJoin(JoinType.LEFT, mapping.schemaName, mapping.tableName, linkAlias, new SqlExpression("{" + linkAlias + "}." + idField + " = {" + alias + "}." + idField));
				Alias subClassAlias = new Alias(linkAlias, mapping.clazz, alias, thisIdField, QueryBuilder.determineIdFields(mapping.clazz));
				subClassAlias.setIsASubClass(true);
				aliases.put(linkAlias, subClassAlias);
				subClasses.put(linkAlias, subClassAlias);
				
				// Also add the idfield of the linked alias, so we have at least one
				addField(new SqlExpression("{" + linkAlias + "}." + idField), linkAlias + "." + idField, thisIdField);
				
				addFields(linkAlias, mapping.clazz, thisMapping.clazz);
				
				subClassesAdded.add(linkAlias);
			}
			aliases.get(alias).setSubClassAliases(subClassesAdded);
		}

	}

	private void addFields(String alias, Class<?> clz, Class<?> superClass) {
		addFields(alias, alias, clz, superClass);
	}
	
	private void addFields(String alias, String fieldsAlias, Class<?> clz, Class<?> superClass) {
		for(Field f : QueryBuilder.collectFieldsOfClass(clz, superClass)) {
			f.setAccessible(true);
			
			Class<?> type = f.getType();
			boolean isRoot = alias.equals(rootAlias);
			if (isListOrArray(type)) {
				Class<?> componentType = type.isArray() ? type.getComponentType() : Types.getComponentType(f.getGenericType());
				
				Link linkAnn = f.getAnnotation(Link.class);
				if (linkAnn != null) {
					if (!Link.NONE.equals(linkAnn.fetchColumn())) {
						String foreignlinkfieldname = f.getAnnotation(Link.class).foreignlinkfield();
						if (Link.NONE.equals(foreignlinkfieldname)) {
							foreignlinkfieldname = linkFieldName(clz);
						}
						
						String linkAlias = alias.equals(rootAlias) ? f.getName() : (alias + "." + f.getName());
						SqlExpression joinCondition = null;
						String idField = null;
						if (f.getAnnotation(JoinCondition.class) != null) {
							joinCondition = SqlQuery.resolveAliases(new SqlExpression(f.getAnnotation(JoinCondition.class).value()), alias, isRoot ? "" : alias, linkAlias);
						} else {
							idField = QueryBuilder.determineIdField(clz).getName();
						}
						
						joinMany(query, alias, f.getName(), linkAnn.linkschema(), linkAnn.linktable(), idField, foreignlinkfieldname, joinCondition);
						
						Alias a = new Alias(linkAlias, componentType, alias, f, QueryBuilder.determineIdFields(componentType));
						a.setIsLinkedValue(true);
						aliases.put(linkAlias, a);
						addField(new SqlExpression("{" + linkAlias + "}." + linkAnn.fetchColumn()), linkAlias + ".value", f);
					} else if (linkAnn.linktable().equals(Link.NONE)) {
						String linkAlias = joinMany(alias, query, f, componentType);
						addClass(componentType, linkAlias, alias, f);
					} else {
						// Many to many
						String linkTableAlias = alias + "_" + f.getName();
						String linkAlias = isRoot ? linkTableAlias : (alias + "." + linkTableAlias);
						String idField = QueryBuilder.determineIdField(clz).getName();
						String linkfieldname = linkAnn.linkfield();
						if (Link.NONE.equals(linkfieldname)) {
							linkfieldname = linkFieldName(clz);
						}
						
						SqlExpression joinCondition = new SqlExpression("{" + alias + "}." + idField + " = {" + linkAlias + "}." + linkfieldname);
						if (f.getAnnotation(JoinCondition.class) != null) {
							joinCondition = SqlQuery.resolveAliases(new SqlExpression(f.getAnnotation(JoinCondition.class).value()), alias, isRoot ? "" : alias, linkAlias);
						}
						query.addJoin(JoinType.LEFT, linkAnn.linkschema(), linkAnn.linktable(), linkAlias, joinCondition);
						
						String foreignLinkAlias = isRoot ? f.getName() : (alias + "." + f.getName());
						String foreignIdField = QueryBuilder.determineIdField(componentType).getName();
						String foreignlinkfieldname = linkAnn.foreignlinkfield();
						if (Link.NONE.equals(foreignlinkfieldname)) {
							foreignlinkfieldname = linkFieldName(componentType);
						}
						SqlExpression foreignJoinCondition = new SqlExpression("{" + linkAlias + "}." + foreignlinkfieldname + " = {" + foreignLinkAlias + "}." + foreignIdField);
						query.addJoin(JoinType.LEFT, determineTableMapping(componentType).get(0).schemaName, determineTableMapping(componentType).get(0).tableName, foreignLinkAlias, foreignJoinCondition);
						
						addClass(componentType, foreignLinkAlias, alias, f);
					}
				} else if (QueryBuilder.determineTableMapping(componentType).size() > 0) {
					String linkAlias = joinMany(alias, query, f, componentType);
					addClass(componentType, linkAlias, alias, f);
				}
				
			} else if (isLinkedClass(type)) {
				String linkAlias = joinOne(alias, query, f, type);
				addClass(type, linkAlias, alias, f);
			} else if (f.getAnnotation(Embedded.class) != null) {
				String prefix = QueryBuilder.determinePrefix(f);

				String foreignAlias = isRoot ? f.getName() : alias + "." + f.getName();
				for (Field embeddedField : QueryBuilder.collectFieldsOfClass(f.getType())) {
					embeddedField.setAccessible(true);
					String fieldName = determineSqlFieldName(embeddedField);
					addField(new SqlExpression("{" + alias + "}." + prefix + fieldName), foreignAlias + "." + fieldName, embeddedField);
				}
				aliases.put(foreignAlias, new Alias(foreignAlias, f.getType(), alias, f, QueryBuilder.determineIdFields(f.getType())));
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
					selectExpression = SqlQuery.resolveAliases(new SqlExpression(f.getAnnotation(Select.class).value()), alias);
				} else {
					String fieldName = determineSqlFieldName(f);
					selectExpression = new SqlExpression("{" + alias + "}." + fieldName);
				}
				addField(selectExpression, fieldsAlias + "." + f.getName(), f);
			}
		}
	}

	public static String determineSqlFieldName(Field f) {
		String fieldName = f.getName();
		if (f.getAnnotation(FieldName.class) != null) {
			fieldName = f.getAnnotation(FieldName.class).value();
		}
		return fieldName;
	}

	private String joinMany(String alias, SqlQuery result, Field f, Class<?> componentType) {
		TableMapping tableMapping = lookupTableMapping(componentType);
		String idField = determineIdField(f.getDeclaringClass()).getName();
		
		String linkField = linkFieldName(f.getDeclaringClass());
		Link linkAnn = f.getAnnotation(Link.class);
		if (linkAnn != null && !Link.NONE.equals(linkAnn.foreignlinkfield())) {
			linkField = linkAnn.foreignlinkfield();
		}
		SqlExpression joinCondition = null;
		if (f.getAnnotation(JoinCondition.class) != null) {
			joinCondition = SqlQuery.resolveAliases(new SqlExpression(f.getAnnotation(JoinCondition.class).value()), alias);
		}
		
		return joinMany(result, alias, f.getName(), tableMapping.schemaName, tableMapping.tableName, idField, linkField, joinCondition);
	}

	private String joinMany(SqlQuery result, String alias, String fieldName, String schemaName, String tableName, String idField, String linkField, SqlExpression joinCondition) {
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
			joinCondition = new SqlExpression("{" + alias + "}." + idField + " = {" + linkAlias + "}." + linkField);
		}
		result.addJoin(JoinType.LEFT, schemaName, tableName, linkAlias, joinCondition);
		return linkAlias;
	}
	
	private String joinOne(String alias, SqlQuery result, Field f, Class<?> type) {
		TableMapping table = lookupTableMapping(type);
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
		
		SqlExpression joinCondition = null;
		if (f.getAnnotation(JoinCondition.class) != null) {
			joinCondition = SqlQuery.resolveAliases(new SqlExpression(f.getAnnotation(JoinCondition.class).value()), parentAlias.getAlias(), alias.equals(rootAlias) ? "" : alias, alias.equals(rootAlias) ? "" : alias);
		} else {
			Field idField = QueryBuilder.determineIdField(type);
			joinCondition = new SqlExpression("{" + alias + "}." + linkFieldName(f) + " = {" + linkAlias + "}." + idField.getName());
		}
		result.addJoin(JoinType.LEFT, table.schemaName, table.tableName, linkAlias, joinCondition);
		return linkAlias;
	}
	
	private void addField(SqlExpression expression, String fieldAlias, Field f) {
		fieldMappings.put(fieldAlias, new SimpleFieldMapping(f));
		query.addField(expression, fieldAlias);
	}

	private static String linkFieldName(Class<?> clz) {
		return lookupTableMapping(clz).tableName + "_id";
	}
	
	private static String linkFieldName(Field f) {
		if (f.getAnnotation(FieldName.class) != null) {
			return f.getAnnotation(FieldName.class).value();
		}
		return f.getName() + "_id";
	}

	private static boolean isListOrArray(Class<?> type) {
		return (type.isArray() && !type.getComponentType().isPrimitive()) || Iterable.class.isAssignableFrom(type);
	}

	public static boolean isLinkedClass(Class<?> type) {
		return !type.isPrimitive() && QueryBuilder.determineTableMapping(type).size() > 0;
	}
	
	public List<T> processRowsStreaming(Callable<Map<String,Object>> rowProvider) {
		
		return new ArrayList<>();
	}
	
	public List<T> processRows(List<Map<String, Object>> rows) {
		try {
			List<T> result = new ArrayList<T>(rows.size());
			Map<Object, Object> allEntities = new HashMap<Object, Object>();
			
			for(Map<String,Object> row : rows) {
				processRow(result, allEntities, row);
			}
			
			return result;
		} catch (Exception e) {
			throw new MappingException(e);
		}
	}

	
	@SuppressWarnings("unchecked")
	public void processRow(List<T> result, Map<Object, Object> allEntities, Map<String, Object> row) {
		Map<String, Values> onThisRow = collectValuesByAlias(row);
		
		onThisRow = remapSubClasses(onThisRow);
		
		for(Alias a : aliases.values()) {
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
					Class<?> entityClass = resultClass;
					if (a.getSubClassAliases() != null) {
						for(String subClassAlias : a.getSubClassAliases()) {
							Values subClassValues = onThisRow.get(subClassAlias); 
							if (subClassValues == null || allNulls(subClassValues)) {
								continue;
							}
							
							merged.putAll(onThisRow.get(subClassAlias));
							
							subClassId = createId(subClassAlias, merged, a.getIdFields());
							entityClass = aliases.get(subClassAlias).getResultClass();
						}
					}
					
					Object entity = buildEntity(entityClass, merged, a.getOtherField());
					allEntities.put(id, entity);
					allEntities.put(subClassId, entity);
					result.add((T) entity);
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
				if (parentValues == null || parentValues.size() == 0) {
				} else {
					parentId = createId(parentAlias, parentValues, aliases.get(parentAlias).getIdFields());
					parent = allEntities.get(parentId);
				}
				
				if (parent == null) {
					Alias parentAliasObject = aliases.get(a.getParentAlias());
					List<String> subs = parentAliasObject.getSubClassAliases();
					if (subs != null && subs.size() > 0) {
						for(String sub : subs) {
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
					if (a.getResultClass().isEnum()) {
						value = enumValueOf(a.getResultClass(), (String)value);
					}
					putValueIntoField(parent, a.getLinkField(), value);
				} else {
					Class<?> entityClass = a.getResultClass();
					if (a.getSubClassAliases() != null) {
						Values merged = new Values();
						merged.putAll(onThisRow.get(a.getAlias()));
						for(String subClassAlias : a.getSubClassAliases()) {
							Values subClassValues = onThisRow.get(subClassAlias); 
							if (subClassValues == null || allNulls(subClassValues)) {
								continue;
							}
							
							merged.putAll(onThisRow.get(subClassAlias));
							id = createId(subClassAlias, merged, a.getIdFields());
							entityClass = aliases.get(subClassAlias).getResultClass();
							values = merged;
						}
					}
					

					// Linked entity
					Object entity = allEntities.get(id);
					if (entity == null) {
						entity = buildEntity(entityClass, values, a.getOtherField());
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

	@SuppressWarnings("unchecked")
	private void putValueIntoField(Object parentEntity, Field linkField, Object entity) {
		try {
			if (List.class.isAssignableFrom(linkField.getType())) {
				List<Object> coll = (List<Object>) linkField.get(parentEntity);
				if (coll == null) {
					coll = new ArrayList<Object>();
					linkField.set(parentEntity, coll);
				}
				if (!coll.contains(entity) && entity != null) {
					coll.add(entity);
				}
			} else if (Set.class.isAssignableFrom(linkField.getType())) {
				Set<Object> coll = (Set<Object>) linkField.get(parentEntity);
				if (coll == null) {
					coll = new HashSet<Object>();
					linkField.set(parentEntity, coll);
				}
				if (!coll.contains(entity) && entity != null) {
					coll.add(entity);
				}
			} else if (linkField.getType().isArray()) {
				Object arr = linkField.get(parentEntity);
				if (arr == null) {
					arr = Array.newInstance(linkField.getType()
							.getComponentType(), 0);
				}
				boolean contains = false;
				int arrlen = Array.getLength(arr);
				for (int i = 0; i < arrlen; i++) {
					if (Array.get(arr, i).equals(entity)) {
						contains = true;
						break;
					}
				}
				if (!contains && entity != null) {
					Object extended = Array.newInstance(linkField.getType()
							.getComponentType(), arrlen + 1);
					if (arrlen > 0) {
						System.arraycopy(arr, 0, extended, 0, arrlen);
					}
					Array.set(extended, arrlen, entity);
					arr = extended;
				}
				linkField.set(parentEntity, arr);
			} else {
				linkField.set(parentEntity, entity);
			}

		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new MappingException(e);
		}
	}

	private Object createId(String alias, Values values, List<Field> idFields) {
		if (idFields.size() == 0) {
			return values;
		}
		List<Object> result = new ArrayList<Object>();
		result.add(alias);
		for(Field f : idFields) {
			result.add(values.get(alias + "." + f.getName()));
		}
		return result;
	}

	private Map<String,Values> collectValuesByAlias(Map<String, Object> row) {
		Map<String,Values> result = new HashMap<>();
		for(Alias a : aliases.values()) {
			String alias = a.getAlias();
			Values values = getAliasValues(row, alias);
			result.put(alias, values);
		}
		return result;
	}

	private <E> E buildEntity(Class<E> resultClass, Values values, Field otherField) {
		if (allNulls(values)) {
			return null;
		}
		E entity = QueryBuilder.createInstance(resultClass);
		Values other = applyValues(entity, values);
		if (otherField != null) {
			otherField.setAccessible(true);
			try {
				otherField.set(entity, other);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new MappingException(e);
			}
		}
		return entity;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <E> E enumValueOf(Class<E> enumClass, String name) {
		return (E) Enum.valueOf((Class<? extends Enum>) enumClass, name);
	}

	private Values applyValues(Object entity, Values aliasValues) {
		Values other = new Values();
		for(String fieldAlias : aliasValues.keySet()) {
			FieldMapping mapping = fieldMappings.get(fieldAlias);
			if (mapping != null) {
				mapping.apply(entity, aliasValues.get(fieldAlias));
			} else {
				String fieldName = fieldAlias.substring(fieldAlias.lastIndexOf(".") + 1);
//				Other otherAnn = alias.otherField.getAnnotation(Other.class);
//				if (otherAnn != null && otherAnn.prefix().length() > 0) {
//					// Remove prefix.
//					fieldName = fieldName.substring(otherAnn.prefix().length());
//				}
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

	private static Values getAliasValues(Map<String, Object> row, String alias) {
		Values result = new Values();
		for(String key : row.keySet()) {
			int dotPos = key.lastIndexOf(".");
			if (alias.equals(key.substring(0, dotPos))) {
				result.put(key, row.get(key));
			}
		}
		return result;
	}

	public LinkedHashMap<String, Alias> getAliases() {
		return aliases;
	}

	public Map<String, FieldMapping> getFieldMappings() {
		return fieldMappings;
	}

    public Class<T> getResultClass() {
		return resultClass;
	}

	public static List<Field> assertIdFields(Class<?> resultClass) {
		List<Field> idFields = QueryBuilder.determineIdFields(resultClass);
		if (idFields.size() == 0) {
			throw new MappingException("No @Id annotations found on fields of class " + resultClass.getName());
		}
		return idFields;
	}

	public static List<SqlExpression> buildIdCondition(Class<?> resultClass, Object id) {
		List<Field> idFields = assertIdFields(resultClass);
		List<TableMapping> tables = QueryBuilder.determineTableMapping(resultClass);
		String tableName = tables.get(tables.size() - 1).tableName;
		if (idFields.size() == 1) {
			return Arrays.asList(new SqlExpression(DB.quoteObjectNames(tableName, idFields.get(0).getName()) + "=?", Arrays.asList((Object) id)));
		} else {
			if (id instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> idvalues = (Map<String, Object>) id;
	
				List<SqlExpression> result = new ArrayList<SqlExpression>();
				for (String field : idvalues.keySet()) {
					result.add(new SqlExpression(DB.quoteObjectNames(tableName, field) + "=?", Arrays.asList((Object) idvalues.get(field))));
				}
				return result;
			} else {
				throw new MappingException("Multiple @Id annotations on class " + resultClass.getName() + ": expecting a map id.");
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

	public static String determinePrefix(Field f) {
		String prefix = f.getAnnotation(Embedded.class).prefix();
		if (prefix.equals(Embedded.DEFAULT)) {
			prefix = f.getName();
		}
		if (!prefix.isEmpty()) {
			prefix = prefix + "_";
		}
		return prefix;
	}

	public static List<TableMapping> determineTableMapping(Class<?> clz) {
		Class<?> mappedClz = clz;
		List<TableMapping> tables = new ArrayList<TableMapping>();
		List<Field> fields = new ArrayList<Field>();
		while (clz != null) {
			if (mappedClz == null) {
				mappedClz = clz;
			}
			Table tableAnn = clz.getAnnotation(Table.class);
			fields.addAll(0, QueryBuilder.collectFieldsOfClass(clz, clz.getSuperclass()));
			if (tableAnn != null) {
				String name = tableAnn.value();
				tables.add(0, new TableMapping(tableAnn.schema(), name, mappedClz, new ArrayList<Field>(fields)));
				fields.clear();
				mappedClz = null;
			}
			clz = clz.getSuperclass();
		}
		if (fields.size() > 0 && tables.size() > 0) {
			tables.get(0).fields.addAll(0, fields);
		}
		return tables;
	}

	public static List<String> getFieldNames(String table, List<Field> fields) {
		List<String> fieldNames = new ArrayList<String>();
		for (Field f : fields) {
			fieldNames.add(table + "." + f.getName());
		}
		return fieldNames;
	}

	public static Collection<Field> filterFields(Class<?> clz) {
		List<Field> result = new ArrayList<Field>();
		for (Field f : clz.getDeclaredFields()) {
			if ((f.getModifiers() & Modifier.STATIC) > 0) {
				continue;
			}
			if ((f.getModifiers() & Modifier.TRANSIENT) > 0) {
				continue;
			}
			if (f.getAnnotation(Transient.class) != null) {
				continue;
			}
			result.add(f);
		}
		return result;
	}

	public static List<Field> collectFieldsOfClass(Class<?> clz, Class<?> stopAtSuperClass) {
		List<Field> result = new ArrayList<Field>();
		while (clz != null && !clz.equals(stopAtSuperClass)) {
			result.addAll(0, filterFields(clz));
			clz = clz.getSuperclass();
		}
		return result;
	}

	public static Iterable<Field> collectFieldsOfClass(Class<?> type) {
		return collectFieldsOfClass(type, null);
	}

	public static final List<Field> determineIdFields(Class<?> clz) {
		Iterable<Field> fields = collectFieldsOfClass(clz);
		ArrayList<Field> result = new ArrayList<Field>();
		for (Field f : fields) {
			if (f.getAnnotation(Id.class) != null) {
				result.add(f);
			}
		}
		return result;
	}

	public static Field determineIdField(Class<?> clz) {
		List<Field> idFields = determineIdFields(clz);
		if (idFields.size() != 1) {
			throw new MappingException("Need single id field annotated with @Id on class " + clz);
		}
		return idFields.get(0);
	}
	
}
