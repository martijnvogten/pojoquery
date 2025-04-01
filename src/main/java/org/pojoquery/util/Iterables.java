package org.pojoquery.util;

import java.util.Collection;

public class Iterables {

	public static final <T> void addAll(Collection<T> target, Iterable<T> iterable) {
		for(T el : iterable) {
			target.add(el);
		}
	}
}
