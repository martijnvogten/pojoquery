package org.pojoquery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as having a UNIQUE constraint in the database schema.
 * When applied to a field, the generated column will have a UNIQUE constraint.
 * 
 * <p>Example usage:</p>
 * <pre>
 * &#64;Table("users")
 * public class User {
 *     &#64;Id
 *     public Long id;
 *     
 *     &#64;NotNull
 *     &#64;Unique
 *     public String username;  // Will be VARCHAR(255) NOT NULL UNIQUE
 *     
 *     &#64;Unique
 *     public String email;     // Will be VARCHAR(255) UNIQUE (nullable but unique)
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Unique {

}
