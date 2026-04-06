package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.STISubClassNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.AbstractQueryTree.ValueCollection;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.typemodel.AnnotationModel;
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

	private static class DDLCollectorImpl implements DDLCollector {
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
	}

    public static List<String> generateSchemaDDLFromClasses(DbContext dbContext, Class<?>... entityClasses) {
        return AQTSchemaGenerator.generateSchemaDDL(dbContext, List.of(entityClasses).stream().map(AQTTransformer::buildQueryTreeForType).toArray(RootNode[]::new));
    }

	public static List<String> generateSchemaDDL(DbContext dbContext, RootNode... queryTrees) {
		DDLCollectorImpl collector = new DDLCollectorImpl();
		for (RootNode queryTree : queryTrees) {
			collectDDLForEntity(queryTree, collector);
		}

		Map<DDLColumnKey, DDLColumnMetadata> columnMetadata = new HashMap<>();
		for (DDLColumnKey columnKey : collector.definingFields.keySet()) {
			List<FieldModel> fields = collector.definingFields.get(columnKey);
			columnMetadata.put(columnKey, buildColumnMetadataFromAnnotations(fields));
		}
		
		List<String> statements = new ArrayList<>();
		
		HashMap<DDLColumnKey, DDLColumn> combinedColumns = new HashMap<>();
		combinedColumns.putAll(collector.columns);
		combinedColumns.putAll(collector.primaryKeys);


		// Generate CREATE TABLE statements
		for (TableInfo table : collector.tables) {
			StringBuilder sb = new StringBuilder();
			sb.append("CREATE TABLE ");
			sb.append(quoteSchemaAndTable(dbContext, table));
			sb.append(" (\n");
			

			List<DDLColumn> allColumns = combinedColumns.values().stream()
				.filter(col -> col.columnKey().tableKey().equals(table))
				.toList();
			
			List<String> columnDefs = new ArrayList<>();
			for (DDLColumn col : allColumns) {
				StringBuilder colDef = new StringBuilder();
				colDef.append("  ");
				colDef.append(dbContext.getQuoteStyle().quote(col.columnKey().columnName()));
				colDef.append(" ");
				
				DDLColumnMetadata metadata = columnMetadata.get(col.columnKey());

				// Determine SQL type
				if (col instanceof DDLPrimaryKeyColumn pkColumn) {
					if (pkColumn.isAutoIncrement()) {
						colDef.append(dbContext.getAutoIncrementKeyColumnType());
						colDef.append(" ");
						colDef.append(dbContext.getAutoIncrementSyntax());
					} else {
						if (pkColumn.field() == null) {
							// This can happen for primary key columns that are only defined via @JoinColumn in an embedded entity
							colDef.append(dbContext.getForeignKeyColumnType());
						} else {
							colDef.append(dbContext.mapJavaTypeToSql(((ReflectionTypeModel)pkColumn.field().getType()).getReflectionClass(), metadata));
						}
					}
				} else if (col instanceof DDLFieldColumn fieldColumn && fieldColumn.field() != null) {
					if (collector.foreignKeys.containsKey(col.columnKey())) {
						colDef.append(dbContext.getForeignKeyColumnType());
					} else {
						colDef.append(dbContext.mapJavaTypeToSql(((ReflectionTypeModel)fieldColumn.field().getType()).getReflectionClass(), metadata));
					}
				} else if (col instanceof DDLInferredColumn inferredColumn) {
					colDef.append(dbContext.mapJavaTypeToSql(((ReflectionTypeModel)inferredColumn.scalarType).getReflectionClass(), metadata));
				} else {
					// Implicit foreign key column (from join table)
					colDef.append(dbContext.getForeignKeyColumnType());
				}

				if (metadata != null) {
					if (!metadata.nullable()) {
						colDef.append(" NOT NULL");
					}
					if (metadata.unique()) {
						colDef.append(" UNIQUE");
					}
				}

				columnDefs.add(colDef.toString());
			}
			
			// Add primary key constraint if there are primary key columns
			List<DDLColumn> primaryKeys = allColumns.stream().filter(col -> col instanceof DDLPrimaryKeyColumn).toList();
			if (!primaryKeys.isEmpty()) {
				String pkColumns = primaryKeys.stream()
					.map(pk -> dbContext.getQuoteStyle().quote(pk.columnKey().columnName()))
					.reduce((a, b) -> a + ", " + b)
					.orElse("");
				columnDefs.add("  PRIMARY KEY (" + pkColumns + ")");
			}
			
			sb.append(String.join(",\n", columnDefs));
			sb.append("\n)");
			statements.add(sb.toString());
		}
		
		// Generate ALTER TABLE statements for foreign keys
		for (DDLForeignKey fk : collector.foreignKeys.values()) {
			StringBuilder sb = new StringBuilder();
			sb.append("ALTER TABLE ");
			sb.append(quoteSchemaAndTable(dbContext, fk.referringColumn().tableKey()));
			sb.append(" ADD CONSTRAINT ");
			sb.append(dbContext.quoteObjectNames("fk_" + fk.referringColumn().tableKey().tableName() + "_" + fk.referringColumn().columnName()));
			sb.append(" FOREIGN KEY (");
			sb.append(dbContext.quoteObjectNames(fk.referringColumn().columnName()));
			sb.append(") REFERENCES ");
			sb.append(quoteSchemaAndTable(dbContext, fk.referencedIdColumn().tableKey()));
			sb.append(" (");
			sb.append(dbContext.quoteObjectNames(fk.referencedIdColumn().columnName()));
			sb.append(")");
			statements.add(sb.toString());
		}
		
		return statements;
	}

	private static String quoteSchemaAndTable(DbContext dbContext, TableInfo tableInfo) {
		if (tableInfo.schemaName() != null) {
			return dbContext.quoteObjectNames(tableInfo.schemaName(), tableInfo.tableName());
		} else {
			return dbContext.quoteObjectNames(tableInfo.tableName());
		}
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
				AnnotationModel annotation = field.getAnnotation(Column.class).orElseThrow();
				
				// Get numeric values - only apply non-defaults
				Number lengthValue = annotation.getNumberAttribute("length");
				if (lengthValue.intValue() != DEFAULT_LENGTH) {
					length = lengthValue.intValue();
				}

				Number precisionValue = annotation.getNumberAttribute("precision");
				if (precisionValue.intValue() != DEFAULT_PRECISION) {
					precision = precisionValue.intValue();
				}

				Number scaleValue = annotation.getNumberAttribute("scale");
				if (scaleValue.intValue() != DEFAULT_SCALE) {
					scale = scaleValue.intValue();
				}

				// For boolean constraints, use most restrictive value
				List<Boolean> nullableValues = annotation.getBooleanValues("nullable");
				if (!nullableValues.isEmpty() && !nullableValues.get(0)) {
					nullable = false; // non-nullable wins
				}

				List<Boolean> uniqueValues = annotation.getBooleanValues("unique");
				if (!uniqueValues.isEmpty() && uniqueValues.get(0)) {
					unique = true; // unique wins
				}
			}
		}

		return new DDLColumnMetadata(length, precision, scale, nullable, unique, isLob);
	}

}
