package org.pojoquery.util;

import java.util.Collection;

public class Iterables {

	public static final <T> void addAll(Collection<T> target, Iterable<T> iterable) {
		iterable.forEach(target::add);
	}

	@SuppressWarnings("unused")
	public static int size(Iterable<?> iterable) {
		int count = 0;
		for (Object ignored : iterable) {
			count++;
		}
		return count;
	}
}
