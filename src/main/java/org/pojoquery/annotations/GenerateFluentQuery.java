package org.pojoquery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity class for fluent query builder code generation.
 *
 * <p>When placed on a class annotated with {@link Table}, the annotation processor
 * will generate a query class with fluent static condition chains following the pattern
 * from FluentExperiments.
 *
 * <p>Example usage:
 * <pre>{@code
 * @Table("book")
 * @GenerateFluentQuery
 * public class Book {
 *     @Id
 *     public Long id;
 *     public String title;
 * }
 *
 * // Generated code enables:
 * BookQuery q = new BookQuery();
 * SqlExpression condition = q.title.eq("John").and().title.isNotNull()
 *     .and(q.id.gt(123L)).get();
 *
 * // Or with where() clause:
 * q.where().title.eq("John").and().title.isNotNull().orderBy().title.asc();
 * }</pre>
 *
 * <p>The generated query class provides:
 * <ul>
 *   <li>Static condition builder fields for building conditions outside of query</li>
 *   <li>A {@code where()} method for fluent where clause building</li>
 *   <li>An {@code orderBy()} method for fluent ordering</li>
 *   <li>A {@code groupBy()} method for fluent grouping</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateFluentQuery {

    /**
     * The suffix to append to the entity class name for the generated query class.
     * Defaults to "Query" (e.g., Book -> BookQuery).
     */
    String querySuffix() default "Query";
}
