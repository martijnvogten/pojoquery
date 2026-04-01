package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.pojoquery.DbContext;
import org.pojoquery.annotations.Column;
import org.pojoquery.pipeline.AbstractQueryTree.EmbeddedEntity;
import org.pojoquery.pipeline.AbstractQueryTree.ForeignKeyInfo;
import org.pojoquery.pipeline.AbstractQueryTree.Join;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKey;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKeyField;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.AbstractQueryTree.ValueCollection;
import org.pojoquery.pipeline.querytree.TableInfo;
import org.pojoquery.typemodel.AnnotationModel;
import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.ReflectionTypeModel;

public class AQTSchemaGenerator {

	public record DDLTable(TableInfo tableKey, List<DDLColumn> primaryKeyColumns) {
	}

	public record DDLColumn(DDLColumnKey columnKey, FieldModel field) {
	}

	public record DDLColumnKey(TableInfo tableKey, String columnName) {
	}

	public record DDLForeignKey(DDLColumnKey referringColumn, DDLColumnKey referencedIdColumn) {
	}

	public record DDLColumnMetadata(int length, int precision, int scale, boolean nullable, boolean unique) {
	}

	interface DDLCollector {
		void registerTable(TableInfo tableKey, List<DDLColumn> primaryKeyColumns);
		DDLColumn registerColumn(TableInfo table, String columnName, FieldModel field);
		void registerForeignKey(ForeignKeyInfo foreignKeyInfo);
	}

	private static class DDLCollectorImpl implements DDLCollector {
		private HashMap<TableInfo, DDLTable> tables = new LinkedHashMap<>();
		private HashMap<DDLColumnKey, DDLColumn> columns = new LinkedHashMap<>();
		private HashMap<DDLColumnKey, List<FieldModel>> definingFields = new HashMap<>();
		private HashSet<DDLColumnKey> implicitForeignKeyColumns = new HashSet<>();
		private HashMap<DDLColumnKey, DDLForeignKey> foreignKeys = new LinkedHashMap<>();
		@Override
		public void registerTable(TableInfo tableKey, List<DDLColumn> primaryKeyColumns) {
			tables.put(tableKey, new DDLTable(tableKey, primaryKeyColumns));
		}
		
		@Override
		public DDLColumn registerColumn(TableInfo table, String columnName, FieldModel field) {
			// System.out.println("COLUMN: " + table.tableName() + "." + columnName + " Field: " + (field != null ? field.getName() : "[implicit foreign key]"));
			DDLColumnKey key = new DDLColumnKey(table, columnName);
			DDLColumn column = new DDLColumn(key, field);
			columns.put(key, column);
			if (field != null) {
				definingFields.computeIfAbsent(key, k -> new ArrayList<>()).add(field);
			} else {
				implicitForeignKeyColumns.add(key);
			}
			return column;
		}

