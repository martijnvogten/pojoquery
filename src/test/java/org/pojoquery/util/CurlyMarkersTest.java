package org.pojoquery.util;

import org.junit.Assert;
import org.junit.Test;

public class CurlyMarkersTest {

	@Test
	public void testBasics() {
		Assert.assertEquals("that", CurlyMarkers.processMarkers("{this}", marker -> "that"));
		Assert.assertEquals("that that", CurlyMarkers.processMarkers("{this} {these}", marker -> "that"));
		
		Assert.assertEquals("that", CurlyMarkers.processMarkers("{this}", marker -> {
			Assert.assertEquals("this", marker);
			return "that";
		}));
		
		Assert.assertEquals("that", CurlyMarkers.processMarkers("{this.is.the.marker}", marker -> "that"));
		
		Assert.assertEquals("b", CurlyMarkers.processMarkers("{a}", marker -> "a".equals(marker) ? "b" : "c"));
	}
}
