package org.pojoquery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Recursive {
	enum Direction { UP, DOWN }

	/** Column on the element table that points to its parent row. */
	String parentLink();

	/** UP = walk toward roots (ancestors); DOWN = walk toward leaves (descendants). */
	Direction direction() default Direction.DOWN;
}
