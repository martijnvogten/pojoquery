package org.pojoquery.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Link {
	public static final String NONE = "--NONE--";
	public static final class DEFAULT {}
	
	String linktable() default NONE;
	String linkschema() default "";
	String fetchColumn() default NONE;
	String foreignlinkfield() default NONE;
	String linkfield() default NONE;
	
	/**
	 * Whether the foreign key column allows NULL values.
	 * Default is true (nullable).
	 */
	boolean nullable() default true;
	
	/**
	 * Whether the foreign key column has a UNIQUE constraint.
	 * Default is false.
	 */
	boolean unique() default false;
}
