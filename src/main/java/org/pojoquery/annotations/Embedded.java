package org.pojoquery.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Embedded {
	public static final String DEFAULT = "__DEFAULT__";
	String prefix() default DEFAULT;
}
