package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.pojoquery.TestUtils.norm;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.QueryBuilder;

public class TestEmbeddedNested {
	
	@Table("country")
	static class Country {
		@Id
		Long id;
		String name;
	}
	
	static class AddressWithCountry {
		String address;
		String city;
		Country country;
	}
	
	
	static class PersonalDetails {
		String phoneNumber;
		@Embedded
		AddressWithCountry home;
	}
	
	@Table("customer")
	static class Customer {
		@Id
		Long id;
		String name;
		
		@Embedded
		PersonalDetails personal;
	}
	
	@Test
	public void testEmbeddedLinkField() {
		assertEquals(
				norm("""
				SELECT
				 `customer`.`id` AS `customer.id`,
				 `customer`.`name` AS `customer.name`,
				 `customer`.`personal_phoneNumber` AS `personal.phoneNumber`,
				 `customer`.`personal_home_address` AS `personal.home.address`,
				 `customer`.`personal_home_city` AS `personal.home.city`,
				 `personal.home.country`.`id` AS `personal.home.country.id`,
				 `personal.home.country`.`name` AS `personal.home.country.name`
				FROM `customer` AS `customer`
				 LEFT JOIN `country` AS `personal.home.country` ON `customer`.`personal_home_country_id` = `personal.home.country`.`id`
				"""), 
				norm(QueryBuilder.from(Customer.class).getQuery().toStatement().getSql()));
	}
	
	private List<Map<String, Object>> RESULT_CUSTOMERS = Collections.singletonList(Map.of(
			 "customer.id", 1L,
			 "customer.name", "Jane",
			 "personal.phoneNumber", "555-123456",
			 "personal.home.address", "501, Broadway",
			 "personal.home.city", "New York D.C.",
			 "personal.home.country.id", 1L,
			 "personal.home.country.name", "United States of America"
			));
//	

	@Test
	public void testEmbeddedNestedWithLinkFieldUsingPipeline() {
		QueryBuilder<Customer> p = QueryBuilder.from(Customer.class);
		List<Customer> users = p.processRows(RESULT_CUSTOMERS);
		
		assertEquals("United States of America", users.get(0).personal.home.country.name);
	}

}
