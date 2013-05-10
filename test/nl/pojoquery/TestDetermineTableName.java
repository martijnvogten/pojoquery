package nl.pojoquery;

import static org.junit.Assert.*;
import nl.pojoquery.annotations.Table;

import org.junit.Test;

public class TestDetermineTableName {

	static class Article {
	}

	@Table("comment")
	static class CommentDetail {
	}

	@Table("user")
	static class UserRecord {
	}

	static class UserDetail extends UserRecord {
	}

	@Test
	public void testDetermineTableName() {
		assertNull(PojoQuery.determineTableName(Article.class));
		assertNull(PojoQuery.determineTableName(Long.class));
		assertEquals("comment", PojoQuery.determineTableName(CommentDetail.class));
		assertEquals("user", PojoQuery.determineTableName(UserDetail.class));
	}
}
