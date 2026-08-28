package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	/**
	 * {@code {this.column}} in a WHERE or ORDER BY clause resolves to the root
	 * alias, as it does in the flat query builder.
	 */
	@Test
	public void testThisAliasResolvesToRootAlias() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.MYSQL), Role.class)
				.addWhere("{this.rolename} = ?", "admin")
				.addOrderBy("{this.id} DESC")
				.toJsonSql();
		assertEquals(norm("""
				SELECT
				 JSON_OBJECT(
				  'id', `role`.`id`,
				  'rolename', `role`.`rolename`
				 ) AS `json`
				FROM `role` AS `role`
				WHERE `role`.`rolename` = ?
				ORDER BY `role`.`id` DESC
				"""), norm(sql));
	}

	// ========== @Recursive collections ==========

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

	public static class CategoryWithAncestors extends Category {
		@Recursive(parentLink = "parent_id", direction = Direction.UP)
		public List<Category> ancestors;
	}

	@Table("topic")
	public static class Topic {
		@Id
		public Long id;
		public String name;
	}

	public static class TopicWithRelated extends Topic {
		@Recursive
		@Link(linktable = "topic_related", linkfield = "topic_id", foreignlinkfield = "related_id")
		public List<Topic> related;
	}

	/**
	 * The recursive CTE is hoisted to the top-level statement and named from
	 * inside the collection's derived table, which joins the element table onto
	 * it like a junction table.
	 */
	@Test
	public void testRecursiveDescendantsHsqldb() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.HSQLDB), CategoryWithDescendants.class).toJsonSql();
		assertEquals(norm("""
				WITH RECURSIVE "descendants_cte" ("root_id", "id", "depth") AS (
				 SELECT
				  "category"."id" AS "root_id",
				  "child"."id" AS "id",
				  1 AS "depth"
				 FROM "category" AS "category"
				 INNER JOIN "category" AS "child" ON "child"."parent_id" = "category"."id"
				 UNION ALL
				 SELECT
				  "r"."root_id" AS "root_id",
				  "category"."id" AS "id",
				  "r"."depth" + 1 AS "depth"
				 FROM "category" AS "category"
				 INNER JOIN "descendants_cte" AS "r" ON "r"."id" = "category"."parent_id"
				)
				SELECT
				 JSON_OBJECT(
				  'id': "category"."id",
				  'name': "category"."name",
				  'parent': "parent"."json" FORMAT JSON,
				  'descendants': COALESCE("descendants"."json", JSON_ARRAY()) FORMAT JSON
				 ) AS "json"
				FROM "category" AS "category"
				LEFT JOIN (
				 SELECT
				  "parent"."id" AS "id",
				  JSON_OBJECT(
				   'id': "parent"."id",
				   'name': "parent"."name"
				  ) AS "json"
				 FROM "category" AS "parent"
				) AS "parent" ON "category"."parent_id" = "parent"."id"
				LEFT JOIN (
				 SELECT
				  "descendants_cte"."root_id" AS "root_id",
				  JSON_ARRAYAGG(
				   JSON_OBJECT(
				    'id': "descendants"."id",
				    'name': "descendants"."name"
				   )
				  ) AS "json"
				 FROM "descendants_cte" AS "descendants_cte"
				 INNER JOIN "category" AS "descendants" ON "descendants"."id" = "descendants_cte"."id"
				 GROUP BY "descendants_cte"."root_id"
				) AS "descendants" ON "descendants"."root_id" = "category"."id"
				"""), norm(sql));
	}

	@Test
	public void testRecursiveAncestorsMysql() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.MYSQL), CategoryWithAncestors.class).toJsonSql();
		assertEquals(norm("""
				WITH RECURSIVE `ancestors_cte` (`root_id`, `id`, `depth`) AS (
				 SELECT
				  `category`.`id` AS `root_id`,
				  `category`.`parent_id` AS `id`,
				  1 AS `depth`
				 FROM `category` AS `category`
				 WHERE `category`.`parent_id` IS NOT NULL
				 UNION ALL
				 SELECT
				  `r`.`root_id` AS `root_id`,
				  `category`.`parent_id` AS `id`,
				  `r`.`depth` + 1 AS `depth`
				 FROM `category` AS `category`
				 INNER JOIN `ancestors_cte` AS `r` ON `r`.`id` = `category`.`id`
				 WHERE `category`.`parent_id` IS NOT NULL
				)
				SELECT
				 JSON_OBJECT(
				  'id', `category`.`id`,
				  'name', `category`.`name`,
				  'ancestors', COALESCE(`ancestors`.`json`, JSON_ARRAY())
				 ) AS `json`
				FROM `category` AS `category`
				LEFT JOIN (
				 SELECT
				  `ancestors_cte`.`root_id` AS `root_id`,
				  JSON_ARRAYAGG(
				   JSON_OBJECT(
				    'id', `ancestors`.`id`,
				    'name', `ancestors`.`name`
				   )
				  ) AS `json`
				 FROM `ancestors_cte` AS `ancestors_cte`
				 INNER JOIN `category` AS `ancestors` ON `ancestors`.`id` = `ancestors_cte`.`id`
				 GROUP BY `ancestors_cte`.`root_id`
				) AS `ancestors` ON `ancestors`.`root_id` = `category`.`id`
				"""), norm(sql));
	}

	/**
	 * Junction recursion walks a link table, and a graph can reach the same
	 * element along several paths - one CTE tuple each. The array aggregate takes
	 * every row, so a second, non-recursive CTE reduces the tuples to distinct
	 * {@code (root_id, id)} pairs first.
	 */
	@Test
	public void testRecursiveJunctionDeduplicatesPostgres() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.POSTGRES), TopicWithRelated.class).toJsonSql();
		assertEquals(norm("""
				WITH RECURSIVE "related_cte" ("root_id", "id", "depth") AS (
				 SELECT
				  "topic"."id" AS "root_id",
				  "link"."related_id" AS "id",
				  1 AS "depth"
				 FROM "topic" AS "topic"
				 INNER JOIN "topic_related" AS "link" ON "link"."topic_id" = "topic"."id"
				 UNION ALL
				 SELECT
				  "r"."root_id" AS "root_id",
				  "topic_related"."related_id" AS "id",
				  "r"."depth" + 1 AS "depth"
				 FROM "topic_related" AS "topic_related"
				 INNER JOIN "related_cte" AS "r" ON "r"."id" = "topic_related"."topic_id"
				),
				"related_cte_distinct" ("root_id", "id") AS (
				 SELECT DISTINCT
				  "related_cte"."root_id",
				  "related_cte"."id"
				 FROM "related_cte" AS "related_cte"
				)
				SELECT
				 JSONB_BUILD_OBJECT(
				  'id', "topic"."id",
				  'name', "topic"."name",
				  'related', COALESCE("related"."json", '[]'::jsonb)
				 ) AS "json"
				FROM "topic" AS "topic"
				LEFT JOIN (
				 SELECT
				  "related_cte_distinct"."root_id" AS "root_id",
				  JSONB_AGG(
				   JSONB_BUILD_OBJECT(
				    'id', "related"."id",
				    'name', "related"."name"
				   )
				  ) AS "json"
				 FROM "related_cte_distinct" AS "related_cte_distinct"
				 INNER JOIN "topic" AS "related" ON "related"."id" = "related_cte_distinct"."id"
				 GROUP BY "related_cte_distinct"."root_id"
				) AS "related" ON "related"."root_id" = "topic"."id"
				"""), norm(sql));
	}
}
