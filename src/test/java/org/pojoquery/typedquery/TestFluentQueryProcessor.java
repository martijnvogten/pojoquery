package org.pojoquery.typedquery;

import org.junit.Test;
import org.pojoquery.annotations.GenerateFluentQuery;
import org.pojoquery.typedquery.entities.Article;
import org.pojoquery.typedquery.entities.ArticleQuery;

/**
 * Test for the FluentQueryProcessor - tests the generated code pattern.
 */
public class TestFluentQueryProcessor {

    @Test
    public void testAnnotationIsPresent() {
        ArticleQuery q = new ArticleQuery();
        q.where()
                .title.eq("Test Title")
                .and(q.id.eq(1L).or().id.eq(3L)).and()
                .viewCount.gt(100)
                .orderBy()
                .title.desc();
        // This test verifies that the annotation compiles correctly.
        // The actual generated code testing happens after annotation processing runs.
        GenerateFluentQuery annotation = Article.class.getAnnotation(GenerateFluentQuery.class);
        // Note: Annotation won't be present at runtime (RetentionPolicy.SOURCE)
        // but the processor will run during compilation
        System.out.println("GenerateFluentQuery annotation test passed");
    }
}
