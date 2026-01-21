package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.internal.MappingException;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Integration tests for streaming query execution with consumer-based processing.
 */
@ExtendWith(DbContextExtension.class)
public class StreamingIT {

	@Table("author")
	public static class Author {
		@Id
		Long id;
		String name;
		Integer birthYear;
	}

	@Table("book")
	public static class Book {
		@Id
		Long id;
		String title;
		Integer publicationYear;
		Author author;
	}

	@Table("book")
	public static class BookWithAuthor {
		@Id
		Long id;
		String title;
		Integer publicationYear;
		Author author;
	}

	@Table("author")
	public static class AuthorWithBooks {
		@Id
		Long id;
		String name;
		Integer birthYear;
		
		List<Book> books;
	}

	@Test
	public void testStreamingWithConsumer_simpleEntities() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			insertTestData(c);
		});

		List<Author> streamedAuthors = new ArrayList<>();
		
		PojoQuery.build(Author.class)
			.addOrderBy("{author}.name ASC")
			.executeStreaming(db, author -> {
				streamedAuthors.add(author);
			});

		Assertions.assertEquals(3, streamedAuthors.size());
		Assertions.assertEquals("Agatha Christie", streamedAuthors.get(0).name);
		Assertions.assertEquals("George Orwell", streamedAuthors.get(1).name);
		Assertions.assertEquals("Jane Austen", streamedAuthors.get(2).name);
	}

	@Test
	public void testStreamingWithConsumer_entitiesWithRelations() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			insertTestData(c);
		});

		List<AuthorWithBooks> streamedAuthors = new ArrayList<>();
		
		PojoQuery.build(AuthorWithBooks.class)
			.executeStreaming(db, author -> {
				// Each author should be complete with all their books when emitted
				streamedAuthors.add(author);
			});

		Assertions.assertEquals(3, streamedAuthors.size());
		
		// Find Agatha Christie and verify she has all her books
		AuthorWithBooks agatha = streamedAuthors.stream()
			.filter(a -> "Agatha Christie".equals(a.name))
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(2, agatha.books.size());
		
		// Find George Orwell and verify he has all his books
		AuthorWithBooks george = streamedAuthors.stream()
			.filter(a -> "George Orwell".equals(a.name))
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(2, george.books.size());
	}

	@Test
	public void testStreamingWithConsumer_orderedByAuthorName() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			insertTestData(c);
		});

		List<AuthorWithBooks> streamedAuthors = new ArrayList<>();
		
		// Order by author's name - a field from the primary entity
		// This is the recommended way to use streaming with ordering
		PojoQuery.build(AuthorWithBooks.class)
			.addOrderBy("{author}.name ASC")
			.executeStreaming(db, author -> {
				streamedAuthors.add(author);
			});

		Assertions.assertEquals(3, streamedAuthors.size());
		
		// Authors should be in alphabetical order
		Assertions.assertEquals("Agatha Christie", streamedAuthors.get(0).name);
		Assertions.assertEquals("George Orwell", streamedAuthors.get(1).name);
		Assertions.assertEquals("Jane Austen", streamedAuthors.get(2).name);
		
		// All authors should have all their books
		Assertions.assertEquals(2, streamedAuthors.get(0).books.size()); // Agatha
		Assertions.assertEquals(2, streamedAuthors.get(1).books.size()); // George
		Assertions.assertEquals(1, streamedAuthors.get(2).books.size()); // Jane
	}

	@Test
	public void testStreamingWithConsumer_throwsOnJoinedTableOrderBy() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			insertTestData(c);
		});

		// Ordering by a field in a joined table should throw an exception
		MappingException exception = Assertions.assertThrows(MappingException.class, () -> {
			PojoQuery.build(AuthorWithBooks.class)
				.addOrderBy("{books}.publicationYear ASC")
				.executeStreaming(db, author -> {
					// Should not reach here
				});
		});
		
		Assertions.assertTrue(exception.getMessage().contains("books"));
		Assertions.assertTrue(exception.getMessage().contains("author"));
		Assertions.assertTrue(exception.getMessage().contains("ORDER BY"));
	}

	@Test
	public void testStreamingWithConsumer_emptyResult() {
		DataSource db = initDatabase();
		// Don't insert any data
		
		List<Author> streamedAuthors = new ArrayList<>();
		
		PojoQuery.build(Author.class)
			.addWhere("{author}.id = ?", -1) // No match
			.executeStreaming(db, author -> {
				streamedAuthors.add(author);
			});

		Assertions.assertEquals(0, streamedAuthors.size());
	}

	@Test
	public void testStreamingWithConsumer_singleEntity() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			insertTestData(c);
		});

		List<AuthorWithBooks> streamedAuthors = new ArrayList<>();
		
		PojoQuery.build(AuthorWithBooks.class)
			.addWhere("{author}.name = ?", "Agatha Christie")
			.executeStreaming(db, author -> {
				streamedAuthors.add(author);
			});

		Assertions.assertEquals(1, streamedAuthors.size());
		Assertions.assertEquals("Agatha Christie", streamedAuthors.get(0).name);
		Assertions.assertEquals(2, streamedAuthors.get(0).books.size());
	}

	@Test
	public void testStreamingWithConnection() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			insertTestData(c);
			
			List<Author> streamedAuthors = new ArrayList<>();
			
			PojoQuery.build(Author.class)
				.executeStreaming(c, author -> {
					streamedAuthors.add(author);
				});

			Assertions.assertEquals(3, streamedAuthors.size());
		});
	}

	@Test
	public void testStreaming_resultsMatchNonStreamingExecution() {
		DataSource db = initDatabase();
		
		DB.withConnection(db, (Connection c) -> {
			insertTestData(c);
		});

		// Get results using regular execute
		List<AuthorWithBooks> regularResults = PojoQuery.build(AuthorWithBooks.class)
			.addOrderBy("{author}.name ASC")
			.execute(db);

		// Get results using streaming
		List<AuthorWithBooks> streamedResults = new ArrayList<>();
		PojoQuery.build(AuthorWithBooks.class)
			.addOrderBy("{author}.name ASC")
			.executeStreaming(db, streamedResults::add);

		// Both should have same number of entities
		Assertions.assertEquals(regularResults.size(), streamedResults.size());
		
		// Compare each entity
		for (int i = 0; i < regularResults.size(); i++) {
			AuthorWithBooks regular = regularResults.get(i);
			AuthorWithBooks streamed = streamedResults.get(i);
			
			Assertions.assertEquals(regular.id, streamed.id);
			Assertions.assertEquals(regular.name, streamed.name);
			Assertions.assertEquals(regular.books.size(), streamed.books.size());
		}
	}

	private void insertTestData(Connection c) {
		// Authors
		Author agatha = new Author();
		agatha.name = "Agatha Christie";
		agatha.birthYear = 1890;
		PojoQuery.insert(c, agatha);

		Author george = new Author();
		george.name = "George Orwell";
		george.birthYear = 1903;
		PojoQuery.insert(c, george);

		Author jane = new Author();
		jane.name = "Jane Austen";
		jane.birthYear = 1775;
		PojoQuery.insert(c, jane);

		// Books by Agatha Christie
		Book murderOnOrientExpress = new Book();
		murderOnOrientExpress.title = "Murder on the Orient Express";
		murderOnOrientExpress.publicationYear = 1934;
		murderOnOrientExpress.author = agatha;
		PojoQuery.insert(c, murderOnOrientExpress);

		Book andThenThereWereNone = new Book();
		andThenThereWereNone.title = "And Then There Were None";
		andThenThereWereNone.publicationYear = 1939;
		andThenThereWereNone.author = agatha;
		PojoQuery.insert(c, andThenThereWereNone);

		// Books by George Orwell
		Book nineteenEightyFour = new Book();
		nineteenEightyFour.title = "1984";
		nineteenEightyFour.publicationYear = 1949;
		nineteenEightyFour.author = george;
		PojoQuery.insert(c, nineteenEightyFour);

		Book animalFarm = new Book();
		animalFarm.title = "Animal Farm";
		animalFarm.publicationYear = 1945;
		animalFarm.author = george;
		PojoQuery.insert(c, animalFarm);

		// Book by Jane Austen
		Book prideAndPrejudice = new Book();
		prideAndPrejudice.title = "Pride and Prejudice";
		prideAndPrejudice.publicationYear = 1813;
		prideAndPrejudice.author = jane;
		PojoQuery.insert(c, prideAndPrejudice);
	}

	private static DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Author.class, Book.class);
		return db;
	}
}
