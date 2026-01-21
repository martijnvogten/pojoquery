package examples.bookstore;

import java.util.List;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.pojoquery.DB;
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

    @Table("author")
    static class Author {
        @Id
        Long id;
        String name;
        String country;
    }

    @Table("book")
    static class Book {
        @Id
        Long id;
        String title;
        Integer year;
        Author author;
    }

    @Table("review")
    static class Review {
        @Id
        Long id;
        Book book;
        Integer rating;
        String comment;
    }

    static class BookDetail extends Book {
        List<Review> reviews;
    }

    public static void main(String[] args) {
        // 1. Create an in-memory database
        DataSource db = createDatabase();

        // 2. Generate tables (and foreign keys) from the joint set of all 
        //    fields and associations of the POJOs
        SchemaGenerator.createTables(db, Author.class, Book.class, BookDetail.class, Review.class);

        DB.withConnection(db, c -> {
            // 3. Insert test data
            Author tolkien = new Author();
            tolkien.name = "J.R.R. Tolkien";
            tolkien.country = "UK";
            PojoQuery.insert(c, tolkien);

            Book lotr = new Book();
            lotr.title = "The Lord of the Rings";
            lotr.year = 1954;
            lotr.author = tolkien;
            PojoQuery.insert(c, lotr);

            Review r1 = new Review();
            r1.book = lotr;
            r1.rating = 5;
            r1.comment = "A masterpiece!";
            PojoQuery.insert(c, r1);

            Review r2 = new Review();
            r2.book = lotr;
            r2.rating = 5;
            r2.comment = "Epic fantasy at its finest.";
            PojoQuery.insert(c, r2);

            // 4. Query with automatic joins - the POJO shape defines what you get!
            List<BookDetail> books = PojoQuery.build(BookDetail.class)
                    .addWhere("{author}.country = ?", "UK")
                    .execute(c);

            for (BookDetail book : books) {
                System.out.println(book.title + " by " + book.author.name);
                System.out.println("Reviews: " + book.reviews.size());
                for (Review review : book.reviews) {
                    System.out.println("  ★" + review.rating + " - " + review.comment);
                }
            }
        });
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
