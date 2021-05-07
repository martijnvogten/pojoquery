package nl.pojoquery;

import nl.pojoquery.DB.QuoteStyle;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

public class TestHelperMethods {
	private static QuoteStyle restoreQuoteStyle;

	@BeforeClass
	public static void saveQuoteStyle() {
		restoreQuoteStyle = DB.quoteStyle;
	}

	@AfterClass
	public static void restoreQuoteStyle() {
		DB.quoteStyle = restoreQuoteStyle;
	}

	@Test
	public void testPrefixAndQuoteTableName() {
		DB.quoteStyle = QuoteStyle.ANSI;
		Assert.assertEquals("\"foo\"", DB.prefixAndQuoteTableName(null, "foo"));
		Assert.assertEquals("\"bar\".\"foo\"", DB.prefixAndQuoteTableName("bar", "foo"));
		DB.quoteStyle = QuoteStyle.MYSQL;
		Assert.assertEquals("`foo`", DB.prefixAndQuoteTableName(null, "foo"));
		Assert.assertEquals("`bar`.`foo`", DB.prefixAndQuoteTableName("bar", "foo"));
	}

	@Test
	public void testQuoteObjectNames() {
		DB.quoteStyle = QuoteStyle.ANSI;
		Assert.assertEquals("", DB.quoteObjectNames());
		Assert.assertEquals("\"foo\"", DB.quoteObjectNames("foo"));
		Assert.assertEquals("\"foo\".\"bar\".\"baz\"", DB.quoteObjectNames("foo", "bar", "baz"));

		DB.quoteStyle = QuoteStyle.MYSQL;
		Assert.assertEquals("`foo`", DB.quoteObjectNames("foo"));
		Assert.assertEquals("`foo`.`bar`.`baz`", DB.quoteObjectNames("foo", "bar", "baz"));
	}
}
