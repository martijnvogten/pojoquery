package org.pojoquery.pipeline;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The slot layout of one JSON document produced by
 * {@link AQTJsonDirectTransformer}: what each value position in the document
 * means, in the order the transformer emits it.
 *
 * <p>A layout is a projection of the transformer's single tree walk, so slot
 * order can never drift from the SQL that fills the slots. It exists for
 * readers that consume documents positionally and need to know which entity
 * field each position belongs to; readers of named JSON objects can ignore
 * it.</p>
 *
 * <p>{@link Slot#rowKey()} is the key the flat-row hydrator
 * ({@link AQTRowProcessor}) looks a value up under, so a document can be
 * flattened into the same {@code alias.field} rows a joined query would have
 * produced.</p>
 */
public record DocumentLayout(String alias, List<Slot> slots) {

	/** What kind of value a slot holds. */
	public enum SlotKind {
		/** A scalar column value, addressed by {@link Slot#rowKey()}. */
		SCALAR,
		/** A discriminator column value for single-table inheritance. */
		DISCRIMINATOR,
		/** A nested document belonging to the same row: a reference, embedded object. */
		NESTED,
		/** An array of nested documents: a one-to-many, many-to-many or recursive collection. */
		COLLECTION,
		/** An array of scalar values. */
		VALUE_COLLECTION
	}

	/**
	 * One value position in a document.
	 *
	 * @param name     the JSON property name, for documents built as named objects
	 * @param rowKey   the hydration row key ({@code alias.field}), or null for
	 *                 slots holding a nested document or an array of them
	 * @param javaType the type a reader must produce for this slot's value, or
	 *                 null for slots holding a nested document
	 * @param nested   the layout of the nested document(s), or null for scalar slots
	 * @param kind     what kind of value the slot holds
	 */
	public record Slot(String name, String rowKey, Class<?> javaType, DocumentLayout nested, SlotKind kind) {

		public static Slot scalar(String name, String rowKey, Class<?> javaType) {
			return new Slot(name, rowKey, javaType, null, SlotKind.SCALAR);
		}

		public static Slot discriminator(String name, String rowKey) {
			return new Slot(name, rowKey, String.class, null, SlotKind.DISCRIMINATOR);
		}

		public static Slot nested(String name, DocumentLayout nested) {
			return new Slot(name, null, null, nested, SlotKind.NESTED);
		}

		public static Slot collection(String name, DocumentLayout nested) {
			return new Slot(name, null, null, nested, SlotKind.COLLECTION);
		}

		public static Slot valueCollection(String name, String rowKey, Class<?> javaType) {
			return new Slot(name, rowKey, javaType, null, SlotKind.VALUE_COLLECTION);
		}

		/**
		 * Renders the slot as part of {@code owningAlias}'s layout. Scalar slots
		 * print their name, or their full row key when the value comes from
		 * another table - a joined subclass table, say - so the rendering stays
		 * unambiguous when two slots share a field name.
		 */
		String describe(String owningAlias) {
			return switch (kind) {
				case SCALAR, DISCRIMINATOR -> (owningAlias + "." + name).equals(rowKey) ? name : rowKey;
				case VALUE_COLLECTION -> name + "[]";
				case NESTED -> name + ":" + nested;
				case COLLECTION -> name + "[]:" + nested;
			};
		}

		@Override
		public String toString() {
			return describe(null);
		}
	}

	/**
	 * A compact, stable rendering of the layout, e.g.
	 * {@code author{id, name, books[]:books{id, title, year}}}. Suitable as a
	 * golden value in tests.
	 */
	@Override
	public String toString() {
		return alias + slots.stream().map(slot -> slot.describe(alias)).collect(Collectors.joining(", ", "{", "}"));
	}
}
