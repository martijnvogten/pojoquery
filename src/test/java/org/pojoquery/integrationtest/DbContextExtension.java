package org.pojoquery.integrationtest;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.pojoquery.DbContext;

/**
 * JUnit 5 extension that sets the default DbContext based on the {@link UseDialect} annotation.
 *
 * <p>{@link DbContext#setDefault(DbContext)} writes a process-global static, so pinning a
 * dialect for one test class would otherwise change it for every class that runs later in
 * the same JVM fork — making dialect a function of test ordering. This extension restores
 * the previous context afterwards so the pin stays scoped to the annotated class.</p>
 *
 * <p>It deliberately restores only what it set. The test harness establishes its own
 * default (see {@code TestDatabaseProvider}); a blanket save/restore around every class
 * would revert that too.</p>
 */
public class DbContextExtension implements BeforeAllCallback, AfterAllCallback {

    private static final Namespace NAMESPACE = Namespace.create(DbContextExtension.class);
    private static final String SAVED = "saved";

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getTestClass()
            .map(cls -> cls.getAnnotation(UseDialect.class))
            .ifPresent(ann -> {
                context.getStore(NAMESPACE).put(SAVED, DbContext.getDefault());
                DbContext.setDefault(DbContext.forDialect(ann.value()));
            });
    }

    @Override
    public void afterAll(ExtensionContext context) {
        DbContext saved = context.getStore(NAMESPACE).get(SAVED, DbContext.class);
        if (saved != null) {
            DbContext.setDefault(saved);
        }
    }
}
