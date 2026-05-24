package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.pojoquery.DbContext;
import org.pojoquery.annotations.Column;
import org.pojoquery.annotations.Lob;
import org.pojoquery.pipeline.AbstractQueryTree.EmbeddedEntity;
import org.pojoquery.pipeline.AbstractQueryTree.EntityNode;
import org.pojoquery.pipeline.AbstractQueryTree.ForeignKeyInfo;
import org.pojoquery.pipeline.AbstractQueryTree.Join;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKeyField;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RecursiveCollection;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.STISubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.TableInfo;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.AbstractQueryTree.ValueCollection;
import org.pojoquery.schema.SchemaInfo;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;

public class AQTSchemaGenerator {

	public sealed interface DDLColumn permits DDLFieldColumn, DDLInferredColumn, DDLPrimaryKeyColumn {
		DDLColumnKey columnKey();
	}

	public record DDLFieldColumn(DDLColumnKey columnKey, FieldModel field) implements DDLColumn {}

	public record DDLPrimaryKeyColumn(DDLColumnKey columnKey, FieldModel field, boolean isAutoIncrement) implements DDLColumn {}

	public record DDLInferredColumn(DDLColumnKey columnKey, TypeModel scalarType) implements DDLColumn {}

	public record DDLColumnKey(TableInfo tableKey, String columnName) {}

	public record DDLForeignKey(DDLColumnKey referringColumn, DDLColumnKey referencedIdColumn) {}

	public record DDLColumnMetadata(int length, int precision, int scale, boolean nullable, boolean unique, boolean isLob) {}

	interface DDLCollector {
		DDLColumn registerColumn(TableInfo table, String columnName, FieldModel field);
		DDLColumn registerPrimaryKeyColumn(TableInfo table, String columnName, FieldModel field, boolean isAutoIncrement);
		DDLColumn registerInferredColumn(TableInfo table, String columnName, TypeModel scalarType);
		DDLColumn registerForeignKey(ForeignKeyInfo foreignKeyInfo);
	}

	interface CollectedSchemaInfo {
		Map<DDLColumnKey, DDLColumn> columns();
		Map<DDLColumnKey, DDLPrimaryKeyColumn> primaryKeys();
		Map<DDLColumnKey, DDLForeignKey> foreignKeys();
		public DDLColumnMetadata buildColumnMetadata(DDLColumnKey columnKey);
		Set<TableInfo> tables();
	}

	private static class DDLCollectorImpl implements DDLCollector, CollectedSchemaInfo {
		private LinkedHashSet<TableInfo> tables = new LinkedHashSet<>();
		private HashMap<DDLColumnKey, DDLColumn> columns = new LinkedHashMap<>();
		private HashMap<DDLColumnKey, List<FieldModel>> definingFields = new HashMap<>();
		private HashSet<DDLColumnKey> implicitForeignKeyColumns = new HashSet<>();
		private HashMap<DDLColumnKey, DDLForeignKey> foreignKeys = new LinkedHashMap<>();
		private HashMap<DDLColumnKey, DDLPrimaryKeyColumn> primaryKeys = new LinkedHashMap<>();

		@Override
		public DDLColumn registerInferredColumn(TableInfo table, String columnName, TypeModel scalarType) {
			tables.add(table);
			DDLColumnKey key = new DDLColumnKey(table, columnName);
			DDLInferredColumn column = new DDLInferredColumn(key, scalarType);
			columns.put(key, column); // placeholder column with null field
			return column;
		}

		@Override
		public DDLColumn registerPrimaryKeyColumn(TableInfo table, String columnName, FieldModel field, boolean isAutoIncrement) {
			DDLColumnKey key = new DDLColumnKey(table, columnName);
			DDLPrimaryKeyColumn pkColumn = new DDLPrimaryKeyColumn(key, field, isAutoIncrement);
			primaryKeys.put(key, pkColumn);
			return registerColumnInternal(table, key, pkColumn, field);
		}

		@Override
		public DDLColumn registerColumn(TableInfo table, String columnName, FieldModel field) {
			DDLColumnKey key = new DDLColumnKey(table, columnName);
			DDLColumn column = new DDLFieldColumn(key, field);
			columns.put(key, column);
			return registerColumnInternal(table, key, column, field);
		}

		private DDLColumn registerColumnInternal(TableInfo table, DDLColumnKey key, DDLColumn column, FieldModel field) {
			tables.add(table);
			if (field != null) {
				definingFields.computeIfAbsent(key, k -> new ArrayList<>()).add(field);
			} else {
				implicitForeignKeyColumns.add(key);
			}
			return column;
		}

