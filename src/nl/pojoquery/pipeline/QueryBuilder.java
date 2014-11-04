package nl.pojoquery.pipeline;

import static nl.pojoquery.util.Strings.implode;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.pojoquery.FieldMapping;
import nl.pojoquery.PojoQuery;
import nl.pojoquery.SqlExpression;
import nl.pojoquery.pipeline.Alias;
import nl.pojoquery.annotations.Embedded;
import nl.pojoquery.annotations.FieldName;
import nl.pojoquery.annotations.JoinCondition;
import nl.pojoquery.annotations.Link;
import nl.pojoquery.annotations.Select;
import nl.pojoquery.annotations.SubClasses;
import nl.pojoquery.internal.MappingException;
import nl.pojoquery.internal.TableMapping;
import nl.pojoquery.pipeline.SqlQuery.JoinType;
import nl.pojoquery.util.Types;

public class QueryBuilder<T> {
	
	@SuppressWarnings("serial")
	public static class Values extends HashMap<String,Object> {
	}
	
	@SuppressWarnings("serial")
	public static class IdValue extends ArrayList<Object> {
	}

	private LinkedHashMap<String,Alias> aliases = new LinkedHashMap<>();
	private Map<String,FieldMapping> fieldMappings = new HashMap<>();
	
	private Class<T> resultClass;
	private SqlQuery query;

	private QueryBuilder(Class<T> clz) {
		this.resultClass = clz;
		query = new SqlQuery();
		query.setTable(determineTableName(clz));
		String alias = query.getTable();
		addClass(clz, alias);
	}

	public static <R> QueryBuilder<R> from(Class<R> clz) {
		return new QueryBuilder<R>(clz);
	}
	
	public SqlExpression toStatement() {
		return query.toStatement();
	}
	
	public SqlExpression buildListIdsStatement(List<Field> idFields) {
		return SqlQuery.toStatement(new SqlExpression("SELECT DISTINCT " + implode("\n , ", PojoQuery.getFieldNames(query.getTable(), idFields))), query.getTable(), query.getJoins(), query.getWheres(), null, query.getOrderBy(), -1, -1);
	}

	public SqlExpression buildCountStatement() {
		List<Field> idFields = PojoQuery.determineIdFields(resultClass);
		String selectClause = "SELECT COUNT(DISTINCT " + implode(", ", PojoQuery.getFieldNames(query.getTable(), idFields)) + ") ";

		return SqlQuery.toStatement(new SqlExpression(selectClause), query.getTable(), query.getJoins(), query.getWheres(), null, null, -1, -1);
	}

	public SqlQuery getQuery() {
		return query;
	}

	private static String determineTableName(Class<?> clz) {
		if (clz == null)
			throw new NullPointerException("clz");

		List<TableMapping> tableMappings = PojoQuery.determineTableMapping(clz);
		if (tableMappings.size() == 0) {
			throw new MappingException("Missing @Table annotation on class " + clz.getName() + " or any of its superclasses");
		}
		return tableMappings.get(tableMappings.size() - 1).tableName;
	}
	
	private void addClass(Class<?> clz, String alias) {
		List<TableMapping> tableMappings = PojoQuery.determineTableMapping(clz);
		for(int i = 0; i < tableMappings.size(); i++) {
			TableMapping mapping = tableMappings.get(i);
			TableMapping superMapping = i > 0 ? tableMappings.get(i - 1) : null;
			
			if (superMapping != null) {
				String linkAlias = alias + "." + superMapping.tableName;
				String subAlias = mapping.clazz.equals(clz) ? alias : alias + "." + mapping.tableName;
				String idField = PojoQuery.determineIdField(superMapping.clazz).getName();
				query.addJoin(JoinType.INNER, superMapping.tableName, linkAlias, new SqlExpression("{" + linkAlias + "}." + idField + " = {" + subAlias + "}." + idField));
			}
			
			String combinedAlias = mapping.clazz.equals(clz) ? alias : alias + "." + mapping.tableName; 
			
			aliases.put(combinedAlias, new Alias(combinedAlias, mapping.clazz, null, null, PojoQuery.determineIdFields(mapping.clazz)));
			addFields(combinedAlias, alias, mapping.clazz, superMapping != null ? superMapping.clazz : null, null, query);
		}
		
		SubClasses subClassesAnn = clz.getAnnotation(SubClasses.class);
		if (subClassesAnn != null) {
			TableMapping thisMapping = tableMappings.get(tableMappings.size() - 1);
			Field thisIdField = PojoQuery.determineIdField(thisMapping.clazz);
			
			for (Class<?> subClass : subClassesAnn.value()) {
				List<TableMapping> mappings = PojoQuery.determineTableMapping(subClass);
				TableMapping mapping = mappings.get(mappings.size() - 1);
				
				String linkAlias = alias + "." + mapping.tableName;
				String idField = PojoQuery.determineIdField(mapping.clazz).getName();
				
				query.addJoin(JoinType.LEFT, mapping.tableName, linkAlias, new SqlExpression("{" + linkAlias + "}." + idField + " = {" + alias + "}." + idField));
				aliases.put(linkAlias, new Alias(linkAlias, mapping.clazz, linkAlias, thisIdField, PojoQuery.determineIdFields(mapping.clazz)));
				
				// Also add the idfield of the linked alias, so we have at least one
				addField(query, new SqlExpression("{" + linkAlias + "}." + idField), linkAlias + "." + idField, thisIdField);
				
				addFields(linkAlias, linkAlias, mapping.clazz, thisMapping.clazz, alias, query);
			}
		}

	}

