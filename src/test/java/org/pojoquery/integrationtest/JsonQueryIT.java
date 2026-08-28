package org.pojoquery.integrationtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.DiscriminatorColumn;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Recursive;
import org.pojoquery.annotations.Recursive.Direction;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for JSON document queries ({@link PojoQuery#executeJson}).
 *
 * <p>Runs against HSQLDB by default, or MySQL/PostgreSQL via
 * {@code -Dtest.database=mysql|postgres} (see {@link TestDatabaseProvider}).
 * Assertions parse the returned JSON rather than comparing strings, because
 * property order and null-vs-absent behavior differ per dialect.</p>
 */
public class JsonQueryIT {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	// ========== Many-to-many: user -> roles -> permissions ==========

	@Table("user")
	public static class User {
		@Id
		public Long id;
		public String username;
		@Link(linktable = "user_role")
		public List<Role> roles;
	}

	@Table("role")
	public static class Role {
		@Id
		public Long id;
		public String rolename;
		@Link(linktable = "role_permission")
		public List<Permission> permissions;
	}

	@Table("permission")
	public static class Permission {
		@Id
		public Long id;
		public String permissionname;
	}

	@Test
	public void testNestedManyToMany() throws Exception {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class, Role.class, Permission.class);
		DB.runInTransaction(db, connection -> {
			Permission read = new Permission();
			read.permissionname = "read";
			PojoQuery.insert(connection, read);

			Permission write = new Permission();
			write.permissionname = "write";
			PojoQuery.insert(connection, write);

			Role admin = new Role();
			admin.rolename = "admin";
			admin.permissions = List.of(read, write);
			PojoQuery.insert(connection, admin);

			Role editor = new Role();
			editor.rolename = "editor";
			editor.permissions = List.of(read);
			PojoQuery.insert(connection, editor);

			User joe = new User();
			joe.username = "joe";
			joe.roles = List.of(admin, editor);
			PojoQuery.insert(connection, joe);

			User nora = new User();
			nora.username = "nora";
			nora.roles = List.of();
			PojoQuery.insert(connection, nora);
		});

		List<String> documents = PojoQuery.build(TestDatabaseProvider.getDbContext(), User.class)
				.addOrderBy("{user.username}")
				.executeJson(db);
		assertEquals(2, documents.size());

		JsonNode joe = MAPPER.readTree(documents.get(0));
		assertEquals("joe", joe.get("username").asText());
		assertEquals(2, joe.get("roles").size());
		JsonNode admin = findByProperty(joe.get("roles"), "rolename", "admin");
		assertEquals(List.of("read", "write"), sortedTexts(admin.get("permissions"), "permissionname"));
		JsonNode editor = findByProperty(joe.get("roles"), "rolename", "editor");
		assertEquals(List.of("read"), sortedTexts(editor.get("permissions"), "permissionname"));

		// A user without roles gets an empty array, not null
		JsonNode nora = MAPPER.readTree(documents.get(1));
		assertEquals("nora", nora.get("username").asText());
		assertTrue(nora.get("roles").isArray());
		assertEquals(0, nora.get("roles").size());
	}

	@Test
	public void testWhereAndLimit() throws Exception {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class, Role.class, Permission.class);
		DB.runInTransaction(db, connection -> {
			for (String name : List.of("anna", "bob", "carol")) {
				User user = new User();
				user.username = name;
				PojoQuery.insert(connection, user);
			}
		});

		List<String> filtered = PojoQuery.build(TestDatabaseProvider.getDbContext(), User.class)
				.addWhere("{user.username} = ?", "bob")
				.executeJson(db);
		assertEquals(1, filtered.size());
		assertEquals("bob", MAPPER.readTree(filtered.get(0)).get("username").asText());

		List<String> limited = PojoQuery.build(TestDatabaseProvider.getDbContext(), User.class)
				.addOrderBy("{user.username} DESC")
				.setLimit(2)
				.executeJson(db);
		assertEquals(2, limited.size());
		assertEquals("carol", MAPPER.readTree(limited.get(0)).get("username").asText());
		assertEquals("bob", MAPPER.readTree(limited.get(1)).get("username").asText());
	}

	// ========== Inheritance: table-per-subclass ==========

	@Table("tps_room")
	@SubClasses({ TpsBedRoom.class, TpsKitchen.class })
	public static class TpsRoom {
		@Id
		public Long id;
		public Double area;
	}

	@Table("tps_bedroom")
	public static class TpsBedRoom extends TpsRoom {
		public Integer numberOfBeds;
	}

	@Table("tps_kitchen")
	public static class TpsKitchen extends TpsRoom {
		public Boolean hasDishWasher;
	}

	@Test
	public void testTablePerSubclassInheritance() throws Exception {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, TpsRoom.class, TpsBedRoom.class, TpsKitchen.class);
		DB.runInTransaction(db, connection -> {
			TpsBedRoom bedRoom = new TpsBedRoom();
			bedRoom.area = 12.5;
			bedRoom.numberOfBeds = 2;
			PojoQuery.insert(connection, bedRoom);

			TpsKitchen kitchen = new TpsKitchen();
			kitchen.area = 8.0;
			kitchen.hasDishWasher = true;
			PojoQuery.insert(connection, kitchen);

			TpsRoom plain = new TpsRoom();
			plain.area = 5.0;
			PojoQuery.insert(connection, plain);
		});

		List<JsonNode> rooms = parseAll(
				PojoQuery.build(TestDatabaseProvider.getDbContext(), TpsRoom.class).executeJson(db));
		assertEquals(3, rooms.size());

		JsonNode bedRoom = findByProperty(rooms, "_type", "TpsBedRoom");
		assertEquals(12.5, bedRoom.get("area").asDouble(), 0.001);
		assertEquals(2, bedRoom.get("numberOfBeds").asInt());
		assertTrue(isNullOrAbsent(bedRoom, "hasDishWasher"));

		JsonNode kitchen = findByProperty(rooms, "_type", "TpsKitchen");
		assertTrue(kitchen.get("hasDishWasher").asBoolean());
		assertTrue(isNullOrAbsent(kitchen, "numberOfBeds"));

		JsonNode plain = findByProperty(rooms, "_type", "TpsRoom");
		assertEquals(5.0, plain.get("area").asDouble(), 0.001);
		assertTrue(isNullOrAbsent(plain, "numberOfBeds"));
		assertTrue(isNullOrAbsent(plain, "hasDishWasher"));
	}

	// ========== Inheritance: single table ==========

	@Table("sti_room")
	@DiscriminatorColumn
	@SubClasses({ StiBedRoom.class, StiKitchen.class })
	public static class StiRoom {
		@Id
		public Long id;
		public Double area;
	}

	public static class StiBedRoom extends StiRoom {
		public Integer numberOfBeds;
	}

	public static class StiKitchen extends StiRoom {
		public Boolean hasDishWasher;
	}

	@Test
	public void testSingleTableInheritance() throws Exception {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, StiRoom.class);
		DB.runInTransaction(db, connection -> {
			StiBedRoom bedRoom = new StiBedRoom();
			bedRoom.area = 12.5;
			bedRoom.numberOfBeds = 2;
			PojoQuery.insert(connection, bedRoom);

			StiKitchen kitchen = new StiKitchen();
			kitchen.area = 8.0;
			kitchen.hasDishWasher = true;
			PojoQuery.insert(connection, kitchen);
		});

		List<JsonNode> rooms = parseAll(
				PojoQuery.build(TestDatabaseProvider.getDbContext(), StiRoom.class).executeJson(db));
		assertEquals(2, rooms.size());

		JsonNode bedRoom = findByProperty(rooms, "_type", "StiBedRoom");
		assertEquals(2, bedRoom.get("numberOfBeds").asInt());
		JsonNode kitchen = findByProperty(rooms, "_type", "StiKitchen");
		assertTrue(kitchen.get("hasDishWasher").asBoolean());
		assertTrue(isNullOrAbsent(kitchen, "numberOfBeds"));
	}

	// ========== Collection of polymorphic entities + reference back ==========

	@Table("apartment")
	public static class Apartment {
		@Id
		public Long id;
		public String name;
	}

	public static class ApartmentWithRooms extends Apartment {
		public List<AptRoom> rooms;
	}

	@Table("apt_room")
	@SubClasses({ AptBedRoom.class, AptKitchen.class })
	public static class AptRoom {
		@Id
		public Long id;
		public Double area;
		public Apartment apartment;
	}

	@Table("apt_bedroom")
	public static class AptBedRoom extends AptRoom {
		public Integer numberOfBeds;
	}

	@Table("apt_kitchen")
	public static class AptKitchen extends AptRoom {
		public Boolean hasDishWasher;
	}

	@Test
	public void testPolymorphicCollectionAndReference() throws Exception {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, Apartment.class, AptRoom.class, AptBedRoom.class, AptKitchen.class);
		DB.runInTransaction(db, connection -> {
			Apartment apartment = new Apartment();
			apartment.name = "Penthouse";
			PojoQuery.insert(connection, apartment);

			AptBedRoom bedRoom = new AptBedRoom();
			bedRoom.area = 12.5;
			bedRoom.numberOfBeds = 2;
			bedRoom.apartment = apartment;
			PojoQuery.insert(connection, bedRoom);

			AptKitchen kitchen = new AptKitchen();
			kitchen.area = 8.0;
			kitchen.hasDishWasher = true;
			kitchen.apartment = apartment;
			PojoQuery.insert(connection, kitchen);

			AptRoom plain = new AptRoom();
			plain.area = 5.0;
			plain.apartment = apartment;
			PojoQuery.insert(connection, plain);

			AptRoom orphan = new AptRoom();
			orphan.area = 3.0;
			PojoQuery.insert(connection, orphan);
		});

		List<String> documents = PojoQuery.build(TestDatabaseProvider.getDbContext(), ApartmentWithRooms.class)
				.executeJson(db);
		assertEquals(1, documents.size());
		JsonNode apartment = MAPPER.readTree(documents.get(0));
		assertEquals("Penthouse", apartment.get("name").asText());
		assertEquals(3, apartment.get("rooms").size());

		JsonNode bedRoom = findByProperty(apartment.get("rooms"), "_type", "AptBedRoom");
		assertEquals(2, bedRoom.get("numberOfBeds").asInt());
		// Each room carries its many-to-one reference as a nested object
		assertEquals("Penthouse", bedRoom.get("apartment").get("name").asText());

		// A room without an apartment gets a null (or absent) reference
		List<JsonNode> allRooms = parseAll(
				PojoQuery.build(TestDatabaseProvider.getDbContext(), AptRoom.class).executeJson(db));
		assertEquals(4, allRooms.size());
		JsonNode orphan = allRooms.stream()
				.filter(room -> room.get("area").asDouble() == 3.0)
				.findFirst().orElseThrow();
		assertTrue(isNullOrAbsent(orphan, "apartment"));
	}

	// ========== Value collection + embedded ==========

	@Table("article")
	public static class Article {
		@Id
		public Long id;
		public String title;
		@Embedded
		public Author author;
		@Link(linktable = "article_tag", fetchColumn = "tag")
		public List<String> tags;
	}

	public static class Author {
		public String name;
		public String email;
	}

	@Test
	public void testValueCollectionAndEmbedded() throws Exception {
		DataSource db = TestDatabaseProvider.getDataSource();
		DbContext context = TestDatabaseProvider.getDbContext();
		SchemaGenerator.createTables(db, Article.class);

		DB.runInTransaction(db, connection -> {
			Article article = new Article();
			article.title = "PojoQuery";
			article.author = new Author();
			article.author.name = "Joe";
			article.author.email = "joe@example.com";
			article.tags = List.of("java", "sql");
			PojoQuery.insert(connection, article);

			Article emptyArticle = new Article();
			emptyArticle.title = "No tags";
			emptyArticle.author = new Author();
			emptyArticle.author.name = "Nora";
			emptyArticle.tags = List.of();
			PojoQuery.insert(connection, emptyArticle);
		});

		List<JsonNode> articles = parseAll(
				PojoQuery.build(context, Article.class).addOrderBy("{article.title}").executeJson(db));
		assertEquals(2, articles.size());

		JsonNode noTags = articles.get(0);
		assertEquals("No tags", noTags.get("title").asText());
		assertEquals("Nora", noTags.get("author").get("name").asText());
		assertTrue(noTags.get("tags").isArray());
		assertEquals(0, noTags.get("tags").size());

		JsonNode tagged = articles.get(1);
		assertEquals("Joe", tagged.get("author").get("name").asText());
		List<String> tags = new ArrayList<>();
		tagged.get("tags").forEach(tag -> tags.add(tag.asText()));
		assertEquals(List.of("java", "sql"), tags.stream().sorted().toList());
	}

	// ========== @Recursive: adjacency-list tree ==========

	@Table("json_tree_category")
	public static class TreeCategory {
		@Id
		public Long id;
		public String name;
	}

	public static class TreeCategoryWithParent extends TreeCategory {
		@FieldName("parent_id")
		public TreeCategory parent;
	}

	public static class TreeCategoryWithDescendants extends TreeCategoryWithParent {
		@Recursive(parentLink = "parent_id", direction = Direction.DOWN)
		public List<TreeCategory> descendants;
	}

	public static class TreeCategoryWithAncestors extends TreeCategoryWithParent {
		@Recursive(parentLink = "parent_id", direction = Direction.UP)
		public List<TreeCategory> ancestors;
	}

	@Test
	public void testRecursiveDescendantsAndAncestors() throws Exception {
		DataSource db = TestDatabaseProvider.getDataSource();
		DbContext context = TestDatabaseProvider.getDbContext();
		SchemaGenerator.createTables(db, TreeCategoryWithParent.class);
		DB.runInTransaction(db, connection -> {
			// Electronics -> Audio -> Phones, plus a childless Books
			TreeCategoryWithParent electronics = insertCategory(connection, "Electronics", null);
			TreeCategoryWithParent audio = insertCategory(connection, "Audio", electronics);
			insertCategory(connection, "Phones", audio);
			insertCategory(connection, "Books", null);
		});

		List<JsonNode> withDescendants = parseAll(PojoQuery.build(context, TreeCategoryWithDescendants.class)
				.addOrderBy("{this.name}")
				.executeJson(db));
		assertEquals(4, withDescendants.size());

		JsonNode audio = findByProperty(withDescendants, "name", "Audio");
		assertEquals(List.of("Phones"), sortedTexts(audio.get("descendants"), "name"));

		JsonNode electronics = findByProperty(withDescendants, "name", "Electronics");
		assertEquals(List.of("Audio", "Phones"), sortedTexts(electronics.get("descendants"), "name"));
		// The transitive closure only, never the root itself
		assertEquals(2, electronics.get("descendants").size());

		JsonNode books = findByProperty(withDescendants, "name", "Books");
		assertTrue(books.get("descendants").isArray());
		assertEquals(0, books.get("descendants").size());

		List<JsonNode> withAncestors = parseAll(PojoQuery.build(context, TreeCategoryWithAncestors.class)
				.addWhere("{this.name} = ?", "Phones")
				.executeJson(db));
		assertEquals(1, withAncestors.size());
		assertEquals(List.of("Audio", "Electronics"), sortedTexts(withAncestors.get(0).get("ancestors"), "name"));
	}

	@Table("json_catalog")
	public static class Catalog {
		@Id
		public Long id;
		public String name;
	}

	public static class CatalogWithCategories extends Catalog {
		public List<TreeCategoryWithDescendants> categories;
	}

	/**
	 * A recursive collection nested inside another collection: the CTE is hoisted
	 * out of the inner derived table up to the top-level statement, where every
	 * dialect accepts the {@code WITH} preamble.
	 */
	@Test
	public void testRecursiveCollectionNestedInsideCollection() throws Exception {
		DataSource db = TestDatabaseProvider.getDataSource();
		DbContext context = TestDatabaseProvider.getDbContext();
		SchemaGenerator.createTables(db, CatalogWithCategories.class);
		DB.runInTransaction(db, connection -> {
			CatalogWithCategories catalog = new CatalogWithCategories();
			catalog.name = "main";
			TreeCategoryWithDescendants top = new TreeCategoryWithDescendants();
			top.name = "Top";
			TreeCategoryWithDescendants child = new TreeCategoryWithDescendants();
			child.name = "Child";
			child.parent = top;
			// Inserted in list order, so `child` sees the id `top` was given.
			catalog.categories = List.of(top, child);
			PojoQuery.insert(connection, catalog);
		});

		List<JsonNode> catalogs = parseAll(PojoQuery.build(context, CatalogWithCategories.class).executeJson(db));
		assertEquals(1, catalogs.size());
		JsonNode categories = catalogs.get(0).get("categories");
		assertEquals(2, categories.size());
		assertEquals(List.of("Child"), sortedTexts(findByProperty(categories, "name", "Top").get("descendants"), "name"));
		assertEquals(0, findByProperty(categories, "name", "Child").get("descendants").size());
	}

	// ========== @Recursive: junction table over a graph ==========

	@Table("json_topic")
	public static class Topic {
		@Id
		public Long id;
		public String name;
	}

	public static class TopicWithLinks extends Topic {
		@Link(linktable = "json_topic_related", linkfield = "topic_id", foreignlinkfield = "related_id")
		public List<Topic> related;
	}

	public static class TopicWithRelatedClosure extends Topic {
		@Recursive
		@Link(linktable = "json_topic_related", linkfield = "topic_id", foreignlinkfield = "related_id")
		public List<Topic> related;
	}

	@Test
	public void testRecursiveJunctionDeduplicatesDiamond() throws Exception {
		DataSource db = TestDatabaseProvider.getDataSource();
		DbContext context = TestDatabaseProvider.getDbContext();
		SchemaGenerator.createTables(db, TopicWithLinks.class);
		DB.runInTransaction(db, connection -> {
			// root -> {left, right}, and both reach shared: a diamond, so the CTE
			// produces two paths to shared.
			Topic shared = new Topic();
			shared.name = "shared";
			PojoQuery.insert(connection, shared);

			TopicWithLinks left = new TopicWithLinks();
			left.name = "left";
			left.related = List.of(shared);
			PojoQuery.insert(connection, left);

			TopicWithLinks right = new TopicWithLinks();
			right.name = "right";
			right.related = List.of(shared);
			PojoQuery.insert(connection, right);

			TopicWithLinks root = new TopicWithLinks();
			root.name = "root";
			root.related = List.of(left, right);
			PojoQuery.insert(connection, root);
		});

		List<JsonNode> topics = parseAll(PojoQuery.build(context, TopicWithRelatedClosure.class)
				.addOrderBy("{this.name}")
				.executeJson(db));

		JsonNode root = findByProperty(topics, "name", "root");
		assertEquals(List.of("left", "right", "shared"), sortedTexts(root.get("related"), "name"));

		JsonNode left = findByProperty(topics, "name", "left");
		assertEquals(List.of("shared"), sortedTexts(left.get("related"), "name"));

		JsonNode shared = findByProperty(topics, "name", "shared");
		assertEquals(0, shared.get("related").size());
	}

	private static TreeCategoryWithParent insertCategory(java.sql.Connection connection, String name,
			TreeCategory parent) {
		TreeCategoryWithParent category = new TreeCategoryWithParent();
		category.name = name;
		category.parent = parent;
		PojoQuery.insert(connection, category);
		return category;
	}

	// ========== Helpers ==========

	private static List<JsonNode> parseAll(List<String> documents) throws Exception {
		List<JsonNode> result = new ArrayList<>();
		for (String document : documents) {
			result.add(MAPPER.readTree(document));
		}
		return result;
	}

	private static JsonNode findByProperty(Iterable<JsonNode> nodes, String property, String value) {
		for (JsonNode node : nodes) {
			if (node.has(property) && value.equals(node.get(property).asText())) {
				return node;
			}
		}
		throw new AssertionError("No node with " + property + "=" + value + " in " + nodes);
	}

	private static List<String> sortedTexts(JsonNode array, String property) {
		List<String> result = new ArrayList<>();
		array.forEach(node -> result.add(node.get(property).asText()));
		return result.stream().sorted().toList();
	}

	private static boolean isNullOrAbsent(JsonNode node, String property) {
		JsonNode value = node.get(property);
		boolean result = value == null || value.isNull();
		assertNotNull(node, "node");
		return result;
	}
}
