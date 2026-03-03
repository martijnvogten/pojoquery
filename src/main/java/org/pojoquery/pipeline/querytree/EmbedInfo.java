package org.pojoquery.pipeline.querytree;

import java.util.Objects;

import org.pojoquery.typemodel.FieldModel;

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
    String sourceAlias
) {
    public EmbedInfo {
        Objects.requireNonNull(linkField, "linkField");
        Objects.requireNonNull(fieldPrefix, "fieldPrefix");
        Objects.requireNonNull(sourceAlias, "sourceAlias");
    }

    public static EmbedInfo of(FieldModel linkField, String fieldPrefix, String sourceAlias) {
        return new EmbedInfo(linkField, fieldPrefix, sourceAlias);
    }
}
