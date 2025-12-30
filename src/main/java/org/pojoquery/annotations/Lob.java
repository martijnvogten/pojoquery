package org.pojoquery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a String field to be stored as a CLOB (Character Large Object) instead of VARCHAR.
 * This is useful for storing large text content like articles, descriptions, or JSON data.
 * 
 * <p>Example usage:</p>
 * <pre>
 * &#64;Table("article")
 * public class Article {
 *     &#64;Id
 *     public Long id;
 *     
 *     public String title;
 *     
 *     &#64;Lob
 *     public String content;  // Will be stored as CLOB
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Lob {

}
