package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.pojoquery.TestUtils.norm;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Recursive;
import org.pojoquery.annotations.Recursive.Direction;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.internal.MappingException;
import org.pojoquery.pipeline.AQTJsonDirectTransformer;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.JsonSqlQuery;

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

	/**
	 * A context that keeps collection subqueries grouped rather than correlated,
	 * so the tests below pin the SQL of the form a dialect without
	 * {@code LATERAL} gets. The correlated form is pinned by
	 * {@code testLateral*} and the two are compared in
	 * {@link #testBothCollectionStrategiesAgreeOnLayout()}.
	 */
	private static DbContext grouped(Dialect dialect) {
		return new DbContextBuilder().dialect(dialect).lateralJoins(false).build();
	}

	@Test
	public void testCollectionHsqldb() {
		String sql = PojoQuery.build(grouped(Dialect.HSQLDB), User.class).toJsonSql();
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
		String sql = PojoQuery.build(grouped(Dialect.MYSQL), User.class).toJsonSql();
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
		String sql = PojoQuery.build(grouped(Dialect.POSTGRES), User.class).toJsonSql();
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

	@Table("film")
	public static class Film {
		@Id
		@FieldName("film_id")
		public Long filmId;
		@FieldName("film_title")
		public String title;
	}

	@Table("actor")
	public static class Actor {
		@Id
		@FieldName("actor_id")
		public Long actorId;
	}

	public static class FilmWithActors extends Film {
		@Link(linktable = "film_actor")
		public List<Actor> actors;
	}

	/**
	 * A JSON query selects one document column, not one column per field, so it
	 * cannot resolve {@code {alias.fieldName}} markers from its own select
	 * labels the way the flat builder does. It has to carry the field-to-column
	 * mapping separately, or every {@link FieldName}-mapped field is emitted as
	 * an identifier under its Java name - which the database does not know.
	 *
	 * <p>Three places in one statement depend on it: the junction-to-element
	 * join of a {@code @Link} collection, the join from the derived table back
	 * to the root, and the user's WHERE clause.</p>
	 */
	@Test
	public void testFieldNameMappedColumnsResolveInJsonQuery() {
		String sql = PojoQuery.build(grouped(Dialect.MYSQL), FilmWithActors.class)
				.addWhere("{this.title} = ?", "Alien")
				.toJsonSql();
		assertEquals(norm("""
				SELECT
				 JSON_OBJECT(
				  'filmId', `film`.`film_id`,
				  'title', `film`.`film_title`,
				  'actors', COALESCE(`actors`.`json`, JSON_ARRAY())
				 ) AS `json`
				FROM `film` AS `film`
				LEFT JOIN (
				SELECT
				 `actors.film_actor`.`film_id` AS `film_id`,
				 JSON_ARRAYAGG(
				  JSON_OBJECT(
				  'actorId', `actors`.`actor_id`
				 )
				 ) AS `json`
				FROM `film_actor` AS `actors.film_actor`
				LEFT JOIN `actor` AS `actors` ON `actors.film_actor`.`actor_id` = `actors`.`actor_id`
				GROUP BY `actors.film_actor`.`film_id`
				) AS `actors` ON `actors`.`film_id` = `film`.`film_id`
				WHERE `film`.`film_title` = ?
				"""), norm(sql));
	}

	@Table("article")
	public static class Article {
		@Id
		public Long id;
		public String title;
		@Embedded
		public Author author;
		@Link(linktable = "article_tag", fetchColumn = "tag")
		public List<String> tags;
	}

	public static class Author {
		public String name;
		public String email;
	}

	@Table("sti_room")
	@DiscriminatorColumn
	@SubClasses({ StiBedRoom.class })
	public static class StiRoom {
		@Id
		public Long id;
		public Double area;
	}

	@Table("sti_room")
	public static class StiBedRoom extends StiRoom {
		public Integer numberOfBeds;
	}

	// ========== Document layout ==========

	/**
	 * The layout is a projection of the same walk that emits the SQL, so slot
	 * order matches the document's value positions. Asserted as a golden string:
	 * {@code name} is a scalar, {@code name:…} a nested document, {@code name[]:…}
	 * an array of them.
	 */
	@Test
	public void testDocumentLayoutFollowsTreeOrder() {
		assertEquals("user{id, username, roles[]:roles{id, rolename}}", layoutOf(User.class));
		assertEquals("category{id, name, parent:parent{id, name}, descendants[]:descendants{id, name}}",
				layoutOf(CategoryWithDescendants.class));
		assertEquals("topic{id, name, related[]:related{id, name}}", layoutOf(TopicWithRelated.class));
		// An embedded object nests a document under its own alias; a value
		// collection is an array of scalars, addressed by "tags.value".
		assertEquals("article{id, title, author:author{name, email}, tags[]}", layoutOf(Article.class));
		// Subclass-specific fields are conditional per row, so they are not slots
		// of the layout - only the base class fields are.
		assertEquals("room{id, area}", layoutOf(Room.class));
	}

	private static String layoutOf(Class<?> type) {
		JsonSqlQuery query = new JsonSqlQuery(DbContext.forDialect(Dialect.HSQLDB));
		return AQTJsonDirectTransformer.toSql(AQTTransformer.buildQueryTreeForType(type), query).toString();
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
	 * it like a junction table. The {@code parent} reference is a plain join, so
	 * its columns stay addressable by WHERE and ORDER BY.
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
				  'parent': CASE WHEN "parent"."id" IS NULL THEN NULL ELSE JSON_OBJECT(
				   'id': "parent"."id",
				   'name': "parent"."name"
				  ) END FORMAT JSON,
				  'descendants': COALESCE("descendants"."json", JSON_ARRAY()) FORMAT JSON
				 ) AS "json"
				FROM "category" AS "category"
				LEFT JOIN "category" AS "parent" ON "category"."parent_id" = "parent"."id"
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

	// ========== Clauses a JSON query cannot evaluate ==========

	/**
	 * A collection is aggregated in a derived table, so its element columns are
	 * not addressable from the outer query. Saying so while building the statement
	 * beats a bare missing-column error from the database at execution.
	 */
	@Test
	public void testConditionOnCollectionContentsIsRejected() {
		MappingException e = assertThrows(MappingException.class,
				() -> PojoQuery.build(DbContext.forDialect(Dialect.HSQLDB), User.class)
						.addWhere("{roles.rolename} = ?", "admin")
						.toJsonSql());
		assertEquals(true, e.getMessage().contains("'roles'"));
		assertEquals(true, e.getMessage().contains("whereExists"));

		// The array shape rejects it at the same point
		assertThrows(MappingException.class,
				() -> PojoQuery.build(DbContext.forDialect(Dialect.HSQLDB), User.class)
						.addWhere("{roles.rolename} = ?", "admin")
						.toJsonArrayStatement());
	}

	@Test
	public void testOrderByCollectionContentsIsRejected() {
		MappingException e = assertThrows(MappingException.class,
				() -> PojoQuery.build(DbContext.forDialect(Dialect.HSQLDB), User.class)
						.addOrderBy("{roles.rolename} DESC")
						.toJsonSql());
		assertEquals(true, e.getMessage().contains("'roles'"));
		assertEquals(true, e.getMessage().contains("one row per root entity"));
	}

	/** The junction table of a many-to-many is inside the derived table too. */
	@Test
	public void testConditionOnJunctionTableIsRejected() {
		assertThrows(MappingException.class,
				() -> PojoQuery.build(DbContext.forDialect(Dialect.HSQLDB), User.class)
						.addWhere("{roles.user_role.user_id} = ?", 1L)
						.toJsonSql());
	}

	/** What stays allowed: the root, a reference (a plain join), and whereExists. */
	@Test
	public void testAddressableClausesArePreserved() {
		String rootAndReference = PojoQuery.build(DbContext.forDialect(Dialect.HSQLDB), CategoryWithDescendants.class)
				.addWhere("{category.name} = ?", "Electronics")
				.addWhere("{parent.name} = ?", "Root")
				.addOrderBy("{parent.name}")
				.toJsonSql();
		assertEquals(true, rootAndReference.contains("\"parent\".\"name\" = ?"));

		// whereExists carries its own joins, so nothing it references is hidden
		String exists = PojoQuery.build(DbContext.forDialect(Dialect.HSQLDB), User.class)
				.whereExists("{roles.rolename} = ?", "admin")
				.toJsonSql();
		assertEquals(true, exists.contains(" IN (SELECT"));
	}

	// ========== Array shape ==========

	/**
	 * The same walk, assembled positionally: no property names in the payload,
	 * one slot per value, and the layout says what each position means. Non-text
	 * values are cast to text so nothing is lost to JSON's number type; the
	 * reader converts back using the layout's java types. HSQLDB
	 * cannot mark a nested document as JSON inside an array, so the nested
	 * collection is embedded as JSON text there.
	 */
	@Test
	public void testArrayShapeHsqldb() {
		PojoQuery.JsonArrayQuery query = PojoQuery.build(grouped(Dialect.HSQLDB), User.class)
				.toJsonArrayStatement();
		assertEquals("user{id, username, roles[]:roles{id, rolename}}", query.layout().toString());
		assertEquals(norm("""
				SELECT
				 JSON_ARRAY(
				  CAST("user"."id" AS VARCHAR),
				  "user"."username",
				  COALESCE("roles"."json", JSON_ARRAY())
				  NULL ON NULL
				 ) AS "json"
				FROM "user" AS "user"
				LEFT JOIN (
				 SELECT
				  "roles.user_role"."user_id" AS "user_id",
				  JSON_ARRAYAGG(
				   JSON_ARRAY(
				    CAST("roles"."id" AS VARCHAR),
				    "roles"."rolename"
				    NULL ON NULL
				   )
				  ) AS "json"
				 FROM "user_role" AS "roles.user_role"
				 LEFT JOIN "role" AS "roles" ON "roles.user_role"."role_id" = "roles"."id"
				 GROUP BY "roles.user_role"."user_id"
				) AS "roles" ON "roles"."user_id" = "user"."id"
				"""), norm(query.statement().getSql()));
	}

	@Test
	public void testArrayShapePostgres() {
		PojoQuery.JsonArrayQuery query = PojoQuery.build(grouped(Dialect.POSTGRES), User.class)
				.toJsonArrayStatement();
		assertEquals(norm("""
				SELECT
				 JSONB_BUILD_ARRAY(
				  CAST("user"."id" AS TEXT),
				  "user"."username",
				  COALESCE("roles"."json", '[]'::jsonb)
				 ) AS "json"
				FROM "user" AS "user"
				LEFT JOIN (
				 SELECT
				  "roles.user_role"."user_id" AS "user_id",
				  JSONB_AGG(
				   JSONB_BUILD_ARRAY(
				    CAST("roles"."id" AS TEXT),
				    "roles"."rolename"
				   )
				  ) AS "json"
				 FROM "user_role" AS "roles.user_role"
				 LEFT JOIN "role" AS "roles" ON "roles.user_role"."role_id" = "roles"."id"
				 GROUP BY "roles.user_role"."user_id"
				) AS "roles" ON "roles"."user_id" = "user"."id"
				"""), norm(query.statement().getSql()));
	}

	/**
	 * Subclass fields are unconditional slots in array shape: table-per-subclass
	 * columns are already NULL for other subclasses, so the hydrator identifies
	 * a row by the subclass primary key exactly as in a flat query.
	 */
	@Test
	public void testArrayShapeTablePerSubclass() {
		PojoQuery.JsonArrayQuery query = PojoQuery.build(DbContext.forDialect(Dialect.HSQLDB), Room.class)
				.toJsonArrayStatement();
		assertEquals("room{id, area, room.bedroom.id, room.bedroom.numberOfBeds}", query.layout().toString());
		assertEquals(norm("""
				SELECT
				 JSON_ARRAY(
				  CAST("room"."id" AS VARCHAR),
				  CAST("room"."area" AS VARCHAR),
				  CAST("room.bedroom"."id" AS VARCHAR),
				  CAST("room.bedroom"."numberOfBeds" AS VARCHAR)
				  NULL ON NULL
				 ) AS "json"
				FROM "room" AS "room"
				LEFT JOIN "bedroom" AS "room.bedroom" ON "room.bedroom"."id" = "room"."id"
				"""), norm(query.statement().getSql()));
	}

	/**
	 * Single-table inheritance carries a discriminator slot rather than a
	 * {@code _type} property, under the row key the hydrator reads.
	 */
	@Test
	public void testArrayShapeSingleTableInheritance() {
		PojoQuery.JsonArrayQuery query = PojoQuery.build(DbContext.forDialect(Dialect.HSQLDB), StiRoom.class)
				.toJsonArrayStatement();
		// No subclass primary key slot: single-table subclasses share the base
		// table's key, and the hydrator keys off the discriminator - as in the flat query.
		assertEquals("sti_room{id, area, sti_room.stibedroom._discriminator, sti_room.stibedroom.numberOfBeds}",
				query.layout().toString());
	}

	/** Object shape is unaffected: still named properties, still a _type. */
	@Test
	public void testObjectShapeStillNamesProperties() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.HSQLDB), User.class).toJsonSql();
		assertEquals(true, sql.contains("JSON_OBJECT("));
		assertEquals(false, sql.contains("JSON_ARRAY(\n"));
	}

	// ========== Correlated collections (LATERAL) ==========

	@Table("screening")
	public static class Screening {
		@Id
		public Long id;
		public String room;
	}

	public static class FilmWithScreenings extends Film {
		public List<Screening> screenings;
	}

	/**
	 * The default where the dialect has {@code LATERAL}: the collection subquery
	 * is correlated rather than grouped, so it aggregates only the rows of the
	 * parent at hand. The junction's foreign key is no longer selected and no
	 * longer grouped by, and the condition that was the join's ON clause becomes
	 * the subquery's WHERE - naming the junction table, which is where the
	 * parent's key lives inside the subquery.
	 */
	@Test
	public void testLateralCollectionMysql() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.MYSQL), User.class).toJsonSql();
		assertEquals(norm("""
				SELECT
				 JSON_OBJECT(
				  'id', `user`.`id`,
				  'username', `user`.`username`,
				  'roles', COALESCE(`roles`.`json`, JSON_ARRAY())
				 ) AS `json`
				FROM `user` AS `user`
				LEFT JOIN LATERAL (
				 SELECT
				  JSON_ARRAYAGG(
				   JSON_OBJECT(
				    'id', `roles`.`id`,
				    'rolename', `roles`.`rolename`
				   )
				  ) AS `json`
				 FROM `user_role` AS `roles.user_role`
				 LEFT JOIN `role` AS `roles` ON `roles.user_role`.`role_id` = `roles`.`id`
				 WHERE `roles.user_role`.`user_id` = `user`.`id`
				 GROUP BY `roles.user_role`.`user_id`
				) AS `roles` ON TRUE
				"""), norm(sql));
	}

	/** The array shape correlates identically: document shape and join strategy are independent. */
	@Test
	public void testLateralArrayShapePostgres() {
		PojoQuery.JsonArrayQuery query = PojoQuery.build(DbContext.forDialect(Dialect.POSTGRES), User.class)
				.toJsonArrayStatement();
		assertEquals("user{id, username, roles[]:roles{id, rolename}}", query.layout().toString());
		assertEquals(norm("""
				SELECT
				 JSONB_BUILD_ARRAY(
				  CAST("user"."id" AS TEXT),
				  "user"."username",
				  COALESCE("roles"."json", '[]'::jsonb)
				 ) AS "json"
				FROM "user" AS "user"
				LEFT JOIN LATERAL (
				 SELECT
				  JSONB_AGG(
				   JSONB_BUILD_ARRAY(
				    CAST("roles"."id" AS TEXT),
				    "roles"."rolename"
				   )
				  ) AS "json"
				 FROM "user_role" AS "roles.user_role"
				 LEFT JOIN "role" AS "roles" ON "roles.user_role"."role_id" = "roles"."id"
				 WHERE "roles.user_role"."user_id" = "user"."id"
				 GROUP BY "roles.user_role"."user_id"
				) AS "roles" ON TRUE
				"""), norm(query.statement().getSql()));
	}

	/**
	 * One-to-many needs no second alias: the subquery's own table is the child,
	 * so the join condition serves unchanged as the correlation. Both sides of it
	 * are {@link FieldName}-mapped here, which only resolves because the primary
	 * key column name is derived from the annotation.
	 */
	@Test
	public void testLateralOneToManyHsqldb() {
		String sql = PojoQuery.build(DbContext.forDialect(Dialect.HSQLDB), FilmWithScreenings.class).toJsonSql();
		assertEquals(norm("""
				SELECT
				 JSON_OBJECT(
				  'filmId': "film"."film_id",
				  'title': "film"."film_title",
				  'screenings': COALESCE("screenings"."json", JSON_ARRAY()) FORMAT JSON
				 ) AS "json"
				FROM "film" AS "film"
				LEFT JOIN LATERAL (
				 SELECT
				  JSON_ARRAYAGG(
				   JSON_OBJECT(
				    'id': "screenings"."id",
				    'room': "screenings"."room"
				   )
				  ) AS "json"
				 FROM "screening" AS "screenings"
				 WHERE "screenings"."film_id" = "film"."film_id"
				 GROUP BY "screenings"."film_id"
				) AS "screenings" ON TRUE
				"""), norm(sql));
	}

	/**
	 * The two strategies differ only in how the database is asked to produce a
	 * collection, never in what the document contains. If that ever stopped
	 * holding, the reader would have to know which strategy built its input.
	 */
	@Test
	public void testBothCollectionStrategiesAgreeOnLayout() {
		for (Dialect dialect : List.of(Dialect.MYSQL, Dialect.POSTGRES, Dialect.HSQLDB)) {
			String correlated = PojoQuery
					.build(new DbContextBuilder().dialect(dialect).lateralJoins(true).build(), User.class)
					.toJsonArrayStatement().layout().toString();
			String grouped = PojoQuery.build(grouped(dialect), User.class)
					.toJsonArrayStatement().layout().toString();
			assertEquals(grouped, correlated, dialect + " layout must not depend on the join strategy");
		}
	}

	/** A dialect without LATERAL keeps the grouped form. */
	@Test
	public void testLateralCanBeTurnedOff() {
		assertEquals(true, PojoQuery.build(DbContext.forDialect(Dialect.MYSQL), User.class)
				.toJsonSql().contains("LEFT JOIN LATERAL ("));
		assertEquals(false, PojoQuery.build(grouped(Dialect.MYSQL), User.class)
				.toJsonSql().contains("LATERAL"));
	}
}
