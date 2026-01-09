package org.pojoquery.pipeline;

import org.pojoquery.DbContext;
import org.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;
import org.pojoquery.typemodel.TypeModel;

public class QueryBuilder<T> extends CustomizableQueryBuilder<DefaultSqlQuery,T> {
	private QueryBuilder(DefaultSqlQuery query, Class<T> clz) {
		super(query, clz);
	}

	private QueryBuilder(DefaultSqlQuery query, TypeModel type) {
		super(query, type);
	}

	public static <R> CustomizableQueryBuilder<DefaultSqlQuery,R> from(DbContext dbContext, Class<R> clz) {
		return new CustomizableQueryBuilder<DefaultSqlQuery,R>(new DefaultSqlQuery(dbContext), clz);
	}

	public static <R,S extends SqlQuery<?>> CustomizableQueryBuilder<S,R> from(SqlQuery<S> query, Class<R> clz) {
		return new CustomizableQueryBuilder<S,R>(query, clz);
	}

	public static <R> QueryBuilder<R> from(Class<R> clz) {
		return new QueryBuilder<R>(new DefaultSqlQuery(DbContext.getDefault()), clz);
	}

	/**
	 * Creates a QueryBuilder from a TypeModel.
	 * This allows using annotation processing types (ElementTypeModel) directly
	 * without loading the entity class via reflection.
	 */
	public static CustomizableQueryBuilder<DefaultSqlQuery, ?> from(TypeModel type) {
		return new CustomizableQueryBuilder<DefaultSqlQuery, Object>(new DefaultSqlQuery(DbContext.getDefault()), type);
	}

	/**
	 * Creates a QueryBuilder from a TypeModel with a specific DbContext.
	 */
	public static CustomizableQueryBuilder<DefaultSqlQuery, ?> from(DbContext dbContext, TypeModel type) {
		return new CustomizableQueryBuilder<DefaultSqlQuery, Object>(new DefaultSqlQuery(dbContext), type);
	}
}
