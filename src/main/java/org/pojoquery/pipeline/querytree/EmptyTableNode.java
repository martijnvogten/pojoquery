package org.pojoquery.pipeline.querytree;

import java.util.List;
import java.util.Objects;

import org.pojoquery.typemodel.TypeModel;

/**
 * Represents a table node that hasn't been fully resolved yet.
 * Used during query tree building before field information is populated.
 *
 * @param alias The alias used to reference this table in the query
 * @param type The Java type that will be instantiated for rows from this table
 * @param superType Where to stop scanning fields (null = scan all inherited fields)
 * @param children Child nodes from this table
 * @param isSuperClass True if this represents a superclass table
 * @param isSubClass True if this represents a subclass table
 * @param joinInfo Join information (non-null for joined tables)
 * @param embedInfo Embed information (non-null for embedded/STI tables)
 */
public record EmptyTableNode(
	String alias,
	TypeModel type,
	TypeModel superType,
	List<QueryNode> children,
	boolean isSuperClass,
	boolean isSubClass,
	JoinInfo joinInfo,
	EmbedInfo embedInfo
) implements QueryNode, HasToStringWithIndent {

	public boolean isEmbedded() {
		return embedInfo != null;
	}

	/**
	 * Returns the alias to use as the source for SQL column references.
	 * <ul>
	 *   <li>For joined nodes (joinInfo != null): returns alias() (has its own table)</li>
	 *   <li>For embedded nodes (embedInfo != null): returns embedInfo.sourceAlias() (shares parent's table)</li>
	 *   <li>For root nodes: returns alias()</li>
	 * </ul>
	 */
	public String sourceAlias() {
		if (embedInfo != null) {
			return embedInfo.sourceAlias();
		}
		return alias;
	}

	public static EmptyTableNode of(String alias, TypeModel type) {
		Objects.requireNonNull(alias, "alias");
		Objects.requireNonNull(type, "type");
		return new EmptyTableNode(alias, type, null, List.of(), false, false, null, null);
    }
	
	public static EmptyTableNode ofJoined(String alias, TypeModel type, JoinInfo joinInfo) {
		Objects.requireNonNull(alias, "alias");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(joinInfo, "joinInfo");
		Objects.requireNonNull(joinInfo.childTable(), "joinInfo.childTable");
		return new EmptyTableNode(alias, type, null, List.of(), false, false, joinInfo, null);
    }
	
	public static EmptyTableNode ofEmbedded(String alias, TypeModel type, EmbedInfo embedInfo) {
		Objects.requireNonNull(alias, "alias");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(embedInfo, "embedInfo");
		Objects.requireNonNull(embedInfo.sourceAlias(), "embedInfo.sourceAlias");
		return new EmptyTableNode(alias, type, embedInfo.superType(), List.of(), false, false, null, embedInfo);
    }

	public JoinedNode toJoinedNode(TableInfo table, List<FieldSelectionBase> fields, List<String> idFields) {
		return new JoinedNode(alias, type, table, fields, children, idFields, false, null, null, null, null, joinInfo, List.of(), isSuperClass, isSubClass);
	}

	public EmbeddedNode toEmbeddedNode(List<FieldSelectionBase> fields) {
		return EmbeddedNode.of(alias, type, fields, embedInfo);
	}

	@Override
	public EmptyTableNode withChildren(List<QueryNode> newChildren) {
		return new EmptyTableNode(alias, type, superType, newChildren, isSuperClass, isSubClass, joinInfo, embedInfo);
	}

	public EmptyTableNode withIsSuperClass(boolean isSuperClass) {
		return new EmptyTableNode(alias, type, superType, children, isSuperClass, isSubClass, joinInfo, embedInfo);
	}

	public EmptyTableNode withIsSubClass(boolean isSubClass) {
		return new EmptyTableNode(alias, type, superType, children, isSuperClass, isSubClass, joinInfo, embedInfo);
	}
	
	public EmptyTableNode withJoinInfo(JoinInfo newJoinInfo) {
		return new EmptyTableNode(alias, type, superType, children, isSuperClass, isSubClass, newJoinInfo, embedInfo);
	}
	
	public EmptyTableNode withSuperType(TypeModel newSuperType) {
		return new EmptyTableNode(alias, type, newSuperType, children, isSuperClass, isSubClass, joinInfo, embedInfo);
	}

	public EmptyTableNode withType(TypeModel newType) {
		return new EmptyTableNode(alias, newType, superType, children, isSuperClass, isSubClass, joinInfo, embedInfo);
	}

	@Override
	public String toString() {
		return toStringWithIndent("");
	}

	public String toStringWithIndent(String indent) {
		StringBuilder sb = new StringBuilder();
		sb.append(indent).append("EmptyTableNode {\n");
		sb.append(indent).append("  alias: \"").append(alias).append("\"\n");
		sb.append(indent).append("  type: ").append(type.getSimpleName()).append("\n");
		if (isSuperClass) {
			sb.append(indent).append("  isSuperClass: true\n");
		}
		if (isSubClass) {
			sb.append(indent).append("  isSubClass: true\n");
		}
		if (joinInfo != null) {
			sb.append(indent).append("  joinInfo: ").append(joinInfo.joinType());
			if (joinInfo.joinCondition() != null) {
				sb.append(" ON ").append(joinInfo.joinCondition());
			}
			sb.append("\n");
		}
		if (embedInfo != null) {
			sb.append(indent).append("  embedInfo: prefix=\"").append(embedInfo.fieldPrefix()).append("\"\n");
		}
		if (!children.isEmpty()) {
			sb.append(indent).append("  children: [\n");
			for (QueryNode child : children) {
				sb.append(QueryTree.toStringNode(child, indent + "    "));
			}
			sb.append(indent).append("  ]\n");
		}
		sb.append(indent).append("}\n");
		return sb.toString();
	}
}
