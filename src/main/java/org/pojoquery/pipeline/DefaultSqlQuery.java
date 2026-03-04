package org.pojoquery.pipeline;

import org.pojoquery.DbContext;

/**
 * Default implementation of SqlQuery used throughout PojoQuery.
 */
public class DefaultSqlQuery extends SqlQuery<DefaultSqlQuery> {

	public DefaultSqlQuery(DbContext context) {
		super(context);
	}
}
