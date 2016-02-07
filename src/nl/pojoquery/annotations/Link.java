package nl.pojoquery.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Link {
	public static final String NONE = "--NONE--";
	public static final class DEFAULT {}
	
	String linktable() default NONE;
	Class<?> resultClass() default DEFAULT.class;
	String foreignvaluefield() default NONE;
	String foreignidfield() default NONE;
}
