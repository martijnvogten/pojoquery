package org.pojoquery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.pojoquery.TestUtils.norm;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabase;
import org.pojoquery.pipeline.QueryBuilder;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.schema.SchemaGenerator;

public class TestDbContext {

	// Save and restore default context to avoid polluting global state for other tests
	private static DbContext originalDefault;
	
	@BeforeClass
	public static void saveDefaultContext() {
		originalDefault = DbContext.getDefault();
	}
	
	@AfterClass
	public static void restoreDefaultContext() {
		DbContext.setDefault(originalDefault);
	}
	
	@Table(value="article", schema="schema1")
	static class Article {
		@Id
		public Long id;
		public String title;
	}
	
	@Table("product")
	static class Product {
		@Id
		public Long id;
		public String name;
		public Integer price;
	}
	
	@Test
	public void testQuoting() {
		{
			// Explicitly use MYSQL context for this test (don't rely on global default)
			DbContext mysqlContext = DbContext.forDialect(Dialect.MYSQL);
			SqlQuery<?> query = QueryBuilder.from(mysqlContext, Article.class).getQuery();
			
			assertEquals(
					norm("""
						SELECT
						 `article`.`id` AS `article.id`,
						 `article`.`title` AS `article.title`
						FROM `schema1`.`article` AS `article`
						"""),
					norm(query.toStatement().getSql()));
		}
		
		{
			DbContext dbContext = DbContext.builder()
				.dialect(Dialect.MYSQL)
				.quoteObjectNames(false)
				.build();
			SqlQuery<?> query = QueryBuilder.from(dbContext, Article.class).getQuery();
			
			assertEquals(
					norm("""
						SELECT
						 `article`.id AS `article.id`,
						 `article`.title AS `article.title`
						FROM schema1.article AS `article`
						"""),
					norm(query.toStatement().getSql()));
		}
	}
	
	@Test
	public void testDbContextAwareInsert() {
		// Test that explicit DbContext is passed through insert operations
		DbContext hsqldbContext = DbContext.forDialect(Dialect.HSQLDB);
		DataSource db = TestDatabase.dropAndRecreate();
		SchemaGenerator.createTables(db, Product.class);
		
		// Insert with explicit context
		Product p = new Product();
		p.name = "Test Product";
		p.price = 100;
		Long id = PojoQuery.insert(hsqldbContext, db, p);
		
		assertNotNull("Insert should return generated ID", id);
		assertEquals("ID should be set on object", id, p.id);
	}
	
	@Test
	public void testDbContextAwareUpdate() {
		DbContext hsqldbContext = DbContext.forDialect(Dialect.HSQLDB);
		DataSource db = TestDatabase.dropAndRecreate();
		SchemaGenerator.createTables(db, Product.class);
		
		// Insert first
		Product p = new Product();
		p.name = "Original Name";
		p.price = 50;
		p.id = PojoQuery.insert(hsqldbContext, db, p);
		
		// Update with explicit context
		p.name = "Updated Name";
		p.price = 75;
		int affected = PojoQuery.update(hsqldbContext, db, p);
		
		assertEquals("Should affect 1 row", 1, affected);
		
		// Verify the update via query
		Product loaded = PojoQuery.build(hsqldbContext, Product.class)
				.findById(db, p.id);
		assertEquals("Updated Name", loaded.name);
		assertEquals(Integer.valueOf(75), loaded.price);
	}
	
	@Test
	public void testDbContextAwareDelete() {
		DbContext hsqldbContext = DbContext.forDialect(Dialect.HSQLDB);
		DataSource db = TestDatabase.dropAndRecreate();
		SchemaGenerator.createTables(db, Product.class);
		
		// Insert first
		Product p = new Product();
		p.name = "To Delete";
		p.price = 99;
		p.id = PojoQuery.insert(hsqldbContext, db, p);
		
		// Delete with explicit context
		PojoQuery.delete(hsqldbContext, db, p);
		
		// Verify deletion
		Product loaded = PojoQuery.build(hsqldbContext, Product.class)
				.findById(db, p.id);
		assertEquals("Should return null for deleted entity", null, loaded);
	}
	
	@Test 
	public void testDbContextAwareDbInsert() {
		DbContext hsqldbContext = DbContext.forDialect(Dialect.HSQLDB);
		DataSource db = TestDatabase.dropAndRecreate();
		SchemaGenerator.createTables(db, Product.class);
		
		// Use low-level DB.insert with explicit context
		Map<String, Object> values = new HashMap<>();
		values.put("name", "Low Level Product");
		values.put("price", 200);
		Long id = DB.insert(hsqldbContext, db, "product", values);
		
		assertNotNull("Insert should return generated ID", id);
	}
	
	@Test
	public void testDbContextAwareDbUpdate() {
		DbContext hsqldbContext = DbContext.forDialect(Dialect.HSQLDB);
		DataSource db = TestDatabase.dropAndRecreate();
		SchemaGenerator.createTables(db, Product.class);
		
		// Insert first
		Map<String, Object> values = new HashMap<>();
		values.put("name", "Original");
		values.put("price", 100);
		Long id = DB.insert(hsqldbContext, db, "product", values);
		
		// Update with explicit context
		Map<String, Object> updateValues = new HashMap<>();
		updateValues.put("name", "Changed");
		updateValues.put("price", 150);
		Map<String, Object> ids = new HashMap<>();
		ids.put("id", id);
		
		int affected = DB.update(hsqldbContext, db, "product", updateValues, ids);
		assertEquals("Should affect 1 row", 1, affected);
	}
}