		@Override
		public DDLColumn registerForeignKey(ForeignKeyInfo foreignKeyInfo) {
			DDLColumnKey ddlColumnKey = new DDLColumnKey(foreignKeyInfo.referringTable(), foreignKeyInfo.fkColumnName());
			DDLColumn column = registerColumn(foreignKeyInfo.referringTable(), foreignKeyInfo.fkColumnName(), foreignKeyInfo.foreignKeyField());
			foreignKeys.put(ddlColumnKey, 
				new DDLForeignKey(ddlColumnKey, new DDLColumnKey(foreignKeyInfo.targetTable(), foreignKeyInfo.idColumnName()))
			);
			return column;
		}

		@Override
		public Map<DDLColumnKey, DDLColumn> columns() {
			return columns;
		}

		@Override
		public Map<DDLColumnKey, DDLPrimaryKeyColumn> primaryKeys() {
			return primaryKeys;
		}

		@Override
		public Map<DDLColumnKey, DDLForeignKey> foreignKeys() {
			return foreignKeys;
		}	

		@Override
		public DDLColumnMetadata buildColumnMetadata(DDLColumnKey columnKey) {
			return buildColumnMetadataFromAnnotations(definingFields.getOrDefault(columnKey, List.of()));
		}

		@Override
		public Set<TableInfo> tables() {
			return tables;
		}
	}

    public static List<String> generateSchemaDDLFromClasses(DbContext dbContext, Class<?>... entityClasses) {
        return AQTSchemaGenerator.generateSchemaDDL(dbContext, collectSchemaInfoFromClasses(entityClasses));
    }

	private static CollectedSchemaInfo collectSchemaInfoFromClasses(Class<?>... entityClasses) {
		RootNode[] queryTrees = List.of(entityClasses).stream().map(AQTTransformer::buildQueryTreeForType).toArray(RootNode[]::new);
		DDLCollectorImpl collector = new DDLCollectorImpl();
		for (RootNode queryTree : queryTrees) {
			collectDDLForEntity(queryTree, collector);
		}

		Map<DDLColumnKey, DDLColumnMetadata> columnMetadata = new HashMap<>();
		for (DDLColumnKey columnKey : collector.definingFields.keySet()) {
			List<FieldModel> fields = collector.definingFields.get(columnKey);
			columnMetadata.put(columnKey, buildColumnMetadataFromAnnotations(fields));
		}
		
		return collector;
	}

	public static List<String> generateMigrationStatementsDDL(DbContext dbContext, SchemaInfo currentSchema, Class<?>... entityClasses) {
		CollectedSchemaInfo desiredSchema = collectSchemaInfoFromClasses(entityClasses);
		SchemaDiff diff = SchemaDiff.diffSchemas(currentSchema, desiredSchema);
		return diff.generateMigrationDDL(dbContext);
	}

	public static List<String> generateSchemaDDL(DbContext dbContext, CollectedSchemaInfo collector) {
		DDLStatementBuilder builder = new DDLStatementBuilder(dbContext, collector);
		List<String> statements = new ArrayList<>();

		// Generate CREATE TABLE statements
		for (TableInfo table : collector.tables()) {
			statements.add(builder.generateCreateTableStatement(table));
		}
		
		// Generate ALTER TABLE statements for foreign keys
		for (DDLForeignKey fk : collector.foreignKeys().values()) {
			statements.add(builder.generateAddForeignKeyStatement(fk));
		}
		
		return statements;
	}

