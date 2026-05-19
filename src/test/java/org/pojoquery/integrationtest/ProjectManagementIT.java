package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.Recursive;
import org.pojoquery.annotations.Recursive.Direction;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Project management domain: tasks with an owner and a set of tags, plus a
 * parent/child hierarchy. Exercises recursive collections whose element type
 * itself carries joins (many-to-one owner) and a many-to-many link (tags).
 */
public class ProjectManagementIT {

	@Table("app_user")
	static class User {
		@Id Long id;
		String name;
	}

	@Table("tag")
	static class Tag {
		@Id Long id;
		String label;
	}

	@Table("task")
	static class Task {
		@Id Long id;
		String title;
	}

	/** Task with its joined owner, parent reference and tag collection. */
	static class TaskDetail extends Task {
		User owner;

		Task parent;

		@Link(linktable = "task_tag")
		List<Tag> tags = new ArrayList<>();
	}

	/** Root task that recursively loads its descendant subtasks (each fully joined). */
	static class TaskTree extends TaskDetail {
		@Recursive(parentLink = "parent_id", direction = Direction.DOWN)
		List<TaskDetail> subtasks;
	}

	@Test
	public void testLoadTaskTreeWithJoinsOnElement() {
		DataSource db = initDatabase();

		List<TaskTree> result = PojoQuery.build(TaskTree.class)
				.addWhere("{this.id} = ?", 1L)
				.execute(db);

		Assertions.assertEquals(1, result.size());
		TaskTree root = result.get(0);
		Assertions.assertEquals("Launch website", root.title);
		Assertions.assertEquals("alice", root.owner.name);
		Assertions.assertEquals(List.of("planning"),
				root.tags.stream().map(t -> t.label).sorted().toList());

		Assertions.assertNotNull(root.subtasks, "subtasks were not populated");
		Map<String, TaskDetail> byTitle = root.subtasks.stream()
				.collect(Collectors.toMap(t -> t.title, t -> t));
		Assertions.assertEquals(
				List.of("Design", "Implement", "Polish UI", "Ship", "Tune DB", "Wire up auth"),
				byTitle.keySet().stream().sorted().toList(),
				"recursive collection must include descendants at every depth");

		TaskDetail design = byTitle.get("Design");
		Assertions.assertEquals("alice", design.owner.name,
				"each subtask element must have its owner joined");
		Assertions.assertEquals(List.of("design"),
				design.tags.stream().map(t -> t.label).sorted().toList(),
				"each subtask element must have its tags loaded");

		TaskDetail implement = byTitle.get("Implement");
		Assertions.assertEquals("bob", implement.owner.name);
		Assertions.assertEquals(List.of("backend", "frontend"),
				implement.tags.stream().map(t -> t.label).sorted().toList());

		TaskDetail ship = byTitle.get("Ship");
		Assertions.assertEquals("bob", ship.owner.name);
		Assertions.assertEquals(List.of(),
				ship.tags.stream().map(t -> t.label).sorted().toList());
	}

	@Test
	public void testWhereOnNestedOwnerFieldInsideRecursiveCollection() {
		DataSource db = initDatabase();

		List<TaskTree> trees = PojoQuery.build(TaskTree.class)
				.addWhere("{this.id} = ?", 1L)
				.addWhere("{subtasks.owner.name} = ?", "bob")
				.execute(db);

		Assertions.assertEquals(1, trees.size(),
				"root task with at least one bob-owned descendant must be returned");
		TaskTree root = trees.get(0);
		Assertions.assertEquals("Launch website", root.title);

		List<String> loaded = root.subtasks.stream().map(t -> t.title).sorted().toList();
		Assertions.assertEquals(List.of("Implement", "Polish UI", "Ship", "Wire up auth"), loaded,
				"recursive CTE must walk the full closure: 'Polish UI' is bob-owned but its parent Design is not,"
						+ " so it should only appear if traversal happens before the WHERE filter");
		Assertions.assertTrue(root.subtasks.stream().allMatch(t -> "bob".equals(t.owner.name)));
	}

