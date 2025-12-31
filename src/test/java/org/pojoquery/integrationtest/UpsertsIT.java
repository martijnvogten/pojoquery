package org.pojoquery.integrationtest;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

public class UpsertsIT {

	@Table("product")
	static class Product {
		@Id
		Long id;
		String name;
		Integer price;
	}
	
	@Table("inventory_item")
	static class InventoryItem {
		@Id
		Long productID;
		String sku;
		Integer quantity;
	}
	
	/** Get value from map case-insensitively (handles HSQLDB uppercase vs PostgreSQL lowercase) */
	private static Object getValue(Map<String, Object> row, String key) {
		// Try exact match first
		if (row.containsKey(key)) return row.get(key);
		// Try uppercase (HSQLDB)
		if (row.containsKey(key.toUpperCase())) return row.get(key.toUpperCase());
		// Try lowercase (PostgreSQL)
		if (row.containsKey(key.toLowerCase())) return row.get(key.toLowerCase());
		return null;
	}

	@Test
	public void testUpsertInsertsNewRecord() {
		DataSource db = initDatabase();

		// Insert a new record using upsert (must include id for HSQLDB MERGE)
		DB.upsert(db, "product", Map.of(
			"id", 1,
			"name", "Widget",
			"price", 100
		), List.of("id"));

		List<Map<String, Object>> results = DB.queryRows(db, "SELECT * FROM product WHERE id=1");
		Assert.assertEquals(1, results.size());
		Assert.assertEquals("Widget", getValue(results.get(0), "name"));
		Assert.assertEquals(100, getValue(results.get(0), "price"));
	}

	@Test
	public void testUpsertUpdatesExistingRecord() {
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
		Assert.assertEquals("Widget", getValue(results.get(0), "name"));
		Assert.assertEquals(100, getValue(results.get(0), "price"));

		// Now use upsert to update the existing record
		DB.upsert(db, "product", Map.of(
			"id", 1,
			"name", "Super Widget",
			"price", 150
		), List.of("id"));

		// Verify it was updated
		results = DB.queryRows(db, "SELECT * FROM product WHERE id=1");
		Assert.assertEquals(1, results.size());
		Assert.assertEquals("Super Widget", getValue(results.get(0), "name"));
		Assert.assertEquals(150, getValue(results.get(0), "price"));

		// Make sure we still only have one record
		results = DB.queryRows(db, "SELECT COUNT(*) AS cnt FROM product");
		Assert.assertEquals(1L, getValue(results.get(0), "cnt"));
	}

	@Test
	public void testMultipleUpserts() {
		DataSource db = initDatabase();

		// Insert first record
		DB.upsert(db, "product", Map.of(
			"id", 1,
			"name", "Widget A",
			"price", 100
		), List.of("id"));

		// Insert second record
		DB.upsert(db, "product", Map.of(
			"id", 2,
			"name", "Widget B",
			"price", 200
		), List.of("id"));

		// Update first record
		DB.upsert(db, "product", Map.of(
			"id", 1,
			"name", "Widget A Updated",
			"price", 110
		), List.of("id"));

		// Verify both records
		List<Map<String, Object>> results = DB.queryRows(db, "SELECT * FROM product ORDER BY id");
		Assert.assertEquals(2, results.size());
		Assert.assertEquals("Widget A Updated", getValue(results.get(0), "name"));
		Assert.assertEquals(110, getValue(results.get(0), "price"));
		Assert.assertEquals("Widget B", getValue(results.get(1), "name"));
		Assert.assertEquals(200, getValue(results.get(1), "price"));
	}

	@Test
	public void testUpsertWithCustomPrimaryKeyName() {
		DataSource db = initDatabaseWithInventory();

		// Insert a new record using upsert with custom primary key name
		DB.upsert(db, "inventory_item", Map.of(
			"productID", 1,
			"sku", "SKU-001",
			"quantity", 50
		), List.of("productID"));

		// Use PojoQuery to query, which handles quoting correctly across databases
		List<InventoryItem> items = PojoQuery.build(InventoryItem.class)
			.addWhere("{inventory_item.productID} = ?", 1L)
			.execute(db);
		Assert.assertEquals(1, items.size());
		Assert.assertEquals("SKU-001", items.get(0).sku);
		Assert.assertEquals(Integer.valueOf(50), items.get(0).quantity);

		// Now update the existing record
		DB.upsert(db, "inventory_item", Map.of(
			"productID", 1,
			"sku", "SKU-001-UPDATED",
			"quantity", 75
		), List.of("productID"));

		// Verify it was updated
		items = PojoQuery.build(InventoryItem.class)
			.addWhere("{inventory_item.productID} = ?", 1L)
			.execute(db);
		Assert.assertEquals(1, items.size());
		Assert.assertEquals("SKU-001-UPDATED", items.get(0).sku);
		Assert.assertEquals(Integer.valueOf(75), items.get(0).quantity);

		// Make sure we still only have one record
		List<Map<String, Object>> results = DB.queryRows(db, "SELECT COUNT(*) AS cnt FROM inventory_item");
		Assert.assertEquals(1L, getValue(results.get(0), "cnt"));
	}

	private static DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Product.class);
		return db;
	}

	private static DataSource initDatabaseWithInventory() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, InventoryItem.class);
		return db;
	}
}