	private void addFields(String alias, String fieldAlias, Class<?> clz, Class<?> superClass, String parentAlias, SqlQuery result) {
		
		for(Field f : PojoQuery.collectFieldsOfClass(clz, superClass)) {
			f.setAccessible(true);
			
			Class<?> type = f.getType();
			if (isListOrArray(type)) {
				Class<?> componentType = type.isArray() ? type.getComponentType() : Types.getComponentType(f.getGenericType());
				
				Link linkAnn = f.getAnnotation(Link.class);
				if (linkAnn != null) {
					if (!Link.NONE.equals(linkAnn.fetchColumn())) {
						String linkAlias = joinMany(result, alias, parentAlias, f.getName(), linkAnn.linktable(), PojoQuery.determineIdField(clz).getName(), linkFieldName(clz), null);
						Alias a = new Alias(linkAlias, componentType, alias, f, PojoQuery.determineIdFields(componentType));
						a.setIsLinkedValue(true);
						aliases.put(linkAlias, a);
						addField(result, new SqlExpression("{" + linkAlias + "}." + linkAnn.fetchColumn()), linkAlias + ".value", f);
					}
				} else if (PojoQuery.determineTableMapping(componentType).size() > 0) {
					String linkAlias = joinMany(alias, parentAlias, result, f, componentType);
					addClass(componentType, linkAlias);
//					aliases.put(linkAlias, new Alias(linkAlias, componentType, alias, f, PojoQuery.determineIdFields(componentType)));
//					addFields(linkAlias, linkAlias, componentType, null, alias, result);
				}
				
			} else if (isLinkedClass(type)) {
				String linkAlias = joinOne(alias, parentAlias, result, f, type);
				addClass(type, linkAlias);
//				aliases.put(linkAlias, new Alias(linkAlias, type, alias, f, PojoQuery.determineIdFields(type)));
//				addFields(linkAlias, linkAlias, type, null, alias, result);
			} else if (f.getAnnotation(Embedded.class) != null) {
				String prefix = PojoQuery.determinePrefix(f);

				String foreignAlias = parentAlias != null ? alias + "." + f.getName() : f.getName();
				for (Field embeddedField : PojoQuery.collectFieldsOfClass(f.getType())) {
					embeddedField.setAccessible(true);
					String fieldName = embeddedField.getName();
					addField(result, new SqlExpression("{" + alias + "}." + prefix + fieldName), foreignAlias + "." + fieldName, embeddedField);
				}
				aliases.put(foreignAlias, new Alias(foreignAlias, f.getType(), alias, f, PojoQuery.determineIdFields(f.getType())));
			} else {
				SqlExpression selectExpression;
				if (f.getAnnotation(Select.class) != null) {
					selectExpression = SqlQuery.resolveAliases(new SqlExpression(f.getAnnotation(Select.class).value()), alias);
				} else {
					String fieldName = f.getName();
					if (f.getAnnotation(FieldName.class) != null) {
						fieldName = f.getAnnotation(FieldName.class).value();
					}
					selectExpression = new SqlExpression("{" + alias + "}." + fieldName);
				}
				addField(result, selectExpression, fieldAlias + "." + f.getName(), f);
			}
		}
	}

	private static String joinMany(String alias, String parentAlias, SqlQuery result, Field f, Class<?> componentType) {
		String tableName = determineTableName(componentType);
		String idField = PojoQuery.determineIdField(f.getDeclaringClass()).getName();
		String linkField = linkFieldName(f.getDeclaringClass());
		SqlExpression joinCondition = null;
		if (f.getAnnotation(JoinCondition.class) != null) {
			joinCondition = SqlQuery.resolveAliases(new SqlExpression(f.getAnnotation(JoinCondition.class).value()), alias);
		}
		
		return joinMany(result, alias, parentAlias, f.getName(), tableName, idField, linkField, joinCondition);
	}

