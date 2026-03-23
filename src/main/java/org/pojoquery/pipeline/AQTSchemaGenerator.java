package org.pojoquery.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import org.pojoquery.DbContext;
import org.pojoquery.pipeline.AbstractQueryTree.ForeignKeyInfo;
import org.pojoquery.pipeline.AbstractQueryTree.Join;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKey;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKeyField;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.querytree.TableInfo;
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

	interface DDLCollector {
		void registerTable(TableInfo tableKey, List<DDLColumn> primaryKeyColumns);
		void registerColumn(TableInfo table, String columnName, FieldModel field);
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
		public void registerColumn(TableInfo table, String columnName, FieldModel field) {
			// System.out.println("COLUMN: " + table.tableName() + "." + columnName + " Field: " + (field != null ? field.getName() : "[implicit foreign key]"));
			DDLColumnKey key = new DDLColumnKey(table, columnName);
			columns.put(key, new DDLColumn(key, field));
			if (field != null) {
				definingFields.computeIfAbsent(key, k -> new ArrayList<>()).add(field);
			} else {
				implicitForeignKeyColumns.add(key);
			}
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

	public static List<String> generateCreateSchemaDDL(DbContext dbContext, RootNode... entities) {
		DDLCollectorImpl collector = new DDLCollectorImpl();
		for (RootNode entity : entities) {
			collectDDLForEntity(entity, collector);
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
				
				// Determine SQL type
				if (col.field() != null) {
					boolean isAutoIncrement = table.primaryKeyColumns().contains(col) &&
						(col.field().getType().isSameType(new ReflectionTypeModel(Long.class)) || 
						col.field().getType().isSameType(new ReflectionTypeModel(long.class)));
					
					if (isAutoIncrement) {
						colDef.append(dbContext.getAutoIncrementKeyColumnType());
						colDef.append(" ");
						colDef.append(dbContext.getAutoIncrementSyntax());
					} else if (collector.foreignKeys.containsKey(col.columnKey())) {
						colDef.append(dbContext.getForeignKeyColumnType());
					} else {
						colDef.append(dbContext.mapJavaTypeToSql(col.field()));
					}
				} else {
					// Implicit foreign key column (from join table) - use BIGINT by default
					colDef.append(dbContext.getForeignKeyColumnType());
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
			} else if (child instanceof Join ref) {
				collectDDLForEntity((TableNode)ref, collector);
				ForeignKeyInfo joinInfo = ref.join();
				collector.registerForeignKey(joinInfo);
			} else if (child instanceof JoinTableEntityCollection jte) {
				collectDDLForEntity(jte, collector);
				collector.registerTable(jte.join().joinTableInfo().tableInfo(), List.of());
				collector.registerColumn(jte.join().joinTableInfo().tableInfo(), jte.join().parentKey().fkColumnName(), null);
				collector.registerColumn(jte.join().joinTableInfo().tableInfo(), jte.join().childKey().fkColumnName(), null);
				collector.registerForeignKey(jte.join().parentKey());
				collector.registerForeignKey(jte.join().childKey());
			}
		}
	}
}
