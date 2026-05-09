package org.pojoquery.multiset;

import java.util.List;

import javax.sql.DataSource;

import org.junit.Test;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.TransformPipeline;
import org.pojoquery.schema.SchemaGenerator;
import org.pojoquery.util.RecordIndenter;

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

	public static class JsonMultiSetObjectsTransform implements TransformPipeline.TreeTransform {

		@Override
		public RootNode transform(RootNode node) {
			System.out.println("Original tree:" + RecordIndenter.indent(node.toString()));
			return node;
		}
	}

	@Test
	public void testBasics() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class, Role.class, Permission.class);
		insertTestData(db);

		// transform the tree to use JSON_ARRAYAGG for the roles collection
		// and add a group by

		System.out.println(
			PojoQuery.build(
				DbContext.getDefault(), 
				TransformPipeline.defaultPipeline().append(JsonMultiSetObjectsTransform.class), 
				User.class)
			.toSql());

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
					JOIN "user_role" "user.user_role" ON "user.user_role"."user_id" = "user"."id"
					JOIN "role" "roles" ON "user.user_role"."role_id" = "roles"."id"
					LEFT JOIN (
						SELECT
							"roles.permissions.role_permission"."role_id" AS "role_id",
							JSON_ARRAYAGG(
								JSON_OBJECT(
									'id': "permissions"."id",
									'permissionname': "permissions"."permissionname"
								)
							) AS "permissions_json"
						FROM "role_permission" "roles.permissions.role_permission"
						JOIN "permission" "permissions" ON "roles.permissions.role_permission"."permission_id" = "permissions"."id"
						GROUP BY "roles.permissions.role_permission"."role_id"
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
