package org.pojoquery.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks a field as the primary key of an entity.
 * 
 * <p>Every entity class should have exactly one field annotated with {@code @Id}.
 * This field is used for:</p>
 * <ul>
 *   <li>Identifying records in {@code update()} and {@code delete()} operations</li>
 *   <li>The {@code findById()} method</li>
 *   <li>Auto-generated primary key retrieval after {@code insert()}</li>
 * </ul>
 * 
 * <h2>Example</h2>
 * <pre>{@code
 * @Table("users")
 * public class User {
 *     @Id
 *     Long id;  // Auto-populated after insert
 *     
 *     String name;
 *     String email;
 * }
 * 
 * // After insert, id is populated with the generated key
 * User user = new User();
 * user.name = "John";
 * PojoQuery.insert(dataSource, user);
 * System.out.println("Generated ID: " + user.id);
 * }</pre>
 * 
 * @see Table
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Id {

}
