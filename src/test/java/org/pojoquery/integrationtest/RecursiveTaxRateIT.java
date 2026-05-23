package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Recursive;
import org.pojoquery.annotations.Recursive.Direction;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Demonstrates using {@link Recursive#recursionJoinCondition()} to short-circuit
 * the recursive CTE step. The category tree carries an optional {@code tax_rate}
 * column; the override stops climbing once an ancestor with a non-null tax rate
 * has been visited, so the recursion yields the chain up to (and including) the
 * nearest taxed ancestor and nothing above it.
 */
public class RecursiveTaxRateIT {

	@Table("category")
	static class Category {
		@Id Long id;
		String name;
		Double tax_rate;
	}

	static class CategoryWithParent extends Category {
		@FieldName("parent_id")
		Category parent;
	}

	static class CategoryWithTaxedAncestors extends CategoryWithParent {
		// Continue climbing only while the most recently added ancestor's tax_rate
		// is still NULL. The first ancestor with a non-null tax_rate is added,
		// then the recursion stops.
		@Recursive(
			parentLink = "parent_id",
			direction = Direction.UP,
			recursionJoinCondition = "{r.id} = {this.id} AND {this.tax_rate} IS NULL"
		)
		List<Category> ancestors;
	}

	@Test
	public void testRecursionStopsAtNearestTaxedAncestor() {
		DataSource db = initDatabase();
		Long leafId = leaf.id;

		List<CategoryWithTaxedAncestors> result = PojoQuery.build(CategoryWithTaxedAncestors.class)
				.addWhere("{this.id} = ?", leafId)
				.execute(db);

		Assertions.assertEquals(1, result.size());
		CategoryWithTaxedAncestors leaf = result.get(0);
		Assertions.assertEquals("Leaf", leaf.name);
		Assertions.assertNotNull(leaf.ancestors, "ancestors collection was not populated");

		// Tree: Top(null) -> GreatGrand(21.0) -> Grand(null) -> Mid(null) -> Leaf(null)
		// Override should include Mid, Grand, GreatGrand (and stop there, NOT include Top).
		List<String> names = leaf.ancestors.stream().map(c -> c.name).sorted().toList();
		Assertions.assertEquals(
			List.of("GreatGrand", "Grand", "Mid").stream().sorted().toList(),
			names,
			"Expected recursion to stop at the nearest taxed ancestor, not climb past it");

		// The nearest taxed ancestor is GreatGrand with tax_rate 21.0.
		Category nearestTaxed = leaf.ancestors.stream()
				.filter(c -> c.tax_rate != null)
				.findFirst()
				.orElseThrow(() -> new AssertionError("No taxed ancestor found"));
		Assertions.assertEquals("GreatGrand", nearestTaxed.name);
		Assertions.assertEquals(21.0, nearestTaxed.tax_rate, 0.0001);
	}

	private Category leaf;

	private DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, CategoryWithParent.class);

		DB.withConnection(db, (Connection c) -> {
			Category top        = insert(c, null, "Top",        null);
			Category greatGrand = insert(c, top,  "GreatGrand", 21.0);
			Category grand      = insert(c, greatGrand, "Grand", null);
			Category mid        = insert(c, grand, "Mid",       null);
			leaf = insert(c, mid, "Leaf", null);
			return null;
		});
		return db;
	}

	private static Category insert(Connection c, Category parent, String name, Double taxRate) {
		CategoryWithParent row = new CategoryWithParent();
		row.name = name;
		row.tax_rate = taxRate;
		row.parent = parent;
		PojoQuery.insert(c, row);
		return row;
	}
}
