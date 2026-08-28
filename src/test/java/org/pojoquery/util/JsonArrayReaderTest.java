package org.pojoquery.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

public class JsonArrayReaderTest {

	@Test
	public void readsScalarsAsText() {
		// Numbers and booleans stay text: one conversion path, no rounding.
		assertEquals(List.of("1", "12.3456", "true", "-2e9"),
				JsonArrayReader.read("[\"1\", 12.3456, true, -2e9]"));
		assertEquals(Arrays.asList("a", null), JsonArrayReader.read("[\"a\", null]"));
		assertNull(JsonArrayReader.read(null));
	}

	@Test
	public void readsNestedArrays() {
		assertEquals(List.of("7", List.of(List.of("1", "Dune"), List.of("2", "Ubik"))),
				JsonArrayReader.read("[\"7\",[[\"1\",\"Dune\"],[\"2\",\"Ubik\"]]]"));
		assertEquals(List.of("7", List.of()), JsonArrayReader.read("[\"7\",[]]"));
	}

	@Test
	public void unescapesStrings() {
		assertEquals(List.of("a\"b\\c/d\n\té"),
				JsonArrayReader.read("[\"a\\\"b\\\\c\\/d\\n\\t\\u00e9\"]"));
		// The text form of a nested array, as HSQLDB returns it
		assertEquals(List.of("[[1,\"Dune\"]]"), JsonArrayReader.read("[\"[[1,\\\"Dune\\\"]]\"]"));
	}

	/**
	 * A stored value must never be able to alter the document's structure: the
	 * database escapes it and the reader unescapes symmetrically, at every
	 * nesting level.
	 */
	@Test
	public void valuesCannotBreakOutOfTheirSlot() {
		assertEquals(List.of("1", "x\"],[9999,\"pwn", "2"),
				JsonArrayReader.read("[\"1\",\"x\\\"],[9999,\\\"pwn\",\"2\"]"));
		// A value ending in a backslash must not swallow the closing quote
		assertEquals(List.of("trailing\\", "next"),
				JsonArrayReader.read("[\"trailing\\\\\",\"next\"]"));
		// Escaped quotes inside a nested document, as a text-nested array arrives
		assertEquals(List.of("[[1,\"a\\\"b\"]]"),
				JsonArrayReader.read("[\"[[1,\\\"a\\\\\\\"b\\\"]]\"]"));
	}

	/** Deep nesting must fail with an exception, never a StackOverflowError. */
	@Test
	public void capsNestingDepth() {
		String deep = "[".repeat(100_000) + "]".repeat(100_000);
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> JsonArrayReader.read(deep));
		assertEquals(true, e.getMessage().contains("nesting deeper than"));
		// A document as deep as any real query tree still parses
		String fine = "[".repeat(200) + "]".repeat(200);
		assertEquals(1, ((List<?>) JsonArrayReader.read(fine)).size());
	}

	@Test
	public void rejectsMalformedDocuments() {
		assertThrows(IllegalArgumentException.class, () -> JsonArrayReader.read("[1,"));
		assertThrows(IllegalArgumentException.class, () -> JsonArrayReader.read("[\"a\"] junk"));
		assertThrows(IllegalArgumentException.class, () -> JsonArrayReader.read("[\"unterminated"));
		// Positional documents never contain objects; say so rather than half-parsing
		assertThrows(IllegalArgumentException.class, () -> JsonArrayReader.read("[{\"id\":1}]"));
		// A unicode escape must be four hex digits; Integer.parseInt alone would take "+041"
		assertThrows(IllegalArgumentException.class, () -> JsonArrayReader.read("[\"\\u+041\"]"));
		assertThrows(IllegalArgumentException.class, () -> JsonArrayReader.read("[\"\\u00\"]"));
	}
}
