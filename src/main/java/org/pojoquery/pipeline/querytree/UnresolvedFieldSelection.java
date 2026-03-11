package org.pojoquery.pipeline.querytree;

import java.util.Objects;

import org.pojoquery.typemodel.FieldModel;

/**
 * Represents a field that has not yet been resolved to a SQL expression.
 * 
 * <p>BasicTableTransform creates these for all fields. Later transforms then:</p>
 * <ul>
 *   <li>EntityReferenceTransform: entity field → add JoinedNode, remove this</li>
 *   <li>CollectionTransform: collection field → add JoinedNode, remove this</li>
 *   <li>EmbeddedTransform: @Embedded field → add EmbeddedNode, remove this</li>
 *   <li>SimpleFieldTransform: remaining → convert to FieldSelection with SQL expression</li>
 * </ul>
 *
 * @param sourceAlias The table alias to select from (e.g., "room" for JoinedNode, parent's alias for EmbeddedNode)
 * @param fieldAlias The alias prefix for the result set column (e.g., "room" or "room.bedroom")
 * @param field The Java field this will map to
 */
public record UnresolvedFieldSelection(
    String sourceAlias,
    String fieldAlias,
    FieldModel field
) implements FieldSelectionBase {
    
    public UnresolvedFieldSelection {
        Objects.requireNonNull(sourceAlias, "sourceAlias");
        Objects.requireNonNull(fieldAlias, "fieldAlias");
        Objects.requireNonNull(field, "field");
    }
    
    /**
     * Creates an unresolved field selection.
     */
    public static UnresolvedFieldSelection of(String sourceAlias, String fieldAlias, FieldModel field) {
        return new UnresolvedFieldSelection(sourceAlias, fieldAlias, field);
    }
    
    @Override
    public String toString() {
        return "UnresolvedFieldSelection{" + fieldAlias + "." + field.getName() + " <- " + sourceAlias + "}";
    }

    @Override
    public FieldSelectionBase withField(FieldModel newField) {
        return new UnresolvedFieldSelection(sourceAlias, fieldAlias, newField);
    }
}
