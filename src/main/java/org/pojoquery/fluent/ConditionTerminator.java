package org.pojoquery.fluent;

/**
 * R: result type (Book)
 * T: terminator interface (this interface)
 */
public interface ConditionTerminator<R, S, T extends ConditionTerminator<R, S, T, O, G, PK>, O, G, PK>
		extends QueryTerminator<R,O,G,PK>, Terminator<S,T> {
}

