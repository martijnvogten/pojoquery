package org.pojoquery.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.pojoquery.pipeline.AbstractQueryTree.EmbeddedEntity;
import org.pojoquery.pipeline.AbstractQueryTree.EntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.EntityReference;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKeyField;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;

public class FluentAQTCodeGenerator {

	public String example() {
		return """
			package org.pojoquery.fluent;

			import org.pojoquery.fluent.FluentConditionChainWithInterfaces.Book;
			import org.pojoquery.fluent.internal.ConditionChainOperators;

			public class BookQuery extends FluentQuery<Book, BookQuery, BookQuery.Where, BookQuery.OrderBy, BookQuery.GroupBy> {

				public final ConditionChainOperators<Terminator<BookQuery>, Long> id;
				public final ConditionChainOperators<Terminator<BookQuery>, String> title;
				public final StaticAuthor author;

				public class StaticAuthor {
					public final ConditionChainOperators<Terminator<BookQuery>, Long> id = staticOp("author", "id", Long.class);
					public final ConditionChainOperators<Terminator<BookQuery>, String> name = staticOp("author", "name", String.class);
				}

				public class Where {
					public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>, Long> id = chainOp("book", "id", Long.class);
					public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>, String> title = chainOp("book", "title", String.class);
					public final WhereAuthor author = new WhereAuthor();

					public class WhereAuthor {
						public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>, Long> id = chainOp("author", "id", Long.class);
						public final ConditionChainOperators<ConditionTerminator<Book, Where, ?, OrderBy, GroupBy>, String> name = chainOp("author", "name", String.class);
					}
				}

				public class OrderBy {
					public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> id = orderByOp("book", "id");
					public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> title = orderByOp("book", "title");
					public final OrderByAuthor author = new OrderByAuthor();

					public class OrderByAuthor {
						public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> id = orderByOp("author", "id");
						public final OrderByChain<QueryTerminator<Book, OrderBy, GroupBy>> name = orderByOp("author", "name");
					}
				}

				public class GroupBy {
					public final QueryTerminator<Book, OrderBy, GroupBy> id = groupByOp("book", "id");
					public final QueryTerminator<Book, OrderBy, GroupBy> title = groupByOp("book", "title");
					public final GroupByAuthor author = new GroupByAuthor();

					public class GroupByAuthor {
						public final QueryTerminator<Book, OrderBy, GroupBy> id = groupByOp("author", "id");
						public final QueryTerminator<Book, OrderBy, GroupBy> name = groupByOp("author", "name");
					}
				}

				public BookQuery() {
					super(Book.class);
					// Initialize static operators after super() completes
					this.id = staticOp("book", "id", Long.class);
					this.title = staticOp("book", "title", String.class);
					this.author = new StaticAuthor();
				}

				@Override
				protected BookQuery.Where createWhereConditionStarter() {
					return new Where();
				}

				@Override
				protected BookQuery.OrderBy createOrderByStarter() {
					return new OrderBy();
				}

				@Override
				protected BookQuery.GroupBy createGroupByStarter() {
					return new GroupBy();
				}
			}
		""";
	}

