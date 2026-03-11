package org.pojoquery.pipeline.querytree;

import org.pojoquery.typemodel.FieldModel;

/**
 * Base interface for field selections in the query tree.
 * 
 * <p>Field selections can be:</p>
 * <ul>
 *   <li>{@link UnresolvedFieldSelection}: A field that needs further processing (entity ref, collection, embedded, or simple)</li>
 *   <li>{@link FieldSelection}: A resolved field with a SQL expression ready for query generation</li>
 * </ul>
 */
public sealed interface FieldSelectionBase permits UnresolvedFieldSelection, FieldSelection {
    
    /**
     * The Java field this selection maps to (may be null for computed fields).
     */
    FieldModel field();

    FieldSelectionBase withField(FieldModel newField);
}
