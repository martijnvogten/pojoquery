package nl.pojoquery.pipeline;

import nl.pojoquery.DbContext;
import nl.pojoquery.pipeline.CustomQueryBuilder.DefaultSqlQuery;

public class QueryBuilder<T> extends CustomQueryBuilder<DefaultSqlQuery,T> {
	public QueryBuilder(DefaultSqlQuery query, Class<T> clz) {
		super(query, clz);
	}
	
	public static <R> CustomQueryBuilder<DefaultSqlQuery,R> from(DbContext dbContext, Class<R> clz) {
		return new CustomQueryBuilder<DefaultSqlQuery,R>(new DefaultSqlQuery(dbContext), clz);
	}
	
	public static <R,S extends SqlQuery<?>> CustomQueryBuilder<S,R> from(SqlQuery<S> query, Class<R> clz) {
		return new CustomQueryBuilder<S,R>(query, clz);
	}
	
	public static <R> QueryBuilder<R> from(Class<R> clz) {
		return new QueryBuilder<R>(new DefaultSqlQuery(DbContext.getDefault()), clz);
	}
}
