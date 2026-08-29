package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;
import org.pojoquery.annotations.Aggregate;
import org.pojoquery.annotations.From;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.JoinCondition;
import org.pojoquery.annotations.Select;
import org.pojoquery.annotations.Table;
import org.pojoquery.integrationtest.UseDialect;
import org.pojoquery.pipeline.AbstractQueryTree.RootNode;
import org.pojoquery.schema.SchemaGenerator;
import org.pojoquery.util.RecordIndenter;

/**
 * Tests for @From annotation support.
 * 
 * @From allows defining a projection/aggregate query class that uses
 * another class's table structure (FROM + JOINs) while selecting only 
 * the fields defined in the projection class.
 */
@UseDialect(Dialect.HSQLDB)
public class TestFromAnnotation {

	// --- Domain classes ---
	
	@Table("team")
	static class Team {
		@Id Long id;
		String name;
	}
	
	@Table("member")
	static class Member {
		@Id Long id;
		String name;
		BigDecimal salary;
		Team team;
	}
	
	// Source class defining table structure (team + members join)
	static class TeamWithMembers extends Team {
		java.util.List<Member> members;
	}
	
	// Projection class using @From
	// Uses TeamWithMembers's structure (team LEFT JOIN member) but only selects aggregates
	@From(TeamWithMembers.class)
	static class TeamStats {
		Long id;
		String name;
		
		@Aggregate("COUNT({members.id})")
		Long memberCount;
		
		@Aggregate("SUM({members.salary})")
		BigDecimal totalSalary;
	}

	// --- Tests ---

	@Test
	public void testFromAnnotationQueryTreeHasSourceJoins() {
		// The query tree for TeamStats should have the LEFT JOIN to members
		// even though TeamStats itself doesn't declare members
		// TransformPipeline pipeline = TransformPipeline.defaultPipeline().prepend(ProcessFromAnnotationTransform.class);
		// RootNode tree = AQTTransformer.buildQueryTreeForType(new ReflectionTypeModel(TeamStats.class), pipeline);

		RootNode tree = PojoQuery.build(TeamStats.class).getTree();

		System.out.println(RecordIndenter.indent(tree.toString()));
		
		// Should use "team" as the FROM table (from TeamWithMembers via @From)
		assertEquals("team", tree.tableInfo().tableName());
		
		// Should have children including the aggregate fields AND the join
		assertNotNull(tree.children());
		assertTrue(tree.children().size() > 0, "Query tree should have children");
		assertEquals(5, tree.children().size(), "Query tree should have 5 children");
		
		// The {members.*} references in @Aggregate should cause members join to be included
		// Check that the tree structure recognizes TeamWithMembers as source
		assertEquals("org.pojoquery.TestFromAnnotation$TeamStats", tree.type().getQualifiedName());

		System.out.println(RecordIndenter.indent(tree.toString()));
	}

	@Test
	public void testFromAnnotationExecutesQuery() {
		JDBCDataSource ds = new JDBCDataSource();
		ds.setURL("jdbc:hsqldb:mem:testfrom");
		
		SchemaGenerator.createTables(ds, TeamWithMembers.class, Member.class);
		
		DB.withConnection(ds, c -> {
			// Insert test data
			Team team = new Team();
			team.name = "Alpha";
			PojoQuery.insert(c, team);
			
			Member m1 = new Member();
			m1.name = "Alice";
			m1.salary = new BigDecimal("50000");
			m1.team = team;
			PojoQuery.insert(c, m1);
			
			Member m2 = new Member();
			m2.name = "Bob";
			m2.salary = new BigDecimal("60000");
			m2.team = team;
			PojoQuery.insert(c, m2);
			
			// Query using @From projection
			List<TeamStats> stats = PojoQuery.build(TeamStats.class)
				.addWhere("{this.name} = ?", "Alpha")
				.execute(c);
			
			assertEquals(1, stats.size());
			assertEquals(2L, stats.get(0).memberCount);
			assertEquals(0, new BigDecimal("110000").compareTo(stats.get(0).totalSalary));
		});
	}

	/**
	 * A projection that declares a field the source relation also has must not
	 * carry both nodes: they join the same table under the same alias, which no
	 * database accepts.
	 */
	@From(TeamWithMembers.class)
	static class TeamWithMembersAndCount {
		@Id Long id;
		String name;
		java.util.List<Member> members;
	}

	@Test
	public void testDeclaredFieldReplacesSourceJoin() {
		String sql = PojoQuery.build(TeamWithMembersAndCount.class).toSql();
		assertEquals(1, countOccurrences(sql, "AS \"members\""),
				"The members table must be joined exactly once:\n" + sql);
	}

