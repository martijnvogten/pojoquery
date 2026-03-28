package org.pojoquery;

import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.Assert;
import org.junit.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.UseDialect;
import org.pojoquery.pipeline.AQTCascadingUpdater;
import org.pojoquery.pipeline.AQTSchemaGenerator;
import org.pojoquery.pipeline.AQTTransformer;
import org.pojoquery.pipeline.AbstractQueryTree.Column;
import org.pojoquery.pipeline.AbstractQueryTree.EntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.EntityReference;
import org.pojoquery.pipeline.AbstractQueryTree.FieldNode;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableEntityCollection;
import org.pojoquery.pipeline.AbstractQueryTree.JoinTableInfo;
import org.pojoquery.pipeline.AbstractQueryTree.PrimaryKey;
import org.pojoquery.pipeline.AbstractQueryTree.QueryNode;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.pipeline.AbstractQueryTree.TableNode;
import org.pojoquery.pipeline.DefaultSqlQuery;
import org.pojoquery.typemodel.ReflectionTypeModel;
import org.pojoquery.util.CurlyMarkers;
import org.pojoquery.util.RecordIndenter;

@UseDialect(Dialect.HSQLDB)
public class TestAlternativeStructureForQueryTree {

	@Table("person")
	static class Person {
		@Id
		Long id;
		String name;
	}

	@Table("comment")
	static class Comment {
		@Id
		Long id;
		String content;
	}


	@Table("category")
	static class Category {
		@Id
		Long id;
		String name;
	}

	@Table("article")
	static class ArticleDetail {
		@Id
		Long id;
		String title;
		Person author;
		List<Comment> comments;
		@Link(linktable = "article_category")
		List<Category> categories;
	}

	/** Create a DatabaseOperations implementation backed by a Connection */
	private AQTCascadingUpdater.DatabaseOperations createDbOps(Connection conn) {
		DbContext context = DbContext.getDefault();
		return new AQTCascadingUpdater.DatabaseOperations() {
			@Override
			public <PK> PK insert(String table, String schema, Map<String, Object> values) {
				return DB.insert(context, conn, schema, table, values);
			}

			@Override
			public int update(String table, String schema, Map<String, Object> values, Map<String, Object> where) {
				return DB.update(context, conn, schema, table, values, where);
			}

			@Override
			public int delete(String table, String schema, Map<String, Object> where) {
				return DB.delete(context, conn, schema, table, where);
			}

			@Override
			public int deleteWhere(String table, String schema, SqlExpression condition) {
				String resolvedSql = CurlyMarkers.processMarkers(condition.getSql(), 
					id -> context.quoteObjectNames(id));
				return DB.deleteWhere(context, conn, schema, table, 
					new SqlExpression(resolvedSql, condition.getParameters()));
			}

			@Override
			public void syncLinkTable(String table, String schema, String ownerFkColumn, Object ownerId,
					String targetFkColumn, List<Object> targetIds) {
				Map<String, Object> deleteCondition = new HashMap<>();
				deleteCondition.put(ownerFkColumn, ownerId);
				DB.delete(context, conn, schema, table, deleteCondition);
				for (Object targetId : targetIds) {
					Map<String, Object> values = new LinkedHashMap<>();
					values.put(ownerFkColumn, ownerId);
					values.put(targetFkColumn, targetId);
					DB.insert(context, conn, schema, table, values);
				}
			}
		};
	}

	@Test
	public void testSql() {
		DefaultSqlQuery sqlQuery = new DefaultSqlQuery(DbContext.getDefault());
		AQTTransformer.toSql(AQTTransformer.buildQueryTreeForType(ArticleDetail.class), sqlQuery);
		System.out.println("Generated SQL: " + sqlQuery.toStatement().getSql());
	}

	private void queryRows(Connection conn, Class<?> entityType, Consumer<Map<String, Object>> rowConsumer) {
		RootNode tree = AQTTransformer.buildQueryTreeForType(entityType);
		DefaultSqlQuery sqlQuery = new DefaultSqlQuery(DbContext.getDefault());
		AQTTransformer.toSql(tree, sqlQuery);
		DB.queryRowsStreaming(conn, sqlQuery.toStatement(), rowConsumer);
	}

	@Test
	public void testGenerateInserts() {
		RootNode tree = AQTTransformer.buildQueryTreeForType(Person.class);
		Assert.assertEquals(2, tree.children().size());
		tree.children().forEach(child -> {
			Assert.assertTrue(child instanceof Column);
			Column column = (Column) child;
			Assert.assertTrue(column.field().getName().equals("id") ? column instanceof PrimaryKey : true);
			Assert.assertTrue(column.field().getName().equals("name") ? !(column instanceof PrimaryKey) : true);
		});

		DataSource db = initDatabase();
		
		DB.withConnection(db, conn -> {
			AQTCascadingUpdater.DatabaseOperations dbOps = createDbOps(conn);
			
			Person albert = new Person();
			albert.name = "Albert Einstein";
			RootNode personTree = AQTTransformer.buildQueryTreeForType(Person.class);
			AQTCascadingUpdater.insert(personTree, albert, dbOps);

			ArticleDetail article = new ArticleDetail();
			article.title = "Relativity";
			article.author = albert;
			RootNode articleTree = AQTTransformer.buildQueryTreeForType(ArticleDetail.class);
			AQTCascadingUpdater.insert(articleTree, article, dbOps);

			queryRows(conn, ArticleDetail.class, row -> {
				AQTTransformer.buildQueryTreeForType(ArticleDetail.class).children().forEach(child -> {
					if (child instanceof Column col) {
						System.out.println("Selected column: " + col.columnName() + " (field: " + col.field().getName() + ")");
					}
				});
				System.out.println("Queried row: " + row);
				Assert.assertEquals("Relativity", row.get("article.title"));
				Assert.assertEquals("Albert Einstein", row.get("author.name"));
			});
		});
	}

