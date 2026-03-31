package org.pojoquery.processor;

import java.util.List;

import org.junit.Test;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;

public class TestBookQueryCopy {

	@Table("book")
	public static class Book {
		@Id
		public Long id;
		public String title;
		public Author author;
		public List<Review> reviews; // One-to-many
		@Link(linktable = "book_category")
		public List<Category> categories; // Many-to-many via join table
	}

	@Table("author")
	public static class Author {
		@Id
		public Long id;
		public String name;
	}

	@Table("review")
	public static class Review {
		@Id
		public Long id;
		public String content;
		public Integer rating;
	}

	@Table("category")
	public static class Category {
		@Id
		public Long id;
		public String name;
	}

	@Test
	public void test() {
		BookQuery q = new BookQuery();
		q.groupBy().id
				.orderBy().title.asc()
				.orderBy().author.name.desc();

		System.out.println("SQL: "
				+ q.where().id.eq(1L).and(q.title.eq("The Hobbit").or().title.eq("The Lord of the Rings")).toSql());
		System.out.println("SQL: " + q.where().categories.name.eq("Fantasy").and().reviews.rating.eq(4).toSql());
	}
}
