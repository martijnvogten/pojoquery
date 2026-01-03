package org.pojoquery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies that single table inheritance should be used for this class hierarchy.
 *
 * <p>When applied to a class with {@link SubClasses}, all subclasses will be stored
 * in the same table with a discriminator column to distinguish between types.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @Table("room")
 * @DiscriminatorColumn  // Uses default column name "dtype"
 * @SubClasses({BedRoom.class, Kitchen.class})
 * class Room {
 *     @Id Long id;
 *     Double area;
 * }
 *
 * @Table("room")  // Same table as parent
 * class BedRoom extends Room {
 *     Integer numberOfBeds;
 * }
 * }</pre>
 *
 * <p>The discriminator value for each class is its simple class name (e.g., "Room", "BedRoom").</p>
 *
 * @see SubClasses
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DiscriminatorColumn {

	/**
	 * The name of the discriminator column in the database table.
	 * Defaults to "dtype".
	 *
	 * @return the discriminator column name
	 */
	String name() default "dtype";
}
