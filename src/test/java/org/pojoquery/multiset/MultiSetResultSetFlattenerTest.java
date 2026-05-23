package org.pojoquery.multiset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.Test;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.SqlExpression;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.pipeline.AQTRowProcessor;
import org.pojoquery.pipeline.AbstractQueryTree.AggregateScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.EntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKey;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.ScalarValue;
import org.pojoquery.pipeline.AbstractQueryTree.TableInfo;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.TransformPipeline;
import org.pojoquery.schema.SchemaGenerator;
import org.pojoquery.util.CurlyMarkers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Proof of concept: query nested collections as positional JSON arrays
 * ("multisets") instead of joined tables. Each row in the result set carries
 * a single JSON_ARRAY value per root entity, with nested arrays per
 * collection. This avoids cartesian fan-out across sibling collections.
 *
 * To re-use the existing {@link AQTRowProcessor} (which expects flat rows
 * keyed by {@code alias.field}), the JSON tree is expanded back into
 * repeated rows. Sibling collections are unioned (not crossed), so the
 * number of expanded rows is {@code sum(|coll_i|)} instead of
 * {@code prod(|coll_i|)}.
 */
public class MultiSetResultSetFlattenerTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Table("user")
	static class User {
		@Id Long id;
		String username;
		@Link(linktable = "user_role")
		List<Role> roles;
	}

	@Table("role")
	static class Role {
		@Id Long id;
		String rolename;
		@Link(linktable = "role_permission")
		List<Permission> permissions;
	}

	@Table("permission")
	static class Permission {
		@Id Long id;
		String permissionname;
	}

	@Table("author")
	static class Author {
		@Id Long id;
		String name;
		List<Book> books;
	}

	@Table("book")
	static class Book {
		@Id Long id;
		String title;
	}

	// ---------------------------------------------------------------------
	// Schema describing positions of fields/collections in the JSON arrays.
	// ---------------------------------------------------------------------

	record MultiSetCollection(String alias, MultiSetSchema childSchema) {}

	record MultiSetSchema(String alias, List<String> fieldNames, List<MultiSetCollection> collections) {}

	// ---------------------------------------------------------------------
	// Builder: emits a derived-table tree that materializes positional JSON
	// arrays for the root entity and each nested collection.
	// ---------------------------------------------------------------------

	static class MultiSetSqlQueryBuilder {
		record FieldExpr(String name, SqlExpression expression) {}

		record CollectionSubQuery(String alias, SqlExpression subquery, SqlExpression joinCondition) {}

		record TableJoin(TableInfo table, String alias, SqlExpression joinCondition) {}

		final DbContext dbContext;
		final int nestLevel;
		final String indent;

		TableInfo table;
		String tableAlias;

		final List<FieldExpr> scalarFields = new ArrayList<>();
		final List<TableJoin> tableJoins = new ArrayList<>();
		final List<CollectionSubQuery> collectionJoins = new ArrayList<>();

		MultiSetSqlQueryBuilder(DbContext dbContext) {
			this(dbContext, 0);
		}

		MultiSetSqlQueryBuilder(DbContext dbContext, int nestLevel) {
			this.dbContext = dbContext;
			this.nestLevel = nestLevel;
			this.indent = "  ".repeat(nestLevel);
		}

		int getNestLevel() {
			return nestLevel;
		}

		void setTable(TableInfo table, String alias) {
			this.table = table;
			this.tableAlias = alias;
		}

		void addScalar(String name, SqlExpression expression) {
			scalarFields.add(new FieldExpr(name, expression));
		}

		void addCollection(String alias, SqlExpression subquery, SqlExpression joinCondition) {
			collectionJoins.add(new CollectionSubQuery(alias, subquery, joinCondition));
		}

		void addTableJoin(TableInfo joinTable, String alias, SqlExpression joinCondition) {
			tableJoins.add(new TableJoin(joinTable, alias, joinCondition));
		}

		/**
		 * Build the JSON expression for one row: positional layout
		 * [scalar0, scalar1, ..., coll0_json, coll1_json, ...].
		 *
		 * Child-collection slots come back as JSON-text strings (we deliberately
		 * do not use FORMAT JSON: HSQLDB rejects it inside JSON_ARRAY arg lists,
		 * and skipping it keeps the SQL portable). The flattener re-parses those
		 * string slots recursively.
		 */
		SqlExpression buildRowJsonExpression() {
			List<SqlExpression> elements = new ArrayList<>();
			for (FieldExpr f : scalarFields) {
				elements.add(resolve(f.expression()));
			}
			for (CollectionSubQuery c : collectionJoins) {
				elements.add(SqlExpression.sql(
						dbContext.quoteAlias(c.alias()) + "." + dbContext.quoteObjectNames("json")));
			}
			return SqlExpression.implode("", List.of(
					SqlExpression.sql("JSON_ARRAY("),
					SqlExpression.implode(", ", elements),
					SqlExpression.sql(")")));
		}

		/**
		 * Emit the outer SELECT for the root: one row per root entity, single
		 * column {@code json} containing the positional JSON array.
		 */
		SqlExpression toRootStatement() {
			List<SqlExpression> parts = new ArrayList<>();
			parts.add(SqlExpression.sql("SELECT"));
			parts.add(SqlExpression.implode("", List.of(
					SqlExpression.sql("  "),
					buildRowJsonExpression(),
					SqlExpression.sql(" AS " + dbContext.quoteAlias("json")))));
			parts.add(SqlExpression.sql("FROM " + quoteTable(table) + " AS " + dbContext.quoteAlias(tableAlias)));
			appendJoins(parts, "");
			return SqlExpression.implode("\n", parts);
		}

		/**
		 * Emit a nested aggregation subquery: inline JSON_ARRAYAGG(JSON_ARRAY(...))
		 * grouped by the parent foreign key.
		 */
		SqlExpression toAggregatedStatement(String parentFkSourceAlias, String parentFkColumn) {
			List<SqlExpression> parts = new ArrayList<>();
			parts.add(SqlExpression.sql(indent + "SELECT"));
			parts.add(SqlExpression.implode("", List.of(
					SqlExpression.sql(indent + "  "),
					resolve(SqlExpression.sql("{" + parentFkSourceAlias + "." + parentFkColumn + "}")),
					SqlExpression.sql(" AS " + dbContext.quoteAlias(parentFkColumn) + ","))));
			parts.add(SqlExpression.implode("", List.of(
					SqlExpression.sql(indent + "  JSON_ARRAYAGG("),
					buildRowJsonExpression(),
					SqlExpression.sql(") AS " + dbContext.quoteAlias("json")))));
			parts.add(SqlExpression.sql(indent + "FROM " + quoteTable(table) + " AS "
					+ dbContext.quoteAlias(tableAlias)));
			appendJoins(parts, indent);
			parts.add(SqlExpression.sql(indent + "GROUP BY " + dbContext.quoteAlias(parentFkSourceAlias) + "."
					+ dbContext.quoteObjectNames(parentFkColumn)));
			return SqlExpression.implode("\n", parts);
		}

		private void appendJoins(List<SqlExpression> parts, String linePrefix) {
			for (TableJoin tj : tableJoins) {
				parts.add(SqlExpression.implode("", List.of(
						SqlExpression.sql(linePrefix + "LEFT JOIN " + quoteTable(tj.table()) + " AS "
								+ dbContext.quoteAlias(tj.alias()) + " ON "),
						resolve(tj.joinCondition()))));
			}
			for (CollectionSubQuery c : collectionJoins) {
				parts.add(SqlExpression.implode("", List.of(
						SqlExpression.sql(linePrefix + "LEFT JOIN (\n"),
						c.subquery(),
						SqlExpression.sql("\n" + linePrefix + ") AS " + dbContext.quoteAlias(c.alias()) + " ON "),
						resolve(c.joinCondition()))));
			}
		}

		private String quoteTable(TableInfo t) {
			return t.schemaName() != null && !t.schemaName().isEmpty()
					? dbContext.quoteObjectNames(t.schemaName(), t.tableName())
					: dbContext.quoteObjectNames(t.tableName());
		}

		private SqlExpression resolve(SqlExpression expr) {
			return new SqlExpression(CurlyMarkers.processMarkers(expr.getSql(), marker -> {
				int dot = marker.lastIndexOf('.');
				if (dot > 0) {
					return dbContext.quoteAlias(marker.substring(0, dot)) + "."
							+ dbContext.quoteObjectNames(marker.substring(dot + 1));
				}
				return dbContext.quoteObjectNames(marker);
			}));
		}
	}

	// ---------------------------------------------------------------------
	// Transformer: walks the AQT and populates a MultiSetSqlQueryBuilder.
	// Returns a schema mapping JSON array positions back to field names.
	// ---------------------------------------------------------------------

	public static MultiSetSchema toMultiSetQuery(TableNode node, MultiSetSqlQueryBuilder builder) {
		if (node instanceof RootNode) {
			builder.setTable(node.tableInfo(), node.tableInfo().tableName());
		}

		List<String> fieldNames = new ArrayList<>();
		List<MultiSetCollection> collections = new ArrayList<>();

		for (QueryNode child : node.children()) {
			if (child instanceof PrimaryKey pk) {
				builder.addScalar(pk.field().getName(), pk.expression());
				fieldNames.add(pk.field().getName());
			} else if (child instanceof AggregateScalarValue agg) {
				builder.addScalar(agg.field().getName(), agg.expression());
				fieldNames.add(agg.field().getName());
			} else if (child instanceof ScalarValue scalar) {
				builder.addScalar(scalar.field().getName(), scalar.expression());
				fieldNames.add(scalar.field().getName());
			} else if (child instanceof EntityCollection ec) {
				MultiSetSqlQueryBuilder inner = new MultiSetSqlQueryBuilder(builder.dbContext,
						builder.getNestLevel() + 1);
				inner.setTable(ec.tableInfo(), ec.alias());
				MultiSetSchema childSchema = toMultiSetQuery(ec, inner);

				String parentFkCol = ec.join().fkColumnName();
				SqlExpression subQuery = inner.toAggregatedStatement(ec.alias(), parentFkCol);
				builder.addCollection(ec.alias(), subQuery, ec.join().joinCondition());
				collections.add(new MultiSetCollection(ec.alias(), childSchema));
			} else if (child instanceof JoinTableEntityCollection jtec) {
				MultiSetSqlQueryBuilder inner = new MultiSetSqlQueryBuilder(builder.dbContext,
						builder.getNestLevel() + 1);

				String junctionAlias = jtec.join().joinTableInfo().joinTableAlias();
				TableInfo junctionTable = jtec.join().joinTableInfo().tableInfo();
				String parentFkCol = jtec.join().parentKey().fkColumnName();
				String parentIdCol = jtec.join().parentKey().idColumnName();

				// FROM junction, LEFT JOIN child entity, GROUP BY junction.parent_fk.
				inner.setTable(junctionTable, junctionAlias);
				inner.addTableJoin(jtec.join().childKey().targetTable(), jtec.alias(),
						jtec.join().childKey().joinCondition());
				MultiSetSchema childSchema = toMultiSetQuery(jtec, inner);

				SqlExpression subQuery = inner.toAggregatedStatement(junctionAlias, parentFkCol);
				SqlExpression onCondition = SqlExpression.sql("{" + jtec.alias() + "." + parentFkCol + "} = {"
						+ node.alias() + "." + parentIdCol + "}");

				builder.addCollection(jtec.alias(), subQuery, onCondition);
				collections.add(new MultiSetCollection(jtec.alias(), childSchema));
			}
		}

		return new MultiSetSchema(node.alias(), fieldNames, collections);
	}

	// ---------------------------------------------------------------------
	// Flattener: expand the JSON tree back into the row shape AQTRowProcessor
	// consumes. Sibling collections are unioned, not crossed.
	// ---------------------------------------------------------------------

	static List<Map<String, Object>> flatten(List<Map<String, Object>> jsonRows, MultiSetSchema schema)
			throws Exception {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> r : jsonRows) {
			JsonNode root = JSON.readTree(String.valueOf(r.get("json")));
			expand(root, schema, new HashMap<>(), out);
		}
		return out;
	}

	private static void expand(JsonNode array, MultiSetSchema schema, Map<String, Object> parentCtx,
			List<Map<String, Object>> out) throws Exception {
		Map<String, Object> base = new HashMap<>(parentCtx);
		for (int i = 0; i < schema.fieldNames().size(); i++) {
			base.put(schema.alias() + "." + schema.fieldNames().get(i),
					jsonToValue(array.path(i)));
		}

		if (schema.collections().isEmpty()) {
			out.add(base);
			return;
		}

		boolean anyEmitted = false;
		for (int ci = 0; ci < schema.collections().size(); ci++) {
			MultiSetCollection coll = schema.collections().get(ci);
			JsonNode slot = array.path(schema.fieldNames().size() + ci);
			JsonNode items = slot.isTextual() ? JSON.readTree(slot.asText()) : slot;
			if (items != null && items.isArray() && items.size() > 0) {
				for (JsonNode item : items) {
					JsonNode itemNode = item.isTextual() ? JSON.readTree(item.asText()) : item;
					expand(itemNode, coll.childSchema(), base, out);
					anyEmitted = true;
				}
			}
		}
		if (!anyEmitted) {
			out.add(base);
		}
	}

	private static Object jsonToValue(JsonNode n) {
		if (n == null || n.isNull() || n.isMissingNode()) return null;
		if (n.isIntegralNumber()) return n.asLong();
		if (n.isFloatingPointNumber()) return n.asDouble();
		if (n.isBoolean()) return n.asBoolean();
		return n.asText();
	}

	// ---------------------------------------------------------------------
	// End-to-end test
	// ---------------------------------------------------------------------

	@Test
	public void testMultiSetRoundTripThroughAqtRowProcessor() throws Exception {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class, Role.class, Permission.class);
		insertTestData(db);

		PojoQuery<User> query = PojoQuery.build(DbContext.getDefault(),
				TransformPipeline.defaultPipeline(), User.class);
		RootNode tree = query.getTree();

		MultiSetSqlQueryBuilder builder = new MultiSetSqlQueryBuilder(DbContext.getDefault());
		MultiSetSchema schema = toMultiSetQuery(tree, builder);
		String sql = builder.toRootStatement().getSql();
		System.out.println(sql);

		List<Map<String, Object>> jsonRows = DB.queryRows(db, sql);
		jsonRows.forEach(row -> System.out.println(row));

		List<Map<String, Object>> flat = flatten(jsonRows, schema);
		flat.forEach(row -> System.out.println(row));

		List<User> users = AQTRowProcessor.processRows(tree, flat);

		assertEquals(1, users.size());
		User user = users.get(0);
		assertEquals("joe", user.username);
		assertNotNull(user.roles);
		assertEquals(2, user.roles.size());

		Role admin = user.roles.stream().filter(r -> "admin".equals(r.rolename)).findFirst().orElse(null);
		assertNotNull(admin);
		assertNotNull(admin.permissions);
		assertEquals(2, admin.permissions.size());

		Role editor = user.roles.stream().filter(r -> "editor".equals(r.rolename)).findFirst().orElse(null);
		assertNotNull(editor);
		assertNotNull(editor.permissions);
		assertEquals(1, editor.permissions.size());
	}

	private static void insertTestData(DataSource db) {
		DB.runInTransaction(db, connection -> {
			Permission readPermission = new Permission();
			readPermission.permissionname = "read";
			PojoQuery.insert(connection, readPermission);

			Permission writePermission = new Permission();
			writePermission.permissionname = "write";
			PojoQuery.insert(connection, writePermission);

			Role adminRole = new Role();
			adminRole.rolename = "admin";
			adminRole.permissions = List.of(readPermission, writePermission);
			PojoQuery.insert(connection, adminRole);

			Role editorRole = new Role();
			editorRole.rolename = "editor";
			editorRole.permissions = List.of(readPermission);
			PojoQuery.insert(connection, editorRole);

			User joe = new User();
			joe.username = "joe";
			joe.roles = List.of(adminRole, editorRole);
			PojoQuery.insert(connection, joe);
		});
	}

	/**
	 * Author has a one-to-many collection of Books (FK author_id in book).
	 * Inserts an Alice with 2 books, a Bob with 0 books, and an orphan book
	 * whose author_id is NULL. Prints the raw JSON the multiset query
	 * produces for each row so the null-slot shape is visible.
	 */
	@Test
	public void testAuthorBooks_printsJsonWhenAuthorIdIsNull() throws Exception {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Author.class, Book.class);

		DB.runInTransaction(db, connection -> {
			Book book1 = new Book();
			book1.title = "Book One";
			Book book2 = new Book();
			book2.title = "Book Two";

			Author alice = new Author();
			alice.name = "Alice";
			alice.books = List.of(book1, book2);
			PojoQuery.insert(connection, alice);

			Author bob = new Author();
			bob.name = "Bob";
			bob.books = List.of();
			PojoQuery.insert(connection, bob);

			// Orphan book with author_id = NULL — not reachable via any Author.
			DB.update(connection, SqlExpression.sql(
					"INSERT INTO \"book\" (\"title\", \"author_id\") VALUES ('Orphan', NULL)"));
		});

		PojoQuery<Author> query = PojoQuery.build(DbContext.getDefault(),
				TransformPipeline.defaultPipeline(), Author.class);
		RootNode tree = query.getTree();

		MultiSetSqlQueryBuilder builder = new MultiSetSqlQueryBuilder(DbContext.getDefault());
		MultiSetSchema schema = toMultiSetQuery(tree, builder);
		String sql = builder.toRootStatement().getSql();
		System.out.println("--- SQL ---");
		System.out.println(sql);

		System.out.println("--- raw JSON rows ---");
		List<Map<String, Object>> jsonRows = DB.queryRows(db, sql);
		jsonRows.forEach(row -> System.out.println(row.get("json")));

		// Confirm the round-trip still hydrates correctly even with a null books slot.
		List<Map<String, Object>> flat = flatten(jsonRows, schema);
		List<Author> authors = AQTRowProcessor.processRows(tree, flat);
		assertEquals(2, authors.size());

		Author alice = authors.stream().filter(a -> "Alice".equals(a.name)).findFirst().orElseThrow();
		Author bob = authors.stream().filter(a -> "Bob".equals(a.name)).findFirst().orElseThrow();
		assertEquals(2, alice.books.size());
		// Bob's books slot was NULL in the JSON; the flattener treats it as no children.
		if (bob.books != null) {
			assertEquals(0, bob.books.size());
		}
	}
}
