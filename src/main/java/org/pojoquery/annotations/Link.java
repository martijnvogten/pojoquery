package org.pojoquery.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Configures the relationship between entities for collection or reference fields.
 * 
 * <p>Use this annotation to customize how PojoQuery joins related tables.
 * For simple foreign key relationships, no annotation is needed—PojoQuery
 * infers the join automatically. Use {@code @Link} for many-to-many relationships
 * or when you need to customize the join behavior.</p>
 * 
 * <h2>Many-to-Many Example</h2>
 * <pre>{@code
 * @Table("event")
 * public class Event {
 *     @Id Long id;
 *     String title;
 * }
 * 
 * @Table("person")
 * public class Person {
 *     @Id Long id;
 *     String name;
 * }
 * 
 * // Query class with many-to-many relationship
 * @Table("event")
 * public class EventWithPersons {
 *     @Id Long id;
 *     String title;
 *     
 *     @Link(linktable = "event_person")
 *     List<Person> attendees;
 * }
 * }</pre>
 * 
 * @see JoinCondition
 * @see Table
 */
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
