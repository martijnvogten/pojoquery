package org.pojoquery.pipeline;

import org.pojoquery.DbContext;
import org.pojoquery.typemodel.TypeModel;

public class QueryBuilder<T> extends CustomizableQueryBuilder<org.pojoquery.pipeline.DefaultSqlQuery,T> {
	private QueryBuilder(org.pojoquery.pipeline.DefaultSqlQuery query, Class<T> clz) {
		super(query, clz);
	}

	private QueryBuilder(org.pojoquery.pipeline.DefaultSqlQuery query, TypeModel type) {
		super(query, type);
	}

	public static <R> CustomizableQueryBuilder<org.pojoquery.pipeline.DefaultSqlQuery,R> from(DbContext dbContext, Class<R> clz) {
		return new CustomizableQueryBuilder<org.pojoquery.pipeline.DefaultSqlQuery,R>(new org.pojoquery.pipeline.DefaultSqlQuery(dbContext), clz);
	}

	public static <R,S extends SqlQuery<?>> CustomizableQueryBuilder<S,R> from(SqlQuery<S> query, Class<R> clz) {
		return new CustomizableQueryBuilder<S,R>(query, clz);
	}

	public static <R> QueryBuilder<R> from(Class<R> clz) {
		return new QueryBuilder<R>(new org.pojoquery.pipeline.DefaultSqlQuery(DbContext.getDefault()), clz);
	}

	/**
	 * Creates a QueryBuilder from a TypeModel.
	 * This allows using annotation processing types (ElementTypeModel) directly
	 * without loading the entity class via reflection.
	 */
	public static CustomizableQueryBuilder<org.pojoquery.pipeline.DefaultSqlQuery, ?> from(TypeModel type) {
		return new CustomizableQueryBuilder<org.pojoquery.pipeline.DefaultSqlQuery, Object>(new org.pojoquery.pipeline.DefaultSqlQuery(DbContext.getDefault()), type);
	}

	/**
	 * Creates a QueryBuilder from a TypeModel with a specific DbContext.
	 */
	public static CustomizableQueryBuilder<org.pojoquery.pipeline.DefaultSqlQuery, ?> from(DbContext dbContext, TypeModel type) {
		return new CustomizableQueryBuilder<org.pojoquery.pipeline.DefaultSqlQuery, Object>(new org.pojoquery.pipeline.DefaultSqlQuery(dbContext), type);
	}
}
