package org.pojoquery.integrationtest;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.pojoquery.DbContext;

/**
 * JUnit 5 extension that saves and restores the default DbContext.
 * 
 * <p>Use this extension on any test class that modifies {@link DbContext#setDefault(DbContext)}
 * to ensure the global state is restored after the test class completes, preventing
 * interference with other tests.
 * 
 * <p>Usage:
 * <pre>{@code
 * @ExtendWith(DbContextExtension.class)
 * public class MyTest {
 *     @BeforeEach
 *     void setup() {
 *         DbContext.setDefault(DbContext.forDialect(Dialect.MYSQL));
 *     }
 *     
 *     @Test
 *     void testSomething() {
 *         // test code
 *     }
 * }
 * }</pre>
 */
public class DbContextExtension implements BeforeAllCallback, AfterAllCallback {

    private static final Namespace NAMESPACE = Namespace.create(DbContextExtension.class);
    private static final String ORIGINAL_CONTEXT_KEY = "originalDbContext";

    @Override
    public void beforeAll(ExtensionContext context) {
        // Save the current default context in the extension store
        context.getStore(NAMESPACE).put(ORIGINAL_CONTEXT_KEY, DbContext.getDefault());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // Restore the original default context
        DbContext original = context.getStore(NAMESPACE).get(ORIGINAL_CONTEXT_KEY, DbContext.class);
        if (original != null) {
            DbContext.setDefault(original);
        }
    }
}
