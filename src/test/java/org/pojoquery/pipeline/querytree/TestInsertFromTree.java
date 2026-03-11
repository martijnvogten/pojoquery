package org.pojoquery.pipeline.querytree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.pojoquery.CascadingUpdater;
import org.pojoquery.CascadingUpdater.DatabaseOperations;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;

public class TestInsertFromTree {

	@Table("person")
	static class Person {
		@Id
		Long id;
		String name;
	}

	@Table("article")
	static class Article {
		@Id
		Long id;
		String title;
		Person author;
	}

	@Table("order")
	static class Order {
		@Id
		Long id;
		String orderNumber;
		List<LineItem> items;
	}

	@Table("line_item")
	static class LineItem {
		@Id
		Long id;
		String product;
		Integer quantity;
	}

	// === Many-to-many models ===
	@Table("tag")
	static class Tag {
		@Id
		Long id;
		String name;
	}

	@Table("post")
	static class Post {
		@Id
		Long id;
		String title;
		@Link(linktable = "post_tag")
		List<Tag> tags;
	}

	// === Inheritance models ===
	@Table("vehicle")
	@SubClasses({Car.class, Motorcycle.class})
	static class Vehicle {
		@Id
		Long id;
		String brand;
	}

	@Table("car")
	static class Car extends Vehicle {
		Integer numberOfDoors;
	}

	@Table("motorcycle")
	static class Motorcycle extends Vehicle {
		Boolean hasSidecar;
	}

	// === Models for testing different relationship types ===
	@Table("company")
	static class Company {
		@Id
		Long id;
		String name;
		List<Employee> employees;  // one-to-many
	}

	@Table("employee")
	static class Employee {
		@Id
		Long id;
		String firstName;
		Department department;  // many-to-one
	}

	@Table("department")
	static class Department {
		@Id
		Long id;
		String name;
	}

	/** Recording implementation for testing */
	static class RecordingDb implements DatabaseOperations {
		final List<String> operations = new ArrayList<>();
		final AtomicLong idGenerator = new AtomicLong(100);

		@Override
		public <PK> PK insert(String table, String schema, Map<String, Object> values) {
			String fullTable = schema != null ? schema + "." + table : table;
			Long generatedId = idGenerator.getAndIncrement();
			operations.add(String.format("INSERT INTO %s %s → id=%d", fullTable, values, generatedId));
			return (PK) generatedId;
		}

		@Override
		public int update(String table, String schema, Map<String, Object> values, Map<String, Object> where) {
			String fullTable = schema != null ? schema + "." + table : table;
			operations.add(String.format("UPDATE %s SET %s WHERE %s", fullTable, values, where));
			return 1; // Simulate one row updated
		}

		@Override
		public int delete(String table, String schema, Map<String, Object> where) {
			String fullTable = schema != null ? schema + "." + table : table;
			operations.add(String.format("DELETE FROM %s WHERE %s", fullTable, where));
			return 1; // Simulate one row deleted
		}

		@Override
		public void syncLinkTable(String table, String schema, 
				String ownerFkColumn, Object ownerId,
				String targetFkColumn, List<Object> targetIds) {
			String fullTable = schema != null ? schema + "." + table : table;
			operations.add(String.format("DELETE FROM %s WHERE %s=%s", fullTable, ownerFkColumn, ownerId));
			for (Object targetId : targetIds) {
				operations.add(String.format("INSERT INTO %s {%s=%s, %s=%s}", 
					fullTable, ownerFkColumn, ownerId, targetFkColumn, targetId));
			}
		}
	}

