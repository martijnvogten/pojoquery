package org.pojoquery.fluent;

import org.junit.Test;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

public class FluentConditionChainWithInterfaces {

	@Table("book")
	public static class Book {
		@Id
		public Long id;
		public String title;
		public Person author;
	}

	@Table("person")
	public static class Person {
		@Id
		public Long id;
		public String name;
	}

	@Test
	public void test() {
		BookQuery q = new BookQuery();
		SqlExpression a = q.title.eq("The Hobbit").and().title.eq("The Lord of the Rings").toSql();
		System.out.println("SQL: " + a.getSql());
		System.out.println("PARAMS: " + a.getParameters());
		System.out.println("SQL: " + new BookQuery().where().title.eq("The Hobbit").or().id.eq(1L).and().title.eq("The Lord of the Rings").toSql().getSql());
		
		// Test nested author access
		SqlExpression authorQuery = new BookQuery().where().author.name.eq("Tolkien").toSql();
		System.out.println("AUTHOR SQL: " + authorQuery.getSql());
	}
}
