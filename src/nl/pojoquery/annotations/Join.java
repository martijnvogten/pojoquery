package nl.pojoquery.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import nl.pojoquery.pipeline.SqlQuery.JoinType;

@Retention(RetentionPolicy.RUNTIME)
public @interface Join {
	JoinType type();
	String schemaName() default "";
	String tableName();
	String alias();
	String joinCondition();
}
