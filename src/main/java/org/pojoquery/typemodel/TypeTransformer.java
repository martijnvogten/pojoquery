package org.pojoquery.typemodel;

public interface TypeTransformer {
	/**
	 * Transforms the given type model and returns the transformed type.
	 *
	 * @param type the original type model to transform
	 * @return the transformed type model
	 */
	TypeModel transformType(TypeModel type);

	FieldModel transformField(FieldModel field);
}
