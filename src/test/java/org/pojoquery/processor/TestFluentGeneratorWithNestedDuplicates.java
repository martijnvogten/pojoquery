package org.pojoquery.processor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;

public class TestFluentGeneratorWithNestedDuplicates {

	/**
	 * Domain model showing the duplicate class name problem.
	 * Store appears at multiple depths in the object graph:
	 * - Root.store (depth 1)
	 * - Root.address.store (depth 2)
	 * - Root.payments.customer.store (depth 3)
	 * - Root.payments.customer.address.store (depth 4)
	 * 
	 * Address also appears multiple times:
	 * - Root.address (depth 1)
	 * - Root.payments.customer.address (depth 2)
	 * - Root.payments.customer.address (depth 3, indirectly)
	 */

	@Table("store")
	public static class Store {
		@Id
		Long storeId;
		String storeName;
	}

	@Table("address")
	public static class Address {
		@Id
		Long addressId;
		String street;
		String city;
		Store store; // Address contains Store
	}

	@Table("customer")
	public static class Customer {
		@Id
		Integer customerId;
		String customerName;
		Store store;       // Store appears here (1st occurrence)
		Address address;   // Address appears here (1st occurrence, which contains Store)
	}

	@Table("payment")
	public static class Payment {
		@Id
		Integer paymentId;
		Double amount;
		Customer customer; // Customer with its Store + Address (2nd and 3rd occurrences)
	}

	@GenerateQuery
	@Table("root_customer")
	public static class Root extends Customer {
		// Inherits: customerId, store, address
		List<Payment> payments; // Contains Customer.store + Customer.address again
	}

	@Test
	public void testGeneratorWithNestedDuplicates() throws Exception {
		// Build the query tree for Root
		RootNode tree = AQTTransformer.buildQueryTreeForType(Root.class);

		// Generate the fluent query code
		FluentAQTCodeGenerator generator = new FluentAQTCodeGenerator();
		StringWriter output = new StringWriter();

		String packageName = "org.pojoquery.processor.generated";
		String entityName = "Root";
		String queryClassName = "RootQuery";

		generator.generate(tree, packageName, entityName, queryClassName, output);

		String generatedCode = output.toString();
		System.out.println("\n========== GENERATED FLUENT QUERY CODE ==========\n");
		System.out.println(generatedCode);
		System.out.println("\n========== END GENERATED CODE ==========\n");

		// Verify basic structure is present
		assertTrue(generatedCode.contains("public class RootQuery"), "Should declare RootQuery class");
		assertTrue(generatedCode.contains("public class Where"), "Should have Where inner class");
		assertTrue(generatedCode.contains("public class OrderBy"), "Should have OrderBy inner class");
		assertTrue(generatedCode.contains("public class GroupBy"), "Should have GroupBy inner class");

		// Verify that entity references are generated
		assertTrue(generatedCode.contains("StaticStore"), "Should reference Store");
		assertTrue(generatedCode.contains("StaticAddress"), "Should reference Address");
		assertTrue(generatedCode.contains("StaticCustomer"), "Should reference Customer");
		assertTrue(generatedCode.contains("StaticPayment"), "Should reference Payment");

		// Root path should include nested type declarations instead of flat duplicates.
		assertContainsInOrder(generatedCode,
				"public class StaticPayments {",
				"public class StaticCustomer {",
				"public class StaticStore {",
				"public class StaticAddress {");

		assertContainsInOrder(generatedCode,
				"public class WherePayments {",
				"public class WhereCustomer {",
				"public class WhereAddress {",
				"public class WhereStore {");

		assertContainsInOrder(generatedCode,
				"public class OrderByPayments {",
				"public class OrderByCustomer {",
				"public class OrderByAddress {",
				"public class OrderByStore {");

		assertContainsInOrder(generatedCode,
				"public class GroupByPayments {",
				"public class GroupByCustomer {",
				"public class GroupByAddress {",
				"public class GroupByStore {");

		// There are multiple Store occurrences by design, but each should come from scoped nesting.
		assertTrue(countOccurrences(generatedCode, "public class StaticStore") >= 2,
				"Expected multiple StaticStore declarations scoped by nested paths");
	}

	@Test
	@SuppressWarnings("unused")
	public void testGeneratorWithSimpleStructure() throws Exception {
		// Test with a simpler structure to verify basic functionality
		@Table("author")
		class Author {
			@Id
			Long id;
			String name;
		}

		@GenerateQuery
		@Table("simple_book")
		class SimpleBook {
			@Id
			Long id;
			String title;
			Author author;
		}

		RootNode tree = AQTTransformer.buildQueryTreeForType(SimpleBook.class);

		FluentAQTCodeGenerator generator = new FluentAQTCodeGenerator();
		StringWriter output = new StringWriter();

		generator.generate(tree, "org.pojoquery.processor.generated", "SimpleBook", "SimpleBookQuery",
				output);

		String generatedCode = output.toString();
		System.out.println("\n========== SIMPLE STRUCTURE TEST ==========\n");
		System.out.println(generatedCode);
		System.out.println("\n========== END SIMPLE TEST ==========\n");

		// Verify the simple structure works
		assertTrue(generatedCode.contains("public class SimpleBookQuery"), "Should declare SimpleBookQuery");
		assertTrue(generatedCode.contains("StaticAuthor"), "Should reference Author as StaticAuthor");
		assertTrue(generatedCode.contains("WhereAuthor"), "Should have WhereAuthor");
		assertTrue(generatedCode.contains("OrderByAuthor"), "Should have OrderByAuthor");
		assertTrue(generatedCode.contains("GroupByAuthor"), "Should have GroupByAuthor");
	}

	private int countOccurrences(String text, String needle) {
		int count = 0;
		int index = 0;
		while ((index = text.indexOf(needle, index)) != -1) {
			count++;
			index += needle.length();
		}
		return count;
	}

	private void assertContainsInOrder(String text, String... parts) {
		int start = 0;
		for (String part : parts) {
			int index = text.indexOf(part, start);
			assertTrue(index >= 0, "Missing snippet in order: " + part);
			start = index + part.length();
		}
	}
}
