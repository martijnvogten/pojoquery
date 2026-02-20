package org.pojoquery.integrationtest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;
import org.pojoquery.DbContext.Dialect;

/**
 * Sets the default DbContext for the test class.
 * 
 * <p>Usage:
 * <pre>{@code
 * @UseDialect(Dialect.HSQLDB)
 * public class MyTest {
 *     @Test
 *     void testSomething() {
 *         // DbContext.getDefault() is already set to HSQLDB
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DbContextExtension.class)
public @interface UseDialect {
    Dialect value();
}
