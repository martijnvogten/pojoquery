package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pojoquery.TestUtils.norm;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.DbContext.QuoteStyle;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.DbContextExtension;
import org.pojoquery.pipeline.QueryBuilder;
import org.pojoquery.schema.SchemaGenerator;

@ExtendWith(DbContextExtension.class)
public class TestEmbedded {

	@BeforeEach
	public void setUp() {
		DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.MYSQL));
	}
	
	@Table("country")
	static class Country {
		@Id
		Long id;
		String name;
	}
	
	static class Address {
		String address;
		String city;
	}
	
	static class AddressWithCountry extends Address {
		Country country;
	}
	
	
	@Table("user")
	static class UserWithCountry {
		@Id
		Long id;
		
		@Embedded
		AddressWithCountry home;
	}
	
	@Table("user")
	static class User {
		@Id
		Long id;
		
		@Embedded
		Address home;
	}
	
	@Table("article")
	static class ArticleDetail {
		@Id
		Long id;
		String title;
		User author;
	}
	
	private static final List<Map<String, Object>> RESULT_ARTICLE_DETAIL = List.of(Map.of(
			"article.id", 1L, 
			"article.title", "The title", 
			"author.id", 1L, 
			"author.home.address", "501, Broadway", 
			"author.home.city", "New York D.C."));
	
	@Test
	public void testBasics() {
		assertEquals(
				norm("""
					SELECT
					 `user`.`id` AS `user.id`,
					 `user`.`home_address` AS `home.address`,
					 `user`.`home_city` AS `home.city`
					 FROM `user` AS `user`
					"""), norm(QueryBuilder.from(User.class).getQuery().toStatement().getSql()));
		
		List<Map<String, Object>> result = List.of(Map.of(
				"user.id", 1L, 
				"home.address", "501, Broadway", 
				"home.city", "New York D.C."));
		
		List<User> users = QueryBuilder.from(User.class).processRows(result);
		assertEquals("501, Broadway", users.get(0).home.address);
	}
	
	@Test
	public void testUsingPipeline() {
		QueryBuilder<ArticleDetail> p = QueryBuilder.from(ArticleDetail.class);
		System.out.println(p.getQuery().toStatement().getSql());
		List<ArticleDetail> articles = p.processRows(RESULT_ARTICLE_DETAIL);
		
		assertEquals("501, Broadway", articles.get(0).author.home.address);
	}
	
	@Test
	public void testEmbeddedLinkField() {
		assertEquals(
				norm("""
				SELECT
				 `user`.`id` AS `user.id`,
				 `user`.`home_address` AS `home.address`,
				 `user`.`home_city` AS `home.city`,
				 `home.country`.`id` AS `home.country.id`,
				 `home.country`.`name` AS `home.country.name`
				FROM `user` AS `user`
				 LEFT JOIN `country` AS `home.country` ON `user`.`home_country_id` = `home.country`.`id`
				"""), 
				norm(QueryBuilder.from(UserWithCountry.class).getQuery().toStatement().getSql()));
	}
	
	private static final List<Map<String, Object>> RESULT_USER_WITH_COUNTRY = List.of(Map.of(
			"user.id", 1L, 
			"home.address", "501, Broadway", 
			"home.city", "New York D.C.",
			"home.country.id", 1L,
			"home.country.name", "United States of America" 
			));
	
	@Test
	public void testEmbeddedLinkFieldUsingPipeline() {
		QueryBuilder<UserWithCountry> p = QueryBuilder.from(UserWithCountry.class);
		List<UserWithCountry> users = p.processRows(RESULT_USER_WITH_COUNTRY);
		
		assertEquals("United States of America", users.get(0).home.country.name);
	}

	// ========== Explicit Prefix Tests ==========

	static class SimpleAddress {
		String street;
		String city;
		String zipCode;
	}

	@Table("company")
	static class CompanyWithAddress {
		@Id
		Long id;

		String name;

		@Embedded(prefix = "address_")
		SimpleAddress address;
	}

	@Table("company_multi")
	static class CompanyMultiAddress {
		@Id
		Long id;

		String name;

		@Embedded(prefix = "billing")
		SimpleAddress billingAddress;

		@Embedded(prefix = "shipping")
		SimpleAddress shippingAddress;
	}

	@Test
	public void testEmbeddedWithExplicitPrefix() {
		DbContext dbContext = DbContext.builder()
			.withQuoteStyle(QuoteStyle.NONE)
			.build();

		List<String> statements = SchemaGenerator.generateCreateTableStatements(dbContext, CompanyWithAddress.class);
		String sql = String.join("\n", statements);

		// @Embedded(prefix="address_") should add the prefix
		assertTrue(sql.contains("address_street"),
			"@Embedded should use specified prefix. Generated SQL:\n" + sql);
		assertTrue(sql.contains("address_city"),
			"@Embedded should use specified prefix. Generated SQL:\n" + sql);
		assertTrue(sql.contains("address_zipCode"),
			"@Embedded should use specified prefix. Generated SQL:\n" + sql);
	}

	@Test
	public void testEmbeddedMultipleFieldsWithPrefix() {
		DbContext dbContext = DbContext.builder()
			.withQuoteStyle(QuoteStyle.NONE)
			.build();

		List<String> statements = SchemaGenerator.generateCreateTableStatements(dbContext, CompanyMultiAddress.class);
		String sql = String.join("\n", statements);

		// Multiple @Embedded fields with different prefixes should be distinct
		assertTrue(sql.contains("billingstreet"),
			"@Embedded should use billing prefix without the underscore. Generated SQL:\n" + sql);
		assertTrue(sql.contains("billingcity"),
			"@Embedded should use billing prefix without the underscore. Generated SQL:\n" + sql);

		assertTrue(sql.contains("shippingstreet"),
			"@Embedded should use shipping prefix without the underscore. Generated SQL:\n" + sql);
		assertTrue(sql.contains("shippingcity"),
			"@Embedded should use shipping prefix without the underscore. Generated SQL:\n" + sql);
	}

	// ========== Embedded with JoinColumn Tests ==========

	@Table("region")
	static class Region {
		@Id
		Long id;
		String name;
	}

	static class AddressWithRegion {
		String street;
		String city;

		@jakarta.persistence.JoinColumn(name = "region_id")
		Region region;
	}

	@Table("store")
	static class StoreWithEmbeddedAddress {
		@Id
		Long id;

		String name;

		@Embedded(prefix = "location_")
		AddressWithRegion location;
	}

	@Test
	public void testEmbeddedWithJoinColumnInSchemaGenerator() {
		DbContext dbContext = DbContext.builder()
			.withQuoteStyle(QuoteStyle.NONE)
			.build();

		List<String> statements = SchemaGenerator.generateCreateTableStatements(dbContext, StoreWithEmbeddedAddress.class);
		String sql = String.join("\n", statements);

		// Embedded fields should have prefix
		assertTrue(sql.contains("location_street"),
			"Embedded street should have prefix. Generated SQL:\n" + sql);
		assertTrue(sql.contains("location_city"),
			"Embedded city should have prefix. Generated SQL:\n" + sql);

		// @JoinColumn inside embedded should use the specified name with prefix
		assertTrue(sql.contains("location_region_id"),
			"@JoinColumn inside embedded should use prefix + specified name. Generated SQL:\n" + sql);
	}

	@Test
	public void testEmbeddedWithJoinColumnInQueryBuilder() {
		String sql = QueryBuilder.from(StoreWithEmbeddedAddress.class).getQuery().toStatement().getSql();

		// @JoinColumn inside embedded should use the specified name with prefix
		assertTrue(sql.contains("location_region_id"),
			"@JoinColumn inside embedded should use prefix + specified name in query. Generated SQL:\n" + sql);
	}

}
