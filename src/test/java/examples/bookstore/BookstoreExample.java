package examples.bookstore;

import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Self-contained example demonstrating PojoQuery's core philosophy:
 * POJOs define *what you want to retrieve*, not how data is stored.
 */
public class BookstoreExample {

    // === Entity definitions (map to database tables) ===
    
    @Table("author")
    static class Author {
        @Id Long id;
        String name;
        String country;
    }

    @Table("book")
    static class Book {
        @Id Long id;
        String title;
        Integer year;
        Author author;  // PojoQuery infers 'author_id' foreign key column
    }

    @Table("review")
    static class Review {
        @Id Long id;
        Book book;      // PojoQuery infers 'book_id' foreign key column
        Integer rating;
        String comment;
    }

    // === Query definitions (shape of results you want) ===
    
    // A book with author AND all its reviews
    static class BookDetail extends Book {
        List<Review> reviews;
    }

    public static void main(String[] args) {
        // 1. Create an in-memory database
        DataSource db = createDatabase();

        // 2. Generate tables from entity classes
        SchemaGenerator.createTables(db, Author.class, Book.class, Review.class);

        // 3. Insert test data
        Author tolkien = new Author();
        tolkien.name = "J.R.R. Tolkien";
        tolkien.country = "UK";
        tolkien.id = PojoQuery.insert(db, tolkien);

        Book lotr = new Book();
        lotr.title = "The Lord of the Rings";
        lotr.year = 1954;
        lotr.author = tolkien;
        lotr.id = PojoQuery.insert(db, lotr);

        Review r1 = new Review();
        r1.book = lotr;
        r1.rating = 5;
        r1.comment = "A masterpiece!";
        PojoQuery.insert(db, r1);

        Review r2 = new Review();
        r2.book = lotr;
        r2.rating = 5;
        r2.comment = "Epic fantasy at its finest.";
        PojoQuery.insert(db, r2);

        // 4. Query with automatic joins - the POJO shape defines what you get!
        List<BookDetail> books = PojoQuery.build(BookDetail.class)
            .addWhere("{author}.country = ?", "UK")
            .execute(db);

        for (BookDetail book : books) {
            System.out.println(book.title + " by " + book.author.name);
            System.out.println("Reviews: " + book.reviews.size());
            for (Review review : book.reviews) {
                System.out.println("  ★" + review.rating + " - " + review.comment);
            }
        }
    }

    static DataSource createDatabase() {
        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:bookstore");
        ds.setUser("SA");
        ds.setPassword("");
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));
        return ds;
    }
}
