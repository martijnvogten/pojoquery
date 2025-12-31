package org.pojoquery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as NOT NULL in the database schema.
 * When applied to a field, the generated column will have a NOT NULL constraint.
 * 
 * <p>Note: Primary key fields (annotated with {@link Id}) are automatically NOT NULL
 * and do not need this annotation.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * &#64;Table("users")
 * public class User {
 *     &#64;Id
 *     public Long id;
 *     
 *     &#64;NotNull
 *     public String username;  // Will be VARCHAR(255) NOT NULL
 *     
 *     public String bio;       // Will be VARCHAR(255) (nullable)
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NotNull {

}
