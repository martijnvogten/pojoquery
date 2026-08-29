package org.pojoquery.integrationtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.pojoquery.DB;
import org.pojoquery.DbContext;
import org.pojoquery.DbContextBuilder;
import org.pojoquery.PojoQuery;
import org.pojoquery.annotations.Aggregate;
import org.pojoquery.annotations.FieldName;
import org.pojoquery.annotations.From;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Select;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.db.TestDatabaseProvider;
import org.pojoquery.schema.SchemaGenerator;

/**
 * Integration tests for {@code @From} aggregate projections fetched as JSON
 * documents ({@link PojoQuery#executeMultiSet(DataSource)}).
 *
 * <p>A projection whose type carries {@code @From} aggregates rows, so its
 * subquery stays a flat statement and is joined as a derived table. Two shapes
 * follow from that. A single projection is a one-row derived table joined on
 * the parent's key, and nests as a plain object. A collection of projections
 * wraps that derived table in a second one that aggregates the rows into an
 * array, correlated through {@code LATERAL}.</p>
 *
 * <p>What is worth running against real databases here is the nesting itself:
 * a derived table inside a derived table inside a document expression, with the
 * projection's own joins reusing aliases the outer query also uses. HSQLDB
 * accepting that says little about PostgreSQL and MySQL, which is why these
 * assertions compare {@code executeMultiSet} against {@code execute} on every
 * dialect rather than pinning SQL.</p>
 *
 * @see StatsQueriesIT for the same projections fetched as joined rows
 */
public class MultiSetStatsQueriesIT {

	@Table("mss_department")
	public static class Department {
		@Id
		public Long id;
		public String name;
		public String location;
	}

	@Table("mss_employee")
	public static class Employee {
		@Id
		public Long id;
		public String name;
		public BigDecimal salary;
		/**
		 * Named explicitly because a reference derives its column from the field
		 * name while a collection derives it from the parent's table name. Without
		 * this the two would disagree and the join would find nothing.
		 */
		@FieldName("mss_department_id")
		public Department department;
	}

	@Table("mss_sale")
	public static class Sale {
		@Id
		public Long id;
		public BigDecimal amount;
		@FieldName("mss_employee_id")
		public Employee employee;
	}

	// --- Source relations: the tables and joins the projections aggregate over ---

	public static class DepartmentWithEmployees extends Department {
		public List<Employee> employees;
	}

	public static class EmployeeWithSales extends Employee {
		public List<Sale> sales;
	}

	// --- A single aggregate projection, joined on the parent's key ---

	@From(DepartmentWithEmployees.class)
	public static class DepartmentEmployeeStats {
		@Id
		public Long id;

		@Aggregate("COUNT({employees.id})")
		public Long headcount;

		@Aggregate("SUM({employees.salary})")
		public BigDecimal totalSalary;

		@Aggregate("MAX({employees.salary})")
		public BigDecimal topSalary;
	}

	public static class DepartmentWithStats extends Department {
		public DepartmentEmployeeStats stats;
	}

	// --- A collection of aggregate projections, one per employee ---

	@From(EmployeeWithSales.class)
	public static class EmployeeSalesStats {
		@Id
		public Long id;
		public String name;

		/** Reaches the parent's key through the source relation's own join. */
		@Select("{department.id}")
		public Long departmentId;

		@Aggregate("COUNT({sales.id})")
		public Long saleCount;

		@Aggregate("SUM({sales.amount})")
		public BigDecimal totalSales;
	}

	public static class DepartmentWithEmployeeStats extends Department {
		@JoinCondition("{this.id} = {employeeStats.departmentId}")
		public List<EmployeeSalesStats> employeeStats;
	}

	/**
	 * Engineering has two employees who between them made three sales; Sales has
	 * one employee with one sale; Facilities has nobody. The empty department is
	 * the interesting one - it decides what an aggregate over no rows and a
	 * collection with no elements look like after a round trip through JSON.
	 */
	private void insertFixture(DataSource db) {
		SchemaGenerator.createTables(db, DepartmentWithEmployees.class, EmployeeWithSales.class, Sale.class);
		DB.runInTransaction(db, connection -> {
			Department engineering = department(connection, "Engineering", "Utrecht");
			Department sales = department(connection, "Sales", "Amsterdam");
			department(connection, "Facilities", "Rotterdam");

			Employee alice = employee(connection, engineering, "Alice", "50000");
			Employee bob = employee(connection, engineering, "Bob", "70000");
			Employee carol = employee(connection, sales, "Carol", "40000");

			sale(connection, alice, "100.00");
			sale(connection, alice, "250.00");
			sale(connection, bob, "70.00");
			sale(connection, carol, "500.00");
		});
	}