	@Test
	public void testWhereOnLinkedTagCollectionInsideRecursiveCollection() {
		DataSource db = initDatabase();

		List<TaskTree> trees = PojoQuery.build(TaskTree.class)
				.addWhere("{this.id} = ?", 1L)
				.addWhere("{subtasks.tags.label} = ?", "backend")
				.execute(db);

		Assertions.assertEquals(1, trees.size());
		TaskTree root = trees.get(0);

		List<String> loaded = root.subtasks.stream().map(t -> t.title).sorted().toList();
		Assertions.assertEquals(List.of("Implement", "Tune DB", "Wire up auth"), loaded,
				"recursive CTE must walk the full closure: 'Tune DB' carries the backend tag but its parent Design does not,"
						+ " so it should only appear if traversal happens before the WHERE filter");
	}

	@Test
	public void testWhereOnParentReferenceFieldInsideRecursiveCollection() {
		DataSource db = initDatabase();

		Long implementId = PojoQuery.build(Task.class)
				.addWhere("{this.title} = ?", "Implement")
				.execute(db).get(0).id;

		List<TaskTree> trees = PojoQuery.build(TaskTree.class)
				.addWhere("{this.id} = ?", 1L)
				.addWhere("{subtasks.parent.id} = ?", implementId)
				.execute(db);

		Assertions.assertEquals(1, trees.size());
		TaskTree root = trees.get(0);

		List<String> loaded = root.subtasks.stream().map(t -> t.title).sorted().toList();
		Assertions.assertEquals(List.of("Wire up auth"), loaded,
				"WHERE on {subtasks.parent.id} must restrict to descendants whose parent matches");
		Assertions.assertEquals(implementId, root.subtasks.get(0).parent.id);
	}

	private DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, User.class, Tag.class, TaskDetail.class);

		DB.withConnection(db, (Connection c) -> {
			User alice = insertUser(c, "alice");
			User bob   = insertUser(c, "bob");

			Tag planning = insertTag(c, "planning");
			Tag design   = insertTag(c, "design");
			Tag frontend = insertTag(c, "frontend");
			Tag backend  = insertTag(c, "backend");

			TaskDetail root = insertTask(c, "Launch website", alice, null, List.of(planning));
			TaskDetail dsgn = insertTask(c, "Design",    alice, root, List.of(design));
			TaskDetail impl = insertTask(c, "Implement", bob, root, List.of(frontend, backend));
			insertTask(c, "Ship",      bob,   root, List.of());

			// A grandchild of root: belongs to Implement. Not asserted directly, but
			// proves the recursive descent traverses multiple levels under each subtask.
			insertTask(c, "Wire up auth", bob, impl, List.of(backend));

			// Grandchildren whose intermediate parent (Design, owned by alice with tag
			// "design") does NOT match the filters used in the recursive-collection
			// where tests. They prove that the recursive CTE walks the full closure
			// before WHERE filters element rows: a bob-owned or backend-tagged grandchild
			// must still surface even though the path goes through an alice-only branch.
			insertTask(c, "Polish UI", bob,   dsgn, List.of(frontend));
			insertTask(c, "Tune DB",   alice, dsgn, List.of(backend));
			return null;
		});
		return db;
	}

	private static User insertUser(Connection c, String name) {
		User u = new User();
		u.name = name;
		PojoQuery.insert(c, u);
		return u;
	}

	private static Tag insertTag(Connection c, String label) {
		Tag t = new Tag();
		t.label = label;
		PojoQuery.insert(c, t);
		return t;
	}

	private static TaskDetail insertTask(Connection c, String title, User owner, Task parent, List<Tag> tags) {
		TaskDetail t = new TaskDetail();
		t.title = title;
		t.owner = owner;
		t.parent = parent;
		t.tags.addAll(tags);
		PojoQuery.insert(c, t);
		return t;
	}
}
