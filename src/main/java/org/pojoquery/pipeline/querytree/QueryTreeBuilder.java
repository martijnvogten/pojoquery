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
    /**
     * Creates a QueryTree from a class using the standard pipeline.
     */
    public static QueryTree from(Class<?> clazz) {
        return from(new ReflectionTypeModel(clazz));
    }
    
    /**
     * Creates a QueryTree from a TypeModel using the standard pipeline.
    */
   public static QueryTree from(TypeModel type) {
        return QueryTreePipeline.standard().build(type);
    }
}
