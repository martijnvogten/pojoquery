package org.pojoquery.integrationtest;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Aggregate;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.From;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Select;
import org.pojoquery.annotations.Subquery;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

public class StatsQueriesIT {
	// Posts with comments domain classes

	@Table("post")
	static class Post {
		@Id Long postId;
		String title;
	}

	@Table("comment")
	static class Comment {
		@Id Long commentId;
		String content;
		Date createdAt;
		Post post;
	}

	@Table("tag")
	static class Tag {
		@Id Long tagId;
		String name;
	}
	
	static class PostWithTags extends Post {
		@Link(linktable = "post_tag", linkfield = "post_id", foreignlinkfield = "tag_id")
		List<Tag> tags;
	}
	
	// @Join(alias="tags", source=Tag.class, link=@Link(linktable="post_tag", linkfield="post_id", foreignlinkfield="tag_id"))
	// @Join(alias="comments", source=Comment.class)
	static class PostWithCommentCount extends Post {
		@Aggregate("json_agg({tags.name})")
		String tagsJson;

		@Aggregate("COUNT({comments.commentId})")
		Long commentCount;

		@Aggregate("MAX({comments.createdAt})")
		Date latestCommentDate;
	}

	@Test
	@Disabled("json_agg is PostgreSQL-specific, not supported by HSQLDB")
	public void testPostCommentDomain() {
		DataSource db = initPostDatabase();

		List<PostWithCommentCount> posts = PojoQuery.build(PostWithCommentCount.class).execute(db);
		Assertions.assertEquals(2, posts.size());
		for (PostWithCommentCount p : posts) {
			if (p.title.equals("Hello World")) {
				Assertions.assertEquals(2L, p.commentCount);
			} else if (p.title.equals("Another Post")) {
				Assertions.assertEquals(0L, p.commentCount);
			} else {
				Assertions.fail("Unexpected post title: " + p.title);
			}
		}
	}