	/**
	 * The projection nests as a plain object: one row per parent, so nothing is
	 * aggregated away and the document carries the aggregates beside the root's
	 * own scalars.
	 */
	@Test
	public void testAggregateProjectionMatchesJoinedFetch() {
		DataSource db = TestDatabaseProvider.getDataSource();
		DbContext context = TestDatabaseProvider.getDbContext();
		insertFixture(db);

		List<DepartmentWithStats> joined = PojoQuery.build(context, DepartmentWithStats.class)
				.addOrderBy("{this.name}").execute(db);
		List<DepartmentWithStats> documents = PojoQuery.build(context, DepartmentWithStats.class)
				.addOrderBy("{this.name}").executeMultiSet(db);

		assertEquals(describeStats(joined), describeStats(documents));
		assertEquals(3, documents.size());

		DepartmentWithStats engineering = byName(documents, "Engineering");
		assertNotNull(engineering.stats);
		assertEquals(2L, engineering.stats.headcount);
		assertEquals(0, new BigDecimal("120000").compareTo(engineering.stats.totalSalary));
		assertEquals(0, new BigDecimal("70000").compareTo(engineering.stats.topSalary));

		DepartmentWithStats sales = byName(documents, "Sales");
		assertEquals(1L, sales.stats.headcount);
		assertEquals(0, new BigDecimal("40000").compareTo(sales.stats.topSalary));

		// COUNT over no rows is 0; SUM and MAX over no rows are NULL. The two
		// fetches must agree on that, whatever the dialect's JSON encoder does.
		DepartmentWithStats facilities = byName(documents, "Facilities");
		assertNotNull(facilities.stats, "a department with no employees still has a stats row");
		assertEquals(0L, facilities.stats.headcount);
		assertEquals(null, facilities.stats.totalSalary);
		assertEquals(null, facilities.stats.topSalary);
	}

	/**
	 * The collection is a derived table over a derived table: the projection
	 * groups per employee, and the wrapper gathers the employees of one
	 * department into an array.
	 *
	 * <p>Both levels reference an alias named {@code department} - the
	 * projection's own join to it, and the root. They do not collide because a
	 * derived table's aliases are not visible outside it, which is precisely what
	 * a real database has to agree with.</p>
	 */
	@Test
	public void testAggregateProjectionCollectionMatchesJoinedFetch() {
		DataSource db = TestDatabaseProvider.getDataSource();
		DbContext context = TestDatabaseProvider.getDbContext();
		insertFixture(db);

		List<DepartmentWithEmployeeStats> joined = PojoQuery.build(context, DepartmentWithEmployeeStats.class)
				.addOrderBy("{this.name}").execute(db);
		List<DepartmentWithEmployeeStats> documents = PojoQuery.build(context, DepartmentWithEmployeeStats.class)
				.addOrderBy("{this.name}").executeMultiSet(db);

		assertEquals(describeEmployeeStats(joined), describeEmployeeStats(documents));
		assertEquals(3, documents.size());

		DepartmentWithEmployeeStats engineering = byName(documents, "Engineering");
		assertEquals(2, engineering.employeeStats.size(),
				"a department must gather its own employees and no others");

		EmployeeSalesStats alice = employeeStatsNamed(engineering, "Alice");
		assertEquals(2L, alice.saleCount);
		assertEquals(0, new BigDecimal("350.00").compareTo(alice.totalSales));
		assertEquals(engineering.id, alice.departmentId);

		EmployeeSalesStats bob = employeeStatsNamed(engineering, "Bob");
		assertEquals(1L, bob.saleCount);
		assertEquals(0, new BigDecimal("70.00").compareTo(bob.totalSales));

		DepartmentWithEmployeeStats sales = byName(documents, "Sales");
		assertEquals(1, sales.employeeStats.size());
		assertEquals(0, new BigDecimal("500.00").compareTo(sales.employeeStats.get(0).totalSales));

		DepartmentWithEmployeeStats facilities = byName(documents, "Facilities");
		assertTrue(facilities.employeeStats == null || facilities.employeeStats.isEmpty(),
				"a department with no employees gathers an empty collection");
	}

	/**
	 * A condition on the root narrows the roots without touching what each one
	 * gathers - the collection of a filtered root stays whole, as it does for an
	 * ordinary collection.
	 */
	@Test
	public void testConditionOnRootKeepsProjectionsWhole() {
		DataSource db = TestDatabaseProvider.getDataSource();
		DbContext context = TestDatabaseProvider.getDbContext();
		insertFixture(db);

		List<DepartmentWithEmployeeStats> filtered = PojoQuery.build(context, DepartmentWithEmployeeStats.class)
				.addWhere("{this.location} = ?", "Utrecht")
				.executeMultiSet(db);

		assertEquals(List.of("Engineering"), filtered.stream().map(d -> d.name).toList());
		assertEquals(2, filtered.get(0).employeeStats.size(), "the collection must stay complete");

		List<DepartmentWithStats> withStats = PojoQuery.build(context, DepartmentWithStats.class)
				.addWhere("{this.location} = ?", "Utrecht")
				.executeMultiSet(db);
		assertEquals(1, withStats.size());
		assertEquals(2L, withStats.get(0).stats.headcount);
	}

