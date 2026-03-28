package org.pojoquery.fluent;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.DefaultSqlQuery;

public abstract class FluentQuery<R> {
	DefaultSqlQuery query = new DefaultSqlQuery(DbContext.getDefault());
	List<SqlExpression> staticConditionSql = new ArrayList<>();
	List<SqlExpression> whereConditionSql = new ArrayList<>();

	FluentQuery(Class<R> type) {
		RootNode aqt = AQTTransformer.buildQueryTreeForType(type);
		AQTTransformer.toSql(aqt, query);
	}

	void appendExpression(String sql, Object... parameters) {
		whereConditionSql.add(SqlExpression.sql(sql, parameters));
	}
	
	void appendStaticExpression(String sql, Object... parameters) {
		staticConditionSql.add(SqlExpression.sql(sql, parameters));
	}

	public void addOrderBy(String orderBy) {
		query.addOrderBy(orderBy);
	}

	public void addGroupBy(String groupBy) {
		query.addGroupBy(groupBy);
	}

	public void setLimit(int limit) {
		query.setLimit(limit);
	}

	public List<R> list(Connection c) {
		return List.of();
	}

	public SqlExpression getStaticConditionSql() {
		SqlExpression result = SqlExpression.implode(" ", staticConditionSql);
		staticConditionSql.clear();
		return result;
	}

	public SqlExpression getSql() {
		query.addWhere(SqlExpression.implode(" ", whereConditionSql));
		whereConditionSql.clear();
		return query.toStatement();
	}
}

