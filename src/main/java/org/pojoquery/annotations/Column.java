package org.pojoquery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides additional column definition options for schema generation.
 * This annotation can be used to customize column properties like length,
 * precision, and scale.
 * 
 * <p>Example usage:</p>
 * <pre>
 * &#64;Table("users")
 * public class User {
 *     &#64;Id
 *     public Long id;
 *     
 *     &#64;Column(length = 50)
 *     public String username;     // Will be VARCHAR(50)
 *     
 *     &#64;Column(length = 100)
 *     public String email;        // Will be VARCHAR(100)
 *     
 *     public String description;  // Will be VARCHAR(255) - default length
 *     
 *     &#64;Column(precision = 10, scale = 2)
 *     public BigDecimal price;    // Will be DECIMAL(10,2)
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    
    /**
     * The length of a VARCHAR column. Only applicable to String fields.
     * Default is 255.
     */
    int length() default 255;
    
    /**
     * The precision for a DECIMAL column (total number of digits).
     * Only applicable to BigDecimal fields.
     * Default is 19.
     */
    int precision() default 19;
    
    /**
     * The scale for a DECIMAL column (number of digits after decimal point).
     * Only applicable to BigDecimal fields.
     * Default is 4.
     */
    int scale() default 4;
}