	/**
     * Generates the complete query class source code.
     * 
     * @param tree The QueryTree describing the entity structure
     * @param packageName The package name for the generated class
     * @param entityName The simple name of the entity class
     * @param queryClassName The name for the generated query class
     * @param out The writer to output the generated code
     */
    public void generate(RootNode tree, String packageName, String entityName, 
            String queryClassName, Writer out) throws IOException {
		
		// Collect scalar fields and entity references from the tree
		List<ScalarField> scalarFields = new ArrayList<>();
		List<EntityField> entityFields = new ArrayList<>();
		collectFields(tree, scalarFields, entityFields);
		
		StringBuilder sb = new StringBuilder();
		
		// Package and imports
		sb.append("""
			package %s;
			
			import org.pojoquery.fluent.ConditionTerminator;
			import org.pojoquery.fluent.FluentQuery;
			import org.pojoquery.fluent.OrderByChain;
			import org.pojoquery.fluent.QueryTerminator;
			import org.pojoquery.fluent.Terminator;
			import org.pojoquery.fluent.internal.ConditionChainOperators;
			import org.pojoquery.fluent.internal.StaticConditionChainTerminator;
			
			""".formatted(packageName));
		
		// Class declaration
		sb.append("public class %s extends FluentQuery<%s, %s, %s.Where, %s.OrderBy, %s.GroupBy> {\n\n"
			.formatted(queryClassName, entityName, queryClassName, queryClassName, queryClassName, queryClassName));
		
		// Static operator field declarations
		for (ScalarField field : scalarFields) {
			sb.append("\tpublic final ConditionChainOperators<Terminator<%s,StaticConditionChainTerminator<%s>>, %s> %s;\n"
				.formatted(queryClassName, queryClassName, field.typeName, field.fieldName));
		}
		for (EntityField entity : entityFields) {
			sb.append("\tpublic final Static%s %s;\n"
				.formatted(capitalize(entity.fieldName), entity.fieldName));
		}
		sb.append("\n");
		
		// Static nested classes for entity references
		for (EntityField entity : entityFields) {
			generateStaticEntityClass(sb, queryClassName, entity, "\t");
		}
		
		// Where class
		sb.append("\tpublic class Where {\n");
		for (ScalarField field : scalarFields) {
			sb.append("\t\tpublic final ConditionChainOperators<ConditionTerminator<%s, Where, ?, OrderBy, GroupBy>, %s> %s = chainOp(\"%s\", \"%s\", %s.class);\n"
				.formatted(entityName, field.typeName, field.fieldName, field.tableAlias, field.fieldName, field.typeName));
		}
		for (EntityField entity : entityFields) {
			String cap = capitalize(entity.fieldName);
			sb.append("\t\tpublic final Where%s %s = new Where%s();\n"
				.formatted(cap, entity.fieldName, cap));
		}
		sb.append("\n");
		
		// Nested Where classes for entity references
		for (EntityField entity : entityFields) {
			generateWhereEntityClass(sb, entityName, entity, "\t\t");
		}
		sb.append("\t}\n\n");
		
		// OrderBy class
		sb.append("\tpublic class OrderBy {\n");
		for (ScalarField field : scalarFields) {
			sb.append("\t\tpublic final OrderByChain<QueryTerminator<%s, OrderBy, GroupBy>> %s = orderByOp(\"%s\", \"%s\");\n"
				.formatted(entityName, field.fieldName, field.tableAlias, field.fieldName));
		}
		for (EntityField entity : entityFields) {
			String cap = capitalize(entity.fieldName);
			sb.append("\t\tpublic final OrderBy%s %s = new OrderBy%s();\n"
				.formatted(cap, entity.fieldName, cap));
		}
		sb.append("\n");
		
		// Nested OrderBy classes for entity references
		for (EntityField entity : entityFields) {
			generateOrderByEntityClass(sb, entityName, entity, "\t\t");
		}
		sb.append("\t}\n\n");
		
		// GroupBy class
		sb.append("\tpublic class GroupBy {\n");
		for (ScalarField field : scalarFields) {
			sb.append("\t\tpublic final QueryTerminator<%s, OrderBy, GroupBy> %s = groupByOp(\"%s\", \"%s\");\n"
				.formatted(entityName, field.fieldName, field.tableAlias, field.fieldName));
		}
		for (EntityField entity : entityFields) {
			String cap = capitalize(entity.fieldName);
			sb.append("\t\tpublic final GroupBy%s %s = new GroupBy%s();\n"
				.formatted(cap, entity.fieldName, cap));
		}
		sb.append("\n");
		
		// Nested GroupBy classes for entity references
		for (EntityField entity : entityFields) {
			generateGroupByEntityClass(sb, entityName, entity, "\t\t");
		}
		sb.append("\t}\n\n");
		
		// Constructor
		sb.append("\tpublic %s() {\n".formatted(queryClassName));
		sb.append("\t\tsuper(%s.class);\n"
			.formatted(entityName, queryClassName));
		for (ScalarField field : scalarFields) {
			sb.append("\t\tthis.%s = staticOp(\"%s\", \"%s\", %s.class);\n"
				.formatted(field.fieldName, field.tableAlias, field.fieldName, field.typeName));
		}
		for (EntityField entity : entityFields) {
			sb.append("\t\tthis.%s = new Static%s();\n"
				.formatted(entity.fieldName, capitalize(entity.fieldName)));
		}
		sb.append("\t}\n");
		sb.append("\n");
		sb.append("\t@Override\n");
		sb.append("\tprotected %s.Where createWhereConditionStarter() {\n".formatted(queryClassName));
		sb.append("\t\treturn new Where();\n");
		sb.append("\t}\n\n");
		
		sb.append("\t@Override\n");
		sb.append("\tprotected %s.OrderBy createOrderByStarter() {\n".formatted(queryClassName));
		sb.append("\t\treturn new OrderBy();\n");
		sb.append("\t}\n\n");
		
		sb.append("\t@Override\n");
		sb.append("\tprotected %s.GroupBy createGroupByStarter() {\n".formatted(queryClassName));
		sb.append("\t\treturn new GroupBy();\n");
		sb.append("\t}\n");
		sb.append("}\n");
		
		out.write(sb.toString());
	}
	
