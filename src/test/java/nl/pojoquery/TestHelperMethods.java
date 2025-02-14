package nl.pojoquery;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.pojoquery.DbContext.QuoteStyle;

public class TestHelperMethods {
	private static QuoteStyle restoreQuoteStyle;

	@BeforeClass
	public static void saveQuoteStyle() {
		restoreQuoteStyle = DbContext.getDefault().getQuoteStyle();
	}

	@AfterClass
	public static void restoreQuoteStyle() {
		DbContext.getDefault().setQuoteStyle(restoreQuoteStyle);
	}

	@Test
	public void testPrefixAndQuoteTableName() {
		DbContext db = DbContext.getDefault();
		db.setQuoteStyle(QuoteStyle.ANSI);
		Assert.assertEquals("\"foo\"", DB.prefixAndQuoteTableName(db, null, "foo"));
		Assert.assertEquals("\"bar\".\"foo\"", DB.prefixAndQuoteTableName(db, "bar", "foo"));
		db.setQuoteStyle(QuoteStyle.MYSQL);
		Assert.assertEquals("`foo`", DB.prefixAndQuoteTableName(db, null, "foo"));
		Assert.assertEquals("`bar`.`foo`", DB.prefixAndQuoteTableName(db, "bar", "foo"));
	}

	@Test
	public void testQuoteObjectNames() {
		DbContext db = DbContext.getDefault();
		
		db.setQuoteStyle(QuoteStyle.ANSI);
		Assert.assertEquals("", db.quoteObjectNames());
		Assert.assertEquals("\"foo\"", db.quoteObjectNames("foo"));
		Assert.assertEquals("\"foo\".\"bar\".\"baz\"", db.quoteObjectNames("foo", "bar", "baz"));

		db.setQuoteStyle(QuoteStyle.MYSQL);
		Assert.assertEquals("`foo`", db.quoteObjectNames("foo"));
		Assert.assertEquals("`foo`.`bar`.`baz`", db.quoteObjectNames("foo", "bar", "baz"));
	}
}
