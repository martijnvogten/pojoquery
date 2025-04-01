package org.pojoquery.annotations;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.pojoquery.pipeline.SqlQuery.JoinType;

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Joins.class)
public @interface Join {
	JoinType type();
	String schemaName() default "";
	String tableName();
	String alias();
	String joinCondition();
}
