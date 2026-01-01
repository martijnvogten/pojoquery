package org.pojoquery;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.QuoteStyle;

public class TestHelperMethods {
	
	private static final DbContext ANSI_DB = new DbContextBuilder().withQuoteStyle(QuoteStyle.ANSI).build();
	private static final DbContext MYSQL_DB = new DbContextBuilder().withQuoteStyle(QuoteStyle.MYSQL).build();

	@Test
	public void testPrefixAndQuoteTableName() {
		Assertions.assertEquals("\"foo\"", DB.prefixAndQuoteTableName(ANSI_DB, null, "foo"));
		Assertions.assertEquals("\"bar\".\"foo\"", DB.prefixAndQuoteTableName(ANSI_DB, "bar", "foo"));
	
		Assertions.assertEquals("`foo`", DB.prefixAndQuoteTableName(MYSQL_DB, null, "foo"));
		Assertions.assertEquals("`bar`.`foo`", DB.prefixAndQuoteTableName(MYSQL_DB, "bar", "foo"));
	}

	@Test
	public void testQuoteObjectNames() {
		Assertions.assertEquals("", ANSI_DB.quoteObjectNames());
		Assertions.assertEquals("\"foo\"", ANSI_DB.quoteObjectNames("foo"));
		Assertions.assertEquals("\"foo\".\"bar\".\"baz\"", ANSI_DB.quoteObjectNames("foo", "bar", "baz"));

		Assertions.assertEquals("`foo`", MYSQL_DB.quoteObjectNames("foo"));
		Assertions.assertEquals("`foo`.`bar`.`baz`", MYSQL_DB.quoteObjectNames("foo", "bar", "baz"));
	}
}
