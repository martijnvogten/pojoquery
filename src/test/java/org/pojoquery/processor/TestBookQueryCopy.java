package org.pojoquery.processor;

import org.junit.Test;

public class TestBookQueryCopy {

	@Test
	public void test() {
		BookQuery q = new BookQuery();
		q.groupBy().id
			.orderBy().title.asc()
			.orderBy().author.name.desc();
			
		System.out.println("SQL: " + q.where().id.eq(1L).and(q.title.eq("The Hobbit").or().title.eq("The Lord of the Rings")).toSql());
		System.out.println("SQL: " + q.where().categories.name.eq("Fantasy").and().reviews.rating.eq(4).toSql());
	}
}
