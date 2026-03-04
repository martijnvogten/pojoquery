package org.pojoquery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE}) 
@Retention(RetentionPolicy.RUNTIME)
public @interface DiscriminatorValue {

	/** The value that indicates that the row is an entity of the annotated entity type. */
    String value();
}