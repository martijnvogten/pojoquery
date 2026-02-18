package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pojoquery.TestUtils.norm;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.DbContextExtension;
import org.pojoquery.integrationtest.db.TestDatabase;
import org.pojoquery.pipeline.QueryBuilder;
import org.pojoquery.pipeline.SqlQuery;
import org.pojoquery.schema.SchemaGenerator;

@ExtendWith(DbContextExtension.class)
public class TestDbContext {

	@BeforeEach
	public void setUpHsqldbContext() {
		// Ensure HSQLDB context is set for SchemaGenerator.createTables calls
		TestDatabase.initDbContext();
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
		
		DB.withConnection(db, c -> {
			// Insert with explicit context
			Product p = new Product();
			p.name = "Test Product";
			p.price = 100;
			Long id = PojoQuery.insert(hsqldbContext, c, p);
			
			assertNotNull(id, "Insert should return generated ID");
			assertEquals(id, p.id, "ID should be set on object");
		});
	}
	
	@Test
	public void testDbContextAwareUpdate() {
		DbContext hsqldbContext = DbContext.forDialect(Dialect.HSQLDB);
		DataSource db = TestDatabase.dropAndRecreate();
		SchemaGenerator.createTables(db, Product.class);
		
		DB.withConnection(db, c -> {
			// Insert first
			Product p = new Product();
			p.name = "Original Name";
			p.price = 50;
			PojoQuery.insert(hsqldbContext, c, p);
			
			// Update with explicit context
			p.name = "Updated Name";
			p.price = 75;
			int affected = PojoQuery.update(hsqldbContext, c, p);
			
			assertEquals(1, affected, "Should affect 1 row");
			
			// Verify the update via query
			Product loaded = PojoQuery.build(hsqldbContext, Product.class)
					.findById(c, p.id).orElseThrow();
			assertEquals("Updated Name", loaded.name);
			assertEquals(Integer.valueOf(75), loaded.price);
		});
	}
	
	@Test
	public void testDbContextAwareDelete() {
		DbContext hsqldbContext = DbContext.forDialect(Dialect.HSQLDB);
		DataSource db = TestDatabase.dropAndRecreate();
		SchemaGenerator.createTables(db, Product.class);
		
		DB.withConnection(db, c -> {
			// Insert first
			Product p = new Product();
			p.name = "To Delete";
			p.price = 99;
			PojoQuery.insert(hsqldbContext, c, p);
			
			// Delete with explicit context
			PojoQuery.delete(hsqldbContext, c, p);
			
			// Verify deletion
			Optional<Product> loaded = PojoQuery.build(hsqldbContext, Product.class)
					.findById(c, p.id);
			assertTrue(loaded.isEmpty(), "Should return empty for deleted entity");
		});
	}
	
	@Test 
	public void testDbContextAwareDbInsert() {
		DbContext hsqldbContext = DbContext.forDialect(Dialect.HSQLDB);
		DataSource db = TestDatabase.dropAndRecreate();
		SchemaGenerator.createTables(db, Product.class);
		
		DB.withConnection(db, c -> {
			// Use low-level DB.insert with explicit context
			Map<String, Object> values = new HashMap<>();
			values.put("name", "Low Level Product");
			values.put("price", 200);
			Long id = DB.insert(hsqldbContext, c, "product", values);
			
			assertNotNull(id, "Insert should return generated ID");
		});
	}
	
	@Test
	public void testDbContextAwareDbUpdate() {
		DbContext hsqldbContext = DbContext.forDialect(Dialect.HSQLDB);
		DataSource db = TestDatabase.dropAndRecreate();
		SchemaGenerator.createTables(db, Product.class);
		
		DB.withConnection(db, c -> {
			// Insert first
			Map<String, Object> values = new HashMap<>();
			values.put("name", "Original");
			values.put("price", 100);
			Long id = DB.insert(hsqldbContext, c, "product", values);
			
			// Update with explicit context
			Map<String, Object> updateValues = new HashMap<>();
			updateValues.put("name", "Changed");
			updateValues.put("price", 150);
			Map<String, Object> ids = new HashMap<>();
			ids.put("id", id);
			
			int affected = DB.update(hsqldbContext, c, "product", updateValues, ids);
			assertEquals(1, affected, "Should affect 1 row");
		});
	}
}
