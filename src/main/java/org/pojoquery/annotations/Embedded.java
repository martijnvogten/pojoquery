package org.pojoquery.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Embeds a value object's fields directly into the parent entity's table.
 * 
 * <p>Use this annotation when you want to group related columns into a reusable
 * class without creating a separate database table. The embedded object's fields
 * are stored as columns in the parent table.</p>
 * 
 * <h2>Example</h2>
 * <pre>{@code
 * // Reusable address value object (no @Table annotation)
 * public class Address {
 *     String street;
 *     String city;
 *     String zipCode;
 *     String country;
 * }
 * 
 * @Table("customers")
 * public class Customer {
 *     @Id Long id;
 *     String name;
 *     
 *     @Embedded(prefix = "billing_")
 *     Address billingAddress;  // Creates billing_street, billing_city, etc.
 *     
 *     @Embedded(prefix = "shipping_")
 *     Address shippingAddress; // Creates shipping_street, shipping_city, etc.
 * }
 * }</pre>
 * 
 * @see Table
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Embedded {
	public static final String DEFAULT = "__DEFAULT__";
	
	/**
	 * Prefix to add to all embedded field column names.
	 * Use this to disambiguate when embedding the same type multiple times.
	 * 
	 * @return the column name prefix
	 */
	String prefix() default DEFAULT;
}
