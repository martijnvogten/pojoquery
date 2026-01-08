package org.pojoquery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity class for query builder code generation.
 *
 * <p>When placed on a class annotated with {@link Table}, the annotation processor
 * will generate:
 * <ul>
 *   <li>A {@code <EntityName>Query} class with a fluent API for type-safe queries</li>
 *   <li>Static field references for use with {@code where()} and {@code orderBy()}</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * @Table("employee")
 * @GenerateQuery
 * public class Employee {
 *     @Id
 *     public Long id;
 *     public String email;
 *     public String lastName;
 * }
 *
 * // Generated code enables:
 * import static com.example.Employee_.email;
 * import static com.example.Employee_.lastName;
 *
 * List<Employee> results = new EmployeeQuery()
 *     .where(email).is("bob@example.com")
 *     .orderBy(lastName)
 *     .list(dataSource);
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateQuery {

    /**
     * The suffix to append to the entity class name for the generated query class.
     * Defaults to "Query" (e.g., Employee -> EmployeeQuery).
     */
    String querySuffix() default "Query";

    /**
     * The suffix to append to the entity class name for the generated fields class.
     * Defaults to "_" (e.g., Employee -> Employee_).
     */
    String fieldsSuffix() default "_";
}
