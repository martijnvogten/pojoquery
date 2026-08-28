package org.pojoquery.integrationtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Recursive;
import org.pojoquery.annotations.Recursive.Direction;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Integration tests for {@link PojoQuery#executeMultiSet(DataSource)}: the same
 * entities as {@link PojoQuery#execute(DataSource)}, fetched as one JSON
 * document per root and hydrated through the flat row processor.
 *
 * <p>Values travel as text, so these tests care most about the round trip of
 * types JSON would otherwise mangle - decimals, dates, times - and about
 * structure: empty collections, null references, and collections that would
 * multiply into a cross product in a joined query. Comparing the two paths is
 * the point: which value a driver yields for a column varies, so agreement has
 * to be asserted per dialect rather than assumed.</p>
 */
public class MultiSetFetchIT {

	@Table("ms_publisher")
	public static class Publisher {
		@Id
		public Long id;
		public String name;
	}

	@Table("ms_book")
	public static class Book {
		@Id
		public Long id;
		public String title;
		public Integer edition;
		public BigDecimal price;
		public LocalDate published;
		public LocalDateTime indexedAt;
		public Instant importedAt;
		public LocalTime shelvedAt;
		public Timestamp scannedAt;
		public Boolean inPrint;
	}

	@Table("ms_author")
	public static class Author {
		@Id
		public Long id;
		public String name;
		@Embedded
		public Address address;
		@FieldName("publisher_id")
		public Publisher publisher;
	}

	public static class Address {
		public String city;
		public String country;
	}

	public static class AuthorWithBooks extends Author {
		public List<Book> books;
		@Link(linktable = "ms_author_award", fetchColumn = "award")
		public List<String> awards;
	}

	@Test
	public void testMultiSetMatchesJoinedFetch() {
		DataSource db = TestDatabaseProvider.getDataSource();
		DbContext context = TestDatabaseProvider.getDbContext();
		SchemaGenerator.createTables(db, AuthorWithBooks.class, Publisher.class);
		DB.runInTransaction(db, connection -> {
			Publisher acme = new Publisher();
			acme.name = "Acme";
			PojoQuery.insert(connection, acme);

			AuthorWithBooks alice = new AuthorWithBooks();
			alice.name = "Alice";
			alice.address = new Address();
			alice.address.city = "Utrecht";
			alice.address.country = "NL";
			alice.publisher = acme;
			alice.books = List.of(book("Dune", 1, "12.3456", LocalDate.of(1965, 6, 1),
					LocalDateTime.of(2024, 1, 2, 3, 4, 5), true),
					book("Ubik", null, "9.99", null, null, false));
			alice.books.get(0).importedAt = Instant.parse("2024-03-04T05:06:07Z");
			alice.books.get(0).shelvedAt = LocalTime.of(3, 4, 5);
			alice.books.get(0).scannedAt = Timestamp.valueOf(LocalDateTime.of(2024, 5, 6, 7, 8, 9));
			alice.awards = List.of("hugo", "nebula");
			PojoQuery.insert(connection, alice);

			AuthorWithBooks nobody = new AuthorWithBooks();   // no books, no awards, no publisher
			nobody.name = "Bob";
			nobody.address = new Address();
			nobody.books = List.of();
			nobody.awards = List.of();
			PojoQuery.insert(connection, nobody);
		});

		List<AuthorWithBooks> joined = PojoQuery.build(context, AuthorWithBooks.class)
				.addOrderBy("{this.name}").execute(db);
		List<AuthorWithBooks> documents = PojoQuery.build(context, AuthorWithBooks.class)
				.addOrderBy("{this.name}").executeMultiSet(db);

		assertEquals(describe(joined), describe(documents));

		// Spot-check the values that JSON would otherwise mangle.
		AuthorWithBooks alice = documents.get(0);
		assertEquals("Alice", alice.name);
		assertEquals("Utrecht", alice.address.city);
		assertEquals("Acme", alice.publisher.name);
		Book dune = alice.books.stream().filter(b -> b.title.equals("Dune")).findFirst().orElseThrow();
		assertEquals(new BigDecimal("12.3456"), dune.price);
		assertEquals(LocalDate.of(1965, 6, 1), dune.published);
		assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5), dune.indexedAt);
		assertEquals(Boolean.TRUE, dune.inPrint);
		assertEquals(Instant.parse("2024-03-04T05:06:07Z"), dune.importedAt);
		assertEquals(LocalTime.of(3, 4, 5), dune.shelvedAt);
		assertEquals(Timestamp.valueOf(LocalDateTime.of(2024, 5, 6, 7, 8, 9)), dune.scannedAt);
		assertEquals(Integer.valueOf(1), dune.edition);

		Book ubik = alice.books.stream().filter(b -> b.title.equals("Ubik")).findFirst().orElseThrow();
		assertNull(ubik.edition);
		assertNull(ubik.published);
		assertNull(ubik.indexedAt);
		assertEquals(Boolean.FALSE, ubik.inPrint);

		assertEquals(List.of("hugo", "nebula"), alice.awards.stream().sorted().toList());

		AuthorWithBooks bob = documents.get(1);
		assertEquals("Bob", bob.name);
		assertNull(bob.publisher, "a null reference must stay null");
		assertTrue(bob.books == null || bob.books.isEmpty());
		assertTrue(bob.awards == null || bob.awards.isEmpty());
	}

	// ========== @Recursive over the document path ==========

	@Table("ms_category")
	public static class Category {
		@Id
		public Long id;
		public String name;
	}

	public static class CategoryWithParent extends Category {
		@FieldName("parent_id")
		public Category parent;
	}

	public static class CategoryWithDescendants extends CategoryWithParent {
		@Recursive(parentLink = "parent_id", direction = Direction.DOWN)
		public List<Category> descendants;
	}

	@Test
	public void testMultiSetHydratesRecursiveCollections() {
		DataSource db = TestDatabaseProvider.getDataSource();
		DbContext context = TestDatabaseProvider.getDbContext();
		SchemaGenerator.createTables(db, CategoryWithParent.class);
		DB.runInTransaction(db, connection -> {
			CategoryWithParent electronics = insert(connection, "Electronics", null);
			CategoryWithParent audio = insert(connection, "Audio", electronics);
			insert(connection, "Phones", audio);
			insert(connection, "Books", null);
		});

		List<CategoryWithDescendants> joined = PojoQuery.build(context, CategoryWithDescendants.class)
				.addOrderBy("{this.name}").execute(db);
		List<CategoryWithDescendants> documents = PojoQuery.build(context, CategoryWithDescendants.class)
				.addOrderBy("{this.name}").executeMultiSet(db);

		assertEquals(4, documents.size());
		assertEquals(describeCategories(joined), describeCategories(documents));

		CategoryWithDescendants electronics = documents.stream()
				.filter(c -> c.name.equals("Electronics")).findFirst().orElseThrow();
		assertEquals(List.of("Audio", "Phones"),
				electronics.descendants.stream().map(c -> c.name).sorted().toList());
	}

	// ========== Helpers ==========

	private static Book book(String title, Integer edition, String price, LocalDate published,
			LocalDateTime indexedAt, boolean inPrint) {
		Book book = new Book();
		book.title = title;
		book.edition = edition;
		book.price = new BigDecimal(price);
		book.published = published;
		book.indexedAt = indexedAt;
		book.inPrint = inPrint;
		return book;
	}

	private static CategoryWithParent insert(java.sql.Connection connection, String name, Category parent) {
		CategoryWithParent category = new CategoryWithParent();
		category.name = name;
		category.parent = parent;
		PojoQuery.insert(connection, category);
		return category;
	}

	/** A stable rendering of the object graph, for comparing the two fetch paths. */
	private static String describe(List<AuthorWithBooks> authors) {
		StringBuilder out = new StringBuilder();
		for (AuthorWithBooks author : authors) {
			assertNotNull(author.id);
			out.append(author.name).append('|')
					.append(author.address == null ? "-" : author.address.city + "," + author.address.country)
					.append('|').append(author.publisher == null ? "-" : author.publisher.name).append('|');
			out.append(author.books == null ? List.of()
					: author.books.stream()
							.map(b -> b.title + ":" + b.edition + ":" + b.price + ":" + b.published + ":"
									+ b.indexedAt + ":" + b.importedAt + ":" + b.shelvedAt + ":" + b.scannedAt
									+ ":" + b.inPrint)
							.sorted().toList());
			out.append('|').append(author.awards == null ? List.of() : author.awards.stream().sorted().toList());
			out.append('\n');
		}
		return out.toString();
	}

	private static String describeCategories(List<CategoryWithDescendants> categories) {
		StringBuilder out = new StringBuilder();
		for (CategoryWithDescendants category : categories) {
			out.append(category.name).append('|')
					.append(category.parent == null ? "-" : category.parent.name).append('|')
					.append(category.descendants == null ? List.of()
							: category.descendants.stream().map(c -> c.name).sorted().toList())
					.append('\n');
		}
		return out.toString();
	}
}
