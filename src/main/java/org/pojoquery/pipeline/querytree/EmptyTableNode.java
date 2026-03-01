package org.pojoquery.pipeline.querytree;

import java.util.List;
import java.util.Objects;

import org.pojoquery.typemodel.TypeModel;

public record EmptyTableNode(
	String alias,
	TypeModel type,
	List<FieldSelection> fields,
	List<JoinedNode> joins,
	boolean isSuperClass
) implements QueryNode {

	public static EmptyTableNode of(String alias, TypeModel type) {
		Objects.requireNonNull(alias, "alias");
		Objects.requireNonNull(type, "type");
		return new EmptyTableNode(alias, type, List.of(), List.of(), false);
    }

	public EmptyTableNode withJoins(List<JoinedNode> newJoins) {
		return new EmptyTableNode(alias, type, fields, newJoins, isSuperClass);
	}

	public EmptyTableNode withIsSuperClass(boolean isSuperClass) {
		return new EmptyTableNode(alias, type, fields, joins, isSuperClass);
	}

	@Override
	public String toString() {
		return toStringWithIndent("");
	}

	String toStringWithIndent(String indent) {
		return indent + alias + " (empty)";
	}
}
