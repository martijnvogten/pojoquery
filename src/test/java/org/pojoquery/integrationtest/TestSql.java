package org.pojoquery.integrationtest;

import org.pojoquery.DbContext;

/**
 * Quotes identifiers for the dialect currently under test.
 *
 * <p>Integration tests run against HSQLDB, MySQL and PostgreSQL in turn, so any literal
 * SQL they execute themselves has to be written for whichever dialect is active. Writing
 * {@code "product"} inline works on the ANSI dialects but MySQL reads a double-quoted
 * word as a string literal rather than an identifier, so such a statement fails there.</p>
 *
 * <p>Use this for setup, teardown and verification statements:</p>
 * <pre>{@code
 * DB.executeDDL(db, "ALTER TABLE " + q("room") + " ADD COLUMN " + q("area") + " INT");
 * }</pre>
 *
 * <p>Tests that assert on <em>generated</em> SQL text are a different matter: those pin a
 * dialect explicitly, for example with {@code PojoQuery.build(DbContext.forDialect(...))}.</p>
 */
public final class TestSql {

	private TestSql() {
	}

	/**
	 * Quotes one identifier, or joins several into a qualified name.
	 *
	 * @param names the identifier parts, e.g. {@code q("product")} or {@code q("product", "id")}
	 * @return the quoted name for the dialect under test
	 */
	public static String q(String... names) {
		return DbContext.getDefault().quoteObjectNames(names);
	}
}