	private static String joinMany(SqlQuery result, String alias, String parentAlias, String fieldName, String tableName, String idField, String linkField, SqlExpression joinCondition) {
		String linkAlias = parentAlias == null ? fieldName : (alias + "." + fieldName);
		if (joinCondition == null) {
			joinCondition = new SqlExpression("{" + alias + "}." + idField + " = {" + linkAlias + "}." + linkField);
		}
		result.addJoin(JoinType.LEFT, tableName, linkAlias, joinCondition);
		return linkAlias;
	}
	
	private static String joinOne(String alias, String parentAlias, SqlQuery result, Field f, Class<?> type) {
		String tableName = determineTableName(type);
		String linkAlias = parentAlias == null ? f.getName() : (alias + "." + f.getName());
		
		SqlExpression joinCondition = null;
		if (f.getAnnotation(JoinCondition.class) != null) {
			joinCondition = SqlQuery.resolveAliases(new SqlExpression(f.getAnnotation(JoinCondition.class).value()), alias);
		} else {
			Field idField = PojoQuery.determineIdField(type);
			joinCondition = new SqlExpression("{" + alias + "}." + linkFieldName(f) + " = {" + linkAlias + "}." + idField.getName());
		}
		result.addJoin(JoinType.LEFT, tableName, linkAlias, joinCondition);
		return linkAlias;
	}
	
	private void addField(SqlQuery result, SqlExpression expression, String fieldAlias, Field f) {
		fieldMappings.put(fieldAlias, new SimpleFieldMapping(f));
		result.addField(expression, fieldAlias);
	}

	private static String linkFieldName(Class<?> clz) {
		return determineTableName(clz) + "_id";
	}
	
	private static String linkFieldName(Field f) {
		if (f.getAnnotation(FieldName.class) != null) {
			return f.getAnnotation(FieldName.class).value();
		}
		return f.getName() + "_id";
	}

	private static boolean isListOrArray(Class<?> type) {
		return type.isArray() || Iterable.class.isAssignableFrom(type);
	}

	private static boolean isLinkedClass(Class<?> type) {
		return !type.isPrimitive() && PojoQuery.determineTableMapping(type).size() > 0;
	}
	
	public List<T> processRows(List<Map<String, Object>> rows) {
		try {
			List<T> result = new ArrayList<T>(rows.size());
			Map<IdValue, Object> allEntities = new HashMap<IdValue, Object>();
			
			for(Map<String,Object> row : rows) {
				Map<String, Values> onThisRow = collectValuesByAlias(row);
				for(Alias a : aliases.values()) {
					Values values = onThisRow.get(a.getAlias());
					if (allNulls(values)) {
						continue;
					}
					IdValue id = createId(a.getAlias(), values, a.getIdFields());
					if (a.getParentAlias() == null) {
						// Primary alias
						if (!allEntities.containsKey(id)) {
							T entity = buildEntity(resultClass, values);
							allEntities.put(id, entity);
							result.add(entity);
						}
					} else {
						// Find the parent
						Values parentValues = onThisRow.get(a.getParentAlias());
						IdValue parentId = createId(a.getParentAlias(), parentValues, aliases.get(a.getParentAlias()).getIdFields());
						Object parent = allEntities.get(parentId);
						
						if (a.isLinkedValue()) {
							// Linked value
							Object value = values.values().iterator().next();
							if (a.getResultClass().isEnum()) {
								value = enumValueOf(a.getResultClass(), (String)value);
							}
							putValueIntoField(parent, a.getLinkField(), value);
						} else {
							// Linked entity
							Object entity = allEntities.get(id);
							if (entity == null) {
								entity = buildEntity(a.getResultClass(), values);
								allEntities.put(id, entity);
							}
							putValueIntoField(parent, a.getLinkField(), entity);
						}
						
					}
				}
			}
			
			return result;
		} catch (Exception e) {
			throw new MappingException(e);
		}
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

	private IdValue createId(String alias, Values values, List<Field> idFields) {
		IdValue result = new IdValue();
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

	private <E> E buildEntity(Class<E> resultClass, Values values) {
		if (allNulls(values)) {
			return null;
		}
		E entity = PojoQuery.createInstance(resultClass);
		applyValues(entity, values);
		return entity;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <E> E enumValueOf(Class<E> enumClass, String name) {
		return (E) Enum.valueOf((Class<? extends Enum>) enumClass, name);
	}

	private void applyValues(Object entity, Values aliasValues) {
		for(String fieldAlias : aliasValues.keySet()) {
			FieldMapping mapping = fieldMappings.get(fieldAlias);
			if (mapping != null) {
				mapping.apply(entity, aliasValues.get(fieldAlias));
			}
		}
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
	
	
}
