package org.pojoquery.multiset;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.Test;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.pipeline.AbstractQueryTree.AggregateScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKey;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.TableInfo;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.DefaultSqlQuery;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.pipeline.SqlQuery.JoinType;
import org.pojoquery.pipeline.TransformPipeline;
import org.pojoquery.schema.SchemaGenerator;
import org.pojoquery.util.CurlyMarkers;

public class TestJsonMultisets {

	@Table("user")
	static class User {
		@Id Long id;
		String username;
		@Link(linktable = "user_role")
		List<Role> roles;
	}
	
	@Table("role")
	static class Role {
		@Id Long id;
		String rolename;
		@Link(linktable = "role_permission")
		List<Permission> permissions;
	}

	@Table("permission")
	static class Permission {
		@Id Long id;
		String permissionname;
	}

	static class JsonSqlQueryBuilder {
		static record JsonSqlField(String jsonPropertyName, SqlExpression expression) {}
		static record SelectField(SqlExpression expression, String alias) {}

		interface JsonJoin {
			JoinType type();
			String alias();
			SqlExpression joinCondition();
		}

		static record JsonSubQueryJoin(JoinType type, SqlExpression subquery, String alias, SqlExpression joinCondition) implements JsonJoin {}
		static record JsonTableJoin(JoinType type, TableInfo table, String alias, SqlExpression joinCondition) implements JsonJoin {}

		final String indent;
		final DbContext dbContext;

		String fromClause;
		List<SqlExpression> groupBy;
		List<SqlExpression> orderBy;
		List<SelectField> selectFields = new ArrayList<>();
		List<JsonSqlField> jsonFields = new ArrayList<>();
		List<JsonJoin> joins = new ArrayList<>();

		public JsonSqlQueryBuilder(DbContext dbContext, String indent) {
			this.indent = indent;
			this.dbContext = dbContext;
		}

		public String getIndent() {
			return indent;
		}

		public void setTable(TableInfo table, String alias) {
			this.fromClause = quoteTableName(table);
			if (alias != null && !alias.isEmpty()) {
				this.fromClause += " AS " + dbContext.quoteAlias(alias);
			}
		}

		public void setGroupBy(List<SqlExpression> groupBy) {
			this.groupBy = groupBy;
		}

		public void setOrderBy(List<SqlExpression> orderBy) {
			this.orderBy = orderBy;
		}

		public void addField(SqlExpression expression, String alias) {
			selectFields.add(new SelectField(expression, alias));
		}

		public void addJsonField(String jsonPropertyName, SqlExpression expression) {
			jsonFields.add(new JsonSqlField(jsonPropertyName, expression));
		}

		public void addSubQueryJoin(JoinType type, SqlExpression subquery, String alias, SqlExpression joinCondition) {
			joins.add(new JsonSubQueryJoin(type, subquery, alias, joinCondition));
		}

		public void addTableJoin(JoinType type, TableInfo table, String alias, SqlExpression joinCondition) {
			joins.add(new JsonTableJoin(type, table, alias, joinCondition));
		}

		public SqlExpression toStatement() {
			List<SqlExpression> parts = new ArrayList<>();
			parts.add(SqlExpression.sql(indent + "SELECT"));
			selectFields.forEach(field -> {
				parts.add(SqlExpression.sql(indent + "  " + resolveAliasesInternal(dbContext, field.expression, null, null).getSql() + " AS " + dbContext.quoteAlias(field.alias) + ","));
			});

			parts.add(SqlExpression.sql(indent + "JSON_ARRAYAGG(\n" + indent + "  JSON_OBJECT(")); // start of JSON_OBJECT
			List<SqlExpression> objectProperties = jsonFields.stream()
					.map(field -> 
						SqlExpression.implode(": ", 
							List.of(SqlExpression.sql(indent + "    '" + field.jsonPropertyName + "'"), 
							resolveAliasesInternal(dbContext, field.expression, null, null)))
						)
					.toList();

			parts.add(SqlExpression.implode(",\n", objectProperties));

			parts.add(SqlExpression.sql(indent + "  )\n" + indent + ") AS " + dbContext.quoteAlias("json")));
			parts.add(SqlExpression.sql(indent + "FROM " + fromClause));

			List<SqlExpression> joinExpressions = joins.stream()
					.map(join -> {
						SqlExpression joinPart = SqlExpression.sql(indent + join.type().name() + " JOIN ");
						if (join instanceof JsonSubQueryJoin joinSubQuery) {
							joinPart = SqlExpression.implode("", List.of(joinPart, SqlExpression.sql("(\n"), joinSubQuery.subquery(), SqlExpression.sql(indent + "\n" + indent + ")")));
						} else if (join instanceof JsonTableJoin joinTable) {
							joinPart = SqlExpression.implode("", List.of(joinPart, SqlExpression.sql(quoteTableName(joinTable.table()))));
						}
						return SqlExpression.implode("", List.of(
							joinPart,
							SqlExpression.sql(" AS " + dbContext.quoteAlias(join.alias()) + " ON "),
							resolveAliasesInternal(dbContext, join.joinCondition(), null, null)));
					})
					.toList();
			if (!joinExpressions.isEmpty()) {
				parts.add(SqlExpression.implode("\n", joinExpressions));
			}

			if (groupBy != null && !groupBy.isEmpty()) {
				parts.add(SqlExpression.sql(indent + "GROUP BY " + String.join(", ", groupBy.stream().map(expr -> resolveAliasesInternal(dbContext, expr, null, null).getSql()).toList())));
			}
			if (orderBy != null && !orderBy.isEmpty()) {
				parts.add(SqlExpression.sql(indent + "ORDER BY " + String.join(", ", orderBy.stream().map(expr -> resolveAliasesInternal(dbContext, expr, null, null).getSql()).toList())));
			}
			return SqlExpression.implode("\n", parts);
		}

