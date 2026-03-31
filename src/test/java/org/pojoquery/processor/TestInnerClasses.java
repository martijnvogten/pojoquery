package org.pojoquery.processor;

import org.junit.Test;
import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

public class TestInnerClasses {
	@GenerateQuery
	@Table("book")
	static class Book {
		@Id
		Long id;
		String title;
	}

	@Test
	public void test() {
		TestInnerClasses_BookQuery q = new TestInnerClasses_BookQuery();
		System.out.println("SQL: " + q.where().id.eq(1L).and(q.title.eq("The Hobbit").or().title.eq("The Lord of the Rings")).toSql());
	}
}
