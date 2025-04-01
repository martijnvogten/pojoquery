package org.pojoquery;

import org.junit.Assert;
import org.junit.Test;
import org.pojoquery.DbContext.QuoteStyle;

public class TestHelperMethods {
	
	private static final DbContext ANSI_DB = new DbContextBuilder().withQuoteStyle(QuoteStyle.ANSI).build();
	private static final DbContext MYSQL_DB = new DbContextBuilder().withQuoteStyle(QuoteStyle.MYSQL).build();

	@Test
	public void testPrefixAndQuoteTableName() {
		Assert.assertEquals("\"foo\"", DB.prefixAndQuoteTableName(ANSI_DB, null, "foo"));
		Assert.assertEquals("\"bar\".\"foo\"", DB.prefixAndQuoteTableName(ANSI_DB, "bar", "foo"));
	
		Assert.assertEquals("`foo`", DB.prefixAndQuoteTableName(MYSQL_DB, null, "foo"));
		Assert.assertEquals("`bar`.`foo`", DB.prefixAndQuoteTableName(MYSQL_DB, "bar", "foo"));
	}

	@Test
	public void testQuoteObjectNames() {
		Assert.assertEquals("", ANSI_DB.quoteObjectNames());
		Assert.assertEquals("\"foo\"", ANSI_DB.quoteObjectNames("foo"));
		Assert.assertEquals("\"foo\".\"bar\".\"baz\"", ANSI_DB.quoteObjectNames("foo", "bar", "baz"));

		Assert.assertEquals("`foo`", MYSQL_DB.quoteObjectNames("foo"));
		Assert.assertEquals("`foo`.`bar`.`baz`", MYSQL_DB.quoteObjectNames("foo", "bar", "baz"));
	}
}
