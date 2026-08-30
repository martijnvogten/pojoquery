package org.pojoquery.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * The indentation rules of {@link SqlIndenter}, stated one at a time.
 *
 * <p>The input of each test is deliberately written flush left: the indenter
 * derives indentation from structure alone, so what a fragment came in as
 * carries no information.</p>
 */
public class SqlIndenterTest {

	@Test
	public void selectItemsSitUnderTheirClause() {
		assertEquals("""
				SELECT
				  "a"."id",
				  "a"."name"
				FROM "a" AS "a"
				WHERE "a"."id" = ?
				  AND "a"."name" = ?""",
				SqlIndenter.indent("""
						SELECT
						"a"."id",
						"a"."name"
						FROM "a" AS "a"
						WHERE "a"."id" = ?
						AND "a"."name" = ?"""));
	}

	@Test
	public void aGroupIndentsItsContentAndItsCloserReturnsToTheOpener() {
		assertEquals("""
				SELECT
				  JSON_OBJECT(
				    'id', "a"."id"
				  ) AS "json"
				FROM "a" AS "a\"""",
				SqlIndenter.indent("""
						SELECT
						JSON_OBJECT(
						'id', "a"."id"
						) AS "json"
						FROM "a" AS "a\""""));
	}

	/**
	 * The two closers below sit at the same parenthesis depth but at different
	 * levels, because their opening lines do: a closer follows its opener, not
	 * the depth it happens to be at.
	 */
	@Test
	public void closersFollowTheirOwnOpeningLine() {
		assertEquals("""
				SELECT
				  JSON_OBJECT(
				    'id', "a"."id"
				  ) AS "json"
				FROM "a" AS "a"
				LEFT JOIN (
				  SELECT
				    "b"."a_id"
				  FROM "b" AS "b"
				) AS "b" ON "b"."a_id" = "a"."id\"""",
				SqlIndenter.indent("""
						SELECT
						JSON_OBJECT(
						'id', "a"."id"
						) AS "json"
						FROM "a" AS "a"
						LEFT JOIN (
						SELECT
						"b"."a_id"
						FROM "b" AS "b"
						) AS "b" ON "b"."a_id" = "a"."id\""""));
	}

	@Test
	public void groupsNestArbitrarilyDeep() {
		assertEquals("""
				SELECT
				  JSON_ARRAYAGG(
				    JSON_OBJECT(
				      'lines', JSON_ARRAYAGG(
				        JSON_OBJECT(
				          'id', "l"."id"
				        )
				      )
				    )
				  ) AS "json\"""",
				SqlIndenter.indent("""
						SELECT
						JSON_ARRAYAGG(
						JSON_OBJECT(
						'lines', JSON_ARRAYAGG(
						JSON_OBJECT(
						'id', "l"."id"
						)
						)
						)
						) AS "json\""""));
	}

	/**
	 * A line that opens a group after closing one on the same line - a column
	 * list before a CTE body - opens it at the line's own level.
	 */
	@Test
	public void aGroupClosedOnItsOwnLineDoesNotShiftTheNextOne() {
		assertEquals("""
				WITH RECURSIVE "cte" ("root_id", "id") AS (
				  SELECT
				    "c"."id"
				  FROM "c" AS "c"
				  UNION ALL
				  SELECT
				    "c"."id"
				  FROM "c" AS "c"
				)
				SELECT
				  "c"."id"
				FROM "cte" AS "c\"""",
				SqlIndenter.indent("""
						WITH RECURSIVE "cte" ("root_id", "id") AS (
						SELECT
						"c"."id"
						FROM "c" AS "c"
						UNION ALL
						SELECT
						"c"."id"
						FROM "c" AS "c"
						)
						SELECT
						"c"."id"
						FROM "cte" AS "c\""""));
	}

	@Test
	public void parenthesesInsideLiteralsAndIdentifiersAreNotStructure() {
		assertEquals("""
				SELECT
				  'a ) and a (' AS "x",
				  "weird ( column" AS "y",
				  `also ) odd` AS "z"
				FROM "a" AS "a\"""",
				SqlIndenter.indent("""
						SELECT
						'a ) and a (' AS "x",
						"weird ( column" AS "y",
						`also ) odd` AS "z"
						FROM "a" AS "a\""""));
	}

	/** A doubled quote escapes the delimiter rather than ending the literal. */
	@Test
	public void escapedQuotesDoNotEndALiteral() {
		assertEquals("""
				SELECT
				  'it''s ( fine' AS "x"
				FROM "a" AS "a\"""",
				SqlIndenter.indent("""
						SELECT
						'it''s ( fine' AS "x"
						FROM "a" AS "a\""""));
	}

	/** A literal's own line breaks are part of its value, so it is left alone. */
	@Test
	public void aLiteralSpanningLinesIsPassedThroughUntouched() {
		assertEquals("""
				SELECT
				  'first
				    second ( third' AS "x"
				FROM "a" AS "a\"""",
				SqlIndenter.indent("""
						SELECT
						'first
						    second ( third' AS "x"
						FROM "a" AS "a\""""));
	}

	@Test
	public void commentsDoNotCountAsStructure() {
		assertEquals("""
				SELECT
				  "a"."id" -- not ( a group
				FROM "a" AS "a"
				WHERE /* nor ) this */ "a"."id" = ?""",
				SqlIndenter.indent("""
						SELECT
						"a"."id" -- not ( a group
						FROM "a" AS "a"
						WHERE /* nor ) this */ "a"."id" = ?"""));
	}

	/** Indentation is a property of the finished statement, not of its history. */
	@Test
	public void indentingIsIdempotent() {
		String source = """
				SELECT
				JSON_OBJECT(
				'id', "a"."id"
				) AS "json"
				FROM "a" AS "a"
				LEFT JOIN (
				SELECT
				"b"."a_id"
				FROM "b" AS "b"
				) AS "b" ON "b"."a_id" = "a"."id\"""";
		String once = SqlIndenter.indent(source);
		assertEquals(once, SqlIndenter.indent(once));
	}

	@Test
	public void blankLinesAndTrailingSpaceAreRemoved() {
		assertEquals("""
				SELECT
				  "a"."id"
				FROM "a" AS "a\"""",
				SqlIndenter.indent("SELECT \n \"a\".\"id\" \n   \n\nFROM \"a\" AS \"a\" "));
	}

	/** An unbalanced closer must not indent the rest of the statement negatively. */
	@Test
	public void aStrayCloserIsSurvivable() {
		assertEquals("""
				SELECT
				  "a"."id"
				)
				FROM "a" AS "a\"""",
				SqlIndenter.indent("""
						SELECT
						"a"."id"
						)
						FROM "a" AS "a\""""));
	}

	@Test
	public void singleLineAndNullStatementsArePassedThrough() {
		String oneLine = "SELECT 1 FROM \"a\" AS \"a\"";
		assertSame(oneLine, SqlIndenter.indent(oneLine));
		assertNull(SqlIndenter.indent(null));
	}
}
