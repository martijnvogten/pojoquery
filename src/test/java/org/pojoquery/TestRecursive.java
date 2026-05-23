package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Recursive;
import org.pojoquery.annotations.Recursive.Direction;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.UseDialect;

@UseDialect(Dialect.HSQLDB)
public class TestRecursive {

	@Table("category")
	static class Category {
		@Id
		Long id;
		String name;
	}

	static class CategoryWithAncestors extends Category {
		@FieldName("parent_id")
		Category parent;

		@Recursive(parentLink = "parent_id", direction = Direction.UP)
		List<Category> ancestors;
	}

	static class CategoryWithDescendants extends Category {
		@FieldName("parent_id")
		Category parent;

		@Recursive(parentLink = "parent_id", direction = Direction.DOWN)
		List<Category> descendants;
	}

	static class CategoryWithStoppingAncestors extends Category {
		@FieldName("parent_id")
		Category parent;

		// Custom override: keep the default link and add an extra predicate.
		@Recursive(parentLink = "parent_id", direction = Direction.UP,
				recursionJoinCondition = "{r.id} = {this.id} AND {r.id} <> 0")
		List<Category> ancestors;
	}

	@Test
	public void testRecursiveAncestorsSql() {
		PojoQuery<CategoryWithAncestors> pq = PojoQuery.build(CategoryWithAncestors.class);

		assertEquals(
			TestUtils.norm("""
				WITH RECURSIVE "ancestors_cte" ("root_id", "id", "depth") AS (
					SELECT "category"."id" AS "root_id",
					       "category"."parent_id" AS "id",
					       1 AS "depth"
					  FROM "category" AS "category"
					 WHERE "category"."parent_id" IS NOT NULL
					UNION ALL
					SELECT "r"."root_id" AS "root_id",
					       "category"."parent_id" AS "id",
					       "r"."depth" + 1 AS "depth"
					  FROM "category" AS "category"
					 INNER JOIN "ancestors_cte" AS "r" ON "r"."id" = "category"."id"
					 WHERE "category"."parent_id" IS NOT NULL
				)
				SELECT
				  "category"."id"     AS "category.id",
				  "category"."name"   AS "category.name",
				  "parent"."id"       AS "parent.id",
				  "parent"."name"     AS "parent.name",
				  "ancestors"."id"    AS "ancestors.id",
				  "ancestors"."name"  AS "ancestors.name"
				FROM "category" AS "category"
				LEFT JOIN "category" AS "parent"
				    ON "category"."parent_id" = "parent"."id"
				LEFT JOIN "ancestors_cte" AS "ancestors_cte"
				    ON "ancestors_cte"."root_id" = "category"."id"
				LEFT JOIN "category" AS "ancestors"
				    ON "ancestors"."id" = "ancestors_cte"."id"
				"""),
			TestUtils.norm(pq.toSql()));
	}

	@Test
	public void testRecursiveDescendantsSql() {
		PojoQuery<CategoryWithDescendants> pq = PojoQuery.build(CategoryWithDescendants.class);

		assertEquals(
			TestUtils.norm("""
				WITH RECURSIVE "descendants_cte" ("root_id", "id", "depth") AS (
					SELECT "category"."id" AS "root_id",
					       "child"."id" AS "id",
					       1 AS "depth"
					  FROM "category" AS "category"
					 INNER JOIN "category" AS "child" ON "child"."parent_id" = "category"."id"
					UNION ALL
					SELECT "r"."root_id" AS "root_id",
					       "category"."id" AS "id",
					       "r"."depth" + 1 AS "depth"
					  FROM "category" AS "category"
					 INNER JOIN "descendants_cte" AS "r" ON "r"."id" = "category"."parent_id"
				)
				SELECT
				  "category"."id"        AS "category.id",
				  "category"."name"      AS "category.name",
				  "parent"."id"          AS "parent.id",
				  "parent"."name"        AS "parent.name",
				  "descendants"."id"     AS "descendants.id",
				  "descendants"."name"   AS "descendants.name"
				FROM "category" AS "category"
				LEFT JOIN "category" AS "parent"
				    ON "category"."parent_id" = "parent"."id"
				LEFT JOIN "descendants_cte" AS "descendants_cte"
				    ON "descendants_cte"."root_id" = "category"."id"
				LEFT JOIN "category" AS "descendants"
				    ON "descendants"."id" = "descendants_cte"."id"
				"""),
			TestUtils.norm(pq.toSql()));
	}

	@Test
	public void testRecursionJoinConditionOverride() {
		String sql = TestUtils.norm(PojoQuery.build(CategoryWithStoppingAncestors.class).toSql());
		assertTrue(
			sql.contains("INNER JOIN \"ancestors_cte\" AS \"r\" ON \"r\".\"id\" = \"category\".\"id\" AND \"r\".\"id\" <> 0"),
			"expected resolved override in step JOIN, got:\n" + sql);
	}
}
