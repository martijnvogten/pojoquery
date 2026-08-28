package org.pojoquery.multiset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
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
import org.pojoquery.pipeline.AbstractQueryTree.ScalarNode;
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
 *
 * The array layout needs no separate schema description: every child node of
 * an AQT node gets exactly one slot, in child order, and children that carry
 * no value get a literal NULL. Slot index therefore equals child index, so the
 * flattener reads the arrays back by walking the same tree the builder walked.
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

	@Table("publisher")
	static class Publisher {
		@Id Long id;
		String name;
	}

	@Table("magazine")
	static class Magazine {
		@Id Long id;
		String title;
		// Many-to-one: not projected into the JSON array, so it occupies a NULL slot
		// between "title" and "articles".
		Publisher publisher;
		List<Article> articles;
	}

	@Table("article")
	static class Article {
		@Id Long id;
		String headline;
	}

	// ---------------------------------------------------------------------
	// Builder: emits a derived-table tree that materializes positional JSON
	// arrays for the root entity and each nested collection.
	// ---------------------------------------------------------------------

	static class MultiSetSqlQueryBuilder {
		record CollectionSubQuery(String alias, SqlExpression subquery, SqlExpression joinCondition) {}

		record TableJoin(TableInfo table, String alias, SqlExpression joinCondition) {}

		final DbContext dbContext;
		final int nestLevel;
		final String indent;

		TableInfo table;
		String tableAlias;

		/** One positional slot per child node of the AQT node, in child order. */
		final List<SqlExpression> slots = new ArrayList<>();
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

		void addScalar(SqlExpression expression) {
			slots.add(resolve(expression));
		}

		/**
		 * Slot for a child node this POC does not project; keeps slot indices
		 * aligned. Cast because HSQLDB rejects an untyped NULL literal inside a
		 * JSON_ARRAY argument list.
		 */
		void addNullSlot() {
			slots.add(SqlExpression.sql("CAST(NULL AS VARCHAR(1))"));
		}

		void addCollection(String alias, SqlExpression subquery, SqlExpression joinCondition) {
			collectionJoins.add(new CollectionSubQuery(alias, subquery, joinCondition));
			slots.add(SqlExpression.sql(
					dbContext.quoteAlias(alias) + "." + dbContext.quoteObjectNames("json")));
		}

		void addTableJoin(TableInfo joinTable, String alias, SqlExpression joinCondition) {
			tableJoins.add(new TableJoin(joinTable, alias, joinCondition));
		}

		/**
		 * Build the JSON expression for one row: one slot per child node of the
		 * AQT node, in child order (see the class javadoc).
		 *
		 * Child-collection slots come back as JSON-text strings (we deliberately
		 * do not use FORMAT JSON: HSQLDB rejects it inside JSON_ARRAY arg lists,
		 * and skipping it keeps the SQL portable). The flattener re-parses those
		 * string slots recursively.
		 *
		 * Positional alignment relies on NULL elements being kept in the array;
		 * engines whose JSON_ARRAY defaults to ABSENT ON NULL (e.g. PostgreSQL 16+)
		 * need an explicit NULL ON NULL here.
		 */
		SqlExpression buildRowJsonExpression() {
			return SqlExpression.implode("", List.of(
					SqlExpression.sql("JSON_ARRAY("),
					SqlExpression.implode(", ", slots),
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
	// ---------------------------------------------------------------------

	public static void toMultiSetQuery(TableNode node, MultiSetSqlQueryBuilder builder) {
		if (node instanceof RootNode) {
			builder.setTable(node.tableInfo(), node.tableInfo().tableName());
		}

		for (QueryNode child : node.children()) {
			if (child instanceof ScalarNode scalar) {
				builder.addScalar(scalarExpression(scalar));
			} else if (child instanceof EntityCollection ec) {
				MultiSetSqlQueryBuilder inner = new MultiSetSqlQueryBuilder(builder.dbContext,
						builder.getNestLevel() + 1);
				inner.setTable(ec.tableInfo(), ec.alias());
				toMultiSetQuery(ec, inner);

				String parentFkCol = ec.join().fkColumnName();
				builder.addCollection(ec.alias(), inner.toAggregatedStatement(ec.alias(), parentFkCol),
						ec.join().joinCondition());
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
				toMultiSetQuery(jtec, inner);

				SqlExpression onCondition = SqlExpression.sql("{" + jtec.alias() + "." + parentFkCol + "} = {"
						+ node.alias() + "." + parentIdCol + "}");
				builder.addCollection(jtec.alias(), inner.toAggregatedStatement(junctionAlias, parentFkCol),
						onCondition);
			} else {
				// Not projected (many-to-one join, embedded entity, ...): emit a
				// NULL slot so the remaining slots stay at their child index.
				builder.addNullSlot();
			}
		}
	}

	private static SqlExpression scalarExpression(ScalarNode scalar) {
		if (scalar instanceof PrimaryKey pk) {
			return pk.expression();
		}
		if (scalar instanceof AggregateScalarValue agg) {
			return agg.expression();
		}
		if (scalar instanceof ScalarValue value) {
			return value.expression();
		}
		throw new IllegalArgumentException("Unsupported scalar node: " + scalar);
	}

	// ---------------------------------------------------------------------
	// Flattener: expand the JSON tree back into the row shape AQTRowProcessor
	// consumes, by walking the same AQT. Sibling collections are unioned, not
	// crossed.
	// ---------------------------------------------------------------------

	static List<Map<String, Object>> flatten(List<Map<String, Object>> jsonRows, TableNode tree) throws Exception {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> r : jsonRows) {
			expand(JSON.readTree(String.valueOf(r.get("json"))), tree, new HashMap<>(), out);
		}
		return out;
	}

	private static void expand(JsonNode array, TableNode node, Map<String, Object> parentCtx,
			List<Map<String, Object>> out) throws Exception {
		List<QueryNode> children = node.children();

		Map<String, Object> base = new HashMap<>(parentCtx);
		for (int i = 0; i < children.size(); i++) {
			if (children.get(i) instanceof ScalarNode scalar) {
				base.put(node.alias() + "." + scalar.field().getName(), jsonToValue(array.path(i)));
			}
		}

		boolean anyEmitted = false;
		for (int i = 0; i < children.size(); i++) {
			QueryNode child = children.get(i);
			if (!(child instanceof EntityCollection) && !(child instanceof JoinTableEntityCollection)) {
				continue;
			}
			for (JsonNode item : items(array.path(i))) {
				expand(item, (TableNode) child, base, out);
				anyEmitted = true;
			}
		}
		if (!anyEmitted) {
			out.add(base);
		}
	}

	/** A NULL or absent slot yields no items; JSON-text slots are re-parsed. */
	private static List<JsonNode> items(JsonNode slot) throws Exception {
		JsonNode array = slot.isTextual() ? JSON.readTree(slot.asText()) : slot;
		if (array == null || !array.isArray()) {
			return List.of();
		}
		List<JsonNode> items = new ArrayList<>();
		for (JsonNode item : array) {
			items.add(item.isTextual() ? JSON.readTree(item.asText()) : item);
		}
		return items;
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
		toMultiSetQuery(tree, builder);
		String sql = builder.toRootStatement().getSql();
		System.out.println(sql);

		List<Map<String, Object>> jsonRows = DB.queryRows(db, sql);
		jsonRows.forEach(row -> System.out.println(row));

		List<Map<String, Object>> flat = flatten(jsonRows, tree);
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
			Book authorless = new Book();
			authorless.title = "Orphan";
			PojoQuery.insert(connection, authorless);
		});

		PojoQuery<Author> query = PojoQuery.build(DbContext.getDefault(),
				TransformPipeline.defaultPipeline(), Author.class);
		RootNode tree = query.getTree();

		MultiSetSqlQueryBuilder builder = new MultiSetSqlQueryBuilder(DbContext.getDefault());
		toMultiSetQuery(tree, builder);
		String sql = builder.toRootStatement().getSql();
		System.out.println("--- SQL ---");
		System.out.println(sql);

		System.out.println("--- raw JSON rows ---");
		List<Map<String, Object>> jsonRows = DB.queryRows(db, sql);
		jsonRows.forEach(row -> System.out.println(row.get("json")));

		// Confirm the round-trip still hydrates correctly even with a null books slot.
		List<Map<String, Object>> flat = flatten(jsonRows, tree);
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

	/**
	 * A child node the builder does not project (here a many-to-one
	 * {@code publisher} reference) still occupies a slot, filled with NULL. That
	 * is what keeps {@code articles} at its child index so the flattener finds
	 * it without any separate schema description.
	 */
	@Test
	public void testUnprojectedChildOccupiesNullSlot() throws Exception {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Publisher.class, Magazine.class, Article.class);

		DB.runInTransaction(db, connection -> {
			Publisher publisher = new Publisher();
			publisher.name = "Acme Press";
			PojoQuery.insert(connection, publisher);

			Article first = new Article();
			first.headline = "Hello";
			Article second = new Article();
			second.headline = "World";

			Magazine magazine = new Magazine();
			magazine.title = "Weekly";
			magazine.publisher = publisher;
			magazine.articles = List.of(first, second);
			PojoQuery.insert(connection, magazine);
		});

		PojoQuery<Magazine> query = PojoQuery.build(DbContext.getDefault(),
				TransformPipeline.defaultPipeline(), Magazine.class);
		RootNode tree = query.getTree();

		MultiSetSqlQueryBuilder builder = new MultiSetSqlQueryBuilder(DbContext.getDefault());
		toMultiSetQuery(tree, builder);
		String sql = builder.toRootStatement().getSql();
		System.out.println(sql);

		List<Map<String, Object>> jsonRows = DB.queryRows(db, sql);
		jsonRows.forEach(row -> System.out.println(row.get("json")));

		List<Magazine> magazines = AQTRowProcessor.processRows(tree,
				flatten(jsonRows, tree));
		assertEquals(1, magazines.size());
		assertEquals("Weekly", magazines.get(0).title);
		assertEquals(2, magazines.get(0).articles.size());
	}
}
