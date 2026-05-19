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

public class RecursiveCategoriesIT {

	@Table("category")
	static class Category {
		@Id Long id;
		String name;
	}

	static class CategoryWithParent extends Category {
		@FieldName("parent_id")
		Category parent;
	}

	static class CategoryWithAncestors extends CategoryWithParent {
		@Recursive(parentLink = "parent_id", direction = Direction.UP)
		List<Category> ancestors;
	}

	static class CategoryWithDescendants extends CategoryWithParent {
		@Recursive(parentLink = "parent_id", direction = Direction.DOWN)
		List<Category> descendants;
	}

	@Test
	public void testLoadAncestors() {
		DataSource db = initCategoryDatabase();

		List<CategoryWithAncestors> result = PojoQuery.build(CategoryWithAncestors.class)
				.addWhere("{this.id} = ?", 3L)
				.execute(db);

		Assertions.assertEquals(1, result.size());
		CategoryWithAncestors leaf = result.get(0);
		Assertions.assertEquals("Phones", leaf.name);
		Assertions.assertNotNull(leaf.ancestors, "ancestors collection was not populated");
		Assertions.assertEquals(2, leaf.ancestors.size(), "expected 2 ancestors");
		Assertions.assertEquals("Electronics", leaf.ancestors.get(0).name);
		Assertions.assertEquals("Audio",       leaf.ancestors.get(1).name);
	}

	@Test
	public void testLoadDescendants() {
		DataSource db = initCategoryDatabase();

		List<CategoryWithDescendants> result = PojoQuery.build(CategoryWithDescendants.class)
				.addWhere("{this.id} = ?", 1L)
				.execute(db);

		Assertions.assertEquals(1, result.size());
		CategoryWithDescendants root = result.get(0);
		Assertions.assertEquals("Electronics", root.name);
		Assertions.assertNotNull(root.descendants, "descendants collection was not populated");
		List<String> names = root.descendants.stream().map(c -> c.name).sorted().toList();
		Assertions.assertEquals(List.of("Audio", "Phones"), names);
	}

	private DataSource initCategoryDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, CategoryWithParent.class);

		DB.withConnection(db, (Connection c) -> {
			insert(c, 1L, null, "Electronics");
			insert(c, 2L, 1L,   "Audio");
			insert(c, 3L, 2L,   "Phones");
			return null;
		});
		return db;
	}

	private static void insert(Connection c, Long id, Long parentId, String name) {
		CategoryWithParent row = new CategoryWithParent();
		row.id = id;
		row.name = name;
		if (parentId != null) {
			Category parent = new Category();
			parent.id = parentId;
			row.parent = parent;
		}
		PojoQuery.insert(c, row);
	}
}