	private static void collectDDLForEntity(TableNode entity, DDLCollector collector) {
		TableInfo tableKey = new TableInfo(entity.tableInfo().schemaName(), entity.tableInfo().tableName());

		for (QueryNode child : entity.children()) {
			if (child instanceof ScalarValue scalar) {
				collector.registerColumn(tableKey, scalar.columnName(), scalar.field());
			} else if (child instanceof PrimaryKeyField pkField) {
				collector.registerPrimaryKeyColumn(tableKey, pkField.columnName(), pkField.field(), pkField.isAutoGenerated());
			} else if (child instanceof ValueCollection valueCollection) {
				String fetchColumn = valueCollection.fetchColumn();
				ForeignKeyInfo joinInfo = valueCollection.join();
				DDLColumn valueColumn = collector.registerInferredColumn(joinInfo.referringTable(), fetchColumn, valueCollection.componentType());
				DDLColumn fkColumn = collector.registerForeignKey(joinInfo);
				collector.registerPrimaryKeyColumn(joinInfo.referringTable(), valueColumn.columnKey().columnName(), null, false);
				collector.registerPrimaryKeyColumn(joinInfo.referringTable(), fkColumn.columnKey().columnName(), null, false);
				// collector.registerTable(joinInfo.referringTable(), List.of(valueColumn.columnKey(), fkColumn.columnKey()));
			} else if (child instanceof Join ref) {
				collectDDLForEntity((TableNode)ref, collector);
				ForeignKeyInfo joinInfo = ref.join();
				collector.registerForeignKey(joinInfo);
				if (ref instanceof EntityNode entityReference) {
					collector.registerColumn(joinInfo.referringTable(), joinInfo.fkColumnName(), entityReference.field());
				}
			} else if (child instanceof EmbeddedEntity emb) {
				collectDDLForEntity((TableNode)emb, collector);
			} else if (child instanceof STISubClassNode stiSubClass) {
				collector.registerInferredColumn(tableKey, stiSubClass.discriminatorColumn(), new ReflectionTypeModel(String.class));
				collectDDLForEntity(stiSubClass, collector);
			} else if (child instanceof JoinTableEntityCollection jte) {
				collectDDLForEntity(jte, collector);
				collector.registerForeignKey(jte.join().parentKey());
				collector.registerForeignKey(jte.join().childKey());
				collector.registerPrimaryKeyColumn(jte.join().joinTableInfo().tableInfo(), jte.join().parentKey().fkColumnName(), null, false);
				collector.registerPrimaryKeyColumn(jte.join().joinTableInfo().tableInfo(), jte.join().childKey().fkColumnName(), null, false);
			} else if (child instanceof RecursiveCollection rc) {
				// Recursive collections over a parent-link column add nothing new to the schema
				// (the FK column lives on the element table itself). Recursive collections that
				// traverse a junction table (@Link(linktable=...)) must emit that junction table.
				AbstractQueryTree.RecursiveLinkTable linkTable = rc.linkTable();
				if (linkTable != null) {
					TableInfo junctionTable = new TableInfo(linkTable.schemaName(), linkTable.tableName());
					FieldModel elementIdField = PojoMetadata.determineIdField(rc.type());
					ForeignKeyInfo sourceFk = new ForeignKeyInfo(
							junctionTable, null, rc.tableInfo(), rc.alias(),
							null, linkTable.sourceColumn(), elementIdField, rc.idColumn(), null);
					ForeignKeyInfo targetFk = new ForeignKeyInfo(
							junctionTable, null, rc.tableInfo(), rc.alias(),
							null, linkTable.targetColumn(), elementIdField, rc.idColumn(), null);
					collector.registerForeignKey(sourceFk);
					collector.registerForeignKey(targetFk);
					collector.registerPrimaryKeyColumn(junctionTable, linkTable.sourceColumn(), null, false);
					collector.registerPrimaryKeyColumn(junctionTable, linkTable.targetColumn(), null, false);
				}
			}
		}
	}

	/**
	 * Builds column metadata by merging @Column annotation values from multiple fields.
	 * When multiple fields define the same column, the most
	 * restrictive constraints are used: non-nullable wins, unique wins.
	 */
	private static DDLColumnMetadata buildColumnMetadataFromAnnotations(List<FieldModel> fields) {
		// Start with default values from @Column annotation
		final int DEFAULT_LENGTH = 255;
		final int DEFAULT_PRECISION = 19;
		final int DEFAULT_SCALE = 4;
		int length = DEFAULT_LENGTH;
		int precision = DEFAULT_PRECISION;
		int scale = DEFAULT_SCALE;
		boolean nullable = true;
		boolean unique = false;
		boolean isLob = false;

		for (FieldModel field : fields) {
			isLob = isLob || field.hasAnnotation(Lob.class);
			if (field.hasAnnotation(Column.class)) {
				
				// Get numeric values - only apply non-defaults
				Number lengthValue = field.getAnnotationAttributeValue(Column.class, "length", Number.class);
				if (lengthValue != null && lengthValue.intValue() != DEFAULT_LENGTH) {
					length = lengthValue.intValue();
				}

				Number precisionValue = field.getAnnotationAttributeValue(Column.class, "precision", Number.class);
				if (precisionValue != null && precisionValue.intValue() != DEFAULT_PRECISION) {
					precision = precisionValue.intValue();
				}

				Number scaleValue = field.getAnnotationAttributeValue(Column.class, "scale", Number.class);
				if (scaleValue != null && scaleValue.intValue() != DEFAULT_SCALE) {
					scale = scaleValue.intValue();
				}

				// For boolean constraints, use most restrictive value
				Boolean nullableValue = field.getAnnotationAttributeValue(Column.class, "nullable", Boolean.class);
				if (nullableValue != null && !nullableValue) {
					nullable = false; // non-nullable wins
				}

				Boolean uniqueValue = field.getAnnotationAttributeValue(Column.class, "unique", Boolean.class);
				if (uniqueValue != null && uniqueValue) {
					unique = true; // unique wins
				}
			}
		}

		return new DDLColumnMetadata(length, precision, scale, nullable, unique, isLob);
	}

}
