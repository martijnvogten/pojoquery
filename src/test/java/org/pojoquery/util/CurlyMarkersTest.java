package org.pojoquery.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CurlyMarkersTest {

	@Test
	public void testBasics() {
		Assertions.assertEquals("that", CurlyMarkers.processMarkers("{this}", marker -> "that"));
		Assertions.assertEquals("that that", CurlyMarkers.processMarkers("{this} {these}", marker -> "that"));
		
		Assertions.assertEquals("that", CurlyMarkers.processMarkers("{this}", marker -> {
			Assertions.assertEquals("this", marker);
			return "that";
		}));
		
		Assertions.assertEquals("that", CurlyMarkers.processMarkers("{this.is.the.marker}", marker -> "that"));
		
		Assertions.assertEquals("b", CurlyMarkers.processMarkers("{a}", marker -> "a".equals(marker) ? "b" : "c"));
	}
}
