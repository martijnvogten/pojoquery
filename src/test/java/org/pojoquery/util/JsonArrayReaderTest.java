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

	@Test
	public void rejectsMalformedDocuments() {
		assertThrows(IllegalArgumentException.class, () -> JsonArrayReader.read("[1,"));
		assertThrows(IllegalArgumentException.class, () -> JsonArrayReader.read("[\"a\"] junk"));
		assertThrows(IllegalArgumentException.class, () -> JsonArrayReader.read("[\"unterminated"));
		// Positional documents never contain objects; say so rather than half-parsing
		assertThrows(IllegalArgumentException.class, () -> JsonArrayReader.read("[{\"id\":1}]"));
	}
}