		private String quoteTableName(TableInfo table) {
			return table.schemaName() != null && !table.schemaName().isEmpty() ? 
				dbContext.quoteObjectNames(table.schemaName(), table.tableName()) : 
				dbContext.quoteObjectNames(table.tableName());
		}

		private SqlExpression resolveAliasesInternal(DbContext context, SqlExpression sql, String thisAlias, String currentFieldAlias) {
			return new SqlExpression(CurlyMarkers.processMarkers(sql.getSql(), marker -> {		
				if ("this".equals(marker)) {
					return context.quoteAlias(thisAlias);
				} else if (currentFieldAlias != null && marker.equals(currentFieldAlias)) {
					return context.quoteAlias(currentFieldAlias);
				} else {
					int lastDotIndex = marker.lastIndexOf('.');
					if (lastDotIndex > 0) {
						String tableAlias = marker.substring(0, lastDotIndex);
						String columnName = marker.substring(lastDotIndex + 1);
						return context.quoteAlias(tableAlias) + "." + context.quoteObjectNames(columnName);
					}
					return context.quoteObjectNames(marker); // leave other markers unchanged
				}
			}));
		}
	}

	public static void toJsonQuery(TableNode node, JsonSqlQueryBuilder sqlQuery) {
		if (node instanceof RootNode rootNode) {
			sqlQuery.setTable(node.tableInfo(), node.tableInfo().tableName());
			if (rootNode.groupBy() != null) {
				sqlQuery.setGroupBy(rootNode.groupBy().stream().map(expr -> SqlExpression.sql(expr)).toList());
			}
			if (rootNode.orderBy() != null) {
				sqlQuery.setOrderBy(rootNode.orderBy().stream().map(expr -> SqlExpression.sql(expr)).toList());
			}
		}
		for (QueryNode child : node.children()) {
			if (false) {
			} else if (child instanceof PrimaryKey pk) {
				sqlQuery.addJsonField(pk.field().getName(), pk.expression());
				sqlQuery.setGroupBy(List.of(pk.expression()));
			} else if (child instanceof AggregateScalarValue agg) {
				sqlQuery.addJsonField(agg.field().getName(), agg.expression());
			} else if (child instanceof ScalarValue scalar) {
				sqlQuery.addJsonField(scalar.field().getName(), scalar.expression());
			} else if (child instanceof JoinTableEntityCollection ec) {
				// Push the junction table into the subquery so the outer query stays at one
				// row per parent. Otherwise the outer JSON_ARRAYAGG would emit one element
				// per row in the parent x junction fan-out, duplicating the parent.
				String junctionAlias = ec.join().joinTableInfo().joinTableAlias();
				TableInfo junctionTable = ec.join().joinTableInfo().tableInfo();
				String parentFkCol = ec.join().parentKey().fkColumnName();
				String parentIdCol = ec.join().parentKey().idColumnName();
				String parentAlias = node.alias();

				JsonSqlQueryBuilder jsonQuery = new JsonSqlQueryBuilder(DbContext.getDefault(), sqlQuery.getIndent() + "  ");
				
				jsonQuery.setTable(junctionTable, junctionAlias);
				jsonQuery.addTableJoin(JoinType.LEFT, ec.join().childKey().targetTable(), ec.alias(),
						ec.join().childKey().joinCondition());
				jsonQuery.addField(SqlExpression.sql("{" + junctionAlias + "." + parentFkCol + "}"), parentFkCol);
				toJsonQuery(ec, jsonQuery);
				// Set GROUP BY *after* the recursive call so the child PrimaryKey handler
				// doesn't overwrite it; we must group by the junction FK, not the child PK.
				jsonQuery.setGroupBy(List.of(SqlExpression.sql("{" + junctionAlias + "." + parentFkCol + "}")));

				SqlExpression onCondition = SqlExpression.sql(
						"{" + ec.alias() + "." + parentFkCol + "} = {" + parentAlias + "." + parentIdCol + "}");
				sqlQuery.addSubQueryJoin(JoinType.LEFT, jsonQuery.toStatement(), ec.alias(), onCondition);
				sqlQuery.addJsonField(ec.field().getName(), SqlExpression.sql("{" + ec.alias() + ".json} FORMAT JSON"));
			}
		}
	}

