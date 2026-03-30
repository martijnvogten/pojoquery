package org.pojoquery.processor;

import org.junit.Test;
import org.junit.jupiter.api.Disabled;
import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

public class TestInnerClasses {

	@GenerateQuery
	@Table("book")
	static class InnerBook {
		@Id
		Long id;
		String title;
	}

	@Test
	@Disabled("Continue here after fixing the code generation for inner classes")
	public void test() {
		// TestInnerClasses$InnerBookQuery q = new TestInnerClasses$InnerBookQuery();
		// System.out.println("SQL: " + q.where().id.eq(1L).and(q.title.eq("The Hobbit").or().title.eq("The Lord of the Rings")).toSql());
	}
}
