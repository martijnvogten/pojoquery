package nl.pojoquery.pipeline;

import nl.pojoquery.DbContext;
import nl.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;

public class QueryBuilder<T> extends CustomizableQueryBuilder<DefaultSqlQuery,T> {
	private QueryBuilder(DefaultSqlQuery query, Class<T> clz) {
		super(query, clz);
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
	
}