	private void generateStaticEntityClass(StringBuilder sb, String queryClassName, 
			EntityField entity, String indent) {
		sb.append("%spublic class Static%s {\n".formatted(indent, capitalize(entity.fieldName)));
		for (ScalarField field : entity.scalarFields) {
			sb.append("%s\tpublic final ConditionChainOperators<Terminator<%s, StaticConditionChainTerminator<%s>>, %s> %s = staticOp(\"%s\", \"%s\", %s.class);\n"
				.formatted(indent, queryClassName, queryClassName, field.typeName, field.fieldName, field.tableAlias, field.fieldName, field.typeName));
		}
		for (EntityField nested : entity.entityFields) {
			String cap = capitalize(nested.fieldName);
			sb.append("%s\tpublic final Static%s %s = new Static%s();\n"
				.formatted(indent, cap, nested.fieldName, cap));
		}
		sb.append("%s}\n\n".formatted(indent));
		
		// Recursively generate nested static classes
		for (EntityField nested : entity.entityFields) {
			generateStaticEntityClass(sb, queryClassName, nested, indent);
		}
	}
	
	private void generateWhereEntityClass(StringBuilder sb, String entityName, 
			EntityField entity, String indent) {
		sb.append("%spublic class Where%s {\n".formatted(indent, capitalize(entity.fieldName)));
		for (ScalarField field : entity.scalarFields) {
			sb.append("%s\tpublic final ConditionChainOperators<ConditionTerminator<%s, Where, ?, OrderBy, GroupBy>, %s> %s = chainOp(\"%s\", \"%s\", %s.class);\n"
				.formatted(indent, entityName, field.typeName, field.fieldName, field.tableAlias, field.fieldName, field.typeName));
		}
		for (EntityField nested : entity.entityFields) {
			String cap = capitalize(nested.fieldName);
			sb.append("%s\tpublic final Where%s %s = new Where%s();\n"
				.formatted(indent, cap, nested.fieldName, cap));
		}
		sb.append("%s}\n\n".formatted(indent));
		
		// Recursively generate nested where classes
		for (EntityField nested : entity.entityFields) {
			generateWhereEntityClass(sb, entityName, nested, indent);
		}
	}
	
	private void generateOrderByEntityClass(StringBuilder sb, String entityName, 
			EntityField entity, String indent) {
		sb.append("%spublic class OrderBy%s {\n".formatted(indent, capitalize(entity.fieldName)));
		for (ScalarField field : entity.scalarFields) {
			sb.append("%s\tpublic final OrderByChain<QueryTerminator<%s, OrderBy, GroupBy>> %s = orderByOp(\"%s\", \"%s\");\n"
				.formatted(indent, entityName, field.fieldName, field.tableAlias, field.fieldName));
		}
		for (EntityField nested : entity.entityFields) {
			String cap = capitalize(nested.fieldName);
			sb.append("%s\tpublic final OrderBy%s %s = new OrderBy%s();\n"
				.formatted(indent, cap, nested.fieldName, cap));
		}
		sb.append("%s}\n\n".formatted(indent));
		
		// Recursively generate nested orderby classes
		for (EntityField nested : entity.entityFields) {
			generateOrderByEntityClass(sb, entityName, nested, indent);
		}
	}
	
