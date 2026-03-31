package org.pojoquery.fluent;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
public abstract class FluentQuery<R, Q extends FluentQuery<R, Q, W, O, G>, W, O, G> implements QueryTerminator<R,O,G> {

	public interface Appender {
		void append(String sql, Iterable<Object> parameters);
	}

	private DefaultSqlQuery query = new DefaultSqlQuery(DbContext.getDefault());
	private List<SqlExpression> staticConditionSql = new ArrayList<>();
	private List<SqlExpression> whereConditionSql = new ArrayList<>();

	private final Terminator<Q, StaticConditionChainTerminator<Q>> staticTerminator;
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
	protected <V> ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, V> staticOp(String tableAlias, String fieldName, Class<V> fieldType) {
		return staticOp(SqlExpression.sql("{" + tableAlias + "." + fieldName + "}"), fieldType);
	}

	private <V> ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, V> staticOp(SqlExpression baseExpression, Class<V> fieldType) {
		return new ConditionChainOperators<>(baseExpression, fieldType, staticTerminator, (String sql, Iterable<Object> params) -> appendStaticExpression(sql, params));
	}

	// Factory method for chained condition operators (q.where().title.eq(...))
	protected <V> ConditionChainOperators<ConditionTerminator<R, W, ?, O, G>, V> chainOp(String tableAlias, String fieldName, Class<V> fieldType) {
		return new ConditionChainOperators<>(SqlExpression.sql("{" + tableAlias + "." + fieldName + "}"), fieldType, (ConditionTerminator<R, W, ?, O, G>) conditionTerminator, (String sql, Iterable<Object> params) -> appendExpression(sql, params));
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
		public List<R> list(Connection c) {
			query.addGroupBy("{" + tableAlias + "." + fieldName + "}");
			return FluentQuery.this.list(c);
		}

		@Override
		public Optional<R> first(Connection c) {
			return FluentQuery.this.first(c);
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

	public ConditionTerminator<R, W, ?, O, G> where(Terminator<Q, StaticConditionChainTerminator<Q>> condition) {
		SqlExpression sql = condition.toSql();
		appendExpression(sql.getSql(), sql.getParameters());
		return (ConditionTerminator<R, W, ?, O, G>) conditionTerminator;
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

	public List<R> list(Connection c) {
		if (!whereConditionSql.isEmpty()) {
			query.addWhere(SqlExpression.implode(" ", whereConditionSql));
			whereConditionSql.clear();
		}
		SqlExpression stmt = query.toStatement();
		try {
			return AQTRowProcessor.processRows(tree, DB.queryRows(c, stmt));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public Optional<R> first(Connection c) {
		List<R> results = list(c);
		if (results.isEmpty()) {
			return Optional.empty();
		} else {
			return Optional.of(results.get(0));
		}
	}

	private SqlExpression getStaticConditionSql() {
		SqlExpression result = SqlExpression.implode(" ", staticConditionSql);
		staticConditionSql.clear();
		return result;
	}

	public SqlExpression getSql() {
		if (!whereConditionSql.isEmpty()) {
			query.addWhere(SqlExpression.implode(" ", whereConditionSql));
			whereConditionSql.clear();
		}
		return query.toStatement();
	}

	public SqlFunctions fn = new SqlFunctions();

	public class SqlFunctions {
		public ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, String> lower(FieldExpression<String> expr) {
			SqlExpression inner = expr.toSql();
			return (ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, String>)staticOp(new SqlExpression("LOWER(" + inner.getSql() + ")", inner.getParameters()), String.class);
		}

		public ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, String> upper(FieldExpression<String> expr) {
			SqlExpression inner = expr.toSql();
			return (ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, String>)staticOp(new SqlExpression("UPPER(" + inner.getSql() + ")", inner.getParameters()), String.class);
		}

		public ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, Integer> length(FieldExpression<?> expr) {
			SqlExpression inner = expr.toSql();
			return (ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, Integer>)staticOp(new SqlExpression("LENGTH(" + inner.getSql() + ")", inner.getParameters()), Integer.class);
		}

		public ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, String> concat(FieldExpression<?>... exprs) {
			SqlExpression inner = SqlExpression.implode(",", List.of(exprs).stream().map(FieldExpression::toSql).toList());
			return (ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, String>) staticOp(new SqlExpression("CONCAT(" + inner.getSql() + ")", inner.getParameters()), String.class);
		}

		public ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>,String> trim(FieldExpression<String> expr) {
			SqlExpression inner = expr.toSql();
			return (ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, String>)staticOp(new SqlExpression("TRIM(" + inner.getSql() + ")", inner.getParameters()), String.class);
		}

		public ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, String> substring(FieldExpression<String> expr, int start, int length) {
			SqlExpression inner = expr.toSql();
			return (ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, String>)staticOp(new SqlExpression("SUBSTRING(" + inner.getSql() + ", " + start + ", " + length + ")", inner.getParameters()), String.class);
		}

		public <V> ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, V> coalesce(FieldExpression<V> firstNullable, FieldExpression<?>... exprs) {
			SqlExpression inner = SqlExpression.implode(",", Stream.concat(List.of(firstNullable).stream(), List.of(exprs).stream()).map(FieldExpression::toSql).toList());
			return (ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, V>) staticOp(new SqlExpression("COALESCE(" + inner.getSql() + ")", inner.getParameters()), Object.class);
		}
		
		public ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, String> literal(String string) {
			return (ConditionChainOperators<Terminator<Q, StaticConditionChainTerminator<Q>>, String>) staticOp(new SqlExpression("?", List.of(string)), String.class);
		}
	}
}

