package org.pojoquery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies an aggregate SQL expression for a field.
 * 
 * <p>Similar to {@link Select}, but signals that this field uses an aggregate function
 * (COUNT, SUM, MAX, MIN, AVG, etc.). When any field in a class has {@code @Aggregate},
 * PojoQuery automatically adds a GROUP BY clause for non-aggregate fields.</p>
 * 
 * <h2>Example</h2>
 * <pre>{@code
 * @From(FilmWithRentals.class)
 * public class FilmStats {
 *     Long filmId;
 *     String title;
 *     
 *     @Aggregate("COUNT({inventory.inventoryId})")
 *     Long copyCount;
 *     
 *     @Aggregate("SUM({inventory.rentals.amount})")
 *     BigDecimal totalRevenue;
 * }
 * // Automatically generates: GROUP BY film.film_id, film.title
 * }</pre>
 * 
 * <p>This eliminates the need for an explicit {@link GroupBy} annotation in most cases.</p>
 * 
 * @see Select
 * @see From
 * @see GroupBy
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Aggregate {

	/**
	 * The aggregate SQL expression for this field.
	 * Use {@code {alias}} syntax to reference table aliases.
	 * 
	 * @return the aggregate expression (e.g., "COUNT(*)", "SUM({inventory.amount})")
	 */
	String value();

}
