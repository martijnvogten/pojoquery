package org.pojoquery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Recursive {
	enum Direction { UP, DOWN }

	/** Column on the element table that points to its parent row. Leave empty when using a junction table via {@link Link}. */
	String parentLink() default "";

	/** UP = walk toward roots (ancestors); DOWN = walk toward leaves (descendants). */
	Direction direction() default Direction.DOWN;

	/**
	 * Overrides the join condition between the previous CTE row (alias {@code r}) and the
	 * current element row (alias {@code c}) used in the recursive step. Empty means use the
	 * default based on {@link #direction()}.
	 */
	String recursionJoinCondition() default "";
}
