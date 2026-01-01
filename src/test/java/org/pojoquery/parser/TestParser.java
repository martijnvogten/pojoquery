package org.pojoquery.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class TestParser {
	@Test
	public void testSimpleParse() {
		assertEquals("[[?]]", findParams("?"));
		assertEquals("'?'", findParams("'?'"));
		
		assertEquals("'?' [[?]]", findParams("'?' ?"));
		assertEquals("'?\\'\\r\r\n' [[?]]", findParams("'?\\'\\r\r\n' ?"));
		
		assertEquals("[[:jantje]]", findParams(":jantje"));
		assertEquals("':a' [[:a]]", findParams("':a' :a"));
		assertEquals("':a' [[:locale_id]],[[?]]", findParams("':a' :locale_id,?"));
		assertEquals("':a' [[:locale_id]],'?'", findParams("':a' :locale_id,'?'"));
		assertEquals("':a' [[:locale_id]],[[?]]", findParams("':a' :locale_id,?"));
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
		assertEquals(": :,:", findParams(": :,:"));
		assertEquals("SELECT 1 :: int", findParams("SELECT 1 :: int"));
	}
	
	@Test
	public void testUnclosedLiteral() {
		assertThrows(RuntimeException.class, () -> findParams("'"));
	}

}
