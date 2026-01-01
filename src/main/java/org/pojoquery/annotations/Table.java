package org.pojoquery.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Maps a class to a database table.
 * 
 * <p>This is the primary annotation for defining entity classes. Every entity class
 * that represents a database table should be annotated with {@code @Table}.</p>
 * 
 * <h2>Basic Usage</h2>
 * <pre>{@code
 * @Table("users")
 * public class User {
 *     @Id Long id;
 *     String name;
 *     String email;
 * }
 * }</pre>
 * 
 * <h2>With Schema</h2>
 * <pre>{@code
 * @Table(value = "users", schema = "public")
 * public class User {
 *     @Id Long id;
 *     String name;
 * }
 * }</pre>
 * 
 * @see Id
 * @see FieldName
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Table {

	/**
	 * The name of the database table.
	 * 
	 * @return the table name
	 */
	String value();

	/**
	 * The database schema containing the table.
	 * 
	 * @return the schema name, or empty string for default schema
	 */
	String schema() default "";

}
