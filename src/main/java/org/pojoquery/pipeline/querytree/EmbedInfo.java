package org.pojoquery.pipeline.querytree;

import java.util.Objects;

import org.pojoquery.typemodel.FieldModel;
import org.pojoquery.typemodel.TypeModel;

/**
 * Encapsulates embed metadata for a node in the query tree.
 * This record captures how a node is embedded within its parent.
 *
 * @param linkField The Java field that created this embed (may be null for manual embeds)
 * @param fieldPrefix The prefix to apply to column names
 * @param isCollection True if this is a one-to-many relationship (List/Set/Array)
 */
public record EmbedInfo(
    FieldModel linkField,
    String fieldPrefix,
    String sourceAlias,
    TypeModel superType
) {
    public EmbedInfo {
        Objects.requireNonNull(fieldPrefix, "fieldPrefix");
        Objects.requireNonNull(sourceAlias, "sourceAlias");
    }

    public static EmbedInfo of(FieldModel linkField, String fieldPrefix, String sourceAlias) {
        return new EmbedInfo(linkField, fieldPrefix, sourceAlias, null);
    }
    
    public static EmbedInfo of(FieldModel linkField, String fieldPrefix, String sourceAlias, TypeModel superType) {
        return new EmbedInfo(linkField, fieldPrefix, sourceAlias, superType);
    }
}