		@Override
		public void registerForeignKey(ForeignKeyInfo foreignKeyInfo) {
			DDLColumnKey ddlColumnKey = new DDLColumnKey(foreignKeyInfo.referringTable(), foreignKeyInfo.fkColumnName());
			registerColumn(foreignKeyInfo.referringTable(), foreignKeyInfo.fkColumnName(), foreignKeyInfo.foreignKeyField());
			foreignKeys.put(ddlColumnKey, 
				new DDLForeignKey(ddlColumnKey, new DDLColumnKey(foreignKeyInfo.targetTable(), foreignKeyInfo.idColumnName()))
			);
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
		
		// Generate CREATE TABLE statements
		for (DDLTable table : collector.tables.values()) {
			StringBuilder sb = new StringBuilder();
			sb.append("CREATE TABLE ");
			sb.append(dbContext.getQuoteStyle().quote(table.tableKey().tableName()));
			sb.append(" (\n");
			
			// Get columns for this table
			List<DDLColumn> tableColumns = collector.columns.values().stream()
				.filter(col -> col.columnKey().tableKey().equals(table.tableKey()))
				.toList();
			
			List<String> columnDefs = new ArrayList<>();
			for (DDLColumn col : tableColumns) {
				StringBuilder colDef = new StringBuilder();
				colDef.append("  ");
				colDef.append(dbContext.getQuoteStyle().quote(col.columnKey().columnName()));
				colDef.append(" ");
				
				DDLColumnMetadata metadata = columnMetadata.get(col.columnKey());

				// Determine SQL type
				if (col.field() != null) {
					boolean isAutoIncrement = table.primaryKeyColumns().size() == 1 && table.primaryKeyColumns().contains(col);
					
					if (isAutoIncrement) {
						colDef.append(dbContext.getAutoIncrementKeyColumnType());
						colDef.append(" ");
						colDef.append(dbContext.getAutoIncrementSyntax());
					} else if (collector.foreignKeys.containsKey(col.columnKey())) {
						colDef.append(dbContext.getForeignKeyColumnType());
					} else {
						colDef.append(dbContext.mapJavaTypeToSql(col.field(), metadata));
					}
				} else {
					// Implicit foreign key column (from join table)
					colDef.append(dbContext.getForeignKeyColumnType());
				}

				if (metadata != null) {
					// if (metadata.length() != 255) {
					// 	colDef.append("(").append(metadata.length()).append(")");
					// }
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
			if (!table.primaryKeyColumns().isEmpty()) {
				String pkColumns = table.primaryKeyColumns().stream()
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
			sb.append(dbContext.quoteObjectNames(fk.referringColumn().tableKey().tableName()));
			sb.append(" ADD CONSTRAINT ");
			sb.append(dbContext.quoteObjectNames("fk_" + fk.referringColumn().tableKey().tableName() + "_" + fk.referringColumn().columnName()));
			sb.append(" FOREIGN KEY (");
			sb.append(dbContext.quoteObjectNames(fk.referringColumn().columnName()));
			sb.append(") REFERENCES ");
			sb.append(dbContext.quoteObjectNames(fk.referencedIdColumn().tableKey().tableName()));
			sb.append(" (");
			sb.append(dbContext.quoteObjectNames(fk.referencedIdColumn().columnName()));
			sb.append(")");
			statements.add(sb.toString());
		}
		
		return statements;
	}

	private static void collectDDLForEntity(TableNode entity, DDLCollector collector) {
		TableInfo tableKey = new TableInfo(entity.tableInfo().schemaName(), entity.tableInfo().tableName());
		List<DDLColumn> primaryKeyColumns = entity.children().stream()
			.filter(child -> child instanceof PrimaryKey)
			.map(child -> (PrimaryKey) child)
			.map(pk -> new DDLColumn(new DDLColumnKey(tableKey, pk.columnName()), pk.field())) // placeholder type
			.toList();
		collector.registerTable(tableKey, primaryKeyColumns);

		for (QueryNode child : entity.children()) {
			if (child instanceof ScalarValue scalar) {
				collector.registerColumn(tableKey, scalar.columnName(), scalar.field());
			} else if (child instanceof PrimaryKeyField pkField) {
				collector.registerColumn(tableKey, pkField.columnName(), pkField.field());
			} else if (child instanceof ValueCollection valueCollection) {
				String fetchColumn = valueCollection.fetchColumn();
				ForeignKeyInfo joinInfo = valueCollection.join();
				collector.registerTable(joinInfo.referringTable(), List.of(fetchColumn, joinInfo.fkColumnName()).stream()
					.map(colName -> new DDLColumn(new DDLColumnKey(joinInfo.referringTable(), colName), null)) // placeholder type
					.toList());
				collector.registerColumn(joinInfo.referringTable(), fetchColumn, valueCollection.field());
				collector.registerForeignKey(joinInfo);
			} else if (child instanceof Join ref) {
				collectDDLForEntity((TableNode)ref, collector);
				ForeignKeyInfo joinInfo = ref.join();
				collector.registerForeignKey(joinInfo);
			} else if (child instanceof EmbeddedEntity emb) {
				collectDDLForEntity((TableNode)emb, collector);
			} else if (child instanceof JoinTableEntityCollection jte) {
				collectDDLForEntity(jte, collector);
				DDLColumn parentKeyColumn = collector.registerColumn(jte.join().joinTableInfo().tableInfo(), jte.join().parentKey().fkColumnName(), null);
				DDLColumn childKeyColumn = collector.registerColumn(jte.join().joinTableInfo().tableInfo(), jte.join().childKey().fkColumnName(), null);
				collector.registerTable(jte.join().joinTableInfo().tableInfo(), List.of(parentKeyColumn, childKeyColumn));
				collector.registerForeignKey(jte.join().parentKey());
				collector.registerForeignKey(jte.join().childKey());
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

		for (FieldModel field : fields) {
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

		return new DDLColumnMetadata(length, precision, scale, nullable, unique);
	}

}
