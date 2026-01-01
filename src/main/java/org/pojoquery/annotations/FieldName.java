package org.pojoquery.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Maps a Java field to a database column with a different name.
 * 
 * <p>Use this annotation when the Java field name doesn't match the database
 * column name, for example when following different naming conventions.</p>
 * 
 * <h2>Example</h2>
 * <pre>{@code
 * @Table("users")
 * public class User {
 *     @Id Long id;
 *     
 *     @FieldName("first_name")
 *     String firstName;  // Maps to column 'first_name'
 *     
 *     @FieldName("last_name")
 *     String lastName;   // Maps to column 'last_name'
 *     
 *     @FieldName("email_address")
 *     String email;      // Maps to column 'email_address'
 * }
 * }</pre>
 * 
 * @see Table
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldName {
	/**
	 * The database column name.
	 * 
	 * @return the column name
	 */
	String value();
}
