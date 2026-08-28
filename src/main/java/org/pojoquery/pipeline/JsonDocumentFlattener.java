package org.pojoquery.pipeline;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.pojoquery.internal.MappingException;
import org.pojoquery.pipeline.DocumentLayout.Slot;
import org.pojoquery.util.JsonArrayReader;

/**
 * Turns the positional JSON documents of a {@link DocumentShape#ARRAY} query
 * into the flat {@code alias.field} rows {@link AQTRowProcessor} consumes, so
 * hydration is the same code that hydrates a joined query.
 *
 * <p>One document becomes one row per leaf path: scalars and nested documents
 * (references, embedded objects) go into the current row, while each element of
 * a collection produces another row carrying a copy of its parent's values.
 * Sibling collections are emitted one after another rather than crossed, so a
 * root costs the <em>sum</em> of its collection sizes rather than the product a
 * joined query would pay.</p>
 *
 * <p>Rows are keyed exactly as the flat query aliases its columns, so entity
 * identity, collection de-duplication and subclass resolution all behave
 * identically - including a document of all-nulls, which the row processor skips
 * the same way it skips an unmatched LEFT JOIN.</p>
 */
public class JsonDocumentFlattener {

	/** Receives each flattened row. */
	public interface RowConsumer {
		void accept(Map<String, Object> row) throws SQLException;
	}

	private final DocumentLayout layout;
	private final RowConsumer consumer;

	public JsonDocumentFlattener(DocumentLayout layout, RowConsumer consumer) {
		this.layout = layout;
		this.consumer = consumer;
	}

	/**
	 * Hydrates entities from the documents of a positional JSON query.
	 *
	 * @param tree      the query tree the documents were built from
	 * @param layout    the layout of the root document
	 * @param documents one document per root entity
	 * @return the hydrated root entities
	 */
	public static <R> List<R> hydrate(AbstractQueryTree.RootNode tree, DocumentLayout layout, List<String> documents)
			throws SQLException {
		List<R> result = new ArrayList<>();
		AQTRowProcessor<R> processor = new AQTRowProcessor<>(tree, result::add);
		JsonDocumentFlattener flattener = new JsonDocumentFlattener(layout, processor::processRow);
		for (String document : documents) {
			flattener.flatten(document);
		}
		processor.flush();
		return result;
	}

	/** Flattens one document into rows, passing each to the consumer. */
	public void flatten(String document) throws SQLException {
		Object value = JsonArrayReader.read(document);
		if (value == null) {
			return;
		}
		emit(layout, asDocument(value, layout), new LinkedHashMap<>());
	}

	/** Fills {@code row} from one document and emits it, once per leaf path. */
	private void emit(DocumentLayout documentLayout, List<Object> values, Map<String, Object> row)
			throws SQLException {
		List<Slot> slots = documentLayout.slots();
		if (values.size() != slots.size()) {
			throw new MappingException("Document for '" + documentLayout.alias() + "' has " + values.size()
					+ " values but its layout declares " + slots.size() + " slots");
		}

		List<Slot> arraySlots = new ArrayList<>();
		List<Object> arrayValues = new ArrayList<>();
		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			Object value = values.get(i);
			switch (slot.kind()) {
				case SCALAR, DISCRIMINATOR ->
					row.put(slot.rowKey(), JsonScalars.decode(text(slot, value), slot.javaType()));
				case NESTED -> {
					// A reference or embedded object belongs to the same row; a null
					// slot means the node is absent, exactly as an unmatched LEFT JOIN.
					if (value != null) {
						emit(slot.nested(), asDocument(value, slot.nested()), row);
					}
				}
				case COLLECTION, VALUE_COLLECTION -> {
					if (value != null) {
						arraySlots.add(slot);
						arrayValues.add(value);
					}
				}
			}
		}

		// One row per collection element, siblings sequentially rather than crossed.
		boolean anyElements = false;
		for (int i = 0; i < arraySlots.size(); i++) {
			Slot slot = arraySlots.get(i);
			for (Object element : asArray(arrayValues.get(i), slot)) {
				anyElements = true;
				Map<String, Object> elementRow = new HashMap<>(row);
				if (slot.kind() == DocumentLayout.SlotKind.VALUE_COLLECTION) {
					elementRow.put(slot.rowKey(), JsonScalars.decode(text(slot, element), slot.javaType()));
					consumer.accept(elementRow);
				} else {
					emit(slot.nested(), asDocument(element, slot.nested()), elementRow);
				}
			}
		}
		if (!anyElements) {
			// Nothing below contributes a row: this row is a leaf.
			consumer.accept(row);
		}
	}

	private static String text(Slot slot, Object value) {
		if (value == null || value instanceof String) {
			return (String) value;
		}
		throw new MappingException("Slot '" + slot.name() + "' holds an array where a value was expected");
	}

	/**
	 * A nested document, which arrives as an array - or, on dialects that cannot
	 * mark a value as JSON inside a JSON array, as the array's text.
	 */
	@SuppressWarnings("unchecked")
	private static List<Object> asDocument(Object value, DocumentLayout expected) {
		Object document = value instanceof String text ? JsonArrayReader.read(text) : value;
		if (document instanceof List<?> values) {
			return (List<Object>) values;
		}
		throw new MappingException("Expected a document for '" + expected.alias() + "' but found: " + value);
	}

	@SuppressWarnings("unchecked")
	private static List<Object> asArray(Object value, Slot slot) {
		Object array = value instanceof String text ? JsonArrayReader.read(text) : value;
		if (array instanceof List<?> values) {
			return (List<Object>) values;
		}
		throw new MappingException("Expected an array for slot '" + slot.name() + "' but found: " + value);
	}
}
