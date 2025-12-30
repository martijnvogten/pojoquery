package org.pojoquery.integrationtest;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;
import org.pojoquery.DB;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabase;
import org.pojoquery.schema.SchemaGenerator;

public class UpsertsIT {

	@Table("product")
	static class Product {
		@Id
		Long id;
		String name;
		Integer price;
	}

	@Test
	public void testInsertOrUpdateInsertsNewRecord() {
		DataSource db = initDatabase();

		// Insert a new record using insertOrUpdate (must include id for HSQLDB MERGE)
		DB.insertOrUpdate(db, "product", Map.of(
			"id", 1,
			"name", "Widget",
			"price", 100
		));

		List<Map<String, Object>> results = DB.queryRows(db, "SELECT * FROM product WHERE id=1");
		Assert.assertEquals(1, results.size());
		Assert.assertEquals("Widget", results.get(0).get("NAME"));
		Assert.assertEquals(100, results.get(0).get("PRICE"));
	}

	@Test
	public void testInsertOrUpdateUpdatesExistingRecord() {
		DataSource db = initDatabase();

		// First insert a record
		DB.insert(db, "product", Map.of(
			"id", 1,
			"name", "Widget",
			"price", 100
		));

		// Verify it was inserted
		List<Map<String, Object>> results = DB.queryRows(db, "SELECT * FROM product WHERE id=1");
		Assert.assertEquals(1, results.size());
		Assert.assertEquals("Widget", results.get(0).get("NAME"));
		Assert.assertEquals(100, results.get(0).get("PRICE"));

		// Now use insertOrUpdate to update the existing record
		DB.insertOrUpdate(db, "product", Map.of(
			"id", 1,
			"name", "Super Widget",
			"price", 150
		));

		// Verify it was updated
		results = DB.queryRows(db, "SELECT * FROM product WHERE id=1");
		Assert.assertEquals(1, results.size());
		Assert.assertEquals("Super Widget", results.get(0).get("NAME"));
		Assert.assertEquals(150, results.get(0).get("PRICE"));

		// Make sure we still only have one record
		results = DB.queryRows(db, "SELECT COUNT(*) AS cnt FROM product");
		Assert.assertEquals(1L, results.get(0).get("CNT"));
	}

	@Test
	public void testMultipleUpserts() {
		DataSource db = initDatabase();

		// Insert first record
		DB.insertOrUpdate(db, "product", Map.of(
			"id", 1,
			"name", "Widget A",
			"price", 100
		));

		// Insert second record
		DB.insertOrUpdate(db, "product", Map.of(
			"id", 2,
			"name", "Widget B",
			"price", 200
		));

		// Update first record
		DB.insertOrUpdate(db, "product", Map.of(
			"id", 1,
			"name", "Widget A Updated",
			"price", 110
		));

		// Verify both records
		List<Map<String, Object>> results = DB.queryRows(db, "SELECT * FROM product ORDER BY id");
		Assert.assertEquals(2, results.size());
		Assert.assertEquals("Widget A Updated", results.get(0).get("NAME"));
		Assert.assertEquals(110, results.get(0).get("PRICE"));
		Assert.assertEquals("Widget B", results.get(1).get("NAME"));
		Assert.assertEquals(200, results.get(1).get("PRICE"));
	}

	private static DataSource initDatabase() {
		DataSource db = TestDatabase.dropAndRecreate();
		SchemaGenerator.createTables(db, Product.class);
		return db;
	}
}
