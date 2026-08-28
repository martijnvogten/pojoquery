package org.pojoquery.pipeline;

/**
 * How {@link AQTJsonDirectTransformer} assembles each document of a JSON query.
 *
 * <p>Both shapes are produced by the same tree walk over the same slots; only
 * the assembly differs, so the two can never disagree about which value belongs
 * to which field.</p>
 */
public enum DocumentShape {

	/**
	 * Named JSON objects, for consumers that read the documents as JSON.
	 * Polymorphic entities carry only the properties of their concrete type plus
	 * a {@code _type} property.
	 */
	OBJECT,

	/**
	 * Positional JSON arrays, for hydration. Every document of a node has the
	 * same arity in every row - the slot count of its {@link DocumentLayout} -
	 * so a reader can address values by index and flatten them into the
	 * {@code alias.field} rows {@link AQTRowProcessor} consumes.
	 *
	 * <p>Subclass fields are unconditional slots here rather than conditional
	 * properties: table-per-subclass columns are already NULL for other
	 * subclasses, and single-table inheritance carries a discriminator slot the
	 * hydrator keys off, exactly as in the flat query.</p>
	 */
	ARRAY
}