	private void generateGroupByEntityClass(StringBuilder sb, String entityName, 
			EntityField entity, String indent) {
		sb.append("%spublic class GroupBy%s {\n".formatted(indent, capitalize(entity.fieldName)));
		for (ScalarField field : entity.scalarFields) {
			sb.append("%s\tpublic final QueryTerminator<%s, OrderBy, GroupBy> %s = groupByOp(\"%s\", \"%s\");\n"
				.formatted(indent, entityName, field.fieldName, field.tableAlias, field.fieldName));
		}
		for (EntityField nested : entity.entityFields) {
			String cap = capitalize(nested.fieldName);
			sb.append("%s\tpublic final GroupBy%s %s = new GroupBy%s();\n"
				.formatted(indent, cap, nested.fieldName, cap));
		}
		sb.append("%s}\n\n".formatted(indent));
		
		// Recursively generate nested groupby classes
		for (EntityField nested : entity.entityFields) {
			generateGroupByEntityClass(sb, entityName, nested, indent);
		}
	}
	
	private void collectFields(TableNode table, List<ScalarField> scalarFields, 
			List<EntityField> entityFields) {
		String alias = table.alias();
		
		for (QueryNode child : table.children()) {
			if (child instanceof ScalarNode scalar && scalar instanceof PrimaryKeyField pk) {
				scalarFields.add(new ScalarField(pk.field().getName(), alias, getBoxedTypeName(pk.field().getType())));
			} else if (child instanceof ScalarNode scalar) {
				scalarFields.add(new ScalarField(scalar.field().getName(), alias, getBoxedTypeName(scalar.field().getType())));
			} else if (child instanceof EntityReference ref) {
				entityFields.add(collectEntityField(ref));
			} else if (child instanceof EntityCollection coll) {
				entityFields.add(collectEntityField(coll));
			} else if (child instanceof JoinTableEntityCollection joinColl) {
				entityFields.add(collectEntityField(joinColl));
			} else if (child instanceof EmbeddedEntity embedded) {
				// For embedded entities, collect fields with embedded's alias
				collectFields(embedded, scalarFields, entityFields);
			}
		}
	}
	
	private EntityField collectEntityField(TableNode entityNode) {
		List<ScalarField> scalarFields = new ArrayList<>();
		List<EntityField> nestedEntities = new ArrayList<>();
		
		String fieldName;
		if (entityNode instanceof EntityReference ref) {
			fieldName = ref.field().getName();
		} else if (entityNode instanceof EntityCollection coll) {
			fieldName = coll.field().getName();
		} else if (entityNode instanceof JoinTableEntityCollection joinColl) {
			fieldName = joinColl.field().getName();
		} else {
			throw new IllegalArgumentException("Unexpected node type: " + entityNode.getClass());
		}
		
		if (entityNode.children() != null) {
			collectFields(entityNode, scalarFields, nestedEntities);
		}
		
		return new EntityField(fieldName, entityNode.alias(), scalarFields, nestedEntities);
	}
	
	private String capitalize(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
	
	private String getBoxedTypeName(org.pojoquery.typemodel.TypeModel type) {
		String name = type.getQualifiedName();
		return switch (name) {
			case "int" -> "Integer";
			case "long" -> "Long";
			case "double" -> "Double";
			case "float" -> "Float";
			case "boolean" -> "Boolean";
			case "byte" -> "Byte";
			case "short" -> "Short";
			case "char" -> "Character";
			default -> name;
		};
	}
	
	// Helper records for collecting field information
	private record ScalarField(String fieldName, String tableAlias, String typeName) {}
	
	private record EntityField(String fieldName, String alias, 
			List<ScalarField> scalarFields, List<EntityField> entityFields) {}
}
