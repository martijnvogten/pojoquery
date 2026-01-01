package org.pojoquery.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Specifies a custom SQL join condition for a relationship.
 * 
 * <p>Use this annotation when the default foreign key join isn't sufficient,
 * such as when you need additional conditions or a non-standard join.</p>
 * 
 * <h2>Filtered Relationship Example</h2>
 * <pre>{@code
 * @Table("event")
 * public class EventWithActiveParticipants {
 *     @Id Long id;
 *     String title;
 *     
 *     @Link(linktable = "event_person")
 *     @JoinCondition("{this}.id = {linktable}.event_id AND {linktable}.role = 'active'")
 *     List<Person> activeParticipants;
 * }
 * }</pre>
 * 
 * <h2>Alias Placeholders</h2>
 * <ul>
 *   <li>{@code {this}} - The current table's alias</li>
 *   <li>{@code {linktable}} - The link/junction table's alias (for many-to-many)</li>
 *   <li>{@code {fieldname}} - The alias of any joined table by field name</li>
 * </ul>
 * 
 * @see Link
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface JoinCondition {
	/**
	 * The SQL join condition expression.
	 * Use {@code {this}}, {@code {linktable}}, or {@code {fieldname}} placeholders
	 * for table aliases.
	 * 
	 * @return the join condition SQL
	 */
	String value();
}
