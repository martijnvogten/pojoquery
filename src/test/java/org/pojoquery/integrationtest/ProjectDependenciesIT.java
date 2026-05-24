package org.pojoquery.integrationtest;

import java.sql.Connection;
import java.util.List;

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
 * Project management domain extension: tasks have a many-to-many
 * "depends on" relationship via a junction table {@code task_dependency}.
 * Verifies that {@link Recursive} combined with {@link Link} walks the
 * dependency graph transitively (dependencies of dependencies).
 */
public class ProjectDependenciesIT {

	@Table("task")
	static class Task {
		@Id Long id;
		String title;
	}

	static class TaskWithDirectDependencies extends Task {
		@Link(linktable = "task_dependency", linkfield = "task_id", foreignlinkfield = "depends_on_id")
		List<Task> dependencies;

		TaskWithDirectDependencies() {}

		TaskWithDirectDependencies(String title, List<Task> dependencies) {
			this.title = title;
			this.dependencies = dependencies;
		}
	}
	
	/** Root task that loads its transitive dependency closure. */
	static class TaskWithTransitiveDependencies extends Task {
		@Link(linktable = "task_dependency", linkfield = "task_id", foreignlinkfield = "depends_on_id")
		@Recursive(direction = Direction.DOWN)
		List<Task> dependencyTree;
	}

	@Test
	public void testLoadTransitiveDependencies() {
		DataSource db = initDatabase();

		Long shipId = PojoQuery.build(Task.class)
				.addWhere("{this.title} = ?", "Ship")
				.execute(db).get(0).id;

		List<TaskWithTransitiveDependencies> result = PojoQuery.build(TaskWithTransitiveDependencies.class)
				.addWhere("{this.id} = ?", shipId)
				.execute(db);

		Assertions.assertEquals(1, result.size());
		TaskWithTransitiveDependencies ship = result.get(0);
		Assertions.assertEquals("Ship", ship.title);
		Assertions.assertNotNull(ship.dependencyTree, "dependencies collection was not populated");

		// Dependency graph:
		//   Ship      -> QA, Implement
		//   QA        -> Implement
		//   Implement -> Design
		//   Design    -> (none)
		// Transitive closure of "Ship": { QA, Implement, Design }.
		List<String> names = ship.dependencyTree.stream().map(t -> t.title).distinct().sorted().toList();
		Assertions.assertEquals(
				List.of("Design", "Implement", "QA"),
				names,
				"Recursive @Link must traverse direct AND indirect dependencies");
	}

	private DataSource initDatabase() {
		DataSource db = TestDatabaseProvider.getDataSource();
		SchemaGenerator.createTables(db, TaskWithTransitiveDependencies.class);

		DB.withConnection(db, (Connection c) -> {
			Task design = new TaskWithDirectDependencies("Design", List.of());
			PojoQuery.insert(c, design);
			Task implement = new TaskWithDirectDependencies("Implement", List.of(design));
			PojoQuery.insert(c, implement);
			Task qa = new TaskWithDirectDependencies("QA", List.of(implement));
			PojoQuery.insert(c, qa);
			PojoQuery.insert(c, new TaskWithDirectDependencies("Ship", List.of(implement, qa)));
			return null;
		});
		return db;
	}

}
