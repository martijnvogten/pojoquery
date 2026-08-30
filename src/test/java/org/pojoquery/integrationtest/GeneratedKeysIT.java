package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Reading a generated key back has to work the same on every dialect.
 *
 * <p>PostgreSQL's driver implements {@link java.sql.Statement#RETURN_GENERATED_KEYS} as
 * {@code RETURNING *}, so an insert that does not name its key column gets the whole
 * inserted row back and cannot locate the key by position. Since
 * {@code SchemaGenerator} emits the id column <em>last</em>, position 1 is never the key
 * there — which is why these tests deliberately put other columns in front of the id.</p>
 *
 * <p>Naming the auto-increment columns is what makes it work, so these tests use the
 * overloads that take them. The deprecated overloads that do not are what
 * {@link #testDeprecatedInsertStillInsertsTheRow()} covers.</p>
 */
public class GeneratedKeysIT {

	@Table("gk_widget")
	static class Widget {
		// Declared before the id on purpose: these become the leading columns of the
		// table, so a positional read of the generated keys would pick one of them.
		String label;
		Integer quantity;

		@Id
		Long id;
	}

	@Table("gk_pair")
	static class Pair {
		@Id
		Long left_id;
		@Id
		Long right_id;
	}

	/**
	 * The map-based insert API must return the real generated key, not whichever column
	 * the driver happened to put first.
	 */
	@Test
	public void testMapInsertReturnsGeneratedKey() {
		DataSource db = initDatabase();

		DB.withConnection(db, (Connection c) -> {
			// 4242 is a value a positional read could plausibly return as if it were the
			// key, which is exactly the failure this guards against.
			Long key = DB.insert(c, "gk_widget", Map.of("label", "first", "quantity", 4242), List.of("id"));

			Assertions.assertNotNull(key, "insert should return the generated key");

			Widget loaded = PojoQuery.build(Widget.class).findById(c, key).orElseThrow(
					() -> new AssertionError("returned key " + key + " does not identify the inserted row"));
			Assertions.assertEquals("first", loaded.label);
			Assertions.assertEquals(4242, loaded.quantity);
			Assertions.assertEquals(key, loaded.id);
			return null;
		});
	}

	/**
	 * Keys must stay distinct across inserts — a positional read can return the same
	 * wrong column value for every row and still look plausible for a single insert.
	 */
	@Test
	public void testSuccessiveInsertsReturnDistinctKeys() {
		DataSource db = initDatabase();

		DB.withConnection(db, (Connection c) -> {
			Long first = DB.insert(c, "gk_widget", Map.of("label", "a", "quantity", 7), List.of("id"));
			Long second = DB.insert(c, "gk_widget", Map.of("label", "b", "quantity", 7), List.of("id"));

			Assertions.assertNotNull(first);
			Assertions.assertNotNull(second);
			Assertions.assertNotEquals(first, second, "each insert should report its own key");

			List<Widget> all = PojoQuery.build(Widget.class).execute(c);
			Assertions.assertEquals(2, all.size());
			return null;
		});
	}

	/**
	 * A table with a composite key has no single generated key. Reporting none is the
	 * honest answer: a null id fails where it is used, whereas an arbitrary column value
	 * would be silently accepted as an id.
	 */
	@Test
	public void testCompositeKeyInsertReportsNoGeneratedKey() {
		DataSource db = initDatabase();

		DB.withConnection(db, (Connection c) -> {
			Object key = DB.insert(c, "gk_pair", Map.of("left_id", 1L, "right_id", 2L), List.of());

			Assertions.assertNull(key, "a composite-key table has no generated key to report");

			Assertions.assertEquals(1, PojoQuery.build(Pair.class).execute(c).size(),
					"the row should still have been inserted");
			return null;
		});
	}

	/**
	 * The entity insert path names its key column, so it was never affected — pin that.
	 */
	@Test
	public void testEntityInsertPopulatesId() {
		DataSource db = initDatabase();

		DB.withConnection(db, (Connection c) -> {
			Widget w = new Widget();
			w.label = "entity";
			w.quantity = 4242;
			PojoQuery.insert(c, w);

			Assertions.assertNotNull(w.id, "insert should populate the id field");
			Assertions.assertEquals("entity",
					PojoQuery.build(Widget.class).findById(c, w.id).orElseThrow().label);
			return null;
		});
	}

	/**
	 * The deprecated overload keeps its old behaviour: it reads the key by position,
	 * which is the real key on HSQLDB and MySQL and the table's first column on
	 * PostgreSQL. Only the insert itself is worth asserting across dialects - that
	 * the returned value cannot be trusted is why the overload is deprecated.
	 */
	@Test
	@SuppressWarnings("deprecation")
	public void testDeprecatedInsertStillInsertsTheRow() {
		DataSource db = initDatabase();

		DB.withConnection(db, (Connection c) -> {
			DB.insert(c, "gk_widget", Map.of("label", "legacy", "quantity", 1));

			List<Widget> all = PojoQuery.build(Widget.class).execute(c);
			Assertions.assertEquals(1, all.size());
			Assertions.assertEquals("legacy", all.get(0).label);
			return null;
		});
	}

	private DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Widget.class, Pair.class);
		return db;
	}
}