	@Test
	public void testManualQueryBuilder() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class, Role.class, Permission.class);
		insertTestData(db);

		PojoQuery<User> userQuery = PojoQuery.build(
				DbContext.getDefault(), 
				TransformPipeline.defaultPipeline(), 
				User.class);

		RootNode tree = userQuery.getTree();
		JsonSqlQueryBuilder jsonQuery = new JsonSqlQueryBuilder(DbContext.getDefault(), "");
		toJsonQuery(tree, jsonQuery);
		String sql = jsonQuery.toStatement().getSql();
		System.out.println(sql);
		DB.queryRows(db, sql).forEach(row -> System.out.println(row));
	}

	@Test
	public void testBasics() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class, Role.class, Permission.class);
		insertTestData(db);

		// transform the tree to use JSON_ARRAYAGG for the roles collection
		// and add a group by

		PojoQuery<User> userQuery = PojoQuery.build(
				DbContext.getDefault(), 
				TransformPipeline.defaultPipeline(), 
				User.class);

		RootNode tree = userQuery.getTree();


		DefaultSqlQuery sqlQuery = new DefaultSqlQuery(DbContext.getDefault());
		// AQTTransformer.toJsonObjectSql(tree, sqlQuery);


		System.out.println(
			userQuery.toSql());


		String expectedSql = """
					SELECT
						JSON_OBJECT(
							'id': "user"."id",
							'username': "user"."username",
							'roles': COALESCE(
								JSON_ARRAYAGG(
									JSON_OBJECT(
										'id': "roles"."id",
										'rolename': "roles"."rolename",
										'permissions': COALESCE("roles.permissions"."permissions_json", JSON_ARRAY()) FORMAT JSON
									)
								),
								JSON_ARRAY()
							) FORMAT JSON
						) AS "json"
					FROM "user" "user"
					LEFT JOIN "user_role" "user.user_role" ON "user.user_role"."user_id" = "user"."id"
					LEFT JOIN "role" "roles" ON "user.user_role"."role_id" = "roles"."id"
					LEFT JOIN (
						SELECT
							"role_permission"."role_id" AS "role_id",
							JSON_ARRAYAGG(
								JSON_OBJECT(
									'id': "permissions"."id",
									'permissionname': "permissions"."permissionname"
								)
							) AS "permissions_json"
						FROM "role_permission" "role_permission"
						LEFT JOIN "permission" "permissions" ON "role_permission"."permission_id" = "permissions"."id"
						GROUP BY "role_permission"."role_id"
					) "roles.permissions" ON "roles.permissions"."role_id" = "roles"."id"
					GROUP BY "user"."id", "user"."username"

			""";

		DB.queryRows(db, expectedSql).forEach(row -> System.out.println(row));
		
	}

	private static void insertTestData(DataSource db) {
		DB.runInTransaction(db, connection -> {
			Permission readPermission = new Permission();
			readPermission.permissionname = "read";
			PojoQuery.insert(connection, readPermission);

			Permission writePermission = new Permission();
			writePermission.permissionname = "write";
			PojoQuery.insert(connection, writePermission);

			Role adminRole = new Role();
			adminRole.rolename = "admin";
			adminRole.permissions = List.of(readPermission, writePermission);
			PojoQuery.insert(connection, adminRole);

			Role editorRole = new Role();
			editorRole.rolename = "editor";
			editorRole.permissions = List.of(readPermission);
			PojoQuery.insert(connection, editorRole);

			User joe = new User();
			joe.username = "joe";
			joe.roles = List.of(adminRole, editorRole);
			PojoQuery.insert(connection, joe);
		});
	}

}
