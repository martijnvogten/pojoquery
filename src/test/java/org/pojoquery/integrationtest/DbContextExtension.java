package org.pojoquery.integrationtest;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.pojoquery.DbContext;

/**
 * JUnit 5 extension that sets the default DbContext based on the {@link UseDialect} annotation.
 */
public class DbContextExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getTestClass()
            .map(cls -> cls.getAnnotation(UseDialect.class))
            .ifPresent(ann -> DbContext.setDefault(DbContext.forDialect(ann.value())));
    }
}
