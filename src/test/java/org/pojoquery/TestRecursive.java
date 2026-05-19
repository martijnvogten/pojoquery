package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

	@Test
	public void testRecursiveAncestorsSql() {
		PojoQuery<CategoryWithAncestors> pq = PojoQuery.build(CategoryWithAncestors.class);

		assertEquals(
			TestUtils.norm("""
				WITH RECURSIVE "ancestors_cte" ("root_id", "id", "depth") AS (
					SELECT c."id", c."parent_id", 1
				      FROM "category" c
				     WHERE c."parent_id" IS NOT NULL
				    UNION ALL
				    SELECT r."root_id", c."parent_id", r."depth" + 1
				      FROM "category" c
				      JOIN "ancestors_cte" r ON r."id" = c."id"
				     WHERE c."parent_id" IS NOT NULL
				)
				SELECT
				"category"."id"        AS "category.id",
				"category"."name"      AS "category.name",
				"parent"."id"          AS "parent.id",
				"parent"."name"        AS "parent.name",
				"ancestors"."id"       AS "ancestors.id",
				"ancestors"."name"     AS "ancestors.name"
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
}
