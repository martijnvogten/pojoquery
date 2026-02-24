package org.pojoquery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a subquery join. The field type must be a class with
 * {@link From} and {@link Aggregate} annotations, which will be rendered
 * as a derived table (subquery) and joined to the main query.
 * 
 * This allows composing multiple aggregate queries to avoid row multiplication
 * when aggregating over independent one-to-many relationships.
 * 
 * <pre>
 * &#64;From(FilmDetail.class)
 * class CategoryStats {
 *     Long filmId;
 *     &#64;Aggregate("GROUP_CONCAT({categories.name})")
 *     String categories;
 * }
 * 
 * &#64;From(FilmDetail.class)
 * class RentalStats {
 *     Long filmId;
 *     &#64;Aggregate("SUM({inventory.rentals.amount})")
 *     BigDecimal totalRevenue;
 * }
 * 
 * &#64;From(Film.class)
 * class CombinedStats {
 *     Long filmId;
 *     String title;
 *     
 *     &#64;Subquery(joinOn = "filmId")
 *     CategoryStats categoryStats;
 *     
 *     &#64;Subquery(joinOn = "filmId")
 *     RentalStats rentalStats;
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Subquery {
    /**
     * The field name to join on. This field must exist in both the main
     * query's source and the subquery result.
     */
    String joinOn();
}
