package org.pojoquery.fluent;

/**
 * R: result type (Book)
 * T: terminator interface (this interface)
 */
public interface ConditionTerminator<R, S, T extends ConditionTerminator<R, S, T>>
		extends QueryTerminator<R>, Terminator<S> {
}

