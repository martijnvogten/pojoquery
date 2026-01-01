package org.pojoquery.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Specifies a custom SQL expression for a field.
 * 
 * <p>Use this annotation to define computed columns, SQL functions, or any
 * custom SQL expression. The expression can reference table aliases using
 * the {@code {alias}} syntax.</p>
 * 
 * <h2>Basic Example</h2>
 * <pre>{@code
 * @Table("users")
 * public class UserWithFullName {
 *     @Id Long id;
 *     String firstName;
 *     String lastName;
 *     
 *     @Select("CONCAT({this}.first_name, ' ', {this}.last_name)")
 *     String fullName;  // Computed from first + last name
 * }
 * }</pre>
 * 
 * <h2>Aggregation Example</h2>
 * <pre>{@code
 * @Table("order")
 * public class OrderSummary {
 *     Long customerId;
 *     
 *     @Select("COUNT(*)")
 *     Integer orderCount;
 *     
 *     @Select("SUM({this}.total)")
 *     BigDecimal totalAmount;
 * }
 * }</pre>
 * 
 * @see Table
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Select {

	/**
	 * The SQL expression to use for this field.
	 * Use {@code {this}} to reference the current table's alias,
	 * or {@code {fieldname}} to reference other joined tables.
	 * 
	 * @return the SQL expression
	 */
	String value();

}
