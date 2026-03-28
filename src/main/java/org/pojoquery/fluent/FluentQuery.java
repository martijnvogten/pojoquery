package org.pojoquery.fluent;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.fluent.internal.ConditionChainOperators;
import org.pojoquery.fluent.internal.ConditionChainTerminator;
import org.pojoquery.fluent.internal.StaticConditionChainTerminator;
import org.pojoquery.pipeline.AQTRowProcessor;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.DefaultSqlQuery;

@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class FluentQuery<R, Q extends FluentQuery<R, Q, W>, W> {

	public interface Appender {
		void append(String sql, Object... parameters);
	}

	private DefaultSqlQuery query = new DefaultSqlQuery(DbContext.getDefault());
	private List<SqlExpression> staticConditionSql = new ArrayList<>();
	private List<SqlExpression> whereConditionSql = new ArrayList<>();

	private final Terminator<Q> staticTerminator;
	private final ConditionChainTerminator conditionTerminator;
	private final W whereStarter;

	private static RootNode tree;

	protected FluentQuery(Class<R> type, Function<FluentQuery<R, Q, W>, W> whereFactory) {
		if (tree == null) {
			tree = AQTTransformer.buildQueryTreeForType(type);
		}
		AQTTransformer.toSql(tree, query);

		this.staticTerminator = new StaticConditionChainTerminator<>((Q) this, (String sql, Object[] params) -> appendStaticExpression(sql, params), this::getStaticConditionSql);
		this.conditionTerminator = new ConditionChainTerminator(this, (String sql, Object[] params) -> appendExpression(sql, params));
		this.whereStarter = whereFactory.apply(this);
		this.conditionTerminator.setStarter(whereStarter);
	}

	// Factory method for static condition operators (q.title.eq(...))
	protected ConditionChainOperators<Terminator<Q>> staticOp(String tableAlias, String fieldName) {
		return new ConditionChainOperators<>(tableAlias, fieldName, staticTerminator, (String sql, Object[] params) -> appendStaticExpression(sql, params));
	}

	// Factory method for chained condition operators (q.where().title.eq(...))
	protected ConditionChainOperators<ConditionTerminator<R, W, ?>> chainOp(String tableAlias, String fieldName) {
		return new ConditionChainOperators<>(tableAlias, fieldName, (ConditionTerminator<R, W, ?>) conditionTerminator, (String sql, Object[] params) -> appendExpression(sql, params));
	}

	public W where() {
		return whereStarter;
	}

	private void appendExpression(String sql, Object[] parameters) {
		whereConditionSql.add(SqlExpression.sql(sql, parameters));
	}
	
	private void appendStaticExpression(String sql, Object[] parameters) {
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

	public List<R> list(Connection c) throws SQLException {
		SqlExpression stmt = query.toStatement();
		return AQTRowProcessor.processRows(tree, DB.queryRows(c, stmt));
	}

	private SqlExpression getStaticConditionSql() {
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

