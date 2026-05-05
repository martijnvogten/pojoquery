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
