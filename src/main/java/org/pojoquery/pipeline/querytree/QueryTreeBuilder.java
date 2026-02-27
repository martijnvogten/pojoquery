package org.pojoquery.pipeline.querytree;

import org.pojoquery.pipeline.querytree.transforms.QueryTreePipeline;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Builds an immutable {@link QueryTree} from a POJO class definition.
 * 
 * <p>This class delegates to {@link QueryTreePipeline} which applies a series
 * of transforms to construct the query tree from annotations.</p>
 * 
 * <p>Usage:</p>
 * <pre>
 * QueryTree tree = QueryTreeBuilder.from(MyEntity.class);
 * </pre>
 */
public class QueryTreeBuilder {

    private final TypeModel rootType;
    private QueryTreePipeline pipeline;

    public QueryTreeBuilder(Class<?> clazz) {
        this(new ReflectionTypeModel(clazz));
    }

    public QueryTreeBuilder(TypeModel type) {
        this.rootType = type;
        this.pipeline = QueryTreePipeline.standard();
    }

    /**
     * Uses a custom pipeline instead of the standard one.
     */
    public QueryTreeBuilder withPipeline(QueryTreePipeline pipeline) {
        this.pipeline = pipeline;
        return this;
    }

    /**
     * Builds the QueryTree from the root type using the configured pipeline.
     */
    public QueryTree build() {
        return pipeline.build(rootType);
    }

    /**
     * Creates a QueryTree from a class using the standard pipeline.
     */
    public static QueryTree from(Class<?> clazz) {
        return new QueryTreeBuilder(clazz).build();
    }

    /**
     * Creates a QueryTree from a TypeModel using the standard pipeline.
     */
    public static QueryTree from(TypeModel type) {
        return new QueryTreeBuilder(type).build();
    }
}