	@Test
	public void testInsertFromTree() {
		Article article = new Article();
		article.title = "My Article";
		article.author = new Person();
		article.author.name = "Alice";

		QueryTree tree = QueryTreeBuilder.from(Article.class);
		System.out.println("Query Tree:" + tree);

		RecordingDb db = new RecordingDb();
		CascadingUpdater.insert(tree, article, db);
		
		System.out.println("\nOperations:");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	@Test
	public void testUpdateFromTree() {
		Article article = new Article();
		article.id = 1L;
		article.title = "Updated Title";
		article.author = new Person();
		article.author.id = 2L;
		article.author.name = "Bob";

		QueryTree tree = QueryTreeBuilder.from(Article.class);
		System.out.println("Query Tree:" + tree);

		RecordingDb db = new RecordingDb();
		CascadingUpdater.update(tree, article, db);
		
		System.out.println("\nOperations:");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	@Test
	public void testInsertWithCollection() {
		Order order = new Order();
		order.orderNumber = "ORD-001";
		order.items = new ArrayList<>();
		order.items.add(createLineItem("Widget", 5));
		order.items.add(createLineItem("Gadget", 3));

		QueryTree tree = QueryTreeBuilder.from(Order.class);
		System.out.println("Query Tree:" + tree);

		RecordingDb db = new RecordingDb();
		CascadingUpdater.insert(tree, order, db);
		
		System.out.println("\nOperations:");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	@Test
	public void testUpdateWithCollection() {
		Order order = new Order();
		order.id = 1L;
		order.orderNumber = "ORD-001-UPDATED";
		order.items = new ArrayList<>();
		order.items.add(createLineItem("New Widget", 10));
		order.items.add(createLineItem("New Gadget", 7));

		QueryTree tree = QueryTreeBuilder.from(Order.class);
		System.out.println("Query Tree:" + tree);

		RecordingDb db = new RecordingDb();
		CascadingUpdater.update(tree, order, db);
		
		System.out.println("\nOperations:");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	// === Many-to-Many Tests (LEFT JOIN through link table) ===

	@Test
	public void testInsertWithManyToMany() {
		Post post = new Post();
		post.title = "My Post";
		post.tags = new ArrayList<>();
		post.tags.add(createTag("Java"));
		post.tags.add(createTag("Testing"));

		QueryTree tree = QueryTreeBuilder.from(Post.class);
		System.out.println("Query Tree (Many-to-Many):" + tree);

		RecordingDb db = new RecordingDb();
		CascadingUpdater.insert(tree, post, db);

		System.out.println("\nOperations (Many-to-Many Insert):");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	@Test
	public void testUpdateWithManyToMany() {
		Post post = new Post();
		post.id = 1L;
		post.title = "Updated Post";
		post.tags = new ArrayList<>();
		post.tags.add(createExistingTag(10L, "Existing Tag"));
		post.tags.add(createExistingTag(20L, "Another Tag"));

		QueryTree tree = QueryTreeBuilder.from(Post.class);
		System.out.println("Query Tree (Many-to-Many):" + tree);

		RecordingDb db = new RecordingDb();
		CascadingUpdater.update(tree, post, db);

		System.out.println("\nOperations (Many-to-Many Update):");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	// === Inheritance Tests (INNER JOIN for subclass to superclass) ===

	@Test
	public void testInsertWithInheritance() {
		Car car = new Car();
		car.brand = "Toyota";
		car.numberOfDoors = 4;

		QueryTree tree = QueryTreeBuilder.from(Car.class);
		System.out.println("Query Tree (Inheritance - Car):" + tree);

		RecordingDb db = new RecordingDb();
		CascadingUpdater.insert(tree, car, db);

		System.out.println("\nOperations (Inheritance Insert):");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	@Test
	public void testUpdateWithInheritance() {
		Car car = new Car();
		car.id = 1L;
		car.brand = "Honda";
		car.numberOfDoors = 2;

		QueryTree tree = QueryTreeBuilder.from(Car.class);
		System.out.println("Query Tree (Inheritance - Car):" + tree);

		RecordingDb db = new RecordingDb();
		CascadingUpdater.update(tree, car, db);

		System.out.println("\nOperations (Inheritance Update):");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	@Test
	public void testInsertMotorcycleVariant() {
		Motorcycle motorcycle = new Motorcycle();
		motorcycle.brand = "Harley";
		motorcycle.hasSidecar = true;

		QueryTree tree = QueryTreeBuilder.from(Motorcycle.class);
		System.out.println("Query Tree (Inheritance - Motorcycle):" + tree);

		RecordingDb db = new RecordingDb();
		CascadingUpdater.insert(tree, motorcycle, db);

		System.out.println("\nOperations (Motorcycle Insert):");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	// === Nested Relationship Tests (One-to-Many with Many-to-One) ===

	@Test
	public void testInsertWithNestedRelationships() {
		Company company = new Company();
		company.name = "Tech Corp";
		company.employees = new ArrayList<>();

		Employee emp1 = new Employee();
		emp1.firstName = "Alice";
		emp1.department = new Department();
		emp1.department.name = "Engineering";

		Employee emp2 = new Employee();
		emp2.firstName = "Bob";
		emp2.department = new Department();
		emp2.department.name = "Marketing";

		company.employees.add(emp1);
		company.employees.add(emp2);

		QueryTree tree = QueryTreeBuilder.from(Company.class);
		System.out.println("Query Tree (Nested Relationships):" + tree);

		RecordingDb db = new RecordingDb();
		CascadingUpdater.insert(tree, company, db);

		System.out.println("\nOperations (Nested Relationships Insert):");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	@Test
	public void testUpdateWithNestedRelationships() {
		Company company = new Company();
		company.id = 1L;
		company.name = "Updated Tech Corp";
		company.employees = new ArrayList<>();

		Employee emp1 = new Employee();
		emp1.id = 10L;
		emp1.firstName = "Alice Updated";
		emp1.department = new Department();
		emp1.department.id = 100L;
		emp1.department.name = "Engineering Updated";

		company.employees.add(emp1);

		QueryTree tree = QueryTreeBuilder.from(Company.class);
		System.out.println("Query Tree (Nested Relationships):" + tree);

		RecordingDb db = new RecordingDb();
		CascadingUpdater.update(tree, company, db);

		System.out.println("\nOperations (Nested Relationships Update):");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	// === Delete Tests for Different Join Types ===

	@Test
	public void testDeleteWithManyToMany() {
		Post post = new Post();
		post.id = 1L;
		post.title = "Post to Delete";
		post.tags = new ArrayList<>();
		post.tags.add(createExistingTag(10L, "Tag1"));

		QueryTree tree = QueryTreeBuilder.from(Post.class);

		RecordingDb db = new RecordingDb();
		CascadingUpdater.delete(tree, post, db);

		System.out.println("\nOperations (Many-to-Many Delete):");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	@Test
	public void testDeleteWithInheritance() {
		Car car = new Car();
		car.id = 1L;
		car.brand = "Toyota";
		car.numberOfDoors = 4;

		QueryTree tree = QueryTreeBuilder.from(Car.class);

		RecordingDb db = new RecordingDb();
		CascadingUpdater.delete(tree, car, db);

		System.out.println("\nOperations (Inheritance Delete):");
		for (String op : db.operations) {
			System.out.println("  " + op);
		}
	}

	// === Helper methods for new model classes ===

	private Tag createTag(String name) {
		Tag tag = new Tag();
		tag.name = name;
		return tag;
	}

	private Tag createExistingTag(Long id, String name) {
		Tag tag = new Tag();
		tag.id = id;
		tag.name = name;
		return tag;
	}

	private LineItem createLineItem(String product, int quantity) {
		LineItem item = new LineItem();
		item.product = product;
		item.quantity = quantity;
		return item;
	}
}
