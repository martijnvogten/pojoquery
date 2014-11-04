package nl.pojoquery.parser;

import org.junit.Assert;
import org.junit.Test;

public class TestParser {
	@Test
	public void testSimpleParse() {
		Assert.assertEquals("[[?]]", findParams("?"));
		Assert.assertEquals("'?'", findParams("'?'"));
		
		Assert.assertEquals("'?' [[?]]", findParams("'?' ?"));
		Assert.assertEquals("'?\\'\\r\r\n' [[?]]", findParams("'?\\'\\r\r\n' ?"));
		
		Assert.assertEquals("[[:jantje]]", findParams(":jantje"));
		Assert.assertEquals("':a' [[:a]]", findParams("':a' :a"));
		Assert.assertEquals("':a' [[:locale_id]],[[?]]", findParams("':a' :locale_id,?"));
		Assert.assertEquals("':a' [[:locale_id]],'?'", findParams("':a' :locale_id,'?'"));
		Assert.assertEquals("':a' [[:locale_id]],[[?]]", findParams("':a' :locale_id,?"));
	}
	
	private String findParams(String sql) {
		StatementParameterMatcher m = new StatementParameterMatcher(sql);
		StringBuilder result = new StringBuilder();
		while(m.find()) {
			m.appendReplacement(result, "[[" + m.getParameterName() + "]]");
		}
		m.appendTail(result);
		
		return result.toString();
	}

	@Test
	public void testColons() {
		Assert.assertEquals(": :,:", findParams(": :,:"));
		Assert.assertEquals("SELECT 1 :: int", findParams("SELECT 1 :: int"));
	}
	
	@Test(expected=RuntimeException.class)
	public void testUnclosedLiteral() {
		findParams("'");
	}

}
