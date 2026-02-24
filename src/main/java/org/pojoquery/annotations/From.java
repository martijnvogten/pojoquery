package org.pojoquery.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Specifies the source relation (tables and joins) for a query result class.
 * 
 * <p>Use this annotation when you want to define a projection or aggregate query
 * that selects specific fields from a more complex entity structure. The referenced
 * class defines all the tables and joins; this class defines only the fields to select.</p>
 * 
 * <h2>Example</h2>
 * <pre>{@code
 * // Source relation with all joins
 * public class FilmWithRentals extends Film {
 *     Inventory inventory;            // Film -> Inventory join
 *     List<Rental> rentals;           // Inventory -> Rentals join
 * }
 * 
 * // Projection/aggregate query using the source relation
 * @From(FilmWithRentals.class)
 * public class FilmStats {
 *     Long filmId;
 *     String title;
 *     
 *     @Aggregate("COUNT(DISTINCT {inventory.inventoryId})")
 *     Long copyCount;
 *     
 *     @Aggregate("SUM({rentals.amount})")
 *     BigDecimal totalRevenue;
 * }
 * }</pre>
 * 
 * <p>This separates the concern of defining the data structure (source relation)
 * from defining what to select (projection).</p>
 * 
 * @see Aggregate
 * @see Select
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface From {

	/**
	 * The source relation class that defines the tables and joins.
	 * 
	 * @return the class defining the source relation
	 */
	Class<?> value();

}