	/**
	 * The projection's columns exist only inside its derived table, so a clause
	 * naming them is refused before the statement is built rather than failing in
	 * the database.
	 */
	@Test
	public void testProjectionColumnsAreNotAddressableFromOutside() {
		DbContext context = TestDatabaseProvider.getDbContext();
		Exception thrown = assertThrows(Exception.class,
				() -> PojoQuery.build(context, DepartmentWithEmployeeStats.class)
						.addWhere("{employeeStats.totalSales} > ?", new BigDecimal("100"))
						.toJsonSql());
		assertTrue(thrown.getMessage().contains("employeeStats"), thrown.getMessage());
	}

	/**
	 * Gathering a collection of projections needs {@code LATERAL}: the grouped
	 * form would have to group the subquery by the parent's key, and a
	 * {@code @JoinCondition} need not expose one. It says so rather than emitting
	 * SQL that cannot mean what was asked.
	 */
	@Test
	public void testProjectionCollectionRefusesTheGroupedForm() {
		DbContext grouped = new DbContextBuilder()
				.dialect(TestDatabaseProvider.getDbContext().getDialect())
				.lateralJoins(false)
				.build();
		UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
				() -> PojoQuery.build(grouped, DepartmentWithEmployeeStats.class).toJsonSql());
		assertTrue(thrown.getMessage().contains("without LATERAL"), thrown.getMessage());
	}

	// --- Fixture and comparison helpers ---

	private static Department department(java.sql.Connection connection, String name, String location) {
		Department department = new Department();
		department.name = name;
		department.location = location;
		PojoQuery.insert(connection, department);
		return department;
	}

	private static Employee employee(java.sql.Connection connection, Department department, String name,
			String salary) {
		Employee employee = new Employee();
		employee.name = name;
		employee.salary = new BigDecimal(salary);
		employee.department = department;
		PojoQuery.insert(connection, employee);
		return employee;
	}

	private static void sale(java.sql.Connection connection, Employee employee, String amount) {
		Sale sale = new Sale();
		sale.amount = new BigDecimal(amount);
		sale.employee = employee;
		PojoQuery.insert(connection, sale);
	}

	private static <T extends Department> T byName(List<T> departments, String name) {
		return departments.stream().filter(d -> name.equals(d.name)).findFirst()
				.orElseThrow(() -> new AssertionError("No department named " + name + " in " + departments.size()
						+ " results"));
	}

	private static EmployeeSalesStats employeeStatsNamed(DepartmentWithEmployeeStats department, String name) {
		return department.employeeStats.stream().filter(s -> name.equals(s.name)).findFirst()
				.orElseThrow(() -> new AssertionError("No stats for employee " + name));
	}

	/**
	 * Renders the graph as text so the joined and document fetches can be
	 * compared whole. Decimals go through {@link BigDecimal#compareTo} rather
	 * than equals - the two paths may disagree on scale, which is not a
	 * difference in the value.
	 */
	private static String describeStats(List<DepartmentWithStats> departments) {
		StringBuilder out = new StringBuilder();
		for (DepartmentWithStats department : departments) {
			assertNotNull(department.id);
			out.append(department.name).append('|').append(department.location).append('|');
			if (department.stats == null) {
				out.append("-");
			} else {
				out.append(department.stats.headcount).append(',')
						.append(plain(department.stats.totalSalary)).append(',')
						.append(plain(department.stats.topSalary));
			}
			out.append('\n');
		}
		return out.toString();
	}

	private static String describeEmployeeStats(List<DepartmentWithEmployeeStats> departments) {
		StringBuilder out = new StringBuilder();
		for (DepartmentWithEmployeeStats department : departments) {
			assertNotNull(department.id);
			out.append(department.name).append('|');
			List<EmployeeSalesStats> stats = department.employeeStats == null ? List.of()
					: department.employeeStats;
			out.append(stats.stream()
					.map(s -> s.name + ":" + s.saleCount + ":" + plain(s.totalSales))
					.sorted().toList());
			out.append('\n');
		}
		return out.toString();
	}

	/** A decimal's value without its scale, so 350 and 350.00 read alike. */
	private static String plain(BigDecimal value) {
		return value == null ? "-" : value.stripTrailingZeros().toPlainString();
	}
}