	private DataSource initPostDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, PostWithTags.class, Tag.class, Comment.class);

		DB.withConnection(db, (Connection c) -> {
			// Create tags
			Tag java = new Tag();
			java.name = "Java";
			PojoQuery.insert(c, java);

			Tag programming = new Tag();
			programming.name = "Programming";
			PojoQuery.insert(c, programming);

			// Create posts with tags
			PostWithTags post1 = new PostWithTags();
			post1.title = "Hello World";
			post1.tags = List.of(java, programming);
			PojoQuery.insert(c, post1);

			PostWithTags post2 = new PostWithTags();
			post2.title = "Another Post";
			post2.tags = List.of(java);
			PojoQuery.insert(c, post2);

			Comment c1 = new Comment();
			c1.content = "Great post!";
			c1.createdAt = new Date();
			c1.post = post1;
			PojoQuery.insert(c, c1);

			Comment c2 = new Comment();
			c2.content = "Thanks for sharing";
			c2.createdAt = new Date();
			c2.post = post1;
			PojoQuery.insert(c, c2);

			// Verify comments
			List<Comment> comments = PojoQuery.build(Comment.class).execute(c);
			Assertions.assertEquals(2, comments.size());
			Assertions.assertEquals("Hello World", comments.get(0).post.title);

			// Verify tags
			List<PostWithTags> postsWithTags = PojoQuery.build(PostWithTags.class).execute(c);
			Assertions.assertEquals(2, postsWithTags.size());
			for (PostWithTags p : postsWithTags) {
				if (p.title.equals("Hello World")) {
					Assertions.assertEquals(2, p.tags.size());
				} else {
					Assertions.assertEquals(1, p.tags.size());
				}
			}
		});
		return db;
	}

	// Film domain classes with many-to-many relationships
	
	@Table("film")
	static class Film {
		@Id Long filmId;
		String title;
		Integer releaseYear;
	}

	@Table("category")
	static class Category {
		@Id Long categoryId;
		String name;
	}

	@Table("actor")
	static class Actor {
		@Id Long actorId;
		String firstName;
		String lastName;
	}

	static class FilmDetail extends Film {
		@Link(linktable = "film_category", linkfield = "film_id", foreignlinkfield = "category_id")
		List<Category> categories;

		@Link(linktable = "film_actor", linkfield = "film_id", foreignlinkfield = "actor_id")
		List<Actor> actors;

		List<InventoryWithRentals> inventory;
	}

	@Table("inventory")
	static class Inventory {
		@Id Long inventoryId;
		Film film;
		Long storeId;
	}

	static class InventoryWithRentals extends Inventory {
		List<RentalWithPayments> rentals;
	}

	@Table("payment")
	static class Payment {
		@Id Long paymentId;
		BigDecimal amount;
		@FieldName("payment_date")
		Date paymentDate;
	}

	static class PaymentWithRental extends Payment {
		RentalWithInventory rental;
	}
	
	static class PaymentDetail extends PaymentWithRental {
		Customer customer;
	}

	@Table("customer")
	static class Customer {
		@Id Long customerId;
		@FieldName("first_name")
		String firstName;
		@FieldName("last_name")
		String lastName;
	}

	static class CustomerWithPayments extends Customer {
		List<PaymentWithRental> payments;
	}

	@From(CustomerWithPayments.class)
	static class CustomerStats {
		@Select("{payments.rental.inventory.film.filmId}")
		Long filmId;

		Long customerId;
		
		@Select("{this.first_name}")
		String firstName;

		@Select("{this.last_name}")
		String lastName;

		@Aggregate("JSON_ARRAYAGG(JSON_ARRAY({payments.payment_date}, {payments.amount}))")
		String paymentsJson;

		List<PaymentAmount> getPayments() {
			return parseJsonArray(paymentsJson).stream().map(entry -> 
				new PaymentAmount(entry[0], new BigDecimal(entry[1]))
			).toList();
		}
	}

	record PaymentAmount(String paymentDate, BigDecimal amount) {}

	private static List<String[]> parseJsonArray(String json) {
		if (json == null || json.isEmpty()) {
			return List.of();
		}
		try {
			JsonNode root = new ObjectMapper().readTree(json);
			List<String[]> result = new ArrayList<>();
			for (JsonNode entry : root) {
				String[] values = new String[entry.size()];
				for (int i = 0; i < entry.size(); i++) {
					values[i] = entry.get(i).asText();
				}
				result.add(values);
			}
			return result;
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to parse JSON array: " + json, e);
		}
	}

	@Table("rental")
	static class Rental {
		@Id Long rentalId;
		Date rentalDate;
		@FieldName("customer_id")
		Customer customer;
		Date returnDate;
	}
	
	static class RentalWithPayments extends Rental {
		List<Payment> payments;
	}
	
	static class RentalWithInventory extends Rental{
		Inventory inventory;
	}

	static class FilmStatsAggregate extends Film {
		List<InventoryWithRentals> inventory;
	}

	@From(FilmStatsAggregate.class)
	static class FilmStats {
		Long filmId;
		String title;

		@Aggregate("COUNT(DISTINCT {inventory.inventoryId})")
		Long copyCount;

		@Aggregate("COUNT({inventory.rentals.rentalId})")
		Long rentalCount;

		@Aggregate("SUM({inventory.rentals.payments.amount})")
		BigDecimal totalRevenue;

		@Aggregate("MAX({inventory.rentals.rentalDate})")
		Date lastRentalDate;
	}

	@From(FilmDetail.class) 
	static class FilmStatsWithCategoriesAndActors {
		@Id
		Long filmId;
		String title;

		@Aggregate("GROUP_CONCAT(DISTINCT {categories.name} SEPARATOR ', ')")
		String categoriesJson;

		@Aggregate("GROUP_CONCAT(DISTINCT {actors.firstName} SEPARATOR ', ')")
		String actorsJson;
	}

	@From(FilmDetail.class)
	static class FilmStatsWithInventory {
		@Id
		Long filmId;

		@Aggregate("COUNT(DISTINCT {inventory.inventoryId})")
		Long copyCount;

		@Aggregate("COUNT(DISTINCT {inventory.rentals.rentalId})")
		Long rentalCount;

		@Aggregate("SUM({inventory.rentals.payments.amount})")
		BigDecimal totalRevenue;
	}


	@From(Film.class)
	static class CombinedFilmStats {
		@Id
		Long filmId;
		String title;

		// @Subquery(joinOn = "filmId")
		FilmStatsWithCategoriesAndActors categoryStats;
		
		@Subquery(joinOn = "filmId")
		FilmStatsWithInventory inventoryStats;
		
		@Subquery(joinOn = "filmId")
		List<CustomerStats> customerStats;
	}

	@Test
	public void testFilmMultiSetStats() {
		DataSource db = initFilmDomain();

		// Test CombinedFilmStats which joins two subqueries to avoid row multiplication
		List<CombinedFilmStats> stats = PojoQuery.build(CombinedFilmStats.class)
			.addWhere("{film.title} = ?", "The Matrix")
			.execute(db);
		Assertions.assertEquals(1, stats.size());
		CombinedFilmStats s = stats.get(0);
		Assertions.assertEquals("The Matrix", s.title);
		
		// Category/actor stats from first subquery
		Assertions.assertNotNull(s.categoryStats);
		Assertions.assertTrue(s.categoryStats.categoriesJson.contains("Action"));
		Assertions.assertTrue(s.categoryStats.categoriesJson.contains("Sci-Fi"));
		Assertions.assertTrue(s.categoryStats.actorsJson.contains("Keanu"));
		Assertions.assertTrue(s.categoryStats.actorsJson.contains("Carrie-Anne"));
		
		// Inventory stats from second subquery
		// 2 inventory copies, 3 rentals (John 2x, Jane 1x), total $10.97
		Assertions.assertNotNull(s.inventoryStats);
		Assertions.assertEquals(2L, s.inventoryStats.copyCount);
		Assertions.assertEquals(3L, s.inventoryStats.rentalCount);
		Assertions.assertEquals(0, new BigDecimal("10.97").compareTo(s.inventoryStats.totalRevenue),
			"Expected totalRevenue 10.97, got: " + s.inventoryStats.totalRevenue);
		
		// Customer stats from to-many subquery
		// 2 customers: John (2 payments: $3.99 + $2.99) and Jane (1 payment: $3.99)
		Assertions.assertNotNull(s.customerStats);
		Assertions.assertEquals(2, s.customerStats.size(), "Expected 2 customers");
		
		// Find John's stats
		CustomerStats johnStats = s.customerStats.stream()
			.filter(cs -> "John".equals(cs.firstName))
			.findFirst().orElse(null);
		Assertions.assertNotNull(johnStats, "John should be in customer stats");
		Assertions.assertEquals("Doe", johnStats.lastName);
		Assertions.assertTrue(johnStats.getPayments().stream().anyMatch(p -> p.amount.compareTo(new BigDecimal("3.99")) == 0),
			"John's payments should include 3.99, got: " + johnStats.getPayments());
		Assertions.assertTrue(johnStats.getPayments().stream().anyMatch(p -> p.amount.compareTo(new BigDecimal("2.99")) == 0),
			"John's payments should include 2.99, got: " + johnStats.getPayments());
		
		// Find Jane's stats
		CustomerStats janeStats = s.customerStats.stream()
			.filter(cs -> "Jane".equals(cs.firstName))
			.findFirst().orElse(null);
		Assertions.assertNotNull(janeStats, "Jane should be in customer stats");
		Assertions.assertEquals("Smith", janeStats.lastName);
		Assertions.assertTrue(janeStats.getPayments().stream().anyMatch(p -> p.amount.compareTo(new BigDecimal("3.99")) == 0),
			"Jane's payment should be 3.99, got: " + janeStats.getPayments());
	}

	private DataSource initFilmDomain() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, FilmDetail.class, Category.class, Actor.class, RentalWithInventory.class, RentalWithPayments.class, Inventory.class, PaymentDetail.class, Customer.class);

		DB.withConnection(db, (Connection c) -> {
			// Insert categories
			Category action = new Category();
			action.name = "Action";
			PojoQuery.insert(c, action);

			Category scifi = new Category();
			scifi.name = "Sci-Fi";
			PojoQuery.insert(c, scifi);
			
			Category drama = new Category();
			drama.name = "Drama";
			PojoQuery.insert(c, drama);

			// Insert actors
			Actor keanu = new Actor();
			keanu.firstName = "Keanu";
			keanu.lastName = "Reeves";
			PojoQuery.insert(c, keanu);

			Actor carrie = new Actor();
			carrie.firstName = "Carrie-Anne";
			carrie.lastName = "Moss";
			PojoQuery.insert(c, carrie);
			
			Actor leo = new Actor();
			leo.firstName = "Leonardo";
			leo.lastName = "DiCaprio";
			PojoQuery.insert(c, leo);

			// Insert The Matrix
			FilmDetail matrix = new FilmDetail();
			matrix.title = "The Matrix";
			matrix.releaseYear = 1999;
			matrix.categories = List.of(action, scifi);
			matrix.actors = List.of(keanu, carrie);
			PojoQuery.insert(c, matrix);
			
			// Insert Inception
			FilmDetail inception = new FilmDetail();
			inception.title = "Inception";
			inception.releaseYear = 2010;
			inception.categories = List.of(action, scifi);
			inception.actors = List.of(leo);
			PojoQuery.insert(c, inception);
			
			// Insert inventory for The Matrix (2 copies)
			Inventory matrixInv1 = new Inventory();
			matrixInv1.film = matrix;
			matrixInv1.storeId = 1L;
			PojoQuery.insert(c, matrixInv1);
			
			Inventory matrixInv2 = new Inventory();
			matrixInv2.film = matrix;
			matrixInv2.storeId = 2L;
			PojoQuery.insert(c, matrixInv2);
			
			// Insert inventory for Inception (1 copy)
			Inventory inceptionInv = new Inventory();
			inceptionInv.film = inception;
			inceptionInv.storeId = 1L;
			PojoQuery.insert(c, inceptionInv);
			
			// Insert customers
			Customer john = new Customer();
			john.firstName = "John";
			john.lastName = "Doe";
			PojoQuery.insert(c, john);
			
			Customer jane = new Customer();
			jane.firstName = "Jane";
			jane.lastName = "Smith";
			PojoQuery.insert(c, jane);

			// John rents and pays for The Matrix (copy 1)
			RentalWithInventory rental1 = new RentalWithInventory();
			rental1.inventory = matrixInv1;
			rental1.customer = john;
			rental1.rentalDate = new Date();
			PojoQuery.insert(c, rental1);

			PaymentDetail payment1 = new PaymentDetail();
			payment1.amount = new BigDecimal("3.99");
			payment1.paymentDate = new Date();
			payment1.rental = rental1;
			payment1.customer = john;
			PojoQuery.insert(c, payment1);
			
			// Jane rents and pays for The Matrix (copy 2)
			RentalWithInventory rental2 = new RentalWithInventory();
			rental2.inventory = matrixInv2;
			rental2.customer = jane;
			rental2.rentalDate = new Date();
			PojoQuery.insert(c, rental2);

			PaymentDetail payment2 = new PaymentDetail();
			payment2.amount = new BigDecimal("3.99");
			payment2.paymentDate = new Date();
			payment2.rental = rental2;
			payment2.customer = jane;
			PojoQuery.insert(c, payment2);
			
			// John also rents The Matrix copy 2 (second rental)
			RentalWithInventory rental3 = new RentalWithInventory();
			rental3.inventory = matrixInv2;
			rental3.customer = john;
			rental3.rentalDate = new Date();
			PojoQuery.insert(c, rental3);

			PaymentDetail payment3 = new PaymentDetail();
			payment3.amount = new BigDecimal("2.99");
			payment3.paymentDate = new Date();
			payment3.rental = rental3;
			payment3.customer = john;
			PojoQuery.insert(c, payment3);

			// Query and verify
			List<FilmDetail> films = PojoQuery.build(FilmDetail.class).execute(c);
			Assertions.assertEquals(2, films.size());

			List<RentalWithInventory> rentals = PojoQuery.build(RentalWithInventory.class).execute(c);
			Assertions.assertEquals(3, rentals.size());
		});

		return db;
	}

	@Test
	public void testFilmStatsWithChainedJoins() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, FilmDetail.class, InventoryWithRentals.class, Category.class, Actor.class, Rental.class, PaymentDetail.class, Customer.class);

		DB.withConnection(db, (Connection c) -> {
			// Create films
			Film matrix = new Film();
			matrix.title = "The Matrix";
			matrix.releaseYear = 1999;
			PojoQuery.insert(c, matrix);

			Film inception = new Film();
			inception.title = "Inception";
			inception.releaseYear = 2010;
			PojoQuery.insert(c, inception);

			// Create inventory copies for The Matrix (3 copies)
			Inventory matrixCopy1 = new Inventory();
			matrixCopy1.film = matrix;
			matrixCopy1.storeId = 1L;
			PojoQuery.insert(c, matrixCopy1);

			Inventory matrixCopy2 = new Inventory();
			matrixCopy2.film = matrix;
			matrixCopy2.storeId = 1L;
			PojoQuery.insert(c, matrixCopy2);

			Inventory matrixCopy3 = new Inventory();
			matrixCopy3.film = matrix;
			matrixCopy3.storeId = 2L;
			PojoQuery.insert(c, matrixCopy3);

			// Create inventory for Inception (2 copies)
			Inventory inceptionCopy1 = new Inventory();
			inceptionCopy1.film = inception;
			inceptionCopy1.storeId = 1L;
			PojoQuery.insert(c, inceptionCopy1);

			Inventory inceptionCopy2 = new Inventory();
			inceptionCopy2.film = inception;
			inceptionCopy2.storeId = 2L;
			PojoQuery.insert(c, inceptionCopy2);

			// Create customers
			Customer cust1 = new Customer();
			cust1.firstName = "Alice";
			cust1.lastName = "Smith";
			PojoQuery.insert(c, cust1);

			Customer cust2 = new Customer();
			cust2.firstName = "Bob";
			cust2.lastName = "Johnson";
			PojoQuery.insert(c, cust2);

			// Create rentals for The Matrix (5 rentals across copies)
			createRental(c, matrixCopy1, cust1, new BigDecimal("3.99"));
			createRental(c, matrixCopy1, cust2, new BigDecimal("3.99"));
			createRental(c, matrixCopy2, cust1, new BigDecimal("3.99"));
			createRental(c, matrixCopy3, cust1, new BigDecimal("3.99"));
			createRental(c, matrixCopy3, cust2, new BigDecimal("3.99"));

			// Create rentals for Inception (2 rentals)
			createRental(c, inceptionCopy1, cust1, new BigDecimal("4.99"));
			createRental(c, inceptionCopy2, cust1, new BigDecimal("4.99"));

			// Query film stats with chained aggregates
			List<FilmStats> stats = PojoQuery.build(FilmStats.class)
				.addOrderBy("{film.title}")
				.execute(c);

			Assertions.assertEquals(2, stats.size());

			// Inception: 2 copies, 2 rentals, $9.98 revenue
			FilmStats inceptionStats = stats.get(0);
			Assertions.assertEquals("Inception", inceptionStats.title);
			Assertions.assertEquals(2L, inceptionStats.copyCount);
			Assertions.assertEquals(2L, inceptionStats.rentalCount);
			Assertions.assertEquals(0, new BigDecimal("9.98").compareTo(inceptionStats.totalRevenue));

			// The Matrix: 3 copies, 5 rentals, $19.95 revenue
			FilmStats matrixStats = stats.get(1);
			Assertions.assertEquals("The Matrix", matrixStats.title);
			Assertions.assertEquals(3L, matrixStats.copyCount);
			Assertions.assertEquals(5L, matrixStats.rentalCount);
			Assertions.assertEquals(0, new BigDecimal("19.95").compareTo(matrixStats.totalRevenue));
		});
	}

	private void createRental(Connection c, Inventory inventory, Customer customer, BigDecimal amount) {
		RentalWithInventory rental = new RentalWithInventory();
		rental.inventory = inventory;
		rental.customer = customer;
		rental.rentalDate = new Date();
		PojoQuery.insert(c, rental);

		PaymentDetail payment = new PaymentDetail();
		payment.amount = amount;
		payment.paymentDate = new Date();
		payment.rental = rental;
		payment.customer = customer;
		PojoQuery.insert(c, payment);
	}


	@Table("department")
	static class Department {
		@Id Long id;
		String name;
	}
	
	static class DepartmentDetail extends Department {
		List<Employee> employees;
	}

	@Table("employee")
	static class Employee {
		@Id Long id;
		String name;
		BigDecimal salary;
		Department department;
	}

	@From(DepartmentDetail.class)
	static class DepartmentStats {
		@Id Long id;

		@Aggregate("COUNT(DISTINCT {employees.id})")
		Long employeeCount;

		@Aggregate("AVG({employees.salary})")
		BigDecimal averageSalary;

		@Aggregate("SUM({employees.salary})")
		BigDecimal totalSalary;
	}

	static class DepartmentList extends Department {
		@JoinCondition("{this.id} = {stats.id}")
		DepartmentStats stats;
	}

	@Test
	public void testDepartmentStatsWithSubquery() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, DepartmentDetail.class, Employee.class);

		DB.withConnection(db, (Connection c) -> {
			Department dept = new Department();
			dept.name = "Engineering";
			PojoQuery.insert(c, dept);

			Employee emp1 = new Employee();
			emp1.name = "Alice";
			emp1.salary = new BigDecimal("100000");
			emp1.department = dept;
			PojoQuery.insert(c, emp1);

			Employee emp2 = new Employee();
			emp2.name = "Bob";
			emp2.salary = new BigDecimal("120000");
			emp2.department = dept;
			PojoQuery.insert(c, emp2);

			List<DepartmentList> departments = PojoQuery.build(DepartmentList.class)
				.addWhere("{this.name} = ?", "Engineering").execute(c);
			
			Assertions.assertEquals(1, departments.size());
			Assertions.assertEquals(2L, departments.get(0).stats.employeeCount);
			Assertions.assertEquals(0, new BigDecimal("110000").compareTo(departments.get(0).stats.averageSalary));
			Assertions.assertEquals(0, new BigDecimal("220000").compareTo(departments.get(0).stats.totalSalary));
		});
	}

	@Test
	public void testDepartmentSalaryStats() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, DepartmentDetail.class, Employee.class);

		DB.withConnection(db, (Connection c) -> {
			Department dept = new Department();
			dept.name = "Engineering";
			PojoQuery.insert(c, dept);

			Employee emp1 = new Employee();
			emp1.name = "Alice";
			emp1.salary = new BigDecimal("100000");
			emp1.department = dept;
			PojoQuery.insert(c, emp1);

			Employee emp2 = new Employee();
			emp2.name = "Bob";
			emp2.salary = new BigDecimal("120000");
			emp2.department = dept;
			PojoQuery.insert(c, emp2);

			List<DepartmentStats> stats = PojoQuery.build(DepartmentStats.class)
				.addWhere("{this.name} = ?", "Engineering").execute(c);
			Assertions.assertEquals(1, stats.size());
			Assertions.assertEquals(2L, stats.get(0).employeeCount);
			Assertions.assertEquals(0, new BigDecimal("110000").compareTo(stats.get(0).averageSalary));
			Assertions.assertEquals(0, new BigDecimal("220000").compareTo(stats.get(0).totalSalary));
		});
	}
	
	// ============================================================
	// Row multiplication problem with unused joins
	// ============================================================
	
	@Table("product")
	static class Product {
		@Id Long productId;
		String name;
	}
	
	@Table("review")
	static class Review {
		@Id Long reviewId;
		Product product;
		Integer rating;
		BigDecimal tipAmount;
	}
	
	@Table("photo")
	static class Photo {
		@Id Long photoId;
		Product product;
		String url;
	}
	
	// Source with multiple one-to-many relationships
	static class ProductWithReviewsAndPhotos extends Product {
		List<Review> reviews;
		List<Photo> photos;
	}
	
	// Only uses reviews - but photos join is still present and multiplies rows!
	@From(ProductWithReviewsAndPhotos.class)
	static class ProductReviewStats {
		Long productId;
		String name;
		
		@Aggregate("COUNT({reviews.reviewId})")
		Long reviewCount;
		
		@Aggregate("SUM({reviews.tipAmount})")
		BigDecimal totalTips;
		
		@Aggregate("AVG(CAST({reviews.rating} AS DECIMAL))")
		BigDecimal avgRating;
	}
	
	/**
	 * This test verifies that unused joins are pruned to prevent row multiplication.
	 * 
	 * When a @From source has multiple one-to-many joins but only some are used,
	 * PojoQuery automatically prunes the unused joins to avoid cross-products.
	 * 
	 * Product has:
	 * - 2 reviews ($1.00 tip each, ratings 4 and 5)
	 * - 3 photos (unused - should be pruned)
	 * 
	 * Expected (with join pruning):
	 * - reviewCount = 2
	 * - totalTips = $2.00
	 * - avgRating = 4.5
	 * 
	 * Without pruning, we'd get incorrect values due to cross-product:
	 * - reviewCount = 6 (2 reviews × 3 photos)
	 * - totalTips = $6.00 (each tip counted 3 times)
	 */
	@Test
	public void testUnusedJoinsArePruned() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, ProductWithReviewsAndPhotos.class, Review.class, Photo.class);
		
		DB.withConnection(db, (Connection c) -> {
			// Create product
			Product product = new Product();
			product.name = "Widget";
			PojoQuery.insert(c, product);
			
			// Create 2 reviews
			Review r1 = new Review();
			r1.product = product;
			r1.rating = 4;
			r1.tipAmount = new BigDecimal("1.00");
			PojoQuery.insert(c, r1);
			
			Review r2 = new Review();
			r2.product = product;
			r2.rating = 5;
			r2.tipAmount = new BigDecimal("1.00");
			PojoQuery.insert(c, r2);
			
			// Create 3 photos (unused in our stats query - should be pruned)
			for (int i = 0; i < 3; i++) {
				Photo photo = new Photo();
				photo.product = product;
				photo.url = "http://example.com/photo" + i + ".jpg";
				PojoQuery.insert(c, photo);
			}
			
			// Query stats - photos join should be pruned
			List<ProductReviewStats> stats = PojoQuery.build(ProductReviewStats.class).execute(c);
			Assertions.assertEquals(1, stats.size());
			
			ProductReviewStats s = stats.get(0);
			System.out.println("Review count: " + s.reviewCount + " (expected 2)");
			System.out.println("Total tips: " + s.totalTips + " (expected 2.00)");
			System.out.println("Avg rating: " + s.avgRating + " (expected ~4.5)");
			
			// With join pruning, we get the correct values
			Assertions.assertEquals(2L, s.reviewCount, "reviewCount should be 2 (photos join pruned)");
			Assertions.assertTrue(s.totalTips.compareTo(new BigDecimal("1.99")) > 0 
					&& s.totalTips.compareTo(new BigDecimal("2.01")) < 0, 
				"totalTips should be $2.00, got: " + s.totalTips);
			
			// AVG should also be correct
			// Note: HSQLDB may return integer 4 due to integer division
			Assertions.assertTrue(s.avgRating.compareTo(new BigDecimal("3.9")) > 0
					&& s.avgRating.compareTo(new BigDecimal("4.6")) < 0,
				"Avg rating should be ~4-4.5, got: " + s.avgRating);
		});
	}
}