	private static int countOccurrences(String haystack, String needle) {
		int count = 0;
		for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
			count++;
		}
		return count;
	}

	/**
	 * A {@code @From} aggregate projection nested under an ordinary root: the
	 * root's own scalars plus the aggregates, fetched as one JSON document per
	 * root and hydrated back into the nested projection object.
	 */
	@Table("department")
	static class Department {
		@Id Long id;
		String name;
		String location;
	}

	@Table("employee")
	static class Employee {
		@Id Long id;
		String name;
		BigDecimal salary;
		Department department;
	}

	static class DepartmentWithEmployees extends Department {
		List<Employee> employees;
	}

	@From(DepartmentWithEmployees.class)
	static class EmployeeStats {
		@Id Long id;

		@Aggregate("COUNT({employees.id})")
		Long headcount;

		@Aggregate("AVG({employees.salary})")
		BigDecimal avgSalary;

		@Aggregate("MAX({employees.salary})")
		BigDecimal topSalary;
	}

	static class DepartmentWithStats extends Department {
		EmployeeStats stats;
	}

	@Test
	public void testAggregateProjectionUnderNormalRootAsJson() {
		JDBCDataSource ds = new JDBCDataSource();
		ds.setURL("jdbc:hsqldb:mem:testdeptstats");

		SchemaGenerator.createTables(ds, DepartmentWithEmployees.class, Employee.class);

		DB.withConnection(ds, c -> {
			Department engineering = new Department();
			engineering.name = "Engineering";
			engineering.location = "Utrecht";
			PojoQuery.insert(c, engineering);

			Department sales = new Department();
			sales.name = "Sales";
			sales.location = "Amsterdam";
			PojoQuery.insert(c, sales);

			insertEmployee(c, engineering, "Alice", "50000");
			insertEmployee(c, engineering, "Bob", "70000");
			insertEmployee(c, sales, "Carol", "40000");

			List<DepartmentWithStats> departments = PojoQuery.build(DepartmentWithStats.class)
				.addOrderBy("{department.name}")
				.executeMultiSet(c);

			assertEquals(2, departments.size());

			DepartmentWithStats first = departments.get(0);
			assertEquals("Engineering", first.name);
			assertEquals("Utrecht", first.location);
			assertNotNull(first.stats);
			assertEquals(2L, first.stats.headcount);
			assertEquals(0, new BigDecimal("60000").compareTo(first.stats.avgSalary));
			assertEquals(0, new BigDecimal("70000").compareTo(first.stats.topSalary));

			DepartmentWithStats second = departments.get(1);
			assertEquals("Sales", second.name);
			assertEquals(1L, second.stats.headcount);
			assertEquals(0, new BigDecimal("40000").compareTo(second.stats.topSalary));
		});
	}

	private static Employee insertEmployee(java.sql.Connection c, Department department, String name, String salary) {
		Employee employee = new Employee();
		employee.name = name;
		employee.salary = new BigDecimal(salary);
		employee.department = department;
		PojoQuery.insert(c, employee);
		return employee;
	}

	/**
	 * A collection of an aggregate projection: per-employee sales figures,
	 * gathered under the department they belong to. The projection reaches the
	 * parent's key through its own source relation, and the collection relates
	 * the two on it.
	 */
	@Table("sale")
	static class Sale {
		@Id Long id;
		BigDecimal amount;
		Employee employee;
	}

	static class EmployeeWithSales extends Employee {
		List<Sale> sales;
	}

	@From(EmployeeWithSales.class)
	static class EmployeeSalesStats {
		@Id Long id;
		String name;

		@Select("{department.id}")
		Long departmentId;

		@Aggregate("COUNT({sales.id})")
		Long saleCount;

		@Aggregate("SUM({sales.amount})")
		BigDecimal totalSales;
	}

	static class DepartmentWithEmployeeStats extends Department {
		@JoinCondition("{this.id} = {employeeStats.departmentId}")
		List<EmployeeSalesStats> employeeStats;
	}

	@Test
	public void testAggregateProjectionCollectionAsJson() {
		JDBCDataSource ds = new JDBCDataSource();
		ds.setURL("jdbc:hsqldb:mem:testdeptsalestats");

		SchemaGenerator.createTables(ds, DepartmentWithEmployees.class, EmployeeWithSales.class, Sale.class);

		DB.withConnection(ds, c -> {
			Department engineering = new Department();
			engineering.name = "Engineering";
			engineering.location = "Utrecht";
			PojoQuery.insert(c, engineering);

			Department sales = new Department();
			sales.name = "Sales";
			sales.location = "Amsterdam";
			PojoQuery.insert(c, sales);

			Employee alice = insertEmployee(c, engineering, "Alice", "50000");
			Employee bob = insertEmployee(c, engineering, "Bob", "70000");
			Employee carol = insertEmployee(c, sales, "Carol", "40000");

			insertSale(c, alice, "100.00");
			insertSale(c, alice, "250.00");
			insertSale(c, bob, "70.00");
			insertSale(c, carol, "500.00");

			List<DepartmentWithEmployeeStats> departments = PojoQuery.build(DepartmentWithEmployeeStats.class)
				.addOrderBy("{department.name}")
				.executeMultiSet(c);

			assertEquals(2, departments.size());

			DepartmentWithEmployeeStats engineeringRow = departments.get(0);
			assertEquals("Engineering", engineeringRow.name);
			assertEquals(2, engineeringRow.employeeStats.size(),
					"Engineering must gather exactly its own two employees");

			EmployeeSalesStats aliceStats = engineeringRow.employeeStats.stream()
					.filter(s -> "Alice".equals(s.name)).findFirst().orElseThrow();
			assertEquals(2L, aliceStats.saleCount);
			assertEquals(0, new BigDecimal("350.00").compareTo(aliceStats.totalSales));

			EmployeeSalesStats bobStats = engineeringRow.employeeStats.stream()
					.filter(s -> "Bob".equals(s.name)).findFirst().orElseThrow();
			assertEquals(1L, bobStats.saleCount);
			assertEquals(0, new BigDecimal("70.00").compareTo(bobStats.totalSales));

			DepartmentWithEmployeeStats salesRow = departments.get(1);
			assertEquals("Sales", salesRow.name);
			assertEquals(1, salesRow.employeeStats.size());
			assertEquals(0, new BigDecimal("500.00").compareTo(salesRow.employeeStats.get(0).totalSales));
		});
	}

	private static void insertSale(java.sql.Connection c, Employee employee, String amount) {
		Sale sale = new Sale();
		sale.amount = new BigDecimal(amount);
		sale.employee = employee;
		PojoQuery.insert(c, sale);
	}

	// --- Join Pruning Test ---

	@Table("product")
	static class Product {
		@Id Long id;
		String name;
	}
	
	@Table("review")
	static class Review {
		@Id Long id;
		Product product;
		Integer rating;
	}
	
	@Table("photo")
	static class Photo {
		@Id Long id;
		Product product;
		String url;
	}
	
	// Source has multiple to-many joins
	static class ProductWithAll extends Product {
		List<Review> reviews;
		List<Photo> photos;
	}
	
	// Only uses reviews - photos join should be pruned to prevent row multiplication
	@From(ProductWithAll.class)
	static class ProductReviewStats {
		Long id;
		
		@Aggregate("COUNT({reviews.id})")
		Long reviewCount;
		
		@Aggregate("AVG(CAST({reviews.rating} AS DECIMAL))")
		BigDecimal avgRating;
		// Note: no reference to {photos.*} - that join should be pruned
	}
	
	@Test
	public void testJoinPruningPreventsRowMultiplication() {
		JDBCDataSource ds = new JDBCDataSource();
		ds.setURL("jdbc:hsqldb:mem:testjoinprune");
		
		SchemaGenerator.createTables(ds, ProductWithAll.class, Review.class, Photo.class);
		
		DB.withConnection(ds, c -> {
			Product p = new Product();
			p.name = "Widget";
			PojoQuery.insert(c, p);
			
			// 2 reviews
			for (int i = 0; i < 2; i++) {
				Review r = new Review();
				r.product = p;
				r.rating = 4 + i; // 4 and 5
				PojoQuery.insert(c, r);
			}
			
			// 3 photos (should not affect counts if pruned correctly)
			for (int i = 0; i < 3; i++) {
				Photo photo = new Photo();
				photo.product = p;
				photo.url = "http://example.com/" + i + ".jpg";
				PojoQuery.insert(c, photo);
			}
			
			// Without pruning: 2 reviews × 3 photos = 6 rows → COUNT=6
			// With pruning: photos join removed → COUNT=2
			var stats = PojoQuery.build(ProductReviewStats.class)
				.execute(c);
			
			assertEquals(1, stats.size());
			assertEquals(2L, stats.get(0).reviewCount, 
				"Without join pruning, reviewCount would be 6 (2×3)");
		});
	}
}
