package org.pojoquery.fluent;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.SqlExpression;
import org.pojoquery.fluent.internal.ConditionChainOperators;
import org.pojoquery.fluent.internal.ConditionChainTerminator;
import org.pojoquery.fluent.internal.OrderByChainField;
import org.pojoquery.fluent.internal.StaticConditionChainTerminator;
import org.pojoquery.pipeline.AQTRowProcessor;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.DefaultSqlQuery;

@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class FluentQuery<R, Q extends FluentQuery<R, Q, W, O, G>, W, O, G> {

	public interface Appender {
		void append(String sql, Iterable<Object> parameters);
	}

	private DefaultSqlQuery query = new DefaultSqlQuery(DbContext.getDefault());
	private List<SqlExpression> staticConditionSql = new ArrayList<>();
	private List<SqlExpression> whereConditionSql = new ArrayList<>();

	private final Terminator<Q> staticTerminator;
	private final ConditionChainTerminator conditionTerminator;
	private final W whereStarter;
	private O orderByStarter;
	private G groupByStarter;

	private static RootNode tree;

	protected FluentQuery(Class<R> type) {
		if (tree == null) {
			tree = AQTTransformer.buildQueryTreeForType(type);
		}
		AQTTransformer.toSql(tree, query);

		this.staticTerminator = new StaticConditionChainTerminator<>((Q) this, (String sql, Iterable<Object> params) -> appendStaticExpression(sql, params), this::getStaticConditionSql);
		this.conditionTerminator = new ConditionChainTerminator(this, (String sql, Iterable<Object> params) -> appendExpression(sql, params));
		this.whereStarter = createWhereConditionStarter();
		this.conditionTerminator.setStarter(whereStarter);
	}

	// Factory method for static condition operators (q.title.eq(...))
	protected ConditionChainOperators<Terminator<Q>> staticOp(String tableAlias, String fieldName) {
		return new ConditionChainOperators<>(tableAlias, fieldName, staticTerminator, (String sql, Iterable<Object> params) -> appendStaticExpression(sql, params));
	}

	// Factory method for chained condition operators (q.where().title.eq(...))
	protected ConditionChainOperators<ConditionTerminator<R, W, ?, O, G>> chainOp(String tableAlias, String fieldName) {
		return new ConditionChainOperators<>(tableAlias, fieldName, (ConditionTerminator<R, W, ?, O, G>) conditionTerminator, (String sql, Iterable<Object> params) -> appendExpression(sql, params));
	}

	protected OrderByChain<QueryTerminator<R,O,G>> orderByOp(String tableAlias, String fieldName) {
		return new OrderByChainField<>(tableAlias, fieldName, (QueryTerminator<R, O, G>)this, (String orderBy) -> query.addOrderBy(orderBy));
	}

	class GroupByChainField<T> implements QueryTerminator<R,O,G> {
		private final String tableAlias;
		private final String fieldName;

		public GroupByChainField(String tableAlias, String fieldName) {
			this.tableAlias = tableAlias;
			this.fieldName = fieldName;
		}

		@Override
		public QueryTerminator<R,O,G> addOrderBy(String orderBy) {
			query.addGroupBy("{" + tableAlias + "." + fieldName + "}");
			return FluentQuery.this.addOrderBy(orderBy);
		}

		@Override
		public QueryTerminator<R, O, G> addGroupBy(String groupBy) {
			query.addGroupBy("{" + tableAlias + "." + fieldName + "}");
			return this;
		}
		
		@Override
		public List<R> list(Connection c) throws SQLException {
			query.addGroupBy("{" + tableAlias + "." + fieldName + "}");
			return FluentQuery.this.list(c);
		}
		
		@Override
		public O orderBy() {
			query.addGroupBy("{" + tableAlias + "." + fieldName + "}");
			return FluentQuery.this.orderBy();
		}
		
		@Override
		public QueryTerminator<R, O, G> setLimit(int limit) {
			query.addGroupBy("{" + tableAlias + "." + fieldName + "}");
			return FluentQuery.this.setLimit(limit);
		}

		@Override
		public G groupBy() {
			query.addGroupBy("{" + tableAlias + "." + fieldName + "}");
			return FluentQuery.this.groupBy();
		}
	}

	protected QueryTerminator<R, O, G> groupByOp(String tableAlias, String fieldName) {
		return new GroupByChainField<>(tableAlias, fieldName);
	}

	public W where() {
		return whereStarter;
	}

	protected abstract W createWhereConditionStarter();
	protected abstract O createOrderByStarter();
	protected abstract G createGroupByStarter();

	private void appendExpression(String sql, Iterable<Object> parameters) {
		whereConditionSql.add(new SqlExpression(sql, parameters));
	}
	
	private void appendStaticExpression(String sql, Iterable<Object> parameters) {
		staticConditionSql.add(new SqlExpression(sql, parameters));
	}

	public QueryTerminator<R,O,G> addOrderBy(String orderBy) {
		query.addOrderBy(orderBy);
		return (QueryTerminator<R,O,G>) this;
	}

	public QueryTerminator<R,O,G> addGroupBy(String groupBy) {
		query.addGroupBy(groupBy);
		return (QueryTerminator<R,O,G>) this;
	}

	public O orderBy() {
		if (orderByStarter == null) {
			orderByStarter = createOrderByStarter();
		}
		return orderByStarter;
	}

	public G groupBy() {
		if (groupByStarter == null) {
			groupByStarter = createGroupByStarter();
		}
		return groupByStarter;
	}

	public QueryTerminator<R,O,G> setLimit(int limit) {
		query.setLimit(limit);
		return (QueryTerminator<R,O,G>) this;
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

