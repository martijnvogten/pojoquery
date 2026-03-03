package org.pojoquery.pipeline.querytree;

import org.junit.Assert;
import org.junit.Test;
import org.pojoquery.DbContext;
import org.pojoquery.TestUtils;
import org.pojoquery.annotations.Embedded;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Link;
import org.pojoquery.annotations.SubClasses;
import org.pojoquery.annotations.Table;
import org.pojoquery.pipeline.CustomizableQueryBuilder;
import org.pojoquery.pipeline.CustomizableQueryBuilder.DefaultSqlQuery;
import org.pojoquery.pipeline.querytree.transforms.BasicTableTransform;
import org.pojoquery.pipeline.querytree.transforms.CollectionTransform;
import org.pojoquery.pipeline.querytree.transforms.CreateRootTransform;
import org.pojoquery.pipeline.querytree.transforms.EmbeddedTransform;
import org.pojoquery.pipeline.querytree.transforms.EntityReferenceTransform;
import org.pojoquery.pipeline.querytree.transforms.FixPointTransform;
import org.pojoquery.pipeline.querytree.transforms.JoinConditionTransform;
import org.pojoquery.pipeline.querytree.transforms.JoinTableTransform;
import org.pojoquery.pipeline.querytree.transforms.QueryTreePipeline;
import org.pojoquery.pipeline.querytree.transforms.SubclassExpansionTransform;
import org.pojoquery.pipeline.querytree.transforms.SuperclassTableTransform;

public class TestBasicTableTransform {

	@Table("person")
	static class Person {
		@Id
		Long id;
		String firstName;
		String lastName;
	}

	static class Author extends Person {
		String penName;
	}

	@Table("book")
	static class Book {
		Long id;
		String title;
		Author author;
	}

	@Test
	public void testBasics() {
		BasicTableTransform transform = new BasicTableTransform();
		{
			QueryTree result = transform.apply(new CreateRootTransform().apply(QueryTree.of(Person.class)));
			System.out.println("Result: " + result);

			Assert.assertEquals(result, transform.apply(result)); // idempotency
		}
		
		{
			QueryTree result = transform.apply(new CreateRootTransform().apply(QueryTree.of(Author.class)));
			System.out.println("Result: " + result);

			Assert.assertEquals(transform.apply(new CreateRootTransform().apply(QueryTree.of(Author.class))), transform.apply(transform.apply(result))); // idempotency
		}

		{
			QueryTree result = transform.apply(new CreateRootTransform().apply(QueryTree.of(Book.class)));
			System.out.println("Result: " + result);

			Assert.assertEquals(result, transform.apply(result)); // idempotency

			EntityReferenceTransform ert = new EntityReferenceTransform();
			QueryTree withEntities = ert.apply(result);
			System.out.println("Result: " + withEntities);

			Assert.assertEquals(withEntities, ert.apply(withEntities)); // idempotency
		}
	}

	@Test
	public void testRunToFixPoint() {
		QueryTreePipeline pipeline = QueryTreePipeline.empty()
		.with(new CreateRootTransform())
		.with(new FixPointTransform(
			new SuperclassTableTransform(), 
			new SubclassExpansionTransform()))
		.with(new BasicTableTransform())
		.with(new EntityReferenceTransform())
		.with(new JoinConditionTransform())
		;

		QueryTree result = pipeline.runToFixPoint(QueryTree.of(Book.class)); // Should not throw and should terminate
		System.out.println("SQL: " + queryTreeToSql(result));
		
		// System.out.println("Result: " + result);
	}

	@Table("room")
	@SubClasses({BedRoom.class})
	static class Room {
		@Id
		Long id;
		Float area;
	}
	
	@Table("bedroom")
	static class BedRoom extends Room {
		Integer numberOfBeds;
	}
	
	@Table("house")
	static class House {
		@Id
		Long id;
		String address;
		Room[] rooms;
	}

	@Test
	public void testWithInheritance() {
		QueryTreePipeline pipeline = QueryTreePipeline.empty()
				.with(new CreateRootTransform())
				.with(new SuperclassTableTransform())
				.with(new SubclassExpansionTransform())
				.with(new BasicTableTransform())
				.with(new EntityReferenceTransform());

		QueryTree result = pipeline.runToFixPoint(QueryTree.of(BedRoom.class));
		System.out.println("SQL: " + queryTreeToSql(result));
		
		System.out.println("Result: " + result);
	}

	@Test
	public void testWithInheritanceDeeper() {
		QueryTreePipeline pipeline = QueryTreePipeline.empty()
				.with(new CreateRootTransform())
				.with(new SuperclassTableTransform())
				.with(new SubclassExpansionTransform())
				.with(new BasicTableTransform())
				.with(new EntityReferenceTransform())
				.with(new CollectionTransform())
				.with(new JoinConditionTransform())
				;

		QueryTree result = pipeline.runToFixPoint(QueryTree.of(House.class));
		System.out.println("SQL: " + queryTreeToSql(result));
		
		System.out.println("Result: " + result);
	}

	private String queryTreeToSql(QueryTree tree) {
		DefaultSqlQuery query = new DefaultSqlQuery(DbContext.getDefault());
		CustomizableQueryBuilder.applyQueryTreeToQuery(query, tree);
		return TestUtils.norm(query.toStatement().getSql());
	}

	@Table("user")
	static class User {
		@Id
		Long id;
		String email;
	}

	@Table("usergroup")
	static class UserGroup {
		@Id
		Long id;
		String name;
	}

	static class UserGroupWithUsers extends UserGroup {
		@Link(linktable = "user_usergroup")
		User[] users;
	}

	@Test
	public void testJoinTable() {
		QueryTreePipeline pipeline = QueryTreePipeline.empty()
				.with(new CreateRootTransform())
				.with(new SuperclassTableTransform())
				.with(new SubclassExpansionTransform())
				.with(new BasicTableTransform())
				.with(new EntityReferenceTransform())
				.with(new JoinTableTransform())
				.with(new JoinConditionTransform())
				;
		QueryTree result = pipeline.runToFixPoint(QueryTree.of(UserGroupWithUsers.class));
		System.out.println("SQL: " + queryTreeToSql(result));
	}

	@Table("person")
	static class PersonWithEmbedded {
		@Id
		Long id;
		String name;
		
		@Embedded
		Address address;
	}

	static class Address {
		String street;
		String city;
		@Embedded(prefix = "country_")
		Country country;
		AddressType type;
	}

	@Table("addresstype")
	static class AddressType {
		@Id
		Long id;
		String name;
	}

	static class Country {
		String name;
		String shortCode;
	}

	@Table("order")
	static class Order {
		@Id
		Long id;
		String orderNumber;
		PersonWithEmbedded customer;
	}

	@Test
	public void testEmbedded() {
		QueryTreePipeline pipeline = QueryTreePipeline.empty()
				.with(new CreateRootTransform())
				.with(new SuperclassTableTransform())
				.with(new SubclassExpansionTransform())
				.with(new BasicTableTransform())
				.with(new EmbeddedTransform())
				.with(new EntityReferenceTransform())
				.with(new JoinTableTransform())
				.with(new JoinConditionTransform())
				;

		{
			QueryTree result = pipeline.runToFixPoint(QueryTree.of(PersonWithEmbedded.class));
			// System.out.println("Tree: " + result.toString());
			System.out.println("SQL: " + queryTreeToSql(result));
		}

		{
			QueryTree result = pipeline.runToFixPoint(QueryTree.of(Order.class));
			// System.out.println("Tree: " + result.toString());
			System.out.println("SQL: " + queryTreeToSql(result));
		}
	}

}
