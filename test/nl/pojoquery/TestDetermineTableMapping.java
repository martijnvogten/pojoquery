package nl.pojoquery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import nl.pojoquery.annotations.Table;

import org.junit.Test;

public class TestDetermineTableMapping {

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
		assertNull(PojoQuery.determineTableMapping(Article.class));
		assertNull(PojoQuery.determineTableMapping(Long.class));
		assertEquals("comment", PojoQuery.determineTableMapping(CommentDetail.class).tableName);
		assertEquals("user", PojoQuery.determineTableMapping(UserDetail.class).tableName);
	}
}
