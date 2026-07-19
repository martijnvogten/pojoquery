package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.pojoquery.TestUtils.norm;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Recursive;
import org.pojoquery.annotations.Recursive.Direction;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;

/**
 * SQL generation tests for JSON document queries
 * ({@link PojoQuery#toJsonStatement()}) across the three dialects.
 *
 * <p>Execution against actual databases is covered by
 * {@code org.pojoquery.integrationtest.JsonQueryIT}.</p>
 */
public class TestJsonQueries {

	@Table("user")
	public static class User {
		@Id
		public Long id;
		public String username;
		@Link(linktable = "user_role")
		public List<Role> roles;
	}

	@Table("role")
	public static class Role {
		@Id
		public Long id;
		public String rolename;
	}

	@Table("room")
	@SubClasses({ BedRoom.class })
	public static class Room {
		@Id
		public Long id;
		public Double area;
	}

	@Table("bedroom")
	public static class BedRoom extends Room {
		public Integer numberOfBeds;
	}

	@Test
	public void testCollectionHsqldb() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.HSQLDB), User.class).toJsonSql();
		assertEquals(norm("""
				SELECT
				 JSON_OBJECT(
				  'id': "user"."id",
				  'username': "user"."username",
				  'roles': COALESCE("roles"."json", JSON_ARRAY()) FORMAT JSON
				 ) AS "json"
				FROM "user" AS "user"
				LEFT JOIN (
				 SELECT
				  "roles.user_role"."user_id" AS "user_id",
				  JSON_ARRAYAGG(
				   JSON_OBJECT(
				    'id': "roles"."id",
				    'rolename': "roles"."rolename"
				   )
				  ) AS "json"
				 FROM "user_role" AS "roles.user_role"
				 LEFT JOIN "role" AS "roles" ON "roles.user_role"."role_id" = "roles"."id"
				 GROUP BY "roles.user_role"."user_id"
				) AS "roles" ON "roles"."user_id" = "user"."id"
				"""), norm(sql));
	}

	@Test
	public void testCollectionMysql() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.MYSQL), User.class).toJsonSql();
		assertEquals(norm("""
				SELECT
				 JSON_OBJECT(
				  'id', `user`.`id`,
				  'username', `user`.`username`,
				  'roles', COALESCE(`roles`.`json`, JSON_ARRAY())
				 ) AS `json`
				FROM `user` AS `user`
				LEFT JOIN (
				 SELECT
				  `roles.user_role`.`user_id` AS `user_id`,
				  JSON_ARRAYAGG(
				   JSON_OBJECT(
				    'id', `roles`.`id`,
				    'rolename', `roles`.`rolename`
				   )
				  ) AS `json`
				 FROM `user_role` AS `roles.user_role`
				 LEFT JOIN `role` AS `roles` ON `roles.user_role`.`role_id` = `roles`.`id`
				 GROUP BY `roles.user_role`.`user_id`
				) AS `roles` ON `roles`.`user_id` = `user`.`id`
				"""), norm(sql));
	}

	@Test
	public void testCollectionPostgres() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.POSTGRES), User.class).toJsonSql();
		assertEquals(norm("""
				SELECT
				 JSONB_BUILD_OBJECT(
				  'id', "user"."id",
				  'username', "user"."username",
				  'roles', COALESCE("roles"."json", '[]'::jsonb)
				 ) AS "json"
				FROM "user" AS "user"
				LEFT JOIN (
				 SELECT
				  "roles.user_role"."user_id" AS "user_id",
				  JSONB_AGG(
				   JSONB_BUILD_OBJECT(
				    'id', "roles"."id",
				    'rolename', "roles"."rolename"
				   )
				  ) AS "json"
				 FROM "user_role" AS "roles.user_role"
				 LEFT JOIN "role" AS "roles" ON "roles.user_role"."role_id" = "roles"."id"
				 GROUP BY "roles.user_role"."user_id"
				) AS "roles" ON "roles"."user_id" = "user"."id"
				"""), norm(sql));
	}

	@Test
	public void testInheritanceHsqldb() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.HSQLDB), Room.class).toJsonSql();
		assertEquals(norm("""
				SELECT
				 JSON_OBJECT(
				  'id': "room"."id",
				  'area': "room"."area",
				  '_type': CASE WHEN "room.bedroom"."id" IS NOT NULL THEN CAST('BedRoom' AS VARCHAR(7)) ELSE CAST('Room' AS VARCHAR(4)) END,
				  'numberOfBeds': CASE WHEN "room.bedroom"."id" IS NOT NULL THEN "room.bedroom"."numberOfBeds" END
				  ABSENT ON NULL
				 ) AS "json"
				FROM "room" AS "room"
				LEFT JOIN "bedroom" AS "room.bedroom" ON "room.bedroom"."id" = "room"."id"
				"""), norm(sql));
	}

	@Test
	public void testInheritanceMysql() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.MYSQL), Room.class).toJsonSql();
		assertEquals(norm("""
				SELECT
				 JSON_MERGE_PATCH(
				  JSON_OBJECT(
				   'id', `room`.`id`,
				   'area', `room`.`area`,
				   '_type', CASE WHEN `room.bedroom`.`id` IS NOT NULL THEN 'BedRoom' ELSE 'Room' END
				  ),
				  CASE WHEN `room.bedroom`.`id` IS NOT NULL THEN JSON_OBJECT(
				   'numberOfBeds', `room.bedroom`.`numberOfBeds`
				  ) ELSE JSON_OBJECT() END
				 ) AS `json`
				FROM `room` AS `room`
				LEFT JOIN `bedroom` AS `room.bedroom` ON `room.bedroom`.`id` = `room`.`id`
				"""), norm(sql));
	}

	@Test
	public void testInheritancePostgres() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.POSTGRES), Room.class).toJsonSql();
		assertEquals(norm("""
				SELECT
				 (JSONB_BUILD_OBJECT(
				  'id', "room"."id",
				  'area', "room"."area",
				  '_type', CASE WHEN "room.bedroom"."id" IS NOT NULL THEN 'BedRoom' ELSE 'Room' END
				 ) || CASE WHEN "room.bedroom"."id" IS NOT NULL THEN JSONB_STRIP_NULLS(JSONB_BUILD_OBJECT(
				  'numberOfBeds', "room.bedroom"."numberOfBeds"
				 )) ELSE '{}'::jsonb END) AS "json"
				FROM "room" AS "room"
				LEFT JOIN "bedroom" AS "room.bedroom" ON "room.bedroom"."id" = "room"."id"
				"""), norm(sql));
	}

	@Test
	public void testWhereOrderByAndLimitApplyToJsonQuery() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.MYSQL), Role.class)
				.addWhere("{role.rolename} = ?", "admin")
				.addOrderBy("{role.rolename} DESC")
				.setLimit(10)
				.toJsonSql();
		assertEquals(norm("""
				SELECT
				 JSON_OBJECT(
				  'id', `role`.`id`,
				  'rolename', `role`.`rolename`
				 ) AS `json`
				FROM `role` AS `role`
				WHERE `role`.`rolename` = ?
				ORDER BY `role`.`rolename` DESC
				LIMIT 10
				"""), norm(sql));
	}

	// ========== Unsupported node types fail loudly ==========

	@Table("category")
	public static class Category {
		@Id
		public Long id;
		public String name;
	}

	public static class CategoryWithDescendants extends Category {
		@FieldName("parent_id")
		public Category parent;

		@Recursive(parentLink = "parent_id", direction = Direction.DOWN)
		public List<Category> descendants;
	}

	@Test
	public void testRecursiveCollectionsAreUnsupported() {
		PojoQuery<CategoryWithDescendants> query = PojoQuery.build(
				DbContext.forDialect(Dialect.MYSQL), CategoryWithDescendants.class);
		UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
				query::toJsonSql);
		assertEquals(true, exception.getMessage().contains("@Recursive"));
	}
}