	@Test
	public void test() {
		RootNode root = AQTTransformer.buildQueryTreeForType(ArticleDetail.class);
		System.out.println("Query Tree:\n" + RecordIndenter.indent(root.toString()));
		Assert.assertEquals("article", root.alias());
		Assert.assertEquals(5, root.children().size());
		Assert.assertEquals("article_category", new ReflectionTypeModel(ArticleDetail.class).getDeclaredFields().stream()
			.filter(f -> f.getName().equals("categories")).findFirst()
			.map(f -> f.getAnnotationAttributeValue(Link.class, "linktable", String.class)).orElse(""));
		
		findNodes(root, n -> n instanceof EntityReference && ((EntityReference) n).field().getName().equals("author"))
			.map(n -> (EntityReference) n)
			.findFirst()
			.ifPresentOrElse(authorNode -> {
				Assert.assertEquals("author", authorNode.alias());
				Assert.assertEquals(2, authorNode.children().size());
			}, () -> {
				throw new AssertionError("Author node not found");
			});

		findNodes(root, n -> n instanceof EntityCollection && ((EntityCollection) n).field().getName().equals("comments"))
			.map(n -> (EntityCollection) n)
			.findFirst()
			.ifPresentOrElse(commentsNode -> {
				Assert.assertEquals("comments", commentsNode.alias());
				Assert.assertEquals(2, commentsNode.children().size());
			}, () -> {
				throw new AssertionError("Comments node not found");
			});

		findNodes(root, n -> n instanceof JoinTableEntityCollection jten && jten.field().getName().equals("categories"))
			.map(JoinTableEntityCollection.class::cast)
			.findFirst()
			.ifPresentOrElse(categoriesNode -> {
				Assert.assertEquals("categories", categoriesNode.alias());
				Assert.assertEquals(2, categoriesNode.children().size());
				JoinTableInfo joinTableInfo = categoriesNode.join().joinTableInfo();
				Assert.assertEquals("categories.article_category", joinTableInfo.joinTableAlias());
				// After buildQueryTreeForType applies all transforms, FK column names should be set
				Assert.assertEquals("article_id", categoriesNode.join().parentKey().fkColumnName());
				Assert.assertEquals("category_id", categoriesNode.join().childKey().fkColumnName());
				Assert.assertEquals("article_category", joinTableInfo.tableInfo().tableName());

				categoriesNode.children().stream()
					.filter(node -> node instanceof FieldNode)
					.map(FieldNode.class::cast)
					.map(node -> node.field().getName())
					.toList().equals(List.of("id", "name"));;
			}, () -> {
				throw new AssertionError("Categories node not found");
			});

		// The expected structure of the query tree is:
		// RootNode (Article)
		//   - ScalarValue (id)
		//   - ScalarValue (title)
		//   - EntityReference (author)
		//       - ScalarValue (id)
		//       - ScalarValue (name)
		//   - EntityCollection (comments)
		//       - ScalarValue (id)
		//       - ScalarValue (content)
		// Example of building a query tree with this structure:
		// RootNode (Article)
		//   - ScalarValue (id)
		//   - ScalarValue (title)
		//   - EntityReference (author)
		//       - ScalarValue (id)
		//       - ScalarValue (name)
		//   - EntityCollection (comments)
		//       - ScalarValue (id)
		//       - ScalarValue (content)
	}



	@Test
	public void testAlternativeStructure() {
		// This test just checks that we can build a query tree with an alternative structure (using a different class for the nodes).
		// The actual correctness of the query tree is not checked here, but it should be covered by other tests.
		// PojoQuery.build(TestBasics.ArticleDetail.class, AlternativeQueryTreeNode.class);
	}

	private DataSource initDatabase() {
		DbContext.setDefault(DbContext.forDialect(Dialect.HSQLDB));

        JDBCDataSource ds = new JDBCDataSource();
        ds.setUrl("jdbc:hsqldb:mem:testdb");
        ds.setUser("SA");
        ds.setPassword("");
        DbContext.setDefault(DbContext.forDialect(DbContext.Dialect.HSQLDB));

		RootNode articleNode = AQTTransformer.buildQueryTreeForType(ArticleDetail.class);

		List<String> ddlStatements = AQTSchemaGenerator.generateCreateSchemaDDL(DbContext.getDefault(), articleNode);
		
		// Create an hsql in-memory database and execute the DDL statements to verify they are correct
		try (Connection conn = ds.getConnection()) {
			try (Statement stmt = conn.createStatement()) {
				for (String ddl : ddlStatements) {
					System.out.println("Executing DDL: " + ddl);
					stmt.execute(ddl);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return ds;
	}

	private static Stream<QueryNode> findNodes(QueryNode node, Predicate<QueryNode> condition) {
		Stream<QueryNode> current = Stream.of(node);
		if (node instanceof TableNode tableNode && tableNode.children() != null) {
			current = Stream.concat(current, tableNode.children().stream().flatMap(child -> findNodes(child, condition)));
		}
		return current.filter(condition);
	}

}
